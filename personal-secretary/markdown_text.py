"""Small Markdown display subset for Tk Text. No HTML or code execution."""
import re


INLINE = re.compile(r"`([^`\n]+)`|\*\*([^*\n]+)\*\*|__([^_\n]+)__|\*([^*\n]+)\*|\[([^\]\n]+)\]\(([^\s)]+)\)")


def insert_markdown(widget, source):
    if not getattr(widget, "markdown_ready", False):
        widget.tag_configure("md-bold", font=("Microsoft YaHei UI", 10, "bold"))
        widget.tag_configure("md-italic", font=("Microsoft YaHei UI", 10, "italic"))
        widget.tag_configure("md-code", font=("Consolas", 10), background="#111c2b")
        widget.tag_configure("md-quote", foreground="#9aadbf", lmargin1=16, lmargin2=16)
        for level, size in ((1, 16), (2, 14), (3, 12), (4, 11), (5, 10), (6, 10)):
            widget.tag_configure("md-h" + str(level), font=("Microsoft YaHei UI", size, "bold"), spacing1=8, spacing3=5)
        widget.markdown_ready = True

    def inline(text, tags=()):
        offset = 0
        for match in INLINE.finditer(text):
            widget.insert("end", text[offset:match.start()], ("body", *tags))
            code, bold, alternate, italic, label, url = match.groups()
            if code is not None:
                widget.insert("end", code, ("body", *tags, "md-code"))
            elif label is not None:
                # Show source URLs as text; never open links during rendering.
                widget.insert("end", label + " (" + url + ")", ("body", *tags))
            else:
                widget.insert("end", bold or alternate or italic, ("body", *tags, "md-italic" if italic is not None else "md-bold"))
            offset = match.end()
        widget.insert("end", text[offset:] + "\n", ("body", *tags))

    fence = None
    for line in str(source).replace("\r\n", "\n").split("\n"):
        marker = re.match(r"^\s*(`{3,}|~{3,})([^\s]*)\s*$", line)
        if fence is not None:
            if re.fullmatch(r"\s*" + re.escape(fence[0]) + "{" + str(len(fence)) + r",}\s*", line):
                fence = None
            else:
                widget.insert("end", line + "\n", ("body", "md-code"))
            continue
        if marker:
            fence = marker[1]
            continue
        heading = re.match(r"^(#{1,6})\s+(.+?)(?:\s+#+)?$", line)
        if heading:
            inline(heading[2], ("md-h" + str(len(heading[1])),))
        elif re.fullmatch(r"\s*(?:-{3,}|\*{3,}|_{3,})\s*", line):
            widget.insert("end", "────────────────────\n", "body")
        elif "|" in line and all(re.fullmatch(r":?-{3,}:?", cell.strip()) for cell in line.strip().strip("|").split("|")):
            continue
        elif "|" in line:
            inline(" | ".join(cell.strip() for cell in line.strip().strip("|").split("|")), ("md-code",))
        elif line.startswith(">"):
            inline(line.removeprefix(">").lstrip(), ("md-quote",))
        else:
            inline(re.sub(r"^\s*[-+*]\s+", "• ", line))
