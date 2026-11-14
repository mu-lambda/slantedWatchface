# Measurement tools

Scripts used to check the WFF face against the legacy 1.16 face pixel by pixel, and to
measure its ambient brightness. They drive emulators over adb and need ImageMagick
(`magick`); see the docstring of each script.

- `compare.py`: puts both faces into the same settings (legacy through its settings
  screens, WFF by reinstalling with `--define=WFF_DEFAULTS=...`), optionally sets both
  clocks, and screenshots both side by side.
- `measure_flat.sh`: bounding boxes of hours, minutes, seconds and date in the face's own
  (untilted) frame, for screenshots in the default green theme.
- `offsets.py`: WFF-minus-legacy differences of those boxes for pairs from `compare.py`.

Ambient brightness (Google Play's 15% limit, WO-P7), on the WFF face alone:

- `ambient_sweep.sh`: ambient screenshots of given fonts through a whole day.
- `ambient.py ink`: from those, how much of the screen each element covers at its worst,
  for `FontConfig.ambient_ink`; `ambient.py check`: the brightness of any screenshot.

Example:

    CLOCKS=100832,235959 python3 -I watchface/tools/compare.py default sweet_hipster
    python3 -I watchface/tools/offsets.py $TMPDIR/slanted-compare/*_wff.png
