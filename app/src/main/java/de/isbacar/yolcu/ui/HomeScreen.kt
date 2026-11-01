package de.isbacar.yolcu.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.isbacar.yolcu.Prefs
import de.isbacar.yolcu.ShizukuManager
import de.isbacar.yolcu.ShizukuManager.State
import de.isbacar.yolcu.UpdateChecker

@Composable
fun HomeScreen(padding: PaddingValues, onOpenApps: () -> Unit, onOpenGuide: () -> Unit) {
    val ctx = LocalContext.current
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val apps by Prefs.carApps.collectAsStateWithLifecycle()
    val passenger by Prefs.passengerMode.collectAsStateWithLifecycle()
    val setPassenger = rememberPassengerToggle()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ShizukuManager.refresh()
        tick++
    }
    val fine = remember(tick) { ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val background = remember(tick) { ctx.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val notifications = remember(tick) { ctx.granted(Manifest.permission.POST_NOTIFICATIONS) }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check() }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(ready = shizuku == State.READY) }

        update?.let { u ->
            item {
                StatusCard(
                    Icons.Rounded.SystemUpdate, "Update auf ${u.version} verfügbar",
                    u.notes.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n")
                        .ifBlank { "Neue Version von Yolcu." },
                    Status.HINT, "Herunterladen",
                ) { ctx.startActivity(Intent(Intent.ACTION_VIEW, u.downloadUrl.toUri())) }
            }
        }

        item { SectionHeader("Einrichtung") }
        item {
            when (shizuku) {
                State.READY -> StatusCard(
                    Icons.Rounded.Bolt, "Shizuku verbunden",
                    "Apps laufen im Auto auf einem eigenen Bildschirm – dein Handy bleibt frei.", Status.OK,
                )
                State.NO_PERMISSION -> StatusCard(
                    Icons.Rounded.Bolt, "Shizuku-Zugriff erlauben",
                    "Shizuku läuft. Yolcu braucht noch deine Erlaubnis.", Status.TODO, "Erlauben",
                ) { if (!ShizukuManager.requestPermission()) ctx.openShizuku() }
                State.NOT_RUNNING -> StatusCard(
                    Icons.Rounded.Bolt, "Shizuku starten",
                    "Nach jedem Neustart des Handys einmal in Shizuku auf „Starten“ tippen (WLAN nötig). " +
                        "Bis dahin geht nur Spiegeln.", Status.TODO, "Shizuku öffnen",
                ) { ctx.openShizuku() }
                State.NOT_INSTALLED -> StatusCard(
                    Icons.Rounded.Bolt, "Shizuku installieren",
                    "Kostenlos im Play Store. Damit laufen Apps im Auto unabhängig vom Handy.", Status.TODO, "Zum Play Store",
                ) { ctx.openPlayStore(ShizukuManager.SHIZUKU_PACKAGE) }
            }
        }
        item {
            if (apps.isEmpty()) {
                StatusCard(
                    Icons.Rounded.Apps, "Apps fürs Auto auswählen",
                    "Lege fest, welche Apps du in Android Auto starten willst.", Status.TODO, "Auswählen", onOpenApps,
                )
            } else {
                StatusCard(
                    Icons.Rounded.Apps, "${apps.size} ${if (apps.size == 1) "App" else "Apps"} im Auto",
                    "Erscheinen in Android Auto unter Yolcu.", Status.OK, "Ändern", onOpenApps,
                )
            }
        }
        item {
            when {
                !fine -> StatusCard(
                    Icons.Rounded.Speed, "Fahrt-Erkennung erlauben",
                    "Yolcu nutzt die Geschwindigkeit des Autos. Liefert das Auto keine, springt das GPS des Handys ein.",
                    Status.TODO, "Standort erlauben",
                ) {
                    locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
                !background -> StatusCard(
                    Icons.Rounded.Speed, "Standort „Immer erlauben“",
                    "Damit die GPS-Reserve auch funktioniert, während Android Auto läuft.", Status.HINT, "Einstellen",
                ) { singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                else -> StatusCard(
                    Icons.Rounded.Speed, "Fahrt-Erkennung bereit",
                    "Geschwindigkeit vom Auto, GPS als Reserve.", Status.OK,
                )
            }
        }
        if (!notifications) {
            item {
                StatusCard(
                    Icons.Rounded.Notifications, "Benachrichtigungen erlauben",
                    "Nötig, damit du das Spiegeln vom Auto aus freigeben kannst.", Status.TODO, "Erlauben",
                ) { singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
        item {
            StatusCard(
                Icons.Rounded.Widgets, "Android Auto vorbereiten",
                "Einmalig „Unbekannte Quellen“ in den Entwicklereinstellungen von Android Auto einschalten.",
                Status.HINT, "Anleitung", onOpenGuide,
            )
        }

        item { SectionHeader("Fahrt") }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (passenger) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (passenger) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bild während der Fahrt", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (passenger) "An – Beifahrer-Modus. Nur mit festem Blickschutz für den Fahrer."
                            else "Aus – beim Fahren läuft nur der Ton, das Bild pausiert.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = passenger, onCheckedChange = setPassenger)
                }
            }
        }

        item {
            OutlinedCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Netflix, Disney+, HBO Max, Tod, Prime Video & Co. sperren ihre Videos für fremde " +
                            "Bildschirme. Im Auto bleibt deren Bild schwarz – das liegt am Kopierschutz der " +
                            "Anbieter und betrifft jede Spiegel-Lösung. YouTube, Browser, IPTV, Mediatheken und " +
                            "die meisten anderen Apps funktionieren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(ready: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(24.dp),
    ) {
        Column {
            Text(
                "Yolcu",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
            )
            Text(
                "Dein Beifahrer-Bildschirm für Android Auto",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimary.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (ready) Color(0xFF7CFFB2) else Color(0xFFFFD27A)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ready) "Bereit – Apps laufen unabhängig vom Handy" else "Nur Spiegeln – Shizuku einrichten",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}
