#!/bin/sh
# Bounding boxes of each element after undoing the face's 20° tilt, i.e. in the face's own
# coordinates on the 454px screen. Output per element: left,top widthxheight.
#   hours = green with x < 236, minutes = white, seconds = green with x >= 300, date = yellow.
# Minutes and seconds are searched above y = 290 only, and the hours left of the minutes,
# to keep the complications (green text, white icons) out.
bbox() {  # image, crop width, crop height, crop x offset, color
  magick "$1" -crop "${2}x${3}+${4}+0" +repage -fuzz 25% -fill black +opaque "$5" \
    -fill white -opaque "$5" -trim -format "%[fx:page.x+$4],%[fx:page.y] %[fx:w]x%[fx:h]" info: 2>/dev/null
}
for f in "$@"; do
  flat="${TMPDIR:-/tmp}/measure_flat_$$.png"
  magick "$f" -background black -virtual-pixel black -distort SRT 227,227,1,-20 "$flat"
  printf '%-26s hours %-16s minutes %-16s seconds %-16s date %s\n' "$(basename "$f")" \
    "$(bbox $flat 236 454 0 '#00FF00')" "$(bbox $flat 454 290 0 '#FFFFFF')" \
    "$(bbox $flat 154 290 300 '#00FF00')" "$(bbox $flat 454 454 0 '#FFFF00')"
  rm -f "$flat"
done
