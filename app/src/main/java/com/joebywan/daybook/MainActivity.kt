package com.joebywan.daybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joebywan.daybook.platform.DailyReminder
import com.joebywan.daybook.ui.DaybookApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The reminder's alarm, set again from the stored setting: a clock or time-zone change since can have moved it.
        if (savedInstanceState == null) DailyReminder.reschedule(applicationContext)
        // DaybookApp applies the theme itself, since the player's light/dark choice is a setting.
        setContent { DaybookApp() }
    }
}
