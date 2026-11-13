"""Puts the legacy face (Wear OS 3 emulator) and the WFF face (Wear OS 5 emulator) into the
same settings and screenshots both at the same moment, side by side.

Setup: the legacy 1.16 APK installed and set as the watch face on a Wear OS 3 (API 30)
emulator, and a Wear OS 5 (API 34) emulator for the WFF face; both 454x454 round. Their
serials are LEGACY and WFF below (override with $LEGACY_SERIAL / $WFF_SERIAL).

Usage: [CLOCKS=hhmmss,...] [COMPARE_OUT=dir] python3 -I compare.py <scenario> ...
With CLOCKS, both emulators' clocks are set (needs `adb root`) and each scenario is shot at
each time. Screenshots go to $COMPARE_OUT (default: $TMPDIR/slanted-compare).
"""

import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.environ.get("COMPARE_OUT") or os.path.join(
    os.environ.get("TMPDIR", "/tmp"), "slanted-compare")
LEGACY = os.environ.get("LEGACY_SERIAL", "emulator-5556")
WFF = os.environ.get("WFF_SERIAL", "emulator-5554")
PKG = "de.mulambda.slantedWatchface"
FONTS = ["limelight", "sweet_hipster", "grafik_text", "fenwick_woodtype", "ordinary", "bebas_kai"]

# name -> (font, is24h, colorful_ambient, ambient_screenshot). The WFF face is always
# legacy's left-wrist, larger-date layout, so legacy is set up that way too.
SCENARIOS = {
    "default": ("limelight", True, True, False),
    "ambient": ("limelight", True, True, True),
    "ambient_plain": ("limelight", True, False, True),
    "h12": ("limelight", False, True, False),
}
for f in FONTS[1:]:
    SCENARIOS[f] = (f, True, True, False)


def adb(serial, *args, capture=False):
    cmd = ["adb", "-s", serial, *args]
    if capture:
        return subprocess.run(cmd, check=True, capture_output=True).stdout
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)


def sleep(s):
    time.sleep(s)


def nodes(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb(serial, "shell", "cat", "/sdcard/ui.xml", capture=True)
    result = []
    for n in ET.fromstring(xml).iter("node"):
        b = n.get("bounds")[1:-1].replace("][", ",").split(",")
        x1, y1, x2, y2 = map(int, b)
        result.append(
            dict(
                id=n.get("resource-id", "").split("/")[-1],
                checked=n.get("checked") == "true",
                cx=(x1 + x2) // 2,
                cy=(y1 + y2) // 2,
                top=y1,
            )
        )
    return result


def tap(serial, node):
    adb(serial, "shell", "input", "tap", str(node["cx"]), str(node["cy"]))


def swipe(serial, up=True, distance=250):
    y1, y2 = (400, 400 - distance) if up else (100, 100 + distance)
    adb(serial, "shell", "input", "swipe", "227", str(y1), "227", str(y2), "300")
    sleep(0.8)


def find(serial, rid, scrolls=8):
    """Finds an element by id, scrolling down through the list if needed."""
    for _ in range(scrolls):
        for n in nodes(serial):
            if n["id"] == rid and 60 < n["cy"] < 420:
                return n
        swipe(serial, up=True)
    raise RuntimeError("not found: " + rid)


def resumed(serial):
    out = adb(serial, "shell", "dumpsys", "activity", "activities", capture=True).decode()
    for line in out.splitlines():
        if "mResumedActivity" in line or "topResumedActivity" in line:
            return line
    return ""


def go_home(serial):
    """Backs out of the watch face's settings screens."""
    for _ in range(4):
        if PKG not in resumed(serial):
            return
        adb(serial, "shell", "input", "keyevent", "KEYCODE_BACK")
        sleep(1)
    raise RuntimeError("stuck in " + resumed(serial))


def open_config(serial):
    go_home(serial)
    adb(serial, "shell", "am", "start", "-n", PKG + "/de.mulambda.slantedwatchface.ConfigActivity")
    sleep(2)


def set_switch(serial, rid, value):
    n = find(serial, rid)
    if n["checked"] != value:
        tap(serial, n)
        sleep(1)


def legacy_font(serial):
    """Index of the currently selected font, read from the picker."""
    open_config(serial)
    tap(serial, find(serial, "set_font_button"))
    sleep(2)
    for _ in range(5):
        swipe(serial, up=False, distance=300)
    index = 0
    seen_top = None
    for _ in range(12):
        items = sorted((n for n in nodes(serial) if n["id"] == "typeface_button"), key=lambda n: n["top"])
        for i, n in enumerate(items):
            if n["checked"]:
                return index + i, items, i
        # Scroll by exactly the items above the last visible one.
        swipe(serial, up=True, distance=items[-1]["top"] - items[0]["top"])
        index += len(items) - 1
    raise RuntimeError("no font selected")


def visible_fonts(serial):
    return sorted((n for n in nodes(serial) if n["id"] == "typeface_button"), key=lambda n: n["top"])


def legacy_next_font(serial):
    """Opens the font picker and selects the font after the current one."""
    open_config(serial)
    tap(serial, find(serial, "set_font_button"))
    sleep(2)
    for _ in range(10):
        items = visible_fonts(serial)
        checked = [k for k, n in enumerate(items) if n["checked"]]
        if checked and checked[0] + 1 < len(items) and items[checked[0] + 1]["cy"] < 440:
            tap(serial, items[checked[0] + 1])
            sleep(1.5)
            return
        swipe(serial, up=True, distance=200)
    raise RuntimeError("no next font: %r" % visible_fonts(serial))


def legacy_set_font(serial, font):
    """Resets the settings (font: Limelight), then steps forward to `font`."""
    open_config(serial)
    tap(serial, find(serial, "reset_button"))
    sleep(1.5)
    for _ in range(FONTS.index(font)):
        legacy_next_font(serial)
    go_home(serial)


def legacy_setup(font, is24h, colorful):
    s = LEGACY
    adb(s, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    legacy_set_font(s, font)
    open_config(s)
    set_switch(s, "handedness", False)
    set_switch(s, "is24h", is24h)
    set_switch(s, "colorful_ambient", colorful)
    set_switch(s, "larger_date", True)
    go_home(s)


def wff_setup(font, is24h, colorful):
    b = lambda v: "TRUE" if v else "FALSE"
    defaults = "font=%s,is24h=%s,colorfulAmbient=%s" % (font, b(is24h), b(colorful))
    # Uninstall first so the new defaults apply to a fresh watch face instance.
    subprocess.run(["adb", "-s", WFF, "uninstall", PKG], stdout=subprocess.DEVNULL)
    subprocess.run(
        ["bazel", "run", "//watchface:install", "--define=WFF_DEFAULTS=" + defaults],
        cwd=REPO,
        check=True,
        env={**os.environ, "ANDROID_SERIAL": WFF},
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    adb(WFF, "shell", "input", "keyevent", "KEYCODE_WAKEUP")


def set_clock(hhmmss):
    """Sets both emulators to today at hh:mm:ss (needs adb root)."""
    for s in (LEGACY, WFF):
        adb(s, "shell", "settings", "put", "global", "auto_time", "0")
        date = time.strftime("%m%d") + hhmmss[:4] + time.strftime("%Y") + "." + hhmmss[4:]
        adb(s, "shell", "date", date)
        adb(s, "shell", "am", "broadcast", "-a", "android.intent.action.TIME_SET")


def screenshot_both(name, ambient, clock=None):
    if clock:
        # Three seconds before the requested time, to account for the sleep below.
        h, m, sec = int(clock[:2]), int(clock[2:4]), int(clock[4:])
        t = (h * 3600 + m * 60 + sec - 3) % 86400
        set_clock("%02d%02d%02d" % (t // 3600, t // 60 % 60, t % 60))
        name += "_" + clock[:4]
    for s in (LEGACY, WFF):
        adb(s, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    sleep(3)
    if ambient:
        for s in (LEGACY, WFF):
            adb(s, "shell", "input", "keyevent", "KEYCODE_SLEEP")
        sleep(3)
    paths = {}
    procs = []
    for label, s in (("legacy", LEGACY), ("wff", WFF)):
        paths[label] = os.path.join(OUT, "%s_%s.png" % (name, label))
        procs.append(subprocess.Popen(["adb", "-s", s, "exec-out", "screencap", "-p"], stdout=open(paths[label], "wb")))
    for p in procs:
        p.wait()
    if ambient:
        for s in (LEGACY, WFF):
            adb(s, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    combined = os.path.join(OUT, name + ".png")
    # ImageMagick may have no default font for the labels.
    font = os.environ.get("LABEL_FONT", "/System/Library/Fonts/Supplemental/Arial.ttf")
    subprocess.run(
        ["magick", "montage", *(["-font", font] if os.path.exists(font) else []),
         "-label", "Original 1.16: " + name, paths["legacy"],
         "-label", "WFF: " + name, paths["wff"],
         "-tile", "2x1", "-geometry", "+8+8", "-background", "#222", "-fill", "white",
         "-pointsize", "16", combined],
        check=True,
    )
    return combined


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in sys.argv[1:]:
        font, is24h, colorful, ambient = SCENARIOS[name]
        legacy_setup(font, is24h, colorful)
        wff_setup(font, is24h, colorful)
        clocks = list(filter(None, os.environ.get("CLOCKS", "").split(","))) or [None]
        for clock in clocks:
            print(screenshot_both(name, ambient, clock), flush=True)


if __name__ == "__main__":
    main()
