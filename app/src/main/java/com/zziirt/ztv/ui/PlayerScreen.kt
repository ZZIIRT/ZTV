package com.zziirt.ztv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zziirt.ztv.channels.Channel
import com.zziirt.ztv.preferences.AppSettings

@Composable
fun ZtvApp(
    viewModel: ZtvViewModel,
    onChannelChanged: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentChannel = state.currentChannel

    LaunchedEffect(currentChannel?.stableKey) {
        currentChannel?.let { onChannelChanged(it.name) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onEnterForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onEnterBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            PlayerScreen(state, viewModel)
        }
    }
}

@Composable
private fun PlayerScreen(state: ZtvUiState, viewModel: ZtvViewModel) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> viewModel.playerController.tvPlayer.createView(context) },
            update = { it.player = viewModel.playerController.tvPlayer.exoPlayer },
        )

        if (state.osdVisible) {
            ChannelOverlay(state)
        }

        state.statusMessage?.let {
            StatusMessage(it, state.settings.textSizeMode.scale)
        }

        if (state.isChannelListOpen) {
            ChannelListOverlay(state)
        }

        if (state.isSettingsOpen) {
            SettingsOverlay(state)
        }
    }
}

@Composable
private fun ChannelOverlay(state: ZtvUiState) {
    val channel = state.currentChannel ?: return
    val scale = state.settings.textSizeMode.scale
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(42.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xCC000000))
                .padding(horizontal = 34.dp, vertical = 24.dp),
        ) {
            if (state.settings.showNumbers) {
                Text(
                    text = channel.number.toString(),
                    color = Color.White,
                    fontSize = 34.sp * scale,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = channel.name.uppercase(),
                color = Color.White,
                fontSize = 42.sp * scale,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusMessage(message: String, scale: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier
                .background(Color(0xD9000000))
                .padding(horizontal = 36.dp, vertical = 24.dp),
            text = message,
            color = Color.White,
            fontSize = 30.sp * scale,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChannelListOverlay(state: ZtvUiState) {
    val listState = rememberLazyListState()
    val scale = state.settings.textSizeMode.scale
    val channels = state.channelListChannels()

    LaunchedEffect(state.channelListIndex) {
        listState.animateScrollToItem(state.channelListIndex.coerceAtLeast(0))
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xE6000000))
                .padding(horizontal = 18.dp, vertical = 22.dp),
        ) {
            Text(
                text = if (state.isMovingFavorite) "Порядок любимых" else "Каналы",
                color = Color.White,
                fontSize = 30.sp * scale,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    state.isMovingFavorite -> "Любимые каналы: ${channels.size}"
                    state.settings.favoritesOnly -> "Переключение: только любимые"
                    else -> "Переключение: все каналы"
                },
                color = Color(0xFFCCCCCC),
                fontSize = 18.sp * scale,
            )
            Spacer(Modifier.height(18.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
            ) {
                if (channels.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            text = "Любимых каналов пока нет",
                            color = Color(0xFFCCCCCC),
                            fontSize = 24.sp * scale,
                        )
                    }
                }
                itemsIndexed(channels, key = { _, channel -> channel.stableKey }) { index, channel ->
                    ChannelRow(
                        channel = channel,
                        settings = state.settings,
                        selected = index == state.channelListIndex,
                        current = channel.stableKey == state.currentChannel?.stableKey,
                        favorite = channel.stableKey in state.settings.favoriteKeys,
                        moving = state.isMovingFavorite && index == state.channelListIndex,
                    )
                }
                if (!state.isMovingFavorite) {
                    item(key = "settings") {
                        SettingsRow(selected = state.channelListIndex == channels.size, scale = scale)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    settings: AppSettings,
    selected: Boolean,
    current: Boolean,
    favorite: Boolean,
    moving: Boolean,
) {
    val scale = settings.textSizeMode.scale
    val background = when {
        selected -> Color.White
        current -> Color(0xFF2E6BFF)
        else -> Color.Transparent
    }
    val foreground = if (selected) Color.Black else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showNumbers) {
            Text(
                modifier = Modifier.width(58.dp),
                text = channel.number.toString(),
                color = foreground,
                fontSize = 26.sp * scale,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = channel.name,
            color = foreground,
            fontSize = 26.sp * scale,
            fontWeight = if (current || selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (favorite) {
            Text(
                text = if (moving) "↕ ★" else "★",
                color = foreground,
                fontSize = 25.sp * scale,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SettingsRow(selected: Boolean, scale: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚙ Настройки",
            color = if (selected) Color.Black else Color.White,
            fontSize = 27.sp * scale,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsOverlay(state: ZtvUiState) {
    val scale = state.settings.textSizeMode.scale
    val items = settingsLabels(state)
    val listState = rememberLazyListState()

    LaunchedEffect(state.settingsIndex) {
        listState.animateScrollToItem(state.settingsIndex + 1)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LazyColumn(
            modifier = Modifier
                .width(720.dp)
                .fillMaxHeight(0.92f)
                .background(Color(0xF2000000))
                .padding(28.dp),
            state = listState,
        ) {
            item(key = "header") {
                Column {
                    Text(
                        text = "Настройки",
                        color = Color.White,
                        fontSize = 34.sp * scale,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
            itemsIndexed(items, key = { index, _ -> SettingsItem.entries[index].name }) { index, label ->
                val selected = index == state.settingsIndex
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color.White else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    text = label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 25.sp * scale,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun settingsLabels(state: ZtvUiState): List<String> {
    val settings = state.settings
    return listOf(
        "Переключать: ${if (settings.favoritesOnly) "Только любимые" else "Все каналы"}",
        "Обновить плейлист сейчас",
        "Изменить порядок любимых",
        "Очистить любимые",
        "Автоматически запускать последний канал: ${settings.autoPlayLastChannel.onOff()}",
        "Автозапуск после загрузки Android TV: ${settings.bootAutostart.onOff()}",
        "Системное разрешение автозапуска: ${state.overlayAutostartPermissionGranted.onOff()}",
        "Проверить автозапуск сейчас",
        "Надёжный автозапуск: открыть Специальные возможности",
        "Пропускать неработающие каналы: ${settings.skipUnavailableChannels.onOff()}",
        "Таймаут подключения: ${settings.connectionTimeoutSeconds} сек.",
        "Размер текста: ${settings.textSizeMode.label}",
        "Показывать логотипы: ${settings.showLogos.onOff()}",
        "Показывать номера каналов: ${settings.showNumbers.onOff()}",
        "Назад к каналам",
    )
}

private fun Boolean.onOff(): String = if (this) "ВКЛ" else "ВЫКЛ"
