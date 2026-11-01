package de.isbacar.yolcu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.isbacar.yolcu.BuildConfig
import de.isbacar.yolcu.Prefs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(padding: PaddingValues, onOpenGuide: () -> Unit) {
    val passenger by Prefs.passengerMode.collectAsStateWithLifecycle()
    val threshold by Prefs.speedThreshold.collectAsStateWithLifecycle()
    val scale by Prefs.dpiScale.collectAsStateWithLifecycle()
    val decorations by Prefs.systemDecorations.collectAsStateWithLifecycle()
    val allHosts by Prefs.allowAllHosts.collectAsStateWithLifecycle()
    val setPassenger = rememberPassengerToggle()

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Einstellungen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(4.dp)) }

        item { SectionHeader("Fahrt") }
        item {
            SettingsCard {
                SwitchRow(
                    "Bild während der Fahrt",
                    "Beifahrer-Modus – nur mit festem Blickschutz für den Fahrer.",
                    passenger, setPassenger,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SliderRow(
                    title = "Bild pausiert ab",
                    value = "$threshold km/h",
                    hint = "Darunter gilt das Auto als stehend. Beim Anhalten kommt das Bild nach 5 Sekunden zurück.",
                    position = threshold.toFloat(),
                    range = 5f..20f,
                    steps = 14,
                ) { Prefs.setSpeedThreshold(it.roundToInt()) }
            }
        }

        item { SectionHeader("Darstellung im Auto") }
        item {
            SettingsCard {
                SliderRow(
                    title = "Größe der Inhalte",
                    value = "${(scale * 100).roundToInt()} %",
                    hint = "Kleiner = mehr Platz, größer = besser lesbar. Gilt beim nächsten Start einer App im Auto.",
                    position = scale,
                    range = 0.6f..1.6f,
                    steps = 9,
                ) { Prefs.setDpiScale((it * 10).roundToInt() / 10f) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(
                    "Tastatur & Systemleiste im Auto",
                    "Zeigt die Tastatur auf dem Auto-Display, sobald ein Textfeld aktiv ist.",
                    decorations, Prefs::setSystemDecorations,
                )
            }
        }

        item { SectionHeader("Kompatibilität") }
        item {
            SettingsCard {
                SwitchRow(
                    "Alle Android-Auto-Versionen zulassen",
                    "Nur einschalten, wenn Yolcu in Android Auto nicht auftaucht. Danach Handy einmal neu verbinden.",
                    allHosts, Prefs::setAllowAllHosts,
                )
            }
        }

        item { SectionHeader("Hilfe") }
        item {
            SettingsCard {
                ListItem(
                    headlineContent = { Text("Einrichtungs-Anleitung") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenGuide),
                )
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("Yolcu ${BuildConfig.VERSION_NAME}") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    hint: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = position, onValueChange = onChange, valueRange = range, steps = steps)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
