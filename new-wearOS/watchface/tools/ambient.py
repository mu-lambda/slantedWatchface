"""Measures ambient-mode brightness (Google Play's WO-P7) from emulator screenshots.

  python3 -I watchface/tools/ambient.py ink <dir>      # FontConfig.ambient_ink values
  python3 -I watchface/tools/ambient.py check <png>... # brightness of each screenshot

WO-P7 counts a pixel as the mean of its R, G and B (white 100%, black 0%) and limits the
average to 15%. Averages here are over the round screen, which is stricter than over the
square image.

The emulator dims ambient mode: it draws 0xFF as 0xDC. Both commands undo that, so their
numbers are for the colors the face specifies.

`ink` reads the shots from ambient_sweep.sh (green theme, named <font>_<TRUE|FALSE>_HHMM.png)
and splits each one by color: green is the hours and AM/PM, gray the minutes, yellow the
date. Green and gray pixels right of center below the date belong to the complications.
For each font it prints the worst case of each element as a fraction of the round screen,
for FontConfig.ambient_ink, and the complications' worst case for AMBIENT_COMPLICATION_INK.

Needs ImageMagick (`magick`).
"""

import collections
import glob
import math
import os
import subprocess
import sys

SIZE = 454
EMULATOR_PEAK = 0xDC
ANGLE = 20  # the face's tilt
COMPLICATION_LEFT = (225 + 10) * SIZE / 450  # CENTER + LARGE_INSET, in screen pixels

_c = (SIZE - 1) / 2
_cos, _sin = math.cos(math.radians(-ANGLE)), math.sin(math.radians(-ANGLE))
ROUND = []  # (index, x, y in the untilted face) of each pixel on the round screen
for _y in range(SIZE):
    for _x in range(SIZE):
        if (_x - _c) ** 2 + (_y - _c) ** 2 <= (SIZE / 2) ** 2:
            fx = _c + (_x - _c) * _cos - (_y - _c) * _sin
            fy = _c + (_x - _c) * _sin + (_y - _c) * _cos
            ROUND.append((_y * SIZE + _x, fx, fy))


def pixels(path):
    raw = subprocess.run(
        ["magick", path, "-depth", "8", "rgb:-"], check=True, capture_output=True
    ).stdout
    assert len(raw) == SIZE * SIZE * 3, "expected a %dx%d screenshot: %s" % (SIZE, SIZE, path)
    return raw


def brightness(path):
    raw = pixels(path)
    total = sum(raw[3 * i] + raw[3 * i + 1] + raw[3 * i + 2] for i, _, _ in ROUND)
    return total / 3 / EMULATOR_PEAK / len(ROUND)


def elements(path):
    """Coverage of hours, minutes, date and the complications, as fractions of the screen."""
    raw = pixels(path)
    found = []
    for i, fx, fy in ROUND:
        r, g, b = raw[3 * i], raw[3 * i + 1], raw[3 * i + 2]
        m = max(r, g, b)
        if m <= 6:
            continue
        if g > r + 25 and g > b + 25:
            kind = "hours"
        elif r > b + 25 and g > b + 25:
            kind = "date"
        else:
            kind = "minutes"
        found.append((kind, m / EMULATOR_PEAK, fx, fy))
    date_bottom = max((fy for kind, _, _, fy in found if kind == "date"), default=SIZE)
    sums = collections.Counter()
    for kind, coverage, fx, fy in found:
        if kind != "date" and fx >= COMPLICATION_LEFT and fy > date_bottom + 2:
            kind = "complication_text" if kind == "hours" else "complication_icon"
        sums[kind] += coverage
    return {k: v / len(ROUND) for k, v in sums.items()}


def ink(directory):
    worst = collections.defaultdict(collections.Counter)
    for path in sorted(glob.glob(os.path.join(directory, "*_*_[0-9][0-9][0-9][0-9].png"))):
        font = os.path.basename(path).rsplit("_", 2)[0]
        for kind, value in elements(path).items():
            bucket = worst["complications"] if kind.startswith("complication") else worst[font]
            bucket[kind] = max(bucket[kind], value)
    for font, w in sorted(worst.items()):
        if font == "complications":
            print("complications: text %.4f, icon %.4f" % (w["complication_text"], w["complication_icon"]))
        else:
            print("%s: ambient_ink=(%.3f, %.3f, %.3f)" % (font, w["hours"], w["minutes"], w["date"]))


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "ink":
        ink(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == "check":
        for path in sys.argv[2:]:
            print("%5.1f%%  %s" % (100 * brightness(path), os.path.basename(path)))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
