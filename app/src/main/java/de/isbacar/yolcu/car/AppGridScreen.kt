package de.isbacar.yolcu.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import de.isbacar.yolcu.AppRepository
import de.isbacar.yolcu.LaunchableApp
import de.isbacar.yolcu.Prefs
import de.isbacar.yolcu.R
import de.isbacar.yolcu.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Target {
    data class App(val app: LaunchableApp) : Target
    data object Mirror : Target
}

/** Startseite im Auto: „Handy spiegeln“ und die auf dem Handy ausgewählten Apps. */
class AppGridScreen(
    ctx: CarContext,
    private val hub: SurfaceHub,
    /** null = Startseite; sonst Auswahl für einen bereits offenen Bildschirm. */
    private val onPick: ((Target) -> Unit)?,
) : Screen(ctx) {

    private var apps: List<Pair<LaunchableApp, CarIcon>>? = null
    private var shizukuReady = ShizukuManager.state.value == ShizukuManager.State.READY

    init {
        lifecycleScope.launch {
            Prefs.carApps.collect { selected ->
                apps = withContext(Dispatchers.IO) { load(selected) }
                invalidate()
            }
        }
        lifecycleScope.launch {
            ShizukuManager.state.collect {
                val ready = it == ShizukuManager.State.READY
                if (ready != shizukuReady) {
                    shizukuReady = ready
                    invalidate()
                }
            }
        }
    }

    private fun load(selected: Set<String>): List<Pair<LaunchableApp, CarIcon>> =
        AppRepository.load(carContext).filter { it.packageName in selected }.mapNotNull { app ->
            val bmp = AppRepository.iconBitmap(carContext, app.packageName, 144) ?: return@mapNotNull null
            app to CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        }

    override fun onGetTemplate(): Template {
        val title = when {
            onPick != null -> "App wählen"
            shizukuReady -> "Yolcu"
            else -> "Yolcu · nur Spiegeln (Shizuku aus)"
        }
        val builder = GridTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(if (onPick == null) Action.APP_ICON else Action.BACK)
        val list = apps ?: return builder.setLoading(true).build()

        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        }.getOrDefault(6)

        val items = ItemList.Builder()
        items.addItem(
            GridItem.Builder()
                .setTitle("Handy spiegeln")
                .setImage(resIcon(R.drawable.ic_mirror), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { open(Target.Mirror) }
                .build()
        )
        if (list.isEmpty()) {
            items.addItem(
                GridItem.Builder()
                    .setTitle("Apps hinzufügen")
                    .setText("in Yolcu am Handy")
                    .setImage(resIcon(R.drawable.ic_info), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Öffne Yolcu auf dem Handy → Tab „Apps“", CarToast.LENGTH_LONG).show()
                    }
                    .build()
            )
        }
        list.take((limit - 1).coerceAtLeast(1)).forEach { (app, icon) ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(app.label)
                    .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { open(Target.App(app)) }
                    .build()
            )
        }
        return builder.setSingleList(items.build()).build()
    }

    private fun open(target: Target) {
        val pick = onPick
        if (pick != null) pick(target) else screenManager.push(DisplayScreen(carContext, hub, target))
    }

    private fun resIcon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()
}
