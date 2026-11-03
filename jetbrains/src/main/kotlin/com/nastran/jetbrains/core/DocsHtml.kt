package com.nastran.jetbrains.core

/**
 * Converts the generated documentation markdown to HTML for the quick
 * documentation popup. The pages only use a small subset of markdown:
 * headings, links, images, fenced code blocks and plain paragraphs, so a
 * dedicated converter keeps the plugin free of a markdown dependency.
 */
object DocsHtml {
    private val link = Regex("""\[([^\]]*)]\(([^)\s]+)\)""")
    private val image = Regex("""!\[([^\]]*)]\(([^)\s]+)\)""")

    fun toHtml(markdown: String): String {
        val out = StringBuilder()
        val paragraph = ArrayList<String>()
        var inFence = false

        fun flush() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(inline(paragraph.joinToString(" "))).append("</p>\n")
                paragraph.clear()
            }
        }

        for (raw in markdown.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            if (line.startsWith("```")) {
                flush()
                out.append(if (inFence) "</pre>\n" else "<pre>")
                inFence = !inFence
                continue
            }
            if (inFence) {
                out.append(escape(raw)).append('\n')
                continue
            }
            val heading = line.takeWhile { it == '#' }.length
            when {
                line.isBlank() -> flush()
                heading in 1..6 && line.getOrNull(heading) == ' ' -> {
                    flush()
                    // Keep headings modest inside the popup
                    val level = (heading + 1).coerceIn(2, 6)
                    out.append("<h$level>").append(inline(line.substring(heading + 1).trim())).append("</h$level>\n")
                }
                Regex("""^(\d+\.|[*-]) """).containsMatchIn(line) -> {
                    // List items are rendered as their own paragraphs, keeping the marker
                    flush()
                    paragraph.add(line)
                }
                else -> paragraph.add(line.trim())
            }
        }
        flush()
        if (inFence) out.append("</pre>\n")
        return out.toString()
    }

    private fun inline(text: String): String {
        val sb = StringBuilder()
        var last = 0
        val tokens = (image.findAll(text) + link.findAll(text))
            .sortedBy { it.range.first }
            .fold(mutableListOf<MatchResult>()) { acc, m ->
                if (acc.isEmpty() || m.range.first > acc.last().range.last) acc.add(m)
                acc
            }
        for (m in tokens) {
            sb.append(escape(text.substring(last, m.range.first)))
            val (label, url) = m.destructured
            if (m.value.startsWith("!")) {
                sb.append("<img src=\"").append(escape(url)).append("\" alt=\"").append(escape(label)).append("\">")
            } else {
                sb.append("<a href=\"").append(escape(url)).append("\">").append(escape(label)).append("</a>")
            }
            last = m.range.last + 1
        }
        sb.append(escape(text.substring(last)))
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
