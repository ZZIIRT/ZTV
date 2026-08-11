package com.zziirt.ztv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zziirt.ztv.channels.Channel
import com.zziirt.ztv.channels.FavoriteOrder
import com.zziirt.ztv.channels.FavoritesRepository
import com.zziirt.ztv.player.PlayerController
import com.zziirt.ztv.playlist.PlaylistRepository
import com.zziirt.ztv.playlist.PlaylistUpdater
import com.zziirt.ztv.playlist.RefreshResult
import com.zziirt.ztv.preferences.AppSettings
import com.zziirt.ztv.preferences.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZtvViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PreferencesRepository(application)
    private val playlistRepository = PlaylistRepository(application)
    private val favoritesRepository = FavoritesRepository()
    private val updater = PlaylistUpdater(viewModelScope, playlistRepository, ::handleRefreshResult)

    private var osdJob: Job? = null
    private var timeoutJob: Job? = null
    private var recoveryJob: Job? = null
    private var directInputJob: Job? = null
    private var playGeneration = 0L
    private var currentRetry = 0
    private var consecutiveFailures = 0

    private val _uiState = MutableStateFlow(ZtvUiState())
    val uiState: StateFlow<ZtvUiState> = _uiState

    val playerController = PlayerController(
        context = application,
        onReady = ::onPlayerReady,
        onError = ::onPlaybackFailed,
    )

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                _uiState.update {
                    val visible = favoritesRepository.visibleChannels(it.channels, settings)
                    val updated = it.copy(settings = settings, visibleChannels = visible)
                    updated.copy(channelListIndex = updated.channelListIndex.coerceIn(0, updated.channelListChannels().size))
                }
            }
        }

        viewModelScope.launch {
            bootstrap()
        }
    }

    fun onEnterForeground() {
        viewModelScope.launch {
            val current = _uiState.value.currentChannel
            if (current != null && _uiState.value.settings.autoPlayLastChannel) {
                playChannel(current, forceReconnect = true)
            }
            updater.start { _uiState.value.settings }
        }
    }

    fun onEnterBackground() {
        playGeneration += 1
        timeoutJob?.cancel()
        recoveryJob?.cancel()
        playerController.stopForBackground()
        updater.stop()
    }

    fun onUp() {
        when {
            _uiState.value.isSettingsOpen -> moveSettings(-1)
            _uiState.value.isChannelListOpen -> moveChannelList(-1)
            else -> switchChannel(1)
        }
    }

    fun onDown() {
        when {
            _uiState.value.isSettingsOpen -> moveSettings(1)
            _uiState.value.isChannelListOpen -> moveChannelList(1)
            else -> switchChannel(-1)
        }
    }

    fun onLeft() {
        if (_uiState.value.isMovingFavorite) return
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                isChannelListOpen = false,
                settingsIndex = 0,
            )
        }
    }

    fun onRight(): Boolean = onBack()

    fun onOk(longPress: Boolean = false) {
        when {
            _uiState.value.isMovingFavorite -> confirmFavoriteOrder()
            longPress && _uiState.value.isChannelListOpen -> toggleFavoriteForMenu()
            longPress -> toggleCurrentFavorite()
            _uiState.value.isSettingsOpen -> activateSetting()
            _uiState.value.isChannelListOpen -> selectMenuItem()
            else -> openChannelList()
        }
    }

    fun onBack(): Boolean {
        return when {
            _uiState.value.isMovingFavorite -> {
                _uiState.update {
                    it.copy(
                        isMovingFavorite = false,
                        pendingFavoriteOrder = null,
                        isChannelListOpen = false,
                        isSettingsOpen = true,
                        settingsIndex = SettingsItem.MoveFavorite.ordinal,
                    )
                }
                true
            }
            _uiState.value.isSettingsOpen -> {
                _uiState.update { it.copy(isSettingsOpen = false, isChannelListOpen = true) }
                true
            }
            _uiState.value.isChannelListOpen -> {
                _uiState.update {
                    it.copy(
                        isChannelListOpen = false,
                        isMovingFavorite = false,
                        pendingFavoriteOrder = null,
                    )
                }
                true
            }
            else -> false
        }
    }

    fun onChannelUp() {
        switchChannel(1)
    }

    fun onChannelDown() {
        switchChannel(-1)
    }

    fun onDigit(digit: Int) {
        if (digit == 0 && _uiState.value.directInput.isBlank()) return
        val next = (_uiState.value.directInput + digit).take(3)
        _uiState.update { it.copy(directInput = next, osdVisible = true, statusMessage = "Канал $next") }
        directInputJob?.cancel()
        directInputJob = viewModelScope.launch {
            delay(1200)
            val number = next.toIntOrNull()
            val channel = _uiState.value.channels.firstOrNull { it.number == number }
            if (channel != null) playChannel(channel) else showMessage("Канал $next не найден")
            _uiState.update { it.copy(directInput = "") }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            showMessage("Обновляю плейлист...")
            handleRefreshResult(playlistRepository.refresh(_uiState.value.settings.playlistUrl))
        }
    }

    private suspend fun bootstrap() {
        val settings = preferences.settings.first()
        val cached = playlistRepository.loadCached()
        if (cached.isNotEmpty()) {
            applyChannels(cached, settings)
        }

        handleRefreshResult(playlistRepository.refresh(settings.playlistUrl))
        updater.start { _uiState.value.settings }
    }

    private suspend fun handleRefreshResult(result: RefreshResult) {
        when (result) {
            is RefreshResult.Success -> {
                preferences.setLastPlaylistUpdate(result.updatedAtEpochMillis)
                applyChannels(result.channels, _uiState.value.settings)
                showMessage("Плейлист обновлён")
            }
            is RefreshResult.Failure -> {
                if (result.cachedChannels.isNotEmpty()) {
                    applyChannels(result.cachedChannels, _uiState.value.settings)
                    showMessage("Интернет недоступен, включена сохранённая копия")
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Не удалось загрузить плейлист: ${result.message}",
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyChannels(channels: List<Channel>, settings: AppSettings) {
        val visible = favoritesRepository.visibleChannels(channels, settings)
        val current = chooseCurrentChannel(channels, visible, settings)
        _uiState.update {
            it.copy(
                channels = channels,
                visibleChannels = visible,
                currentChannel = current,
                isLoading = false,
                statusMessage = null,
            )
        }
        if (current != null && settings.autoPlayLastChannel) {
            playChannel(current)
        }
    }

    private fun chooseCurrentChannel(
        channels: List<Channel>,
        visibleChannels: List<Channel>,
        settings: AppSettings,
    ): Channel? {
        val preferred = visibleChannels.ifEmpty { channels }
        return preferred.firstOrNull { it.stableKey == settings.lastChannelKey } ?: preferred.firstOrNull()
    }

    private fun switchChannel(direction: Int) {
        val state = _uiState.value
        val current = state.currentChannel ?: return
        val visible = state.visibleChannels
        if (visible.isEmpty()) {
            showMessage("Нет любимых каналов")
            return
        }
        val index = visible.indexOfFirst { it.stableKey == current.stableKey }
        val targetIndex = if (index < 0) {
            if (direction >= 0) 0 else visible.lastIndex
        } else {
            (index + direction).floorMod(visible.size)
        }
        playChannel(visible[targetIndex])
    }

    private fun playChannel(channel: Channel, forceReconnect: Boolean = false) {
        if (!forceReconnect && _uiState.value.currentChannel?.stableKey == channel.stableKey && playerController.tvPlayer.isReady()) {
            showOsd(channel)
            return
        }

        playGeneration += 1
        recoveryJob?.cancel()
        currentRetry = 0
        _uiState.update {
            it.copy(
                currentChannel = channel,
                isChannelListOpen = false,
                isSettingsOpen = false,
                statusMessage = null,
            )
        }
        viewModelScope.launch { preferences.saveLastChannel(channel.stableKey) }
        startPlayback(channel, playGeneration)
        showOsd(channel)
    }

    private fun startPlayback(channel: Channel, generation: Long) {
        playerController.play(channel, generation)
        scheduleTimeout(channel, generation)
    }

    private fun scheduleTimeout(channel: Channel, generation: Long) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(_uiState.value.settings.connectionTimeoutSeconds * 1000L)
            if (generation != playGeneration || playerController.tvPlayer.isReady()) return@launch
            if (currentRetry == 0) {
                currentRetry = 1
                showMessage("Повторное подключение: ${channel.name}")
                startPlayback(channel, generation)
            } else {
                onPlaybackFailed(generation, "Канал временно недоступен")
            }
        }
    }

    private fun onPlayerReady(playbackId: Long) {
        if (playbackId != playGeneration) return
        timeoutJob?.cancel()
        consecutiveFailures = 0
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun onPlaybackFailed(playbackId: Long, message: String) {
        if (playbackId != playGeneration) return
        timeoutJob?.cancel()
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(PLAYBACK_RECOVERY_DELAY_MS)
            if (playbackId != playGeneration) return@launch
            consecutiveFailures += 1
            val state = _uiState.value
            val skipLimit = state.visibleChannels.size.coerceAtMost(MAX_CONSECUTIVE_SKIPS)
            if (state.settings.skipUnavailableChannels && state.visibleChannels.size > 1 && consecutiveFailures < skipLimit) {
                showMessage("$message. Переключаю дальше")
                switchChannel(1)
            } else {
                showMessage(message)
            }
        }
    }

    private fun openChannelList() {
        val state = _uiState.value
        val visible = state.visibleChannels
        val currentIndex = visible.indexOfFirst { it.stableKey == state.currentChannel?.stableKey }.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                isChannelListOpen = true,
                isSettingsOpen = false,
                channelListIndex = currentIndex,
                isMovingFavorite = false,
                pendingFavoriteOrder = null,
            )
        }
    }

    private fun moveChannelList(direction: Int) {
        val state = _uiState.value
        if (state.isMovingFavorite) {
            moveCurrentFavorite(direction)
            return
        }
        val itemCount = state.channelListChannels().size + 1
        if (itemCount <= 0) return
        _uiState.update { it.copy(channelListIndex = (it.channelListIndex + direction).floorMod(itemCount)) }
    }

    private fun selectMenuItem() {
        val state = _uiState.value
        if (state.isMovingFavorite) {
            confirmFavoriteOrder()
            return
        }
        val channels = state.channelListChannels()
        if (state.channelListIndex >= channels.size) {
            _uiState.update { it.copy(isSettingsOpen = true, isChannelListOpen = false, settingsIndex = 0) }
            return
        }
        playChannel(channels[state.channelListIndex])
    }

    private fun toggleCurrentFavorite() {
        val current = _uiState.value.currentChannel ?: return
        val isFavorite = current.stableKey in _uiState.value.settings.favoriteKeys
        viewModelScope.launch {
            preferences.toggleFavorite(current.stableKey)
            showMessage(if (isFavorite) "Удалено из любимых" else "Добавлено в любимые")
        }
    }
    private fun toggleFavoriteForMenu() {
        val state = _uiState.value
        val channel = state.channelListChannels().getOrNull(state.channelListIndex) ?: return
        viewModelScope.launch {
            preferences.toggleFavorite(channel.stableKey)
            showMessage("Любимые обновлены")
        }
    }

    private fun moveCurrentFavorite(direction: Int) {
        val state = _uiState.value
        val channels = state.channelListChannels()
        val channel = channels.getOrNull(state.channelListIndex) ?: return
        val currentOrder = state.pendingFavoriteOrder ?: channels.map { it.stableKey }
        val movedOrder = FavoriteOrder.move(currentOrder, channel.stableKey, direction)
        _uiState.update {
            it.copy(
                pendingFavoriteOrder = movedOrder,
                channelListIndex = movedOrder.indexOf(channel.stableKey).coerceAtLeast(0),
            )
        }
    }

    private fun moveSettings(direction: Int) {
        _uiState.update { it.copy(settingsIndex = (it.settingsIndex + direction).floorMod(SettingsItem.entries.size)) }
    }

    private fun activateSetting() {
        when (SettingsItem.entries[_uiState.value.settingsIndex]) {
            SettingsItem.FavoritesOnly -> viewModelScope.launch { preferences.toggleFavoritesOnly() }
            SettingsItem.RefreshNow -> refreshNow()
            SettingsItem.MoveFavorite -> startMovingFavorites()
            SettingsItem.ClearFavorites -> viewModelScope.launch { preferences.clearFavorites() }
            SettingsItem.AutoPlay -> viewModelScope.launch { preferences.toggleAutoPlay() }
            SettingsItem.BootAutostart -> viewModelScope.launch { preferences.toggleBootAutostart() }
            SettingsItem.SkipUnavailable -> viewModelScope.launch { preferences.toggleSkipUnavailable() }
            SettingsItem.Timeout -> viewModelScope.launch { preferences.cycleTimeout() }
            SettingsItem.TextSize -> viewModelScope.launch { preferences.cycleTextSize() }
            SettingsItem.ShowLogos -> viewModelScope.launch { preferences.toggleShowLogos() }
            SettingsItem.ShowNumbers -> viewModelScope.launch { preferences.toggleShowNumbers() }
            SettingsItem.Close -> _uiState.update { it.copy(isSettingsOpen = false, isChannelListOpen = true) }
        }
    }

    private fun startMovingFavorites() {
        val favorites = favoritesRepository.orderedFavorites(_uiState.value.channels, _uiState.value.settings)
        if (favorites.isEmpty()) {
            showMessage("Сначала добавьте любимые каналы долгим нажатием OK")
            return
        }
        val currentIndex = favorites.indexOfFirst { it.stableKey == _uiState.value.currentChannel?.stableKey }
            .coerceAtLeast(0)
        _uiState.update {
            it.copy(
                isSettingsOpen = false,
                isChannelListOpen = true,
                isMovingFavorite = true,
                pendingFavoriteOrder = favorites.map { channel -> channel.stableKey },
                channelListIndex = currentIndex,
            )
        }
    }

    private fun confirmFavoriteOrder() {
        val order = _uiState.value.pendingFavoriteOrder ?: return
        _uiState.update {
            it.copy(
                settings = it.settings.copy(favoriteOrder = order),
                isMovingFavorite = false,
                pendingFavoriteOrder = null,
                isChannelListOpen = false,
                isSettingsOpen = true,
                settingsIndex = SettingsItem.MoveFavorite.ordinal,
            )
        }
        viewModelScope.launch {
            preferences.setFavoriteOrder(order)
            showMessage("Порядок любимых сохранён")
        }
    }

    private fun showOsd(channel: Channel) {
        osdJob?.cancel()
        _uiState.update { it.copy(osdVisible = true) }
        osdJob = viewModelScope.launch {
            delay(2800)
            _uiState.update { it.copy(osdVisible = false) }
        }
    }

    private fun showMessage(message: String) {
        osdJob?.cancel()
        _uiState.update { it.copy(osdVisible = true, statusMessage = message) }
        osdJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(osdVisible = false, statusMessage = null) }
        }
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        recoveryJob?.cancel()
        updater.stop()
        playerController.release()
        super.onCleared()
    }

    private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size
}

enum class SettingsItem {
    FavoritesOnly,
    RefreshNow,
    MoveFavorite,
    ClearFavorites,
    AutoPlay,
    BootAutostart,
    SkipUnavailable,
    Timeout,
    TextSize,
    ShowLogos,
    ShowNumbers,
    Close,
}

private const val MAX_CONSECUTIVE_SKIPS = 5
private const val PLAYBACK_RECOVERY_DELAY_MS = 250L
