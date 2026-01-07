# AURA Integration Guide

Dieser Guide erklärt, wie du die **AURA Logging-Bibliothek** in deine Android-App integrierst und mit deiner eigenen CouchDB verbindest.

## 1. Installation

Füge Aura über JitPack zu deinem Projekt hinzu.

**Schritt 1:** Öffne `settings.gradle.kts` in deinem Projekt und füge JitPack hinzu:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // <-- Hinzufügen
    }
}
```

**Schritt 2:** Öffne `app/build.gradle.kts` und füge die Abhängigkeit hinzu:
```kotlin
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.1.0")
}
```

---

## 2. Netzwerkkonfiguration (Wichtig für lokale Tests!)

Aura sendet Daten über HTTP. Wenn du eine **lokale CouchDB** (z. B. `http://192.168.x.x:5984`) oder einen Server ohne HTTPS verwendest, blockiert Android dies standardmäßig.

Um das zu erlauben, musst du `usesCleartextTraffic` in deiner `app/src/main/AndroidManifest.xml` aktivieren:

```xml
<manifest ...>
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:usesCleartextTraffic="true"  <!-- HIER EINFÜGEN -->
        ... >
        <!-- ... -->
    </application>
</manifest>
```

---

## 3. Aura Konfigurieren & Starten

Initialisiere Aura am besten ganz am Anfang deiner App (z. B. in `MainActivity.onCreate` oder einer `Application`-Klasse).

Du benötigst eine laufende CouchDB-Instanz. Stelle sicher, dass die Datenbank (z. B. `my_study_logs`) **bereits existiert**!

```kotlin
import com.example.aura.lib.Aura

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 1. Konfiguration erstellen
    val config = Aura.Config(
        experimentID = "Experiment_XY",  // Name deiner Studie
        condition = "Condition_A",       // z.B. "Mit_Feedback" oder "Ohne_Feedback"
        userID = "Proband_01",           // Eindeutige ID des Teilnehmers
        
        // CouchDB Einstellungen
        couchDbUrl = "http://192.168.178.20:5984", // URL deiner Datenbank
        dbName = "my_study_logs",                  // Name der Datenbank (Muss existieren!)
        username = "admin",                        // Dein DB User
        password = "password"                      // Dein DB Passwort
    )

    // 2. Aura initialisieren
    Aura.setupExperiment(config)
}
```

---

## 4. Events Loggen

Wann immer etwas Wichtiges in deiner App passiert (Button geklickt, Fehler aufgetreten, Level beendet), logge es einfach mit `Aura.logEvent`.

Aura fügt automatisch Zeitstempel, UserID und ExperimentID hinzu.

```kotlin
// Einfaches Event
Aura.logEvent("app_started", emptyMap())

// Event mit Daten
val eventData = mapOf(
    "button_id" to "submit_btn",
    "click_count" to 5,
    "screen" to "LoginScreen"
)
Aura.logEvent("button_clicked", eventData)
```

---

## Troubleshooting

*   **Fehler: "Cleartext HTTP traffic not permitted"**
    -> Du hast vergessen, `android:usesCleartextTraffic="true"` im Manifest zu setzen (siehe Punkt 2).
*   **Daten kommen nicht an?**
    -> Prüfe, ob die Datenbank (`dbName`) in CouchDB wirklich existiert. Aura erstellt sie nicht automatisch.
    -> Prüfe, ob dein Android-Gerät im selben WLAN ist wie dein PC (bei lokaler DB).
