package com.zziirt.ztv.channels

data class Channel(
    val number: Int,
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
) {
    val stableKey: String = tvgId?.takeIf { it.isNotBlank() } ?: normalizeName(name)

    companion object {
        fun normalizeName(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), "")
                .trim()
    }
}
