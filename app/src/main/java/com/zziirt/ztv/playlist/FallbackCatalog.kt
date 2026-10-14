package com.zziirt.ztv.playlist

import android.content.Context
import android.util.AtomicFile
import com.zziirt.ztv.channels.Channel
import com.zziirt.ztv.preferences.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

internal class FallbackCatalog(context: Context, client: OkHttpClient) {
    private val assets = context.applicationContext.assets
    private val cacheFile = File(context.filesDir, FILE_NAME)
    private val httpClient = client.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun decorate(channels: List<Channel>, refresh: Boolean): List<Channel> {
        if (channels.isEmpty()) return channels
        val downloaded = if (refresh) runCatching { download() }.getOrNull() else null
        val catalog = downloaded ?: runCatching {
            require(cacheFile.length() in 1..MAX_BYTES)
            parse(cacheFile.readText())
        }.getOrElse {
            runCatching { assets.open(FILE_NAME).bufferedReader().use { parse(it.readText()) } }
                .getOrDefault(emptyMap())
        }
        return channels.map { channel ->
            channel.copy(alternativeUrls = catalog[channel.tvgId].orEmpty()
                .filter { it != channel.url }.distinct().take(8))
        }
    }

    private fun download(): Map<String, List<String>> {
        val request = Request.Builder().url(AppSettings.DEFAULT_FALLBACK_URL).build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Fallback catalog HTTP ${response.code}" }
            val body = requireNotNull(response.body)
            val source = body.source()
            check(!source.request(MAX_BYTES + 1)) { "Fallback catalog is too large" }
            val text = source.readUtf8()
            val catalog = parse(text)
            val atomicFile = AtomicFile(cacheFile)
            val output = atomicFile.startWrite()
            try {
                output.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
            catalog
        }
    }

    private fun parse(text: String): Map<String, List<String>> {
        val document = JSONObject(text)
        require(document.getInt("schema") == 1)
        val channels = document.getJSONObject("channels")
        require(channels.length() in 1..100)
        return channels.keys().asSequence().associateWith { key ->
            val urls = channels.getJSONArray(key)
            (0 until urls.length().coerceAtMost(9)).mapNotNull { index ->
                urls.optString(index).takeIf(::isPlayableUrl)
            }.distinct()
        }
    }

    private fun isPlayableUrl(url: String): Boolean = runCatching {
        if (url.length > 8192 || url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = URI(url)
        uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
            uri.path?.endsWith(".m3u8") == true
    }.getOrDefault(false)

    private companion object {
        const val FILE_NAME = "playlist-fallbacks.json"
        const val MAX_BYTES = 256L * 1024
    }
}
