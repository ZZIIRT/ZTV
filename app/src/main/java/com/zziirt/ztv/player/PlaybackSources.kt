package com.zziirt.ztv.player

internal class PlaybackSources(primary: String, alternatives: List<String>) {
    private var urls = (listOf(primary) + alternatives).distinct().take(MAX_ATTEMPTS)
    private val attempted = linkedSetOf(primary)
    var currentUrl = primary
        private set

    val hasNext: Boolean
        get() = attempted.size < MAX_ATTEMPTS && urls.any { it !in attempted }

    fun update(primary: String, alternatives: List<String>) {
        urls = (listOf(primary) + alternatives).distinct().take(MAX_ATTEMPTS)
    }

    fun next(): String? {
        if (!hasNext) return null
        val next = urls.first { it !in attempted }
        attempted.add(next)
        currentUrl = next
        return next
    }

    private companion object {
        const val MAX_ATTEMPTS = 9
    }
}
