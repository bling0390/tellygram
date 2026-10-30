package tv.telegram.ui.settings

internal fun releaseVersion(tag: String): String? {
    val trimmed = tag.trim().removePrefix("v").removePrefix("V")
    val core = trimmed.substringBefore('-').trim()
    return core.takeIf { Regex("""^\d+(\.\d+)*$""").matches(it) }
}

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
