package de.isbacar.yolcu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YolcuTheme { YolcuUi() } }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Start", Icons.Rounded.DirectionsCar),
    Tab("Apps", Icons.Rounded.Apps),
    Tab("Einstellungen", Icons.Rounded.Tune),
)

@Composable
private fun YolcuUi() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var guide by rememberSaveable { mutableStateOf(false) }

    if (guide) {
        BackHandler { guide = false }
        GuideScreen(onClose = { guide = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(padding, onOpenApps = { tab = 1 }, onOpenGuide = { guide = true })
            1 -> AppsScreen(padding)
            else -> SettingsScreen(padding, onOpenGuide = { guide = true })
        }
    }
}
