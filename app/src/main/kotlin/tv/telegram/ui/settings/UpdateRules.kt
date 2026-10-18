package tv.telegram.ui.settings

/**
 * "v1.0.129" or "1.0.129-debug" -> "1.0.129"; null when the tag is not a dotted number (the
 * About pane only offers an update when it can compare versions).
 */
internal fun releaseVersion(tag: String): String? {
    val trimmed = tag.trim().removePrefix("v").removePrefix("V")
    val core = trimmed.substringBefore('-').trim()
    return core.takeIf { Regex("""^\d+(\.\d+)*$""").matches(it) }
}

/**
 * Segment-wise numeric comparison: 1.0.100 is newer than 1.0.99 (a string compare would not see
 * that), and a missing segment counts as zero.
 */
internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = candidate.split('.').mapNotNull { it.toIntOrNull() }
    val b = current.split('.').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * Release notes are Markdown; the TV dialog shows plain text. Headings lose their hashes,
 * bullets become a middle dot, and inline markers (**bold**, `code`, [label](url)) keep only
 * their text.
 */
internal fun plainChangelog(markdown: String): String {
    val lines = markdown.replace("\r\n", "\n").split('\n').map { raw ->
        var line = raw.trimEnd()
        line = line.replace(Regex("""^#{1,6}\s*"""), "")
        line = line.replace(Regex("""^[-*+]\s+"""), "• ")
        line = line.replace(Regex("""\*\*(.+?)\*\*""")) { it.groupValues[1] }
        line = line.replace(Regex("""__(.+?)__""")) { it.groupValues[1] }
        line = line.replace(Regex("""`([^`]*)`""")) { it.groupValues[1] }
        line = line.replace(Regex("""\[([^\]]*)]\(([^)]*)\)""")) { it.groupValues[1] }
        line.trimEnd()
    }
    return lines.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
}
