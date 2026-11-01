# Yolcu

**Beifahrer-Bildschirm für Android Auto.** Yolcu startet beliebige Handy-Apps auf dem Display im Auto, etwa YouTube, Browser, IPTV oder Mediatheken. Das Handy bleibt dabei frei benutzbar oder kann ausgeschaltet in der Tasche liegen.

*Yolcu* ist Türkisch und heißt Reisender oder Fahrgast.

## Funktionen

- **Zweiter Bildschirm:** Apps laufen auf einem eigenen virtuellen Display in der Größe des Auto-Bildschirms. Dafür braucht es Shizuku.
- **Handy spiegeln:** Das ist der Ersatzweg ohne Shizuku. Er nutzt die normale Android-Bildschirmfreigabe.
- **Touch im Auto:** Tippen, Wischen, Zurück, App wechseln. Die Tastatur erscheint auf dem Auto-Display.
- **Fahrt-Sperre:** Während der Fahrt wird das Bild pausiert, der Ton läuft weiter.
  - Die Geschwindigkeit kommt vom Auto, ersatzweise vom GPS.
  - Hysterese verhindert Flackern an der Ampel.
- **Beifahrer-Modus:** Damit bleibt das Bild auch während der Fahrt an. Er ist nur für Autos mit festem Blickschutz für den Fahrer gedacht und lässt sich nur mit Bestätigung einschalten.
- **Updates:** Die App prüft beim Start das neueste GitHub-Release und bietet es zum Herunterladen an.

## Grenzen

- **DRM-Apps** wie Netflix, Disney+, HBO Max, Tod oder Prime Video zeigen im Auto nur ein schwarzes Bild. Ihr Kopierschutz sperrt fremde Bildschirme, und das gilt für jede Spiegel-Lösung.
- **Shizuku nach jedem Neustart:** Shizuku muss nach jedem Handy-Neustart einmal über kabelloses Debugging gestartet werden. Bis dahin funktioniert nur das Spiegeln.
- **Android-Auto-Updates** von Google können das Verhalten ändern.

## Installation (Handy)

1. Samsung „Automatische Sperre“ kurz ausschalten und die APK aus dem neuesten [Release](../../releases/latest) installieren.
2. [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) installieren und starten.
3. In Yolcu der Einrichtung auf der Startseite folgen: Shizuku-Zugriff, Standort, Benachrichtigungen, Apps auswählen.
4. In Android Auto die Entwicklereinstellungen öffnen und dort „Unbekannte Quellen“ einschalten.

Die ausführliche Anleitung steht in der App unter **Einstellungen → Hilfe**.

## Aufbau

```
app/src/main/java/de/isbacar/yolcu/
├── car/        Android-Auto-Teil: Sitzung, App-Raster, Vollbild (DisplayScreen),
│               OpenGL-Renderer, Fahrt-Erkennung
├── service/    Läuft über Shizuku als Shell (uid 2000): virtuelles Display,
│               App-Start, Touch-Einspeisung
├── mirror/     Spiegeln per MediaProjection (Ersatzweg)
├── ui/         Handy-Oberfläche (Jetpack Compose, Material You)
└── *.kt        Einstellungen, App-Liste, Shizuku-Verbindung, Update-Prüfung
```

So läuft ein App-Start ab:

1. Android Auto übergibt eine Zeichenfläche (`NavigationTemplate`).
2. `SurfaceRenderer` legt dafür eine Eingangsfläche an.
3. Der `DisplayService` im Shell-Prozess erzeugt darauf ein virtuelles Display und startet dort die App mit `am start --display`.
4. Touch vom Auto wird umgerechnet und per `InputManager` in dieses Display eingespeist.

## Entwickeln

Voraussetzungen: JDK 21 und Android SDK 36. Den Pfad zum SDK trägst du in `local.properties` ein (`sdk.dir=…`).

```bash
./gradlew assembleDebug     # Test-Build, mit Debug-Schlüssel signiert
./gradlew assembleRelease   # braucht keystore.properties (siehe unten)
```

Die Testversion lässt sich **nicht** über die Release-Version installieren, weil die Schlüssel verschieden sind. Vorher deinstallieren.

## Release & Updates

Ein Update lässt sich nur über die installierte App spielen, wenn es mit **demselben Schlüssel** signiert ist. Der Schlüssel liegt lokal unter `keystore/` und `keystore.properties` und ist **nicht im Repo**. Ihn an einem zweiten, sicheren Ort aufbewahren, z. B. im Passwortmanager. Geht er verloren, ist kein Update mehr möglich. Dann muss die App deinstalliert und neu installiert werden, und die Einstellungen sind weg.

Ablauf für eine neue Version:

1. Änderungen committen.
2. In `CHANGELOG.md` einen Abschnitt `## 1.1` anlegen.
3. `scripts/release.sh 1.1` ausführen. Das Skript:
   - zählt die Versionsnummer hoch,
   - baut und signiert die APK,
   - committet, taggt `v1.1` und pusht,
   - legt mit der GitHub-CLI (`gh`) das Release samt APK und Prüfsumme an.

Danach zeigt die App auf allen Handys „Update verfügbar“. Dafür muss das Repo **öffentlich** sein, sonst kommt die App nicht an die Release-Infos.
