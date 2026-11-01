package de.isbacar.yolcu.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.isbacar.yolcu.Prefs
import de.isbacar.yolcu.ShizukuManager

enum class Status { OK, TODO, HINT }

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    text: String,
    status: Status,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val (badge, tint) = when (status) {
        Status.OK -> Icons.Rounded.CheckCircle to scheme.primary
        Status.TODO -> Icons.Rounded.ErrorOutline to scheme.error
        Status.HINT -> Icons.Rounded.WarningAmber to scheme.tertiary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = scheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Icon(badge, null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (action != null && onAction != null) {
                    FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
                }
            }
        }
    }
}

/** Einschalten des Beifahrer-Modus nur nach ausdrücklicher Bestätigung. */
@Composable
fun rememberPassengerToggle(): (Boolean) -> Unit {
    var ask by remember { mutableStateOf(false) }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            icon = { Icon(Icons.Rounded.WarningAmber, null) },
            title = { Text("Bild während der Fahrt?") },
            text = {
                Text(
                    "Im Beifahrer-Modus läuft das Video auch während der Fahrt weiter.\n\n" +
                        "Schalte ihn nur ein, wenn der Fahrer das Display durch einen fest montierten " +
                        "Blickschutz nicht sehen kann. Video im Blickfeld des Fahrers ist gefährlich und " +
                        "nach § 23 StVO verboten."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setPassengerMode(true)
                    ask = false
                }) { Text("Blickschutz ist da – einschalten") }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Abbrechen") } },
        )
    }
    return remember { { on: Boolean -> if (on) ask = true else Prefs.setPassengerMode(false) } }
}

fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.openShizuku() {
    val launch = packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
    if (launch != null) startActivity(launch) else openPlayStore(ShizukuManager.SHIZUKU_PACKAGE)
}

fun Context.openPlayStore(pkg: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()))
    }
}
