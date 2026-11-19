package com.healthcoach.ircoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthcoach.ircoach.data.Settings
import com.healthcoach.ircoach.reminders.Reminders
import com.healthcoach.ircoach.ui.ChatScreen
import com.healthcoach.ircoach.ui.ChatViewModel
import com.healthcoach.ircoach.ui.CoachColors
import com.healthcoach.ircoach.ui.CoachTheme
import com.healthcoach.ircoach.ui.ProgressScreen
import com.healthcoach.ircoach.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.createChannel(this)
        Reminders.scheduleAll(this)
        val startTab = if (!Settings(this).setupDone) 2 else 0
        setContent {
            CoachTheme {
                val vm: ChatViewModel = viewModel()
                var tab by rememberSaveable { mutableIntStateOf(startTab) }
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CoachColors.Green,
                    selectedTextColor = CoachColors.Green,
                    indicatorColor = CoachColors.Mint,
                    unselectedIconColor = CoachColors.InkMuted,
                    unselectedTextColor = CoachColors.InkMuted,
                )
                Scaffold(
                    containerColor = Color.White,
                    bottomBar = {
                        Column {
                            HorizontalDivider(color = CoachColors.Outline)
                            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(painterResource(R.drawable.ic_chat), null) }, label = { Text("Coach") }, colors = navColors)
                                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(painterResource(R.drawable.ic_chart), null) }, label = { Text("Progress") }, colors = navColors)
                                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(painterResource(R.drawable.ic_settings), null) }, label = { Text("Settings") }, colors = navColors)
                            }
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    when (tab) {
                        0 -> ChatScreen(vm, m)
                        1 -> ProgressScreen(vm, m)
                        else -> SettingsScreen(m, onSaved = { Reminders.scheduleAll(this@MainActivity); tab = 0 })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.tryEmit(Unit)
    }

    companion object {
        /** Ping to make the UI reload from the database (e.g. after a reminder fires). */
        val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    }
}
