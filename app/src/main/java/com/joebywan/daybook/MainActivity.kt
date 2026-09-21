package com.joebywan.daybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joebywan.daybook.ui.DaybookApp
import com.joebywan.daybook.ui.theme.DaybookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DaybookTheme {
                DaybookApp()
            }
        }
    }
}
