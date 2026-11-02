package de.isbacar.yolcu;

import android.view.Surface;

// Läuft über Shizuku im Shell-Prozess (uid 2000) und hat damit die Rechte,
// eigene Bildschirme anzulegen, fremde Apps dort zu starten und Eingaben einzuspeisen.
interface IDisplayService {
    void destroy() = 16777114;

    int createDisplay(in Surface surface, int width, int height, int dpi, boolean decorations) = 1;
    void releaseDisplay(int displayId) = 2;
    boolean launch(int displayId, String component) = 3;
    void tap(int displayId, float x, float y) = 4;
    void touch(int displayId, int action, float x, float y, long downTime) = 5;
    void key(int displayId, int keyCode) = 6;
    String lastError() = 7;
    int myUid() = 8;

    // Trägt den Play Store als Installationsquelle ein, damit Android Auto die App anzeigt.
    String setInstaller(String packageName, String apkPath) = 9;
}
