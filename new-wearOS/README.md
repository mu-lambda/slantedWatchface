# Slanted! for the Watch Face Format

Slanted! rebuilt as a [Watch Face Format](https://developer.android.com/training/wearables/wff)
(WFF) watch face. Google Play requires WFF for watch faces since January 2026, and Wear OS 5
and later no longer run watch faces that contain code, so the original face (last release
1.16) cannot be updated as it is. This directory is self-contained: it shares no code, build
files or resources with the original, so the original can be retired without touching it.

How the code is organized is described in [SPEC.md](SPEC.md).

## Goals

- **Look like the original.** Big tilted hours, smaller minutes and seconds beside them, and
  the date below. Each font keeps the proportions and squeeze it had in the original. The
  layout is checked against screenshots of release 1.16 and matches it to within a few pixels
  for every font.
- **No code on the watch.** The APK contains only resources (`android:hasCode="false"`). The
  system's WFF renderer draws the face, and the system's editor handles the settings, so
  there is no settings activity and no companion app.
- **Run on as many watches as possible.** The face uses WFF version 1 and needs Wear OS 4
  (API 33) or later.
- **Builds that are reproducible and checked.** Bazel builds the face from source. Every
  build checks the generated face against Google's official WFF schema.

## Design choices

**The face description is generated, not written by hand.** The original measured the text
it was about to draw (`Paint.getTextBounds()`) and placed everything relative to that. For
example, the minutes start just right of the hours' ink, which is narrower for "11" than for
"10". WFF can't measure text. So a Python generator reads the glyph metrics from the font
files at build time and writes these measurements into `watchface.xml` as lookup
expressions over the current hour, minute and second. Six fonts, 24h/12h and ambient
variants add up to about 127 KB of XML that nobody has to maintain by hand.

**Measured corrections per font.** Even with the same metrics, the WFF renderer places scaled
text a few pixels differently than Android's canvas did, and the difference depends on the
font. A small table of offsets per font, fitted to screenshots of the original, absorbs this
(see `Calibration` in the generator and the scripts in `watchface/tools/`).

**One layout instead of the original's several.** The original could tilt the face either
way (left or right wrist), had a small and a larger date, and rearranged up to three
complications depending on how many were filled. In WFF, complication slots have fixed
positions: they cannot sit inside a setting or a rotated group, and the face can't tell
which slots are empty. A layout that changes would therefore leave slots in the wrong
place. The face uses one layout that the slots fit:

- tilted for the left wrist,
- always the larger date,
- two complication slots below the date, tilted with the face, at a position that fits
  every font.

**Settings use the system editor.** The settings are:
- a color theme: nine base colors, each expanded into the original's scheme (hours and
  seconds in the base color, minutes in a white of the same brightness, the date in a
  matching yellow);
- the font, from six, each shown with a "12" icon drawn in that font;
- 24-hour clock;
- colorful or plain white ambient mode;
- the two complication slots.

The original also let you pick the color of each element separately; WFF's editor
offers themes instead.

**The device font for the date.** As in the original, the date and AM/PM use the watch's
system font. Its metrics can't be read at build time, so the generator uses Roboto's, which
were fitted to screenshots.

**Bazel with small custom rules.** A resource-only APK doesn't fit the general Android rules,
which always add compiled code. A few short Starlark rules call `aapt2`, `zipalign` and
`apksigner` directly. Build-tools are downloaded at a pinned version, so only `android.jar`,
`adb` and the debug keystore come from the local Android SDK.

**Same package name, higher version code.** The APK keeps the original's package name
(`de.mulambda.slantedWatchface`) so that it updates the existing Play listing. Its version
codes start at 1000, above every original release.

## Known limitations

- Right-wrist users get the left-wrist tilt.
- Text that is too long for the bottom complication slot ends in "…", because that row runs
  into the round edge of the screen.
- With plain white ambient mode, the complications keep their colors.
- In the editor, the complication slots are outlined with upright rectangles around the
  tilted content: WFF's editor can't show tilted slots.
- Sweet Hipster's date comes very close to the top complication; the original's layout is
  just as tight for that font.

## Building

Install [Bazelisk](https://github.com/bazelbuild/bazelisk) and the Android SDK (found via
`$ANDROID_HOME`, or `~/Library/Android/sdk`), then, in this directory:

```shell
bazel build //watchface:slanted                 # bazel-bin/watchface/slanted.apk
bazel test //...                                # validate against the official WFF schema
bazel run //watchface:install                   # install on a connected watch/emulator and select it
```

To try other settings without the watch's editor, override their defaults at build time,
for example
`bazel run //watchface:install --define=WFF_DEFAULTS=font=bebas_kai,is24h=FALSE`.
`bazel run //watchface:install -- --help` lists all settings and their values.

The scripts in `watchface/tools/` compare the face with the original on two emulators.
