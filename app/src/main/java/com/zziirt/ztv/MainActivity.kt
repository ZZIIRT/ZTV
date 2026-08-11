package com.zziirt.ztv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.zziirt.ztv.accessibility.ChannelAnnouncer
import com.zziirt.ztv.ui.ZtvApp
import com.zziirt.ztv.ui.ZtvViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ZtvViewModel by viewModels()
    private lateinit var channelAnnouncer: ChannelAnnouncer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelAnnouncer = ChannelAnnouncer(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ZtvApp(viewModel, channelAnnouncer::announce)
        }
    }

    override fun onDestroy() {
        channelAnnouncer.release()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.onUp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.onDown()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.onLeft()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (viewModel.onRight()) true else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                viewModel.onOk(event.isLongPress)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                viewModel.onChannelUp()
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                viewModel.onChannelDown()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (viewModel.onBack()) true else super.onKeyDown(keyCode, event)
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                viewModel.onDigit(keyCode - KeyEvent.KEYCODE_0)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
