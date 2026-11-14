#!/bin/sh
# Play Store screenshots of the face (WO-G6), at 10:08 on Saturday, November 21, 2026.
#
#   watchface/tools/store_screenshots.sh <outdir>
#
# Run from new-wearOS/ with the large round Wear OS 5 emulator connected (or
# ANDROID_SERIAL set); needs adb root. Each shot is a fresh install with other default
# settings, so it reinstalls the default build at the end. Copy the results to
# playstore/screenshots/ and the first one, made round, to playstore/icon.png (see
# playstore/listing.md).
set -u
out=$1
mkdir -p "$out"
adb root >/dev/null; sleep 2
adb shell settings put global auto_time 0
shot() {  # name defaults [ambient]
  adb uninstall de.mulambda.slantedWatchface >/dev/null 2>&1
  bazel run //watchface:install "--define=WFF_DEFAULTS=$2" >/dev/null 2>&1 </dev/null
  adb shell input keyevent KEYCODE_WAKEUP </dev/null; sleep 3
  adb shell "date 112110082026.27 >/dev/null; am broadcast -a android.intent.action.TIME_SET >/dev/null" </dev/null
  adb shell input tap 227 227 </dev/null; sleep 4
  if [ "${3:-}" = ambient ]; then adb shell input keyevent KEYCODE_SLEEP </dev/null; sleep 5; fi
  adb exec-out screencap -p > "$out/$1.png"
  adb shell input keyevent KEYCODE_WAKEUP </dev/null
}
shot 1_limelight_green "font=limelight,theme=green"
shot 2_sweet_hipster_orange_12h "font=sweet_hipster,theme=orange,is24h=FALSE"
shot 3_bebas_kai_cyan "font=bebas_kai,theme=cyan"
shot 4_fenwick_magenta "font=fenwick_woodtype,theme=magenta"
shot 5_grafik_text_red_12h "font=grafik_text,theme=red,is24h=FALSE"
shot 6_ordinary_blue "font=ordinary,theme=blue"
shot 7_ambient_colorful "font=limelight,theme=green" ambient
shot 8_ambient_offwhite "font=limelight,theme=green,colorfulAmbient=FALSE" ambient
adb shell settings put global auto_time 1
adb uninstall de.mulambda.slantedWatchface >/dev/null 2>&1
bazel run //watchface:install >/dev/null 2>&1 </dev/null
echo done
