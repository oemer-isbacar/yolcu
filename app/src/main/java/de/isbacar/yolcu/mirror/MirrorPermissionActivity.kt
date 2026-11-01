package de.isbacar.yolcu.mirror

import android.app.NotificationManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Unsichtbare Activity, die nur den Android-Dialog „Bildschirm teilen“ anzeigt. */
class MirrorPermissionActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val req = MirrorController.request
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null && req != null) {
            startForegroundService(
                android.content.Intent(this, MirrorService::class.java)
                    .putExtra(MirrorService.EXTRA_CODE, result.resultCode)
                    .putExtra(MirrorService.EXTRA_DATA, data)
            )
        } else {
            req?.listener?.onMirrorStopped("Freigabe wurde abgelehnt.")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(MirrorController.NOTIFICATION_REQUEST)
        if (MirrorController.request == null) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }
}
