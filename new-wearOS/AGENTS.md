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
- The maintainer commits changes either directly to main or through a GitHub pull request,
  with their own script. Prepare changes (and a commit message if asked), but don't commit or push
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

Round Wear OS AVDs, all 454×454:

| AVD | Serial | Runs |
|---|---|---|
| `Wear_OS_Large_Round_API_34` (Wear OS 5) | `emulator-5554` | this face (`raw/watchface.xml`) |
| `Wear_OS_Large_Round_API_36` (Wear OS 6) | `emulator-5558` | this face (`raw-v36/watchface.xml`) |
| `Wear_OS_Large_Round_API_30` (Wear OS 3) | `emulator-5556` | the original, release 1.16 |

The Wear OS 6 emulator (`-port 5558`) needs hardware virtualization, which the command
sandbox blocks: start it outside the sandbox, or it hangs in software emulation. If it
starts without a visible window, the emulator is waiting on a hidden dialog asking to
send old crash reports; delete `/tmp/android-$USER/emu-crash-*.db` and start it again. On Wear OS 6, `wff_install`'s broadcast doesn't
select the face; add it once through the watch face picker ("Add new"), after which
reinstalls keep it selected. The Wear OS 7 image doesn't run on this Mac (M1).

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

### Driving the system editor

To test settings the way users change them:

- Open it: long-press the face (`adb shell input swipe 227 227 227 227 1500`), then tap
  the element with content description "Edit watch face". Find elements with
  `adb shell uiautomator dump` and tap the center of their bounds.
- Pages: color theme, font, complications, other options (the switches). The editor
  reopens on the page last used. Swipe left with `adb shell input swipe 400 227 60 227 300`.
  A right swipe that starts near the left edge is the system back gesture and closes the
  editor; use `input motionevent DOWN/MOVE/UP` starting at x=120 instead.
- Lists (theme, font) change with the crown:
  `adb shell input rotaryencoder scroll --axis SCROLL,-2` is one step. Switches change by
  tapping them.
- The editor closes by itself after a few idle seconds. Leave it with `KEYCODE_BACK`.
- The stored settings are in
  `adb shell dumpsys activity service com.google.wear.watchface.runtime` (`userStyle=`).
  If the face doesn't match them, the face isn't following the editor.
- `adb shell am force-stop com.google.wear.watchface.runtime` falls back to the default
  face; reselect ours with the `DEBUG_SURFACE set-watchface` broadcast that `wff_install`
  uses.

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
  inside the tilted group or a configuration. In `raw/watchface.xml` (Wear OS 4 and 5),
  don't give it an `angle`: that editor draws its outline in the wrong place. Rotate a
  `Group` inside the `Complication` instead. `raw-v36/watchface.xml` rotates the slot.
- Changes to the slots need checking in the editor on both Wear OS 5 and 6, since each
  uses a different document.
- Don't nest configuration elements (`ListConfiguration`, `BooleanConfiguration`) in the
  `Scene`. The schema test and the first render pass, but the face stops following the
  editor after the first change. Use a `Condition` on `[CONFIGURATION.<id>]` instead.
- A fresh install always looks right. Bugs in how the face reacts to the editor only show
  when you change settings several times in a row in the editor, so test that (see below).
- A font whose digits are short for its size, or whose line metrics are unusual (such as
  seven-segment fonts), can overlap the date and the complications. Check the layout on
  the emulator with every new font.
