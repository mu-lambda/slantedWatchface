"""Generates the Watch Face Format XML (and its strings) for Slanted!.

The original (pre-WFF) face computed its layout on the watch, from the
measured text. Here the same layout math runs once per font at build time,
using glyph metrics read from the TTF files, and the results are written out as
coordinates and lookup tables.

Usage:
  generate --font limelight=path/to/limelight.ttf ... \
      --watchface_out watchface.xml --watchface_tilted_slots_out watchface_v36.xml \
      --strings_out strings.xml [--defaults font=bebas_kai,is24h=FALSE,...]
"""

import argparse
import colorsys
import dataclasses
import math
from xml.sax.saxutils import escape, quoteattr

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

CANVAS = 450
CENTER = CANVAS / 2
GOLDEN = 1.61803398875
LARGE_INSET = 10
SMALL_INSET = 2
ANGLE = 20
LEGACY_MAX_COMPLICATIONS = 3  # The original face sized the date for three rows of complications.
DATE_HEIGHT_FACTOR = 0.6  # Height of the date, relative to such a row.

BLACK = "#FF000000"
AMBIENT_COLOR = "#FFFFFFFF"
AMBIENT_COLOR_SOFT = "#FFCCCCCC"  # android.graphics.Color.LTGRAY
DEFAULT_DATE_COLOR = 0xFFFF00  # Yellow


@dataclasses.dataclass(frozen=True)
class FontConfig:
    """A font and the scale factors the original face used with it."""

    resource: str
    display_name: str
    y_size_ratio: float
    hour_scale_x: float
    hour_scale_x_single_digit: float
    minutes_scale_x: float
    seconds_scale_x: float


FONTS = [
    FontConfig("limelight", "Limelight", 1.0, 0.4, 0.6, 0.33, 0.4),
    FontConfig("sweet_hipster", "Sweet Hipster", 1.3, 0.9, 1.0, 0.7, 0.6),
    FontConfig("grafik_text", "Grafik Text", 1.0, 0.5, 0.9, 0.5, 0.5),
    FontConfig("fenwick_woodtype", "Fenwick Woodtype", 1.0, 0.7, 1.0, 0.6, 0.5),
    FontConfig("ordinary", "Ordinary", 1.2, 0.6, 1.0, 0.5, 0.5),
    FontConfig("bebas_kai", "Bebas Kai", 1.0, 0.6, 0.8, 0.5, 0.5),
]
DEFAULT_FONT = "limelight"


@dataclasses.dataclass(frozen=True)
class Calibration:
    """Corrections in canvas px, fitted to screenshots of the legacy face (watchface/tools).

    Even with the same glyph metrics, the WFF renderer places scaled text a few pixels
    differently from Android's Paint.getTextBounds()/drawText(), by an amount that depends
    on the font; these constants absorb the average difference.
    """

    hours_one_digit_dx: float = 0
    hours_two_digits_dx: float = 0
    hours_dy: float = 0
    seconds_dx: float = 0  # Also moves the AM/PM, which align with the seconds.
    minutes_dy: float = 0  # Also moves everything aligned with the minutes.


# Fitted with watchface/tools at 04:59 (one-digit hours) and 10:08, 12:20, 23:59 (two
# digits); everything then stays within +-5px of legacy across those times, mostly +-3px.
CALIBRATION = {
    "limelight": Calibration(-5, -3, 2, 1, -7),
    "sweet_hipster": Calibration(-13, 7, 5, -6, 0),
    "grafik_text": Calibration(-7, -13, 1, 0, -4),
    "fenwick_woodtype": Calibration(-3, -5, 5, 0, -7),
    "ordinary": Calibration(-8, -7, 2, 6, 0),
    "bebas_kai": Calibration(-12, -10, 2, 1, -2),
}

# Base colors offered by the original face's color picker (minus black and dark gray).
THEMES = [
    ("green", "Green", 0x00FF00),
    ("red", "Red", 0xFF0000),
    ("orange", "Orange", 0xFFA500),
    ("yellow", "Yellow", 0xFFFF00),
    ("cyan", "Cyan", 0x00FFFF),
    ("blue", "Blue", 0x0000FF),
    ("magenta", "Magenta", 0xFF00FF),
    ("white", "White", 0xFFFFFF),
    ("light_gray", "Light gray", 0xCCCCCC),
]
DEFAULT_THEME = "green"

# Indices into the colors of a theme option: [CONFIGURATION.theme.N].
HOURS, MINUTES, SECONDS, AM_PM, DATE = range(5)


# The date and AM/PM use the device font (Typeface.DEFAULT in the original face).
# We cannot read it at build time, so these are Roboto's metrics, in em.
DEVICE_FONT = "SYNC_TO_DEVICE"
DEVICE_ASCENT = 1900 / 2048
DEVICE_DESCENT = 500 / 2048
DEVICE_TEXT_HEIGHT = 1560 / 2048  # Ink height of e.g. "Wed 11"
DEVICE_AVG_ADVANCE = 0.53  # Average advance of a character (fitted to legacy screenshots)
DATE_MAX_CHARS = 6  # "Wed 30"


# --- Colors (android.graphics.Color HSV semantics) -------------------------------


def _hsv(rgb):
    return colorsys.rgb_to_hsv(*(((rgb >> s) & 0xFF) / 255 for s in (16, 8, 0)))


def _rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return "#FF%02X%02X%02X" % tuple(round(c * 255) for c in (r, g, b))


def theme_colors(base):
    """The full color scheme the original face derived from one base color."""
    base_h, base_s, base_v = _hsv(base)
    white_of_base = _rgb(0, 0, base_v)
    date_h, date_s, _ = _hsv(DEFAULT_DATE_COLOR)
    colors = [None] * 5
    colors[HOURS] = _rgb(base_h, base_s, base_v)
    colors[MINUTES] = white_of_base
    colors[SECONDS] = _rgb(base_h, base_s, base_v)
    colors[AM_PM] = _rgb(base_h, base_s, base_v)
    colors[DATE] = _rgb(date_h, date_s, base_v)
    return colors


AMBIENT_COLORS = [None] * 5
AMBIENT_COLORS[HOURS] = AMBIENT_COLOR_SOFT
AMBIENT_COLORS[MINUTES] = AMBIENT_COLOR
AMBIENT_COLORS[SECONDS] = AMBIENT_COLOR_SOFT
AMBIENT_COLORS[AM_PM] = AMBIENT_COLOR_SOFT
AMBIENT_COLORS[DATE] = AMBIENT_COLOR


# --- Font metrics --------------------------------------------------------------


class Metrics:
    """Glyph metrics of a font, in font units."""

    def __init__(self, path):
        font = TTFont(path)
        self.upm = font["head"].unitsPerEm
        self._ascent = font["hhea"].ascent
        self._descent = -font["hhea"].descent
        cmap = font.getBestCmap()
        glyphs = font.getGlyphSet()
        hmtx = font["hmtx"]
        # digit -> (advance, xMin, xMax, yMin, yMax)
        self.digits = []
        for d in "0123456789":
            name = cmap[ord(d)]
            pen = BoundsPen(glyphs)
            glyphs[name].draw(pen)
            x_min, y_min, x_max, y_max = pen.bounds or (0, 0, 0, 0)
            self.digits.append((hmtx[name][0], x_min, x_max, y_min, y_max))

        self._font = font

    def draw(self, text, pen, scale_x=1.0):
        """Draws `text` (no kerning) at the origin, y pointing down, in font units."""
        glyphs = self._font.getGlyphSet()
        cmap = self._font.getBestCmap()
        x = 0
        for c in text:
            name = cmap[ord(c)]
            glyphs[name].draw(TransformPen(pen, (scale_x, 0, 0, -1, x * scale_x, 0)))
            x += self._font["hmtx"][name][0]

    def ascent(self, size):
        return self._ascent * size / self.upm

    def descent(self, size):
        return self._descent * size / self.upm

    def max_advance(self, n_digits, size):
        return n_digits * max(d[0] for d in self.digits) * size / self.upm


# --- Expressions ---------------------------------------------------------------


class E:
    """A number that may vary at runtime: a WFF expression plus its value at PREVIEW_TIME."""

    def __init__(self, expr, value, const=False):
        self.expr, self.value, self.const = expr, value, const

    @staticmethod
    def of(v):
        return v if isinstance(v, E) else E(_num(v), v, const=True)

    def _op(self, other, op, f):
        other = E.of(other)
        if self.const and other.const:
            return E.of(f(self.value, other.value))
        return E("(%s%s%s)" % (self.expr, op, other.expr), f(self.value, other.value))

    def __add__(self, o):
        return self._op(o, "+", lambda a, b: a + b)

    def __radd__(self, o):
        return E.of(o) + self

    def __sub__(self, o):
        return self._op(o, "-", lambda a, b: a - b)

    def __rsub__(self, o):
        return E.of(o) - self

    def __mul__(self, o):
        return self._op(o, "*", lambda a, b: a * b)

    def __truediv__(self, o):
        return self._op(o, "/", lambda a, b: a / b)


def choose(cond_expr, cond_value, a, b):
    a, b = E.of(a), E.of(b)
    if a.const and b.const and a.value == b.value:
        return a
    return E("(%s?%s:%s)" % (cond_expr, a.expr, b.expr), a.value if cond_value else b.value)


# Differences below this (in px) are not worth a runtime lookup.
TOLERANCE = 1.5


def lookup(digit, table, digits):
    """table[digit] for a runtime digit that is one of `digits`."""
    values = [table[d] for d in digits]
    if max(values) - min(values) <= TOLERANCE:
        return E.of((max(values) + min(values)) / 2)
    result = E.of(table[digits[-1]])
    for d in reversed(digits[:-1]):
        result = choose("%s==%d" % (digit.expr, d), digit.value == d, table[d], result)
    return result


def by_value(source, preview, table):
    """table[value] for a runtime value; values within TOLERANCE share one entry."""
    groups = []  # [(representative, [values])], built from values sorted by result
    for value, result in sorted(table.items(), key=lambda kv: kv[1]):
        if groups and result - groups[-1][2] <= TOLERANCE:
            groups[-1][1].append(value)
            groups[-1][3] = result
        else:
            groups.append([None, [value], result, result])
    for g in groups:
        g[0] = (g[2] + g[3]) / 2
    groups.sort(key=lambda g: len(g[1]))
    *special, default = groups
    result = E.of(default[0])
    for representative, values, _, _ in special:
        cond = "||".join("%s==%d" % (source, v) for v in sorted(values))
        result = choose(cond, preview in values, representative, result)
    return result


def ink_height(metrics, text, size):
    """Height of Paint.getTextBounds() for a string of digits."""
    glyphs = [metrics.digits[int(c)] for c in text]
    return (max(g[4] for g in glyphs) - min(g[3] for g in glyphs)) * size / metrics.upm


def ink_width(metrics, source, preview, n_digits, digit_ranges, size, scale_x):
    """Width of Paint.getTextBounds() for a runtime number (kerning ignored)."""
    k = size / metrics.upm * scale_x
    adv, x_min, x_max = ([m[i] * k for m in metrics.digits] for i in range(3))
    if n_digits == 1:
        (only,) = digit_ranges
        return lookup(E(source, preview), [b - a for a, b in zip(x_min, x_max)], only)
    tens_range, ones_range = digit_ranges
    t = E("floor(%s/10)" % source, preview // 10)
    o = E("(%s%%10)" % source, preview % 10)
    return lookup(t, [a - b for a, b in zip(adv, x_min)], tens_range) + lookup(
        o, x_max, ones_range
    )


# --- Layout --------------------------------------------------------------------

PREVIEW = (10, 8, 32)  # Must match the PREVIEW_TIME metadata.


@dataclasses.dataclass
class Text:
    """One line of text: where its pen starts on the baseline, and how it looks."""

    name: str
    expression: object  # e.g. "[MINUTE_Z]", or (template, param, ...)
    color: int  # index into the theme colors
    family: str
    size: float
    scale_x: float
    x: E  # Pen position at the start of the baseline.
    baseline: E
    ascent: float
    descent: float
    max_width: float  # Unscaled advance of the widest possible text.
    hide_in_ambient: bool = False


@dataclasses.dataclass
class Layout:
    hours_source: str
    hours_two_digits: Text
    hours_one_digit: Text
    minutes: Text
    seconds: Text
    date: Text
    am_pm: Text


def layout(font, metrics, is24h):
    """The original face's layout of the time and date.

    The original face measured the text actually shown; here the same measurements
    are lookup tables evaluated at runtime. Always the original's "larger date" layout: the
    minutes and seconds shrink to make room for the date below them.
    """
    cal = CALIBRATION.get(font.resource, Calibration())
    hours_size = CANVAS * font.y_size_ratio
    text_scale = 2 / GOLDEN
    minutes_size = hours_size / 2
    minutes_scale_x = font.minutes_scale_x * text_scale
    seconds_size = minutes_size / 2
    seconds_scale_x = font.seconds_scale_x * text_scale
    date_size = hours_size / 2 / LEGACY_MAX_COMPLICATIONS * DATE_HEIGHT_FACTOR
    am_pm_size = minutes_size / 4

    hours_source = "[HOUR_0_23]" if is24h else "[HOUR_1_12]"
    hour = PREVIEW[0] if is24h else (PREVIEW[0] - 1) % 12 + 1
    digits = list(range(10))
    one_digit_hours = digits if is24h else digits[1:]
    tens_of_hours = [1, 2] if is24h else [1]
    ones_of_hours = digits if is24h else [0, 1, 2]

    hour_values = range(0, 24) if is24h else range(1, 13)
    hours_height = by_value(
        hours_source, hour, {h: ink_height(metrics, str(h), hours_size) for h in hour_values}
    )
    hours2_width = ink_width(metrics, hours_source, hour, 2, (tens_of_hours, ones_of_hours),
                             hours_size, font.hour_scale_x)
    hours1_width = ink_width(metrics, hours_source, hour, 1, (one_digit_hours,),
                             hours_size, font.hour_scale_x_single_digit)
    minutes_height = by_value(
        "[MINUTE]", PREVIEW[1], {m: ink_height(metrics, "%02d" % m, minutes_size) for m in range(60)}
    )
    minutes_width = ink_width(metrics, "[MINUTE]", PREVIEW[1], 2, (digits[:6], digits),
                              minutes_size, minutes_scale_x)
    seconds_height = by_value(
        "[SECOND]", PREVIEW[2], {m: ink_height(metrics, "%02d" % m, seconds_size) for m in range(60)}
    )
    seconds_00 = (2 * metrics.digits[0][0]) * seconds_size / metrics.upm * seconds_scale_x

    date_height = DEVICE_TEXT_HEIGHT * date_size
    date_width = DEVICE_AVG_ADVANCE * DATE_MAX_CHARS * date_size
    date_scale_x = min(1.0, CENTER * 0.95 / date_width)
    am_pm_width = DEVICE_AVG_ADVANCE * 2 * am_pm_size * 1.25  # Capitals are wider.
    am_pm_scale_x = min(1.0, seconds_00 * 0.7 / am_pm_width)

    minutes_x = E.of(CENTER + LARGE_INSET)
    minutes_baseline = CENTER - hours_height / 2 + minutes_height + cal.minutes_dy
    seconds_x = minutes_x + minutes_width + LARGE_INSET + cal.seconds_dx
    date_baseline = minutes_baseline + date_height + LARGE_INSET
    seconds_baseline = minutes_baseline
    am_pm_baseline = seconds_baseline - seconds_height - 4 * SMALL_INSET

    def own(name, expression, color, size, scale_x, x, baseline, n_digits, **kw):
        return Text(
            name=name,
            expression=expression,
            color=color,
            family=font.resource,
            size=size,
            scale_x=scale_x,
            x=x,
            baseline=baseline,
            ascent=metrics.ascent(size),
            descent=metrics.descent(size),
            max_width=metrics.max_advance(n_digits, size),
            **kw,
        )

    def device(name, expression, color, size, scale_x, x, baseline, chars):
        return Text(
            name=name,
            expression=expression,
            color=color,
            family=DEVICE_FONT,
            size=size,
            scale_x=scale_x,
            x=x,
            baseline=baseline,
            ascent=DEVICE_ASCENT * size,
            descent=DEVICE_DESCENT * size,
            max_width=DEVICE_AVG_ADVANCE * chars * size * 1.5,
        )

    # The hours' ink ends at CENTER (or LARGE_INSET before it, for a single digit).
    return Layout(
        hours_source=hours_source,
        hours_two_digits=own(
            "hours", hours_source, HOURS, hours_size, font.hour_scale_x,
            CENTER - hours2_width + cal.hours_two_digits_dx,
            CENTER + hours_height / 2 + cal.hours_dy, 2,
        ),
        hours_one_digit=own(
            "hours", hours_source, HOURS, hours_size, font.hour_scale_x_single_digit,
            CENTER - LARGE_INSET - hours1_width + cal.hours_one_digit_dx,
            CENTER + hours_height / 2 + cal.hours_dy, 1,
        ),
        minutes=own(
            "minutes", "[MINUTE_Z]", MINUTES, minutes_size, minutes_scale_x,
            minutes_x, minutes_baseline, 2,
        ),
        seconds=own(
            "seconds", "[SECOND_Z]", SECONDS, seconds_size, seconds_scale_x,
            seconds_x, seconds_baseline, 2, hide_in_ambient=True,
        ),
        date=device(
            "date", ("%s %s", "[DAY_OF_WEEK_S]", "[DAY]"), DATE, date_size, date_scale_x,
            minutes_x, date_baseline, DATE_MAX_CHARS,
        ),
        am_pm=device(
            # [AMPM_STRING_ENG] and [AMPM_STRING_SHORT] render empty on Wear OS 5.
            "am_pm", "[AMPM_STRING]", AM_PM, am_pm_size, am_pm_scale_x,
            seconds_x, am_pm_baseline, 2,
        ),
    )


# --- XML -----------------------------------------------------------------------


def _num(v):
    return ("%.1f" % v).rstrip("0").rstrip(".")


class Xml:
    def __init__(self):
        self._lines = []
        self._depth = 0

    def line(self, text):
        self._lines.append(" " * self._depth + text)

    def open(self, tag, **attrs):
        self.line("<%s%s>" % (tag, _attrs(attrs)))
        self._depth += 1

    def close(self, tag):
        self._depth -= 1
        self.line("</%s>" % tag)

    def empty(self, tag, **attrs):
        self.line("<%s%s/>" % (tag, _attrs(attrs)))

    def __str__(self):
        return "\n".join(self._lines) + "\n"


def _attrs(attrs):
    return "".join(" %s=%s" % (k, quoteattr(str(v))) for k, v in attrs.items() if v is not None)


def full_group(xml, name, **attrs):
    xml.open("Group", name=name, x=0, y=0, width=CANVAS, height=CANVAS, **attrs)


def emit_text(xml, text, colors):
    """A PartText whose box is exactly one line high, so the baseline is unambiguous.

    The box starts at the pen position and is scaled around its left edge. Positions
    that depend on the time are updated by Transforms; the static attributes (which
    must be integers in WFF v1) show the preview time.
    """
    color = colors[text.color] if colors else "[CONFIGURATION.theme.%d]" % text.color
    y = text.baseline - text.ascent
    xml.open(
        "PartText",
        name=text.name,
        x=round(text.x.value),
        y=round(y.value),
        width=round(text.max_width * 1.1),
        height=round(text.ascent + text.descent),
        pivotX="0",
        scaleX=("%.3f" % text.scale_x).rstrip("0") if text.scale_x != 1 else None,
    )
    for target, value in (("x", text.x), ("y", y)):
        if not value.const:
            xml.empty("Transform", target=target, value=value.expr)
    if text.hide_in_ambient:
        xml.empty("Variant", mode="AMBIENT", target="alpha", value="0")
    xml.open("Text", align="START")
    # Custom fonts with a fractional size (even "139.0") are drawn at a small default size
    # by the Wear OS 5 renderer, although the schema allows floats. Whole numbers work.
    xml.open("Font", family=text.family, size=round(text.size), color=color)
    if isinstance(text.expression, tuple):
        template, *params = text.expression
    else:
        template, params = "%s", [text.expression]
    xml.line(
        "<Template>%s%s</Template>"
        % (escape(template), "".join('<Parameter expression="%s"/>' % p for p in params))
    )
    xml.close("Font")
    xml.close("Text")
    xml.close("PartText")


def open_condition(xml, name, expression):
    """Opens a Condition and its Compare; the caller adds content and closes both.

    Settings other than the font are tested this way, not with a BooleanConfiguration:
    configurations can't be nested in the Scene. The renderer accepts a nested one but
    stops applying changes from the editor after the first.
    """
    xml.open("Condition")
    xml.open("Expressions")
    xml.line('<Expression name="%s">%s</Expression>' % (name, escape(expression)))
    xml.close("Expressions")
    xml.open("Compare", expression=name)


def emit_hours(xml, lay, colors=None):
    xml.open("Condition")
    xml.open("Expressions")
    xml.line('<Expression name="twoDigits">%s &gt; 9</Expression>' % lay.hours_source)
    xml.close("Expressions")
    xml.open("Compare", expression="twoDigits")
    full_group(xml, "hours_two_digits")
    emit_text(xml, lay.hours_two_digits, colors)
    xml.close("Group")
    xml.close("Compare")
    xml.open("Default")
    full_group(xml, "hours_one_digit")
    emit_text(xml, lay.hours_one_digit, colors)
    xml.close("Group")
    xml.close("Default")
    xml.close("Condition")


def emit_rest(xml, lay, is24h, colors=None, ambient=False):
    """Everything but the hours."""
    emit_text(xml, lay.minutes, colors)
    if not ambient:
        emit_text(xml, lay.seconds, colors)
    emit_text(xml, lay.date, colors)
    if not is24h:
        emit_text(xml, lay.am_pm, colors)


def emit_time(xml, lay, is24h):
    """The time in theme colors, plus a plain white copy for ambient mode if so configured.

    The plain copy covers the colored one with a black backdrop instead of hiding it, so
    the colored layout needs no second copy inside the colorfulAmbient=FALSE option. (The
    backdrop is the face's full square, which covers the round screen at any angle.)
    """
    emit_hours(xml, lay)
    emit_rest(xml, lay, is24h)
    open_condition(xml, "plainAmbient", '[CONFIGURATION.colorfulAmbient] == "FALSE"')
    full_group(xml, "time_ambient_plain", alpha=0)
    xml.empty("Variant", mode="AMBIENT", target="alpha", value="255")
    xml.open("PartDraw", x=0, y=0, width=CANVAS, height=CANVAS)
    xml.open("Rectangle", x=0, y=0, width=CANVAS, height=CANVAS)
    xml.empty("Fill", color=BLACK)
    xml.close("Rectangle")
    xml.close("PartDraw")
    emit_hours(xml, lay, AMBIENT_COLORS)
    emit_rest(xml, lay, is24h, AMBIENT_COLORS, ambient=True)
    xml.close("Group")
    xml.close("Compare")
    xml.close("Condition")


COMPLICATION_SLOTS = 2
COMPLICATION_LEFT = CENTER + LARGE_INSET
COMPLICATION_INSET = 3
COMPLICATION_DEFAULTS = ["WATCH_BATTERY", "STEP_COUNT"]


def complication_area(font, metrics):
    """Top and bottom of the original face's complication area in the tilted frame.

    With the larger date, the area went from below the date to halfway between the hours'
    baseline and the bottom edge.
    """
    hours_size = CANVAS * font.y_size_ratio
    ink = max(d[4] for d in metrics.digits) - min(d[3] for d in metrics.digits)
    max_hours = ink * hours_size / metrics.upm
    hours_y = CENTER + max_hours / 2
    # minutes_y = hours_y - max_hours + max_minutes, and the minutes are half the hours' size.
    minutes_y = CENTER
    bottom = (hours_y + CANVAS) / 2
    top = minutes_y + LARGE_INSET
    date = (bottom - top) / LEGACY_MAX_COMPLICATIONS * DATE_HEIGHT_FACTOR
    return top + date + COMPLICATION_INSET, bottom


def emit_complications(xml, metrics, tilted_slots):
    """SHORT_TEXT slots stacked in the complication area, tilted like the face.

    ComplicationSlot must be a direct child of Scene, and its position is static, so the
    slots can neither be inside the tilted Group nor depend on the font. Each slot is
    centered where the tilted Group would put it, in the part of the area that all fonts
    have in common.

    With tilted_slots, the slot itself is rotated, which the editor of Wear OS 6 and later
    shows correctly. Otherwise the slot stays upright and a Group inside it tilts the
    content: the editor of Wear OS 5 ignores a slot's angle and would outline it in the
    wrong place (and not offer the bottom slot at all).
    """
    areas = [complication_area(font, metrics[font.resource]) for font in FONTS]
    area_top = max(top for top, _ in areas)
    area_bottom = min(bottom for _, bottom in areas)
    width = CANVAS - COMPLICATION_LEFT
    row = (area_bottom - area_top) / COMPLICATION_SLOTS
    height = round(row - COMPLICATION_INSET)
    a = math.radians(ANGLE)
    for i, name in enumerate(("top", "bottom")):
        cx = COMPLICATION_LEFT + width / 2 - CENTER
        cy = area_top + row * i + height / 2 - CENTER
        x = CENTER + cx * math.cos(a) - cy * math.sin(a) - width / 2
        y = CENTER + cx * math.sin(a) + cy * math.cos(a) - height / 2
        xml.open(
            "ComplicationSlot", slotId=i, name="complication_" + name,
            displayName="complication_" + name, x=round(x), y=round(y),
            width=round(width), height=height,
            **(dict(pivotX="0.5", pivotY="0.5", angle=ANGLE) if tilted_slots else {}),
            supportedTypes="SHORT_TEXT EMPTY", isCustomizable="TRUE",
        )
        xml.empty(
            "DefaultProviderPolicy", defaultSystemProvider=COMPLICATION_DEFAULTS[i],
            defaultSystemProviderType="SHORT_TEXT",
        )
        xml.empty("BoundingBox", x=0, y=0, width=round(width), height=height)
        xml.open("Complication", type="SHORT_TEXT")
        if not tilted_slots:
            xml.open("Group", name="tilted", x=0, y=0, width=round(width), height=height,
                     pivotX="0.5", pivotY="0.5", angle=ANGLE)
        # Like the original face: icon in the minutes' color, text in the hours'.
        icon = round(height * 0.45)
        xml.open(
            "PartImage", x=4, y=round((height - icon) / 2), width=icon, height=icon,
            tintColor="[CONFIGURATION.theme.%d]" % MINUTES,
        )
        xml.empty("Image", resource="[COMPLICATION.MONOCHROMATIC_IMAGE]")
        xml.close("PartImage")
        # The slot extends past the round screen; the text ends (with an ellipsis) where
        # the screen's edge crosses the middle of the row, less a margin.
        text_x = icon + 10
        edge = CENTER + math.sqrt(CENTER ** 2 - cy ** 2) - LARGE_INSET
        text_width = round(edge - COMPLICATION_LEFT - text_x)
        xml.open("PartText", x=text_x, y=0, width=text_width, height=height)
        xml.open("Text", align="START", ellipsis="TRUE")
        xml.open("Font", family=DEVICE_FONT, size=round(height * 0.33),
                 color="[CONFIGURATION.theme.%d]" % HOURS)
        xml.line('<Template>%s %s<Parameter expression="[COMPLICATION.TEXT]"/>'
                 '<Parameter expression="[COMPLICATION.TITLE]"/></Template>')
        xml.close("Font")
        xml.close("Text")
        xml.close("PartText")
        if not tilted_slots:
            xml.close("Group")
        xml.close("Complication")
        xml.close("ComplicationSlot")


def settings():
    """The user settings: (id, label, [(value, description)], default value)."""
    return [
        ("theme", "Color theme", [(i, name) for i, name, _ in THEMES], DEFAULT_THEME),
        ("font", "Font", [(f.resource, f.display_name) for f in FONTS], DEFAULT_FONT),
        ("is24h", "24-hour clock", [("TRUE", "24 hours"), ("FALSE", "12 hours")], "TRUE"),
        (
            "colorfulAmbient",
            "Colorful ambient mode",
            [("TRUE", "colored"), ("FALSE", "plain white")],
            "TRUE",
        ),
    ]


def config_defaults(overrides):
    """Default value of each user setting, with overrides ("id=value,...") applied."""
    options = {key: [value for value, _ in values] for key, _, values, _ in settings()}
    defaults = {key: default for key, _, _, default in settings()}
    for item in filter(None, overrides.split(",")):
        key, _, value = item.partition("=")
        if key not in options or value not in options[key]:
            raise SystemExit(
                "invalid default %r; valid: %s"
                % (item, "; ".join("%s=%s" % (k, "|".join(v)) for k, v in options.items()))
            )
        defaults[key] = value
    return defaults


def help_text():
    """How to override the settings' defaults, for `bazel run //watchface:install -- --help`."""
    lines = [
        "WFF_DEFAULTS sets the values the user settings start with after a fresh install.",
        'It is a build setting, so it goes before any "--":',
        "",
        "  bazel run //watchface:install --define=WFF_DEFAULTS=font=bebas_kai,is24h=FALSE",
        "",
        "The value is a comma-separated list of setting=value. It is meant for screenshots",
        "and testing; releases are built without it. An installed face keeps its settings,",
        "so uninstall it first (adb uninstall <package>) for new defaults to show.",
        "",
        "Settings and their values (* marks the default):",
    ]
    for key, label, values, default in settings():
        lines.append("")
        lines.append("  %s: %s" % (key, label))
        for value, description in values:
            lines.append("    %-18s%s%s" % (value, description, " *" if value == default else ""))
    return "\n".join(lines) + "\n"


def watchface_xml(metrics, defaults, tilted_slots=False):
    xml = Xml()
    xml.line('<?xml version="1.0" encoding="utf-8"?>')
    xml.line("<!-- Generated by //watchface/generator. Do not edit. -->")
    xml.open("WatchFace", width=CANVAS, height=CANVAS)
    xml.empty("Metadata", key="CLOCK_TYPE", value="DIGITAL")
    xml.empty("Metadata", key="PREVIEW_TIME", value="10:08:32")

    xml.open("UserConfigurations")
    xml.open("ColorConfiguration", id="theme", displayName="theme_label", defaultValue=defaults["theme"])
    for option_id, _, base in THEMES:
        xml.empty(
            "ColorOption",
            id=option_id,
            displayName="theme_" + option_id,
            colors=" ".join(theme_colors(base)),
        )
    xml.close("ColorConfiguration")
    xml.open("ListConfiguration", id="font", displayName="font_label", defaultValue=defaults["font"])
    for font in FONTS:
        xml.empty(
            "ListOption",
            id=font.resource,
            displayName="font_" + font.resource,
            icon="font_icon_" + font.resource,
        )
    xml.close("ListConfiguration")
    xml.empty(
        "BooleanConfiguration", id="is24h", displayName="is24h_label", defaultValue=defaults["is24h"]
    )
    xml.empty(
        "BooleanConfiguration",
        id="colorfulAmbient",
        displayName="colorful_ambient_label",
        defaultValue=defaults["colorfulAmbient"],
    )
    xml.close("UserConfigurations")

    xml.open("Scene", backgroundColor=BLACK)
    # The face tilts clockwise, as the original did for the left wrist.
    full_group(xml, "slanted", angle=ANGLE, pivotX="0.5", pivotY="0.5")
    xml.open("ListConfiguration", id="font")
    for font in FONTS:
        xml.open("ListOption", id=font.resource)
        full_group(xml, font.resource)
        open_condition(xml, "is24h", '[CONFIGURATION.is24h] == "TRUE"')
        for is24h in (True, False):
            if not is24h:
                xml.close("Compare")
                xml.open("Default")
            full_group(xml, "h24" if is24h else "h12")
            emit_time(xml, layout(font, metrics[font.resource], is24h), is24h)
            xml.close("Group")
        xml.close("Default")
        xml.close("Condition")
        xml.close("Group")
        xml.close("ListOption")
    xml.close("ListConfiguration")
    xml.close("Group")
    emit_complications(xml, metrics, tilted_slots)
    xml.close("Scene")
    xml.close("WatchFace")
    return str(xml)


ICON_TEXT = "12"
ICON_DP = 48


def font_icon_xml(font, metrics):
    """A vector drawable showing ICON_TEXT in `font`, squeezed like the hours on the face."""
    bounds = BoundsPen(None)
    metrics.draw(ICON_TEXT, bounds, font.hour_scale_x)
    x_min, y_min, x_max, y_max = bounds.bounds
    side = max(x_max - x_min, y_max - y_min) * 1.2
    dx = (side - (x_max - x_min)) / 2 - x_min
    dy = (side - (y_max - y_min)) / 2 - y_min
    path = SVGPathPen(None, ntos=lambda v: _num(v))
    metrics.draw(ICON_TEXT, TransformPen(path, (1, 0, 0, 1, dx, dy)), font.hour_scale_x)
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by //watchface/generator. Do not edit. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%ddp" android:height="%ddp"' % (ICON_DP, ICON_DP),
        '    android:viewportWidth="%s" android:viewportHeight="%s">' % (_num(side), _num(side)),
        '  <path android:fillColor="#FFFFFFFF" android:pathData="%s"/>' % path.getCommands(),
        "</vector>",
        "",
    ])


def strings_xml():
    strings = {
        "theme_label": "Color theme",
        "font_label": "Font",
        "is24h_label": "24-hour clock",
        "colorful_ambient_label": "Colorful ambient mode",
        "complication_top": "Top",
        "complication_bottom": "Bottom",
    }
    strings.update({"theme_" + i: name for i, name, _ in THEMES})
    strings.update({"font_" + f.resource: f.display_name for f in FONTS})
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated. Do not edit. -->", "<resources>"]
    lines += ['  <string name="%s">%s</string>' % (k, escape(v)) for k, v in strings.items()]
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", action="append", default=[], help="resource=path.ttf")
    parser.add_argument("--watchface_out", required=True)
    parser.add_argument(
        "--watchface_tilted_slots_out",
        help="Where to write the variant with rotated complication slots (Wear OS 6+)",
    )
    parser.add_argument("--strings_out", required=True)
    parser.add_argument("--help_out", help="Where to write help_text()")
    parser.add_argument(
        "--icon_out", action="append", default=[], help="resource=path.xml: the font's icon"
    )
    parser.add_argument(
        "--defaults",
        default="",
        help="Override setting defaults, e.g. font=bebas_kai,is24h=FALSE (for screenshots)",
    )
    args = parser.parse_args()

    paths = dict(f.split("=", 1) for f in args.font)
    missing = {f.resource for f in FONTS} - paths.keys()
    if missing:
        parser.error("missing --font for: " + ", ".join(sorted(missing)))
    unknown = paths.keys() - {f.resource for f in FONTS}
    if unknown:
        parser.error("no FontConfig in FONTS for: " + ", ".join(sorted(unknown)))
    metrics = {f.resource: Metrics(paths[f.resource]) for f in FONTS}

    defaults = config_defaults(args.defaults)
    with open(args.watchface_out, "w") as f:
        f.write(watchface_xml(metrics, defaults))
    if args.watchface_tilted_slots_out:
        with open(args.watchface_tilted_slots_out, "w") as f:
            f.write(watchface_xml(metrics, defaults, tilted_slots=True))
    with open(args.strings_out, "w") as f:
        f.write(strings_xml())
    if args.help_out:
        with open(args.help_out, "w") as f:
            f.write(help_text())
    by_resource = {f.resource: f for f in FONTS}
    for item in args.icon_out:
        resource, path = item.split("=", 1)
        with open(path, "w") as f:
            f.write(font_icon_xml(by_resource[resource], metrics[resource]))


if __name__ == "__main__":
    main()
