package de.isbacar.yolcu.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.isbacar.yolcu.ShizukuManager

private class Step(val title: String, val body: String, val action: String? = null, val onAction: ((Context) -> Unit)? = null)

private val steps = listOf(
    Step(
        "Samsung „Automatische Sperre“ kurz aus",
        "Einstellungen → Sicherheit und Datenschutz → Automatische Sperre → aus. " +
            "Dann Yolcu installieren und die Sperre wieder einschalten.",
    ),
    Step(
        "Shizuku installieren",
        "Shizuku gibt Yolcu die Rechte, Apps auf einem eigenen Bildschirm im Auto zu starten.",
        "Zum Play Store",
    ) { it.openPlayStore(ShizukuManager.SHIZUKU_PACKAGE) },
    Step(
        "Shizuku starten",
        "Einmalig Entwickleroptionen aktivieren: Einstellungen → Telefoninfo → Softwareinformationen → " +
            "7× auf „Buildnummer“ tippen. Dann in Shizuku „Über kabelloses Debugging starten“ wählen und " +
            "der Kopplung folgen.\n\nNach jedem Neustart des Handys in Shizuku erneut auf „Starten“ tippen " +
            "(dafür muss das Handy in einem WLAN sein, z. B. zu Hause).",
        "Shizuku öffnen",
    ) { it.openShizuku() },
    Step(
        "Yolcu Zugriff geben",
        "Auf der Startseite von Yolcu bei „Shizuku-Zugriff erlauben“ tippen und bestätigen.",
    ),
    Step(
        "Android Auto: Unbekannte Quellen",
        "Einstellungen → Verbundene Geräte → Android Auto. Ganz unten 10× auf „Version“ tippen und bestätigen. " +
            "Dann oben rechts ⋮ → „Entwicklereinstellungen“ → „Unbekannte Quellen“ einschalten.",
    ),
    Step(
        "Apps auswählen",
        "Im Tab „Apps“ festlegen, welche Apps im Auto erscheinen sollen.",
    ),
    Step(
        "Los geht’s im Auto",
        "Handy verbinden, im Android-Auto-Menü Yolcu öffnen und eine App antippen. " +
            "Oben rechts: ← Zurück, ▦ andere App, ✕ schließen.\n\n" +
            "Taucht Yolcu nicht auf: In den Einstellungen „Alle Android-Auto-Versionen zulassen“ einschalten " +
            "oder Yolcu mit KingInstaller (Shizuku-Modus) neu installieren.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einrichtung") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(steps) { i, step ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                step.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (step.action != null && step.onAction != null) {
                                FilledTonalButton(onClick = { step.onAction.invoke(ctx) }, modifier = Modifier.padding(top = 8.dp)) {
                                    Text(step.action)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
