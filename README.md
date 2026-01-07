# Fitts' Law Experiment Example

This branch contains a reference implementation of a Fitts' Law experiment using the AURA logging library (v1.1.1).

## Overview

The application implements a standard Fitts' Law pointing experiment with three target size conditions:
- Small: 48dp
- Medium: 96dp  
- Large: 144dp

Participants complete 10 trials per condition. The order of conditions is automatically counterbalanced based on participant ID to control for learning effects.

## Implementation

### Dependencies

Add the JitPack repository to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add AURA dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.1.1")
}
```

### Configuration

Create `local.properties` file:

```properties
couchdb.user=your_username
couchdb.password=your_password
```

Configure BuildConfig in `build.gradle.kts`:

```kotlin
android {
    buildTypes {
        debug {
            val properties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                properties.load(localPropertiesFile.inputStream())
            }
            
            buildConfigField("String", "COUCHDB_USER", 
                "\"${properties.getProperty("couchdb.user", "")}\"")
            buildConfigField("String", "COUCHDB_PASSWORD", 
                "\"${properties.getProperty("couchdb.password", "")}\"")
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

### Usage

Initialize AURA at experiment start:

```kotlin
val config = Aura.Config(
    context = applicationContext,
    experimentID = "Fitts_Law_Exp",
    userID = participantId,
    couchDbUrl = "https://couchdb.hci.uni-hannover.de",
    dbName = "aura",
    username = BuildConfig.COUCHDB_USER,
    password = BuildConfig.COUCHDB_PASSWORD,
    availableConditions = listOf("Small", "Medium", "Large")
)

Aura.setupExperiment(config)
```

Get counterbalanced condition order:

```kotlin
val order = Aura.getSuggestedConditionOrder()
// Returns ["Small", "Medium", "Large"] or ["Large", "Medium", "Small"]
// based on participant ID parity
```

Log experiment events:

```kotlin
Aura.setCondition("Small")

Aura.logEvent("target_hit", mapOf(
    "trial" to 5,
    "reaction_time_ms" to 543,
    "distance_px" to 342.7,
    "target_size_dp" to 48,
    "index_of_difficulty" to 2.47
))
```

## Data Logging

All events are logged locally and automatically synced to CouchDB when network is available.

### Event Types

**experiment_started**
```json
{
  "event_name": "experiment_started",
  "participant_id": "7",
  "condition_order": "Small,Medium,Large",
  "trials_per_condition": 10,
  "device": "Pixel 6"
}
```

**target_hit** (per trial)
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

**condition_completed**
```json
{
  "event_name": "condition_completed",
  "condition": "Small",
  "avg_reaction_time_ms": 612.3,
  "total_trials": 10
}
```

All events include automatic metadata:
- experiment_id
- user_id
- timestamp
- condition (if set)

## Fitts' Law

The application calculates Index of Difficulty (ID) for each trial:

```
ID = log₂(D/W + 1)
```

where D is distance to target and W is target width.

According to Fitts' Law, movement time (MT) should increase linearly with ID:

```
MT = a + b × ID
```

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/aura/
│   │   └── MainActivity.kt
│   └── res/
│       └── layout/
│           └── activity_main.xml
└── build.gradle.kts
```

## References

- Main repository: [github.com/kollmeralex/Aura](https://github.com/kollmeralex/Aura)
- JitPack: [jitpack.io/#kollmeralex/Aura](https://jitpack.io/#kollmeralex/Aura)
