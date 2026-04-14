from pathlib import Path
import textwrap

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.pdfgen import canvas


def main() -> None:
    src = Path("docs/parameters-api-reference.md")
    out = Path("docs/parameters-api-reference.pdf")

    text = src.read_text(encoding="utf-8")
    lines = text.splitlines()

    c = canvas.Canvas(str(out), pagesize=A4)
    _, height = A4

    left = 2 * cm
    top = height - 2 * cm
    bottom = 2 * cm
    line_height = 14

    def new_page() -> float:
        c.showPage()
        c.setFont("Helvetica", 10)
        return top

    y = top
    c.setFont("Helvetica-Bold", 14)
    c.drawString(left, y, "Parameters API Engine Analysis")
    y -= 20
    c.setFont("Helvetica", 10)

    for raw in lines:
        line = raw.expandtabs(4)

        if line.strip() == "":
            y -= line_height
            if y < bottom:
                y = new_page()
            continue

        if line.startswith("### "):
            font_name, font_size = "Helvetica-Bold", 11
            content = line[4:]
            wrap_width = 92
        elif line.startswith("## "):
            font_name, font_size = "Helvetica-Bold", 12
            content = line[3:]
            wrap_width = 90
        elif line.startswith("# "):
            font_name, font_size = "Helvetica-Bold", 13
            content = line[2:]
            wrap_width = 88
        else:
            font_name, font_size = "Helvetica", 10
            content = line
            wrap_width = 108

        c.setFont(font_name, font_size)
        wrapped = textwrap.wrap(
            content,
            width=wrap_width,
            replace_whitespace=False,
            drop_whitespace=False,
        ) or [""]

        for part in wrapped:
            if y < bottom:
                y = new_page()
                c.setFont(font_name, font_size)
            c.drawString(left, y, part)
            y -= line_height

    c.save()
    print(str(out))


if __name__ == "__main__":
    main()
