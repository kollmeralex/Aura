# AURA (Accessibility User Research Analytics)

[![](https://jitpack.io/v/kollmeralex/Aura.svg)](https://jitpack.io/#kollmeralex/Aura)

Eine leichtgewichtige Android-Logging-Bibliothek für HCI-Forschung und Nutzerstudien.

## Features

- 🚀 **Easy Setup**: Einzeilen-Initialisierung mit `setupExperiment()`
- 📊 **Auto-Metadata**: Automatische Erfassung von Zeitstempel, UserID, ExperimentID und Condition
- 🔄 **CouchDB Integration**: Direkte Backend-Speicherung mit automatischer Synchronisierung
- 🔒 **Offline-First**: Lokales Caching für zuverlässige Datenerfassung
- 📝 **Flexible Events**: Logge beliebige Events mit individuellen Daten-Payloads

## Installation

### Schritt 1: JitPack Repository hinzufügen

Füge JitPack zu deiner `settings.gradle.kts` hinzu:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Schritt 2: Dependency hinzufügen

Füge AURA zu deiner `app/build.gradle.kts` hinzu:

```kotlin
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.0.0")
}
```

### Schritt 3: Clear Text Traffic aktivieren (für lokale Tests)

Falls du eine lokale CouchDB verwendest, füge dies zu deiner `AndroidManifest.xml` hinzu:

```xml
<application
    android:usesCleartextTraffic="true"
    ... >
```

## Quick Start

```kotlin
import com.example.aura.lib.Aura

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Experiment konfigurieren
        val config = Aura.Config(
            experimentID = "MyExperiment",
            condition = "ConditionA",
            userID = "Participant01",
            couchDbUrl = "http://192.168.1.100:5984",
            dbName = "study_logs",
            username = "admin",
            password = "password"
        )

        // AURA initialisieren
        Aura.setupExperiment(config)

        // Events loggen
        Aura.logEvent("app_started", emptyMap())

        Aura.logEvent("button_clicked", mapOf(
            "button_id" to "submit",
            "screen" to "LoginScreen",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
```

## Dokumentation

Für detaillierte Integrations-Anweisungen (auf Deutsch) siehe [GUIDE.md](GUIDE.md).

## Anforderungen

- **Android API 24+** (Android 7.0 Nougat)
- **Kotlin 1.9.0+**
- **CouchDB-Instanz** für Backend-Speicherung

## Event-Datenstruktur

Jedes geloggte Event enthält automatisch:

```json
{
  "experimentID": "MyExperiment",
  "userID": "Participant01",
  "condition": "ConditionA",
  "eventName": "button_clicked",
  "timestamp": 1732723200000,
  "payload": {
    "button_id": "submit",
    "screen": "LoginScreen"
  }
}
```

## Anwendungsfälle

AURA ist perfekt für:

- **A/B Testing**: Vergleiche verschiedene UI-Bedingungen
- **Fitts' Law-Studien**: Logge Klick-Positionen und Timings
- **User Behavior Research**: Erfasse Interaktionsmuster
- **Usability-Studien**: Sammle Task-Completion-Metriken
- **Mobile HCI-Experimente**: Alle Forschungsarbeiten, die Event-Logging benötigen

## Repository-Struktur

- `/aura` - Library-Modul (publiziert auf JitPack)
- `/app` - Beispiel-App zur Demonstration der Nutzung

## Autoren

- **Alex Kollmer** - [@kollmeralex](https://github.com/kollmeralex)
- **Hevend Hussein**

## Danksagungen

Teil eines HCI-Lab-Projekts an der Leibniz Universität Hannover.

Betreut von Lukas Köhler.

## Support

Bei Fragen oder Problemen bitte ein Issue auf [GitHub](https://github.com/kollmeralex/Aura/issues) erstellen.

## Lizenz

Dieses Projekt wurde im Rahmen eines Universitätsprojekts entwickelt.
