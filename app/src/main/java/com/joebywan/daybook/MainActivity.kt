package com.joebywan.daybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joebywan.daybook.ui.DaybookApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // DaybookApp applies the theme itself, since the player's light/dark choice is a setting.
        setContent { DaybookApp() }
    }
}
