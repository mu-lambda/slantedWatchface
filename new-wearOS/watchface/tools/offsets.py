"""Per element offsets (WFF minus legacy, px on the 454px screen, in the face's own frame).

Usage: python3 -I offsets.py <dir>/<name>_wff.png ...   (from compare.py; the legacy
screenshot is the same name with _legacy)
Prints, per screenshot pair and element: d_left d_right d_top d_bottom.
"""
import re
import subprocess
import sys
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def boxes(path):
    out = subprocess.run(["sh", os.path.join(HERE, "measure_flat.sh"), path], capture_output=True,
                         text=True, check=True).stdout
    result = {}
    for name, x, y, w, h in re.findall(r"(\w+) (-?\d+),(-?\d+) (\d+)x(\d+)", out):
        x, y, w, h = map(int, (x, y, w, h))
        result[name] = (x, x + w, y, y + h)
    return result


for wff in sys.argv[1:]:
    legacy = wff.replace("_wff", "_legacy")
    a, b = boxes(wff), boxes(legacy)
    cells = []
    for k in ("hours", "minutes", "seconds", "date"):
        if k in a and k in b:
            cells.append("%s %+3d %+3d %+3d %+3d" % ((k[:3],) + tuple(p - q for p, q in zip(a[k], b[k]))))
    print("%-28s %s" % (os.path.basename(wff).replace("_wff.png", ""), " | ".join(cells)), flush=True)
