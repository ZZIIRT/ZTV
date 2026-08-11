package com.zziirt.ztv.playlist

import com.zziirt.ztv.channels.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class M3uParser {
    fun parse(content: String): List<Channel> =
        content.byteInputStream(Charsets.UTF_8).use(::parse)

    fun parse(inputStream: InputStream): List<Channel> {
        val channelsByKey = linkedMapOf<String, Channel>()
        var pendingInfo: ExtInfo? = null

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.lineSequence().forEach { rawLine ->
                val line = rawLine.removePrefix("\uFEFF").trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseInfo(line)
                    line.startsWith("#") -> Unit
                    pendingInfo != null -> {
                        val info = pendingInfo ?: return@forEach
                        if (isSupportedUrl(line)) {
                            val channel = Channel(
                                number = channelsByKey.size + 1,
                                name = info.name.ifBlank { "Канал ${channelsByKey.size + 1}" },
                                url = line,
                                tvgId = info.tvgId,
                                logoUrl = info.logoUrl,
                                groupTitle = info.groupTitle,
                            )
                            if (channel.stableKey !in channelsByKey) {
                                channelsByKey[channel.stableKey] = channel
                            }
                        }
                        pendingInfo = null
                    }
                }
            }
        }

        return channelsByKey.values.mapIndexed { index, channel ->
            channel.copy(number = index + 1)
        }
    }

    private fun parseInfo(line: String): ExtInfo {
        val payload = line.substringAfter(':', missingDelimiterValue = "")
        val commaIndex = findFirstUnquotedComma(payload)
        val name = if (commaIndex >= 0) payload.substring(commaIndex + 1).trim() else ""
        return ExtInfo(
            name = name,
            tvgId = attribute(line, "tvg-id"),
            logoUrl = attribute(line, "tvg-logo"),
            groupTitle = attribute(line, "group-title"),
        )
    }

    private fun findFirstUnquotedComma(value: String): Int {
        var inQuotes = false
        value.forEachIndexed { index, character ->
            when (character) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return index
            }
        }
        return -1
    }

    private fun attribute(line: String, name: String): String? {
        val pattern = Regex(
            pattern = """(?:^|\s)${Regex.escape(name)}\s*=\s*"([^"]*)"""",
            option = RegexOption.IGNORE_CASE,
        )
        return pattern.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun isSupportedUrl(value: String): Boolean {
        if (value.length > MAX_URL_LENGTH || value.any { it == '\r' || it == '\n' }) return false
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("rtsp://", ignoreCase = true)
    }

    private data class ExtInfo(
        val name: String,
        val tvgId: String?,
        val logoUrl: String?,
        val groupTitle: String?,
    )

    private companion object {
        const val MAX_URL_LENGTH = 8_192
    }
}
