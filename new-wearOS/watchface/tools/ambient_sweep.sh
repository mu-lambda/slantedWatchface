#!/bin/sh
# Ambient-mode screenshots through a whole day, for measuring FontConfig.ambient_ink.
#
#   watchface/tools/ambient_sweep.sh <outdir> <font>...
#
# Run from new-wearOS/ with one emulator connected (or ANDROID_SERIAL set); needs adb root.
# For each font, installs the face in the green theme with colorful ambient mode, then:
#   24h: 60 shots at (i % 24):i for i = 0..59, so every hour and every minute appears once;
#   12h: 24 shots at HH:00 for HH = 0..23 (AM and PM).
# The date is fixed to Wednesday, October 28, 2026 ("Wed 28", a wide date). About 10
# minutes per font. Then run `ambient.py ink <outdir>`.
set -u
out=$1
shift
mkdir -p "$out"
adb root >/dev/null
sleep 2
adb shell settings put global auto_time 0
for font in "$@"; do
    for h24 in TRUE FALSE; do
        adb uninstall de.mulambda.slantedWatchface >/dev/null 2>&1
        bazel run //watchface:install \
            "--define=WFF_DEFAULTS=font=$font,is24h=$h24,theme=green,colorfulAmbient=TRUE" \
            >/dev/null 2>&1 || { echo "install failed: $font" >&2; exit 1; }
        adb shell input keyevent KEYCODE_WAKEUP
        sleep 3
        adb shell input keyevent KEYCODE_SLEEP
        sleep 3
        if [ $h24 = TRUE ]; then n=60; else n=24; fi
        i=0
        while [ $i -lt $n ]; do
            if [ $h24 = TRUE ]; then h=$((i % 24)); m=$i; else h=$i; m=0; fi
            hm=$(printf '%02d%02d' $h $m)
            adb shell "date 1028${hm}2026.00 >/dev/null; am broadcast -a android.intent.action.TIME_SET >/dev/null"
            sleep 1.2
            adb exec-out screencap -p >"$out/${font}_${h24}_${hm}.png"
            i=$((i + 1))
        done
        echo "done: $font $h24"
    done
done
adb shell settings put global auto_time 1
adb shell input keyevent KEYCODE_WAKEUP
