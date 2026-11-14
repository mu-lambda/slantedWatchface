# Architecture

The goals and design choices are in [README.md](README.md). This file describes how the
code is organized and how the pieces fit together.

## Directory layout

```
new-wearOS/
├── README.md, SPEC.md, AGENTS.md     Design choices, architecture, notes for coding agents
├── MODULE.bazel, MODULE.bazel.lock   Bazel module: dependencies and external repositories
├── .bazelversion                    Pinned Bazel version
├── tools/android/
│   ├── sdk.bzl                       Module extension: Android build-tools and local SDK
│   └── wff.bzl                       Rules wff_apk, wff_install; macro res_files
└── watchface/
    ├── BUILD.bazel                   Generation, packaging, installation, schema test
    ├── AndroidManifest.xml           Code-free app declaring WFF version 1
    ├── fonts/*.ttf                   The six fonts, named by resource name
    ├── res/                          Hand-written resources (watch face info, preview, app name)
    ├── generator/generate.py         Writes watchface.xml, its strings and the font icons
    ├── generator/defs.bzl            Rule watchface_resources, which runs the generator
    ├── validate_schema.py            XSD 1.1 validation (the schema test)
    ├── testdata/                     Reference screenshots of the original face, release 1.16
    └── tools/                        Comparison scripts against the original (not part of the build)
```

## Build graph

```
fonts/*.ttf ──► watchface_resources :watchface ──► raw/watchface.xml
                (runs //watchface/generator:generate) values/generated_strings.xml
                                                      drawable/font_icon_<font>.xml (x6)
                                                      font/<font>.ttf (the input fonts)
                                                      help.txt (output group help)
                                                              │ WffResourcesInfo
                                                              ▼
res/** + AndroidManifest.xml ───────────────────────► wff_apk :slanted ──► slanted.apk
                                                              │
                                                              └──► wff_install :install (bazel run)
:watchface_xml (output group of :watchface) + @wff_spec//:v1 ──► py_test :watchface_schema_test
```

### External dependencies (MODULE.bazel)

| Repository | What | Source |
|---|---|---|
| `rules_python` | Python 3.12 toolchain for the generator and the test | Bazel Central Registry |
| `@slanted_pypi` | `fonttools` (generator) and `xmlschema` (test) | pip, from `generator/requirements_lock.txt` |
| `@android_build_tools` | `aapt2`, `zipalign`, `apksigner.jar` | build-tools 36.1 download, pinned by SHA-256 |
| `@local_android_sdk` | `android.jar` (newest installed platform), `adb`, debug keystore | local SDK, via symlinks |
| `@wff_spec` | Official WFF XSD schemas, filegroups `v1`…`v4` | `google/watchface` at a pinned commit |

### Packaging rules (tools/android/wff.bzl)

- **`wff_apk`** takes a manifest, a map from file to resource path (such as
  `raw/watchface.xml`) in `resources`, and targets in `resource_sets` that provide
  `WffResourcesInfo` (a map from resource path to file). It symlinks every file into a
  staged `res/` tree, then runs
  `aapt2 compile --dir`, `aapt2 link` (against `android.jar`, with SDK and version
  attributes), `zipalign -p 4` and `apksigner sign`. It signs with the debug keystore by
  default; the `keystore*` and `key_alias` attributes are there for a release key.
- **`res_files(glob(["res/**"]))`** maps hand-written resources to their path under `res/`.
- **`wff_install`** is a `bazel run` script that runs `adb install -r` and then broadcasts
  `com.google.android.wearable.app.DEBUG_SURFACE set-watchface` to make the face active.
  This works on emulators and debuggable builds. Set `ANDROID_SERIAL` to pick a device.
  With `-- --help` it prints its usage, followed by the text file in its `help` attribute.

### Generation rule (watchface/generator/defs.bzl)

**`watchface_resources`** runs the generator on a list of fonts. A font's file name is its
resource name (`fonts/bebas_kai.ttf` is `bebas_kai`), so the BUILD file just globs
`fonts/*.ttf`. The rule passes each font and its icon output to the generator, and returns
everything as `WffResourcesInfo`: `raw/watchface.xml`, `values/generated_strings.xml`, one
`drawable/font_icon_<font>.xml` per font, and the fonts themselves as `font/<font>.ttf`. The
output group `watchface_xml` holds just `watchface.xml`, for the schema test.

`--define=WFF_DEFAULTS=id=value,...` is passed to the generator as `--defaults` and changes
the settings' default values. It is meant for screenshots; without it, the generator's
defaults apply. The generator's `settings()` lists every setting with its values and
default, and `help_text()` turns that list into `help.txt`. The BUILD file passes it to
`wff_install`, so `bazel run //watchface:install -- --help` always shows the current
settings.

## The generator (watchface/generator/generate.py)

A single script with no state between runs. Its inputs are the font files plus a few
command-line arguments. Its outputs are `watchface.xml`, `generated_strings.xml` and one
vector drawable per font.

### Tables at the top

- `FONTS`: a `FontConfig` for each font, holding the resource name, display name, size ratio
  and the horizontal squeeze of the hours (one and two digits), minutes and seconds. These
  are the original face's values. Each resource name must have a file
  `watchface/fonts/<resource>.ttf`, and the reverse; the generator fails otherwise. The
  fonts are `limelight` (the default), `sweet_hipster`, `grafik_text`, `fenwick_woodtype`,
  `ordinary` and `bebas_kai`.
- `THEMES`: the color schemes, as (id, display name, base color). The ids are `green` (the
  default), `red`, `orange`, `yellow`, `cyan`, `blue`, `magenta`, `white` and `light_gray`.
  `theme_colors()` expands each base color into the five colors of the scheme, as the
  original did: hours, seconds and AM/PM in the base color, minutes in a gray with the base
  color's brightness, and the date in the original's date yellow at that brightness. The
  five colors are indexed by `HOURS, MINUTES, SECONDS, AM_PM, DATE`; in the XML they are
  `[CONFIGURATION.theme.N]`. Ambient mode with plain colors uses `AMBIENT_COLORS` instead.
- `CALIBRATION`: a `Calibration` for each font. These are offsets in canvas pixels for the
  one-digit and two-digit hours (x), the hours (y), the seconds (x) and the minutes (y),
  fitted to screenshots of the original.
- `DEVICE_*`: metrics assumed for the system font (Roboto), which the date and AM/PM use.

### Pipeline

1. **`Metrics`** loads a font with fontTools. It records the ascent and descent, and for each
   digit its advance and ink bounds. It can also draw a string through a pen (used for the
   icons).
2. **`layout(font, metrics, is24h)`** reproduces the original face's layout math and
   returns a `Layout`. That is one `Text` each for the hours (two digits and one digit),
   minutes, seconds, date and AM/PM. A `Text` holds the pen position on the baseline (`x`,
   `baseline`), font family, size, horizontal scale, color index, and the content, which is
   a WFF source such as `[MINUTE_Z]` or a template with parameters.
3. **`watchface_xml()`** writes the document with the small `Xml` helper, which handles
   indentation and attribute escaping.

### Values that change at runtime: `E`

Positions that depend on the time are `E` values. An `E` pairs a WFF expression string with
its value at the preview time 10:08:32 (`PREVIEW`, which must match the `PREVIEW_TIME`
metadata). Arithmetic on `E` builds both at once and folds constants. The static XML
attributes get the preview value, and a `Transform` with the expression keeps them correct
at runtime.

The ink measurements become expressions like this:

- **`ink_width()`**: the width of a two-digit number is (advance − left bearing) of the tens
  digit plus the right ink edge of the ones digit. Each part is a `lookup()` over the digit,
  `floor(v/10)` or `(v%10)`. Kerning is ignored.
- **`ink_height()` / `by_value()`**: the ink height of each possible value (0–59, 0–23 or
  1–12), grouped into runs that differ by at most `TOLERANCE` (1.5 px). The largest group
  becomes the default and the others become nested `cond ? a : b` choices.
- **`lookup()`** returns a single constant when every candidate is within `TOLERANCE`, which
  keeps the XML small.

### Document structure

```
WatchFace 450×450
├── Metadata CLOCK_TYPE, PREVIEW_TIME
├── UserConfigurations
│   ├── ColorConfiguration theme        (9 options × 5 colors)
│   ├── ListConfiguration font          (6 options, each with icon font_icon_<font>)
│   ├── BooleanConfiguration is24h
│   └── BooleanConfiguration colorfulAmbient
└── Scene
    ├── Group "slanted" (angle 20°, pivot at the center)
    │   └── ListConfiguration font → Group <font>
    │       └── BooleanConfiguration is24h → Group h24 | h12
    │           ├── hours: Condition [HOUR_…] > 9 → two-digit | one-digit PartText
    │           ├── minutes, seconds (hidden in ambient), date, AM/PM (12h only)
    │           └── BooleanConfiguration colorfulAmbient, option FALSE only:
    │               Group "time_ambient_plain" (alpha 0, AMBIENT variant alpha 255)
    │               ├── PartDraw black rectangle covering the canvas
    │               └── the same texts in white and light gray, without seconds
    └── ComplicationSlot × 2
```

**Text.** `emit_text()` writes one `PartText` per `Text`. The box is exactly one line high
(ascent + descent) and starts at the pen position, so its top is `baseline − ascent`. It is
scaled horizontally around its left edge (`pivotX="0"`, `scaleX`). The `x` and `y`
attributes hold the preview values, and `Transform` children hold the expressions when
these vary.

**Ambient.** The colored texts stay visible in ambient mode; only the seconds hide, through
an `AMBIENT` variant on alpha. For plain ambient mode, an overlay group fades in only in
ambient mode. It covers the colored face with a black rectangle and redraws the time in
white. Because the overlay draws over the colored face instead of replacing it, the
colored layout appears only once in the XML.

**Complications.** `ComplicationSlot` must be a direct child of `Scene`, and its geometry is
static. So the slots can't be inside the tilted group or depend on the font. For each font,
`complication_area()` computes the original's area for the larger-date layout: from below
the date to halfway between the hours' baseline and the bottom edge, and from the minutes
to the right edge. The generator then keeps only the part that every font has in common.
That part is split into `COMPLICATION_SLOTS` rows. The center of each row is rotated about
the canvas center by `ANGLE`, and the slot is placed there with `angle=ANGLE` about its own
center. Each slot accepts `SHORT_TEXT` and starts with a system provider
(`COMPLICATION_DEFAULTS`). It shows the monochrome icon, tinted in the minutes' color, then
`TEXT TITLE` in the hours' color. The text box stops where the round screen edge crosses
the row and ends in an ellipsis when the text is too long.

**Strings and icons.** `strings_xml()` writes all setting labels and option names.
`font_icon_xml()` draws "12" in each font, squeezed like the hours, as a white-filled
vector path centered in a square viewport.

### WFF renderer behavior to know about

Seen on Wear OS 5. The schema allows all of these, but the renderer behaves differently:

- Custom fonts with a fractional `size` (even `139.0`) are drawn at a small default size, so
  the generator rounds font sizes to whole numbers.
- `[AMPM_STRING_ENG]` and `[AMPM_STRING_SHORT]` render empty; `[AMPM_STRING]` works.
- `pivotX` and `pivotY` are fractions of the element's size (0..1), not pixels.

## Verification

- **Schema:** `//watchface:watchface_schema_test` validates the generated XML against the
  WFF version 1 XSD with `xmlschema` (XSD 1.1). It prints the first 20 errors.
- **Look:** `watchface/tools/` (see its README) puts the original 1.16 APK on a Wear OS 3
  emulator and this face on a Wear OS 5 emulator in the same settings. It sets both clocks,
  screenshots both, and measures each element's bounding box in the untilted frame.
  `offsets.py` prints the differences, which is how `CALIBRATION` was fitted (at 04:59,
  10:08, 12:20 and 23:59). `testdata/` keeps two reference screenshots of the original.

## Common changes

- **Add a font:** put the TTF in `watchface/fonts/<resource>.ttf` and add a `FontConfig` to
  `FONTS` in the generator. Then fit a `Calibration` entry with the comparison tools.
  Check that `complication_area()` for the new font doesn't shrink the shared complication
  area too much.
- **Add a theme:** append to `THEMES`; the five colors are derived.
- **Add a setting:** declare it in `UserConfigurations` and add it to `settings()` (which
  makes it work with `WFF_DEFAULTS` and lists it in `--help`). Then branch on it in `Scene`
  and add its label to `strings_xml()`. Every option of a setting that wraps the time
  multiplies that part of the XML.
- **Release:** raise `version_code` and `version_name` in `watchface/BUILD.bazel`, and pass a
  release `keystore` to `wff_apk`.
