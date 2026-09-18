package tv.telegram.td

/**
 * The media wall's filter chips. Declaration order is the order the design draws
 * them in.
 *
 * Each chip maps to a different server-side query, which is why this lives in the
 * data layer rather than the UI: switching chips re-queries from page one instead
 * of sifting whatever happens to be loaded. "Text" is the exception — TDLib has no
 * text-only search filter, so it pages through everything and keeps
 * MessageText messages client-side.
 */
enum class MediaFilter(val label: String) {
    All("ALL"),
    Video("Video"),
    Audio("Audio"),
    Image("Image"),
    Text("Text"),
}
