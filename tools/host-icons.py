import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ARTWORK = "/tmp/dsh-src/packages/client/ui-primitives/src/icons/artwork.ts"
OUT = os.path.join(REPO, "app/src/main/java/uk/xa0/dsh/ui/components/DshIconVectors.kt")


def circle_to_path(cx, cy, r):
    return "M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0Z" % (cx - r, cy, r, r, 2 * r, r, r, 2 * r)


def rect_to_path(x, y, w, h, rx):
    if not rx:
        return "M%s,%sh%sv%sh-%sZ" % (x, y, w, h, w)
    return (
        "M{X},{Y}h{W}a{R},{R} 0 0 1 {R},{R}v{H}a{R},{R} 0 0 1 -{R},{R}"
        "h-{W}a{R},{R} 0 0 1 -{R},-{R}v-{H}a{R},{R} 0 0 1 {R},-{R}Z"
    ).format(X=x + rx, Y=y, W=w - 2 * rx, H=h - 2 * rx, R=rx)


CIRCLE = re.compile(r"<circle\s+cx=\"([\d.\-]+)\"\s+cy=\"([\d.\-]+)\"\s+r=\"([\d.\-]+)\"\s*/>")
RECT = re.compile(
    r"<rect\s+x=\"([\d.\-]+)\"\s+y=\"([\d.\-]+)\"\s+width=\"([\d.\-]+)\"\s+height=\"([\d.\-]+)\"(?:\s+rx=\"([\d.\-]+)\")?\s*/>"
)


def normalise_svg(svg):
    def sub_circle(m):
        cx, cy, r = float(m.group(1)), float(m.group(2)), float(m.group(3))
        return '<path d="%s" stroke="currentColor"/>' % circle_to_path(cx, cy, r)

    def sub_rect(m):
        x, y = float(m.group(1)), float(m.group(2))
        w, h = float(m.group(3)), float(m.group(4))
        rx = float(m.group(5)) if m.group(5) else 0.0
        return '<path d="%s" stroke="currentColor"/>' % rect_to_path(x, y, w, h, rx)

    return RECT.sub(sub_rect, CIRCLE.sub(sub_circle, svg))


ENTRY = re.compile(
    r"'([a-z0-9-]+)':\s*\{ size: (\d+),\s*(?:aspect: \[([^\]]+)\],\s*)?"
    r"svg: strokeWidth => `(.+?)` \}",
    re.S,
)


def parse_artwork():
    src = open(ARTWORK, encoding="utf-8").read()
    return [
        {
            "glyph": m.group(1),
            "size": int(m.group(2)),
            "aspect": m.group(3),
            "svg": normalise_svg(m.group(4)),
        }
        for m in ENTRY.finditer(src)
    ]




def kotlin_name(glyph):
    """`new-chat-outline` -> `NewChatOutline`, the host artwork's own name."""
    return "".join(part.capitalize() for part in glyph.split("-"))


def parse_paths(svg):
    """(pathData, filled, evenOdd, strokeWidth) per `<path>` in document order."""
    vb = re.search(r'viewBox="([\d.\- ]+)"', svg)
    stroke_w = re.search(r'stroke-width="([\d.]+)"', svg)
    default_w = float(stroke_w.group(1)) if stroke_w else 1.0
    out = []
    for m in re.finditer(r"<path\s+([^>]+?)/?>", svg):
        attrs = m.group(1)
        d = re.search(r'd="([^"]+)"', attrs)
        if not d:
            continue
        filled = 'fill="currentColor"' in attrs
        even_odd = 'fill-rule="evenodd"' in attrs
        w = re.search(r'stroke-width="([\d.]+)"', attrs)
        out.append((d.group(1), filled, even_odd, float(w.group(1)) if w else default_w))
    vw, vh = (vb.group(1).split() if vb else ["0", "0", "16", "16"])[2:4]
    return out, float(vw), float(vh)


def main():
    items = parse_artwork()
    lines = []
    a = lines.append
    a("package uk.xa0.dsh.ui.components")
    a("")
    a("import androidx.compose.ui.graphics.Color")
    a("import androidx.compose.ui.graphics.PathFillType")
    a("import androidx.compose.ui.graphics.SolidColor")
    a("import androidx.compose.ui.graphics.vector.ImageVector")
    a("import androidx.compose.ui.graphics.vector.PathParser")
    a("import androidx.compose.ui.unit.dp")
    a("")
    a("/**")
    a(" * DSH's own product icon set, generated from the host's SVG artwork.")
    a(" *")
    a(" * ## Why generated, and why from the host")
    a(" *")
    a(" * The web client draws these glyphs and this app used to draw Material lookalikes,")
    a(" * which made the two clients visibly different products: the branch line, the queue")
    a(" * arrow, the goal ring and the `+` are all shaped differently, and a reader who has")
    a(" * used both recognises the shape long before they read the label. Generating from")
    a(" * the host's own artwork is the only version of 'same icon' that survives a redraw")
    a(" * upstream.")
    a(" *")
    a(" * ## Regenerating")
    a(" *")
    a(" * `python3 tools/host-icons.py`, with the DSH source checkout at")
    a(" * `/tmp/dsh-src`. The tool is committed; the generated file is checked in so a build")
    a(" * needs no TypeScript toolchain.")
    a(" *")
    a(" * ## What the conversion preserves")
    a(" *")
    a(" * `viewBox` becomes the viewport, so no geometry is rescaled. Each path keeps its own")
    a(" * stroke-or-fill from its own attributes, because these glyphs mix both in one")
    a(" * document and a global choice would erase the fill-only dots. `fill-rule=\"evenodd\"`")
    a(" * is carried through: the close-circle and check-circle punch their holes with it,")
    a(" * and Compose defaults to non-zero.")
    a(" */")
    a("object DshIconVectors {")
    ok = 0
    skipped = []
    for it in items:
        paths, vw, vh = parse_paths(it["svg"])
        if not paths:
            skipped.append(it["glyph"])
            continue
        name = kotlin_name(it["glyph"])
        a("")
        a("    /** `%s`, from the host artwork at %dpx. */" % (it["glyph"], it["size"]))
        a("    val %s: ImageVector by lazy {" % name)
        a("        ImageVector.Builder(")
        a('            name = "%s",' % it["glyph"])
        a("            defaultWidth = %s.dp," % _num(it["size"]))
        a("            defaultHeight = %s.dp," % _num(it["size"]))
        a("            viewportWidth = %sf," % _num(vw))
        a("            viewportHeight = %sf," % _num(vh))
        a("        ).apply {")
        for d, filled, even_odd, w in paths:
            a("            addPath(")
            a("                pathData = PathParser().parsePathString(%s).toNodes()," % _str(d))
            a("                fill = %s," % ("SolidColor(Color.Black)" if filled else "null"))
            a("                stroke = %s," % ("null" if filled else "SolidColor(Color.Black)"))
            if not filled:
                a("                strokeLineWidth = %sf," % _num(w))
            if even_odd:
                a("                pathFillType = PathFillType.EvenOdd,")
            a("            )")
        a("        }.build()")
        a("    }")
        ok += 1
    a("}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("wrote %d icons to %s" % (ok, OUT))
    if skipped:
        print("skipped (no drawable geometry):", ", ".join(skipped))


def _num(v):
    return ("%d" % v) if float(v) == int(v) else ("%g" % v)


def _str(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\\"') + '"'


if __name__ == "__main__":
    main()