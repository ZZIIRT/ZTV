package com.zziirt.ztv.accessibility

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

class ChannelAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private var engine: TextToSpeech? = null
    private var released = false
    private val queue = ChannelAnnouncementQueue(::speakNow)

    init {
        engine = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (released || status != TextToSpeech.SUCCESS) return
        val activeEngine = engine ?: return
        activeEngine.setLanguage(Locale.forLanguageTag("ru-RU"))
        activeEngine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        queue.markReady()
    }

    fun announce(channelName: String) {
        queue.announce(channelName)
    }

    fun release() {
        if (released) return
        released = true
        queue.clear()
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun speakNow(channelName: String) {
        engine?.speak(
            channelName,
            TextToSpeech.QUEUE_FLUSH,
            null,
            CHANNEL_UTTERANCE_ID,
        )
    }

    private companion object {
        const val CHANNEL_UTTERANCE_ID = "ztv-channel-name"
    }
}

internal class ChannelAnnouncementQueue(
    private val speaker: (String) -> Unit,
) {
    private var ready = false
    private var pendingChannelName: String? = null

    fun announce(channelName: String) {
        val normalizedName = channelName.trim().takeIf { it.isNotEmpty() } ?: return
        if (ready) {
            speaker(normalizedName)
        } else {
            pendingChannelName = normalizedName
        }
    }

    fun markReady() {
        ready = true
        pendingChannelName?.let(speaker)
        pendingChannelName = null
    }

    fun clear() {
        ready = false
        pendingChannelName = null
    }
}
