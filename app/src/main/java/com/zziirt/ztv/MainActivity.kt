package com.zziirt.ztv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.zziirt.ztv.ui.ZtvApp
import com.zziirt.ztv.ui.ZtvViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ZtvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ZtvApp(viewModel)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.onUp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.onDown()
                true
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
                if (viewModel.onBack()) true else super.dispatchKeyEvent(event)
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                viewModel.onDigit(event.keyCode - KeyEvent.KEYCODE_0)
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }
}
