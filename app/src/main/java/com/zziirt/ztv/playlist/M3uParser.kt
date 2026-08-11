package com.zziirt.ztv.playlist

import com.zziirt.ztv.channels.Channel

class M3uParser {
    fun parse(content: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var pendingInfo: ExtInfo? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseInfo(line)
                    line.startsWith("#") -> Unit
                    pendingInfo != null -> {
                        val info = pendingInfo ?: return@forEach
                        result += Channel(
                            number = result.size + 1,
                            name = info.name.ifBlank { "Канал ${result.size + 1}" },
                            url = line,
                            tvgId = info.tvgId,
                            logoUrl = info.logoUrl,
                            groupTitle = info.groupTitle,
                        )
                        pendingInfo = null
                    }
                }
            }

        return result.distinctBy { it.stableKey }
    }

    private fun parseInfo(line: String): ExtInfo {
        val name = line.substringAfterLast(",", missingDelimiterValue = "").trim()
        return ExtInfo(
            name = name,
            tvgId = attribute(line, "tvg-id"),
            logoUrl = attribute(line, "tvg-logo"),
            groupTitle = attribute(line, "group-title"),
        )
    }

    private fun attribute(line: String, name: String): String? {
        val match = Regex("""$name="([^"]*)"""").find(line)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private data class ExtInfo(
        val name: String,
        val tvgId: String?,
        val logoUrl: String?,
        val groupTitle: String?,
    )
}
