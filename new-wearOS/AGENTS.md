# Notes for coding agents

Practical knowledge for working in this directory. The design choices are in
[README.md](README.md) and the architecture in [SPEC.md](SPEC.md); read SPEC.md before
changing the generator. Keep the three files to their purpose: README.md and SPEC.md are
for human readers, this file is for agents.

## Ground rules

- Everything for the WFF face lives in `new-wearOS/` and must not reference the original
  app in the parent directory (its code, build files or assets). Copy what you need.
- Never edit generated output. `watchface.xml`, `generated_strings.xml` and the font icons
  are written by `watchface/generator/generate.py`.
- Keep the package name `de.mulambda.slantedWatchface` and keep `version_code` at 1000 or
  above: the APK updates the original's Play listing.
- The maintainer reviews changes as GitHub pull requests and commits and pushes with their
  own script. Prepare changes (and a commit message if asked), but don't commit or push
  unless asked.
- When a change affects the design or the architecture, update README.md or SPEC.md too.
  The `--help` of `//watchface:install` is generated from `settings()` in the generator,
  so it needs no separate update.

## Build and check

Run from `new-wearOS/`:

```shell
bazel build //watchface:slanted      # APK: bazel-bin/watchface/slanted.apk
bazel build //watchface:watchface    # just the generated files: bazel-bin/watchface/watchface/
bazel test //...                     # schema test; run after every generator change
bazel run //watchface:install        # install on $ANDROID_SERIAL (or the only device) and select it
```

- A passing schema test does not mean the face renders correctly. The renderer ignores or
  misreads some things the schema allows (see "WFF renderer behavior" in SPEC.md). Check
  visible changes on the emulator.
- Watch the size of `watchface.xml` (about 127 KB). Each font and each 12h/24h variant
  repeats the whole time layout, so a setting that wraps the time multiplies the XML.
- System `python3` has no fontTools, and `python3 -I` ignores `PYTHONPATH`. To run font
  code outside Bazel, make a venv in a scratch directory and `pip install fonttools`. The
  generator's own dependencies are pinned in `watchface/generator/requirements_lock.txt`.

## Emulators

Two round Wear OS AVDs, both 454×454:

| AVD | Serial | Runs |
|---|---|---|
| `Wear_OS_Large_Round_API_34` (Wear OS 5) | `emulator-5554` | this face |
| `Wear_OS_Large_Round_API_30` (Wear OS 3) | `emulator-5556` | the original, release 1.16 |

Start them headless in the background, for example
`emulator -avd Wear_OS_Large_Round_API_30 -port 5556 -no-window -no-audio -no-boot-anim`
(the emulator binary is in `$ANDROID_HOME/emulator`). Leave out `-no-window` when the user
wants to watch.

### Screenshots of a given setting and time

1. `adb uninstall de.mulambda.slantedWatchface`. An installed face keeps its settings, so
   new defaults only apply to a fresh install.
2. `bazel run //watchface:install --define=WFF_DEFAULTS=font=...,theme=...`. For all
   settings and values, run `bazel run //watchface:install -- --help`.
3. Set the clock (needs `adb root`):
   `adb shell settings put global auto_time 0`, then
   `adb shell date MMDDhhmmYYYY.ss`, then
   `adb shell am broadcast -a android.intent.action.TIME_SET`.
   The preview time is 10:08:32; set the clock a few seconds earlier to allow for the
   steps that follow.
4. `adb shell input keyevent KEYCODE_WAKEUP`, wait about 5 seconds, then
   `adb exec-out screencap -p > shot.png`.
5. When done, restore the clock with `adb shell settings put global auto_time 1`, and
   reinstall the default build if you installed a test build.

To try fonts or other changes without touching the working tree, copy `new-wearOS/`
(without the `bazel-*` symlinks) to a scratch directory and build there.

### Comparing with the original

`watchface/tools/` (see its README) drives both emulators into the same settings, takes
screenshots side by side and measures each element. Use it after changing layout math or
`CALIBRATION`; elements should stay within 5 px of the original. The tools need ImageMagick
(`magick`). On macOS, labels in montages need an explicit `-font` path.

Fonts that the original didn't have can't be compared this way. Size them against an
existing font (Limelight is the reference the maintainer prefers) and check them by eye.

## Pitfalls seen so far

- Font `size` attributes must be whole numbers, or the font renders tiny.
- `[AMPM_STRING_ENG]` and `[AMPM_STRING_SHORT]` render empty; use `[AMPM_STRING]`.
- `pivotX` and `pivotY` are fractions (0..1) of the element's size.
- `ComplicationSlot` must be a direct child of `Scene`, with static geometry. It can't be
  inside the tilted group or a configuration.
- A font whose digits are short for its size, or whose line metrics are unusual (such as
  seven-segment fonts), can overlap the date and the complications. Check the layout on
  the emulator with every new font.
