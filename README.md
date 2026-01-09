# AURA – Android User Research Analytics

[![](https://jitpack.io/v/kollmeralex/Aura.svg)](https://jitpack.io/#kollmeralex/Aura)

AURA ist eine Android-Logging-Bibliothek für HCI-Forschung und Nutzerstudien. Die Library ermöglicht das einfache Erfassen von Experimentdaten mit automatischer Synchronisierung zu CouchDB.

## Features

- Einfache Initialisierung mit einer Konfiguration
- Automatische Metadaten (Zeitstempel, UserID, ExperimentID, Condition)
- CouchDB-Integration mit automatischer Synchronisierung
- Offline-First: Lokales JSONL-Caching bei fehlender Netzwerkverbindung
- Flexibles Event-Logging mit beliebigen Payloads
- Integriertes Counterbalancing mit verschiedenen Modi

---

## Anforderungen

| Anforderung | Version |
|-------------|---------|
| Android API | 24+ (Android 7.0) |
| Kotlin | 1.9.0+ |
| Gradle | 8.0+ |
| CouchDB | 3.x empfohlen |

---

## Installation

### 1. JitPack Repository hinzufügen

In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Dependency hinzufügen

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.4.0")
}
```

### 3. CouchDB-Credentials konfigurieren

Erstelle eine `local.properties` Datei im Projekt-Root:

```properties
couchdb.user=dein_benutzername
couchdb.password=dein_passwort
```

In `app/build.gradle.kts` die Credentials als BuildConfig-Felder einbinden:

```kotlin
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    defaultConfig {
        buildConfigField("String", "COUCHDB_USER", "\"${localProperties.getProperty("couchdb.user", "")}\"")
        buildConfigField("String", "COUCHDB_PASSWORD", "\"${localProperties.getProperty("couchdb.password", "")}\"")
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

---

## Dokumentation

### Grundlegende Verwendung

```kotlin
import com.example.aura.lib.Aura

// Experiment konfigurieren
val config = Aura.Config(
    context = applicationContext,
    experimentID = "MeinExperiment",
    userID = participantId,
    couchDbUrl = "https://couchdb.example.com",
    dbName = "study_logs",
    username = BuildConfig.COUCHDB_USER,
    password = BuildConfig.COUCHDB_PASSWORD,
    availableConditions = listOf("Condition_A", "Condition_B", "Condition_C")
)

// AURA initialisieren
Aura.setupExperiment(config)

// Counterbalanced Order abrufen
val order = Aura.getCounterbalancedOrder()
println("Condition-Reihenfolge: ${order.conditionOrder}")

// Condition setzen
Aura.setCondition("Condition_A")

// Events loggen
Aura.logEvent("trial_completed", mapOf(
    "trial" to 5,
    "reaction_time_ms" to 423,
    "correct" to true
))
```

### Counterbalancing-Modi

AURA bietet verschiedene Modi zur Kontrolle von Reihenfolgeeffekten:

| Modus | Beschreibung | Anwendungsfall |
|-------|--------------|----------------|
| `LATIN_SQUARE` | Balancierte Rotation der Bedingungen | Standard für Within-Subject Designs |
| `FULL_PERMUTATION` | Alle n! möglichen Reihenfolgen | Kleine Anzahl an Bedingungen (≤4) |
| `RANDOM` | Zufällige Reihenfolge pro Teilnehmer | Wenn Reihenfolge keine Rolle spielt |
| `CUSTOM` | Feste Reihenfolge definieren | Spezifische Anforderungen |
| `LEGACY` | Einfache odd/even Umkehrung | Abwärtskompatibilität |

#### Latin Square (Standard)

Für n Bedingungen werden n Gruppen erstellt, wobei jede Bedingung an jeder Position gleich oft vorkommt:

```kotlin
val config = Aura.Config(
    // ...
    counterbalanceConfig = Aura.CounterbalanceConfig(
        mode = Aura.CounterbalanceMode.LATIN_SQUARE
    )
)
```

Beispiel mit 3 Bedingungen (A, B, C):
```
Gruppe 0: A → B → C
Gruppe 1: B → C → A
Gruppe 2: C → A → B
```

#### Custom Mode

Für eine feste Reihenfolge unabhängig vom Teilnehmer:

```kotlin
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.CUSTOM,
    customOrders = mapOf("default" to listOf("Medium", "Small", "Large"))
)
```

#### Start-/End-Bedingung festlegen

Optional kann eine feste Start- oder Endbedingung definiert werden:

```kotlin
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.LATIN_SQUARE,
    startCondition = "Training",
    endCondition = "Questionnaire"
)
```

---

## Event-Datenstruktur

Jedes geloggte Event enthält automatisch folgende Felder:

```json
{
  "experimentID": "MeinExperiment",
  "userID": "P01",
  "condition": "Condition_A",
  "eventName": "trial_completed",
  "timestamp": 1736438400000,
  "payload": {
    "trial": 5,
    "reaction_time_ms": 423,
    "correct": true
  }
}
```

Die Daten werden sowohl lokal als JSONL-Datei gespeichert als auch automatisch zu CouchDB synchronisiert.

---

## Anwendungsfälle

- **Fitts' Law Experimente**: Erfassung von Klickpositionen, Reaktionszeiten, Target-Größen
- **A/B Testing**: Vergleich verschiedener UI-Varianten
- **Usability-Studien**: Task-Completion-Zeiten, Fehlerquoten
- **Within-Subject Designs**: Automatisches Counterbalancing
- **Mobile Interaktionsstudien**: Touch-Events, Scrollverhalten

---

## Repository-Struktur

```
Aura/
├── aura/                    # Library-Modul (auf JitPack veröffentlicht)
│   └── src/main/java/
│       └── com/example/aura/lib/
│           ├── Aura.kt          # Hauptklasse
│           ├── network/
│           │   ├── CouchDbApi.kt
│           │   └── LogEntry.kt
│           └── worker/
│               └── UploadWorker.kt
├── app/                     # Demo-App
│   └── src/main/java/
│       └── com/example/aura/
│           ├── MainActivity.kt
│           └── FittsLawActivity.kt
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Beispiel-Projekt

Der Branch [`fittslaws`](https://github.com/kollmeralex/Aura/tree/fittslaws) enthält eine vollständige Beispielimplementierung eines Fitts' Law Experiments mit:

- Drei Target-Größen (Small, Medium, Large)
- Automatischem Counterbalancing
- Reaktionszeit-Messung
- CouchDB-Synchronisierung

---

## Lokale Entwicklung

Für die Entwicklung an der Library selbst kann das lokale Modul verwendet werden:

```kotlin
// In settings.gradle.kts
include(":app")
include(":aura")

// In app/build.gradle.kts
dependencies {
    implementation(project(":aura"))
}
```

---

## Links

- JitPack: [jitpack.io/#kollmeralex/Aura](https://jitpack.io/#kollmeralex/Aura)
- Beispiel-Branch: [github.com/kollmeralex/Aura/tree/fittslaws](https://github.com/kollmeralex/Aura/tree/fittslaws)
