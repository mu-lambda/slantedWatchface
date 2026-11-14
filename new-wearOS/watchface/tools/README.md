# Comparison tools

Scripts used to check the WFF face against the legacy 1.16 face pixel by pixel. They drive
two emulators over adb and need ImageMagick (`magick`); see the docstring of each script.

- `compare.py`: puts both faces into the same settings (legacy through its settings
  screens, WFF by reinstalling with `--define=WFF_DEFAULTS=...`), optionally sets both
  clocks, and screenshots both side by side.
- `measure_flat.sh`: bounding boxes of hours, minutes, seconds and date in the face's own
  (untilted) frame, for screenshots in the default green theme.
- `offsets.py`: WFF-minus-legacy differences of those boxes for pairs from `compare.py`.

Example:

    CLOCKS=100832,235959 python3 -I watchface/tools/compare.py default sweet_hipster
    python3 -I watchface/tools/offsets.py $TMPDIR/slanted-compare/*_wff.png
