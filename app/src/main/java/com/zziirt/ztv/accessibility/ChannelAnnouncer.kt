package com.zziirt.ztv.accessibility

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class ChannelAnnouncer(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var released = false
    private var ready = false
    private var initializationGeneration = 0
    private var failureCount = 0
    private val queue = ChannelAnnouncementQueue(::speakNow)

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        if (released || ready) return
        val generation = ++initializationGeneration
        engine = TextToSpeech(applicationContext) { status ->
            handleInitialization(generation, status)
        }
        mainHandler.postDelayed(
            {
                if (!released && !ready && generation == initializationGeneration) {
                    Log.w(TAG, "Speech engine initialization timed out")
                    scheduleRetry(generation)
                }
            },
            ChannelAnnouncerRetryPolicy.INITIALIZATION_TIMEOUT_MS,
        )
    }

    private fun handleInitialization(generation: Int, status: Int) {
        if (released || ready || generation != initializationGeneration) return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Speech engine initialization failed with status $status")
            scheduleRetry(generation)
            return
        }
        val activeEngine = engine ?: return
        val languageStatus = activeEngine.setLanguage(Locale.forLanguageTag("ru-RU"))
        if (
            languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "Russian speech data is not ready: $languageStatus")
            scheduleRetry(generation)
            return
        }
        activeEngine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        activeEngine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.i(TAG, "Channel announcement started")
                }

                override fun onDone(utteranceId: String?) = Unit

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "Channel announcement failed")
                }
            },
        )
        ready = true
        queue.markReady()
    }

    private fun scheduleRetry(generation: Int) {
        if (released || ready || generation != initializationGeneration) return
        initializationGeneration++
        engine?.shutdown()
        engine = null
        val delay = ChannelAnnouncerRetryPolicy.delayAfterFailure(failureCount++)
        if (delay == null) {
            Log.e(TAG, "Speech engine did not become ready after all retries")
            return
        }
        mainHandler.postDelayed(::initializeEngine, delay)
    }

    fun announce(channelName: String) {
        queue.announce(channelName)
    }

    fun release() {
        if (released) return
        released = true
        initializationGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        queue.clear()
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun speakNow(channelName: String) {
        val result = engine?.speak(
            channelName,
            TextToSpeech.QUEUE_FLUSH,
            null,
            CHANNEL_UTTERANCE_ID,
        )
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "Speech engine rejected a channel announcement")
        }
    }

    private companion object {
        const val TAG = "ZtvChannelAnnouncer"
        const val CHANNEL_UTTERANCE_ID = "ztv-channel-name"
    }
}

internal object ChannelAnnouncerRetryPolicy {
    const val INITIALIZATION_TIMEOUT_MS = 15_000L
    private val retryDelaysMs = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L)

    fun delayAfterFailure(failureCount: Int): Long? = retryDelaysMs.getOrNull(failureCount)
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
