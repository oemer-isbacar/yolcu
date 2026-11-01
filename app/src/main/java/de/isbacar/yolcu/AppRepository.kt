package de.isbacar.yolcu

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(
    val packageName: String,
    val label: String,
    /** Komponente im Format für `am start -n`, z. B. com.google.android.youtube/.HomeActivity */
    val component: String,
)

object AppRepository {
    fun load(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                val info = it.activityInfo
                LaunchableApp(
                    packageName = info.packageName,
                    label = it.loadLabel(pm).toString(),
                    component = "${info.packageName}/${info.name}",
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun iconBitmap(ctx: Context, pkg: String, size: Int): Bitmap? = try {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(size, size)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
