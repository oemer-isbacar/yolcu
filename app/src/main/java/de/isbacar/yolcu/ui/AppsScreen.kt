package de.isbacar.yolcu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.isbacar.yolcu.AppRepository
import de.isbacar.yolcu.LaunchableApp
import de.isbacar.yolcu.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Bekannte Apps, deren Videos per Kopierschutz für fremde Bildschirme gesperrt sind.
private val drmHints = listOf(
    "netflix", "disney", "hbo", "wbd.stream", "amazon.avod", "dazn", "digiturk",
    "de.sky", "com.sky", "paramount", "rtlplus", "joyn", "exxen", "blutv",
)

private fun likelyDrm(pkg: String) = drmHints.any { pkg.contains(it, ignoreCase = true) }

@Composable
fun AppsScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { AppRepository.load(ctx) } }
    val selected by Prefs.carApps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(padding).fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("Apps im Auto", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Diese Apps kannst du in Android Auto über Yolcu starten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("App suchen") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            )
        }

        val list = all
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        val (on, off) = filtered.partition { it.packageName in selected }

        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            if (on.isNotEmpty()) {
                item { SectionHeader("Im Auto · ${on.size}", Modifier.padding(horizontal = 12.dp)) }
                items(on, key = { "on-" + it.packageName }) { AppRow(it, checked = true) }
            }
            item { SectionHeader("Alle Apps", Modifier.padding(horizontal = 12.dp)) }
            items(off, key = { "off-" + it.packageName }) { AppRow(it, checked = false) }
        }
    }
}

@Composable
private fun AppRow(app: LaunchableApp, checked: Boolean) {
    ListItem(
        headlineContent = { Text(app.label) },
        supportingContent = if (likelyDrm(app.packageName)) {
            { Text("Kopierschutz – Bild bleibt im Auto vermutlich schwarz") }
        } else null,
        leadingContent = { AppIcon(app.packageName) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { Prefs.setCarApp(app.packageName, it) }) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { Prefs.setCarApp(app.packageName, !checked) },
    )
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { AppRepository.iconBitmap(ctx, pkg, 112)?.asImageBitmap() }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, null, Modifier.size(42.dp))
    } else {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
