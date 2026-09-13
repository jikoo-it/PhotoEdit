package com.momi.watermarker.presentation.video

/**
 * Turns optional user input into a MediaStore display name (no extension).
 * Empty or punctuation-only input falls back to [fallback].
 */
fun resolveVideoDisplayName(requested: String, fallback: String): String {
    val cleaned = requested.trim()
        .replace(Regex("(?i)\\.mp4$"), "")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim('.', ' ')
        .take(80)
    return cleaned.ifBlank { fallback }
}
