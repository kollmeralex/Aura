# Fitts' Law Experiment – AURA Beispielimplementierung

Diese Beispiel-App demonstriert die Verwendung der [AURA Library](https://github.com/kollmeralex/Aura) anhand eines Fitts' Law Experiments.

## Über das Experiment

Das Experiment implementiert eine klassische Fitts' Law Pointing-Aufgabe mit drei Target-Größen:

| Condition | Target-Größe |
|-----------|--------------|
| Small | 48dp |
| Medium | 96dp |
| Large | 144dp |

Pro Condition werden 10 Trials durchgeführt. Die Reihenfolge der Conditions wird automatisch counterbalanced, um Lerneffekte zu kontrollieren.

## AURA Integration

### Dependency

Die App nutzt AURA über JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.4.0")
}
```

### Konfiguration

CouchDB-Credentials werden in `local.properties` gespeichert:

```properties
couchdb.user=benutzername
couchdb.password=passwort
```

### Experiment-Setup

```kotlin
val config = Aura.Config(
    context = applicationContext,
    experimentID = "Fitts_Law_Exp",
    userID = participantId,
    couchDbUrl = "https://couchdb.hci.uni-hannover.de",
    dbName = "aura",
    username = BuildConfig.COUCHDB_USER,
    password = BuildConfig.COUCHDB_PASSWORD,
    availableConditions = listOf("Small", "Medium", "Large"),
    counterbalanceConfig = Aura.CounterbalanceConfig(
        mode = Aura.CounterbalanceMode.LATIN_SQUARE
    )
)

Aura.setupExperiment(config)
val order = Aura.getCounterbalancedOrder()
```

### Event-Logging

Bei jedem Trial werden folgende Daten erfasst:

```kotlin
Aura.logEvent("target_hit", mapOf(
    "trial" to trialNumber,
    "reaction_time_ms" to reactionTime,
    "distance_px" to distance,
    "target_size_dp" to targetSize,
    "index_of_difficulty" to id
))
```

## Geloggte Events

### experiment_started
```json
{
  "event_name": "experiment_started",
  "participant_id": "7",
  "condition_order": "Small,Medium,Large",
  "trials_per_condition": 10
}
```

### target_hit (pro Trial)
```json
{
  "event_name": "target_hit",
  "condition": "Small",
  "trial": 5,
  "reaction_time_ms": 543,
  "distance_px": 342.7,
  "target_size_dp": 48,
  "index_of_difficulty": 2.47
}
```

### condition_completed
```json
{
  "event_name": "condition_completed",
  "condition": "Small",
  "avg_reaction_time_ms": 612.3,
  "total_trials": 10
}
```

### experiment_completed
```json
{
  "event_name": "experiment_completed",
  "total_conditions": 3,
  "total_trials": 30
}
```

## Fitts' Law

Der Index of Difficulty (ID) wird für jeden Trial berechnet:

```
ID = log₂(D/W + 1)
```

- D = Distanz zum Target (px)
- W = Target-Breite (px)

Nach Fitts' Law steigt die Bewegungszeit (MT) linear mit dem ID:

```
MT = a + b × ID
```

## Projektstruktur

```
app/
├── src/main/
│   ├── java/com/example/aura/
│   │   ├── MainActivity.kt        # Hauptmenü
│   │   └── FittsLawActivity.kt    # Experiment-Screen
│   └── res/
│       └── layout/
│           ├── activity_main.xml
│           └── activity_fitts_law.xml
└── build.gradle.kts
```

## App starten

1. Repository klonen
2. `local.properties` mit CouchDB-Credentials anlegen
3. App auf Android-Gerät oder Emulator installieren
4. Teilnehmer-ID eingeben und Experiment starten

## Links

- AURA Library: [github.com/kollmeralex/Aura](https://github.com/kollmeralex/Aura)
- JitPack: [jitpack.io/#kollmeralex/Aura](https://jitpack.io/#kollmeralex/Aura)
