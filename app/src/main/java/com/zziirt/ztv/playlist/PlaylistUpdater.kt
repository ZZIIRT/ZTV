package com.zziirt.ztv.playlist

import com.zziirt.ztv.preferences.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaylistUpdater(
    private val scope: CoroutineScope,
    private val repository: PlaylistRepository,
    private val onResult: suspend (RefreshResult) -> Unit,
) {
    private var job: Job? = null

    fun start(settingsProvider: () -> AppSettings) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val settings = settingsProvider()
                onResult(repository.refresh(settings.playlistUrl))
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 15L * 60L * 1000L
    }
}
