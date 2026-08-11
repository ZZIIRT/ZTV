package com.zziirt.ztv

import android.os.Bundle
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
}
