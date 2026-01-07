# 🎯 AURA Fitts' Law Example

> **A realistic HCI research experiment demonstrating AURA's capabilities**

This branch contains a complete, production-ready Fitts' Law experiment app built with AURA v1.1.1. Perfect for understanding how to use AURA in real HCI research!

![JitPack](https://img.shields.io/badge/JitPack-v1.1.1-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue)
![Android](https://img.shields.io/badge/Android-24%2B-green)

---

## 📱 What is This?

A beautiful, Material Design Android app that conducts a **Fitts' Law experiment** - one of the most fundamental experiments in Human-Computer Interaction research. 

### The Experiment

Participants tap on targets of different sizes while the app measures:
- ⏱️ **Reaction Time** - How fast participants respond
- 🎯 **Accuracy** - Hit rate for each condition
- 📏 **Index of Difficulty** - Calculated per Fitts' Law formula
- 🔄 **Counterbalancing** - Automatic condition order randomization

### Three Conditions

1. **Small Targets** (48dp) - High difficulty
2. **Medium Targets** (96dp) - Medium difficulty  
3. **Large Targets** (144dp) - Low difficulty

Each participant completes **10 trials per condition**, and the order is automatically counterbalanced based on their participant ID.

---

## ✨ Features Demonstrated

This example shows you how to use:

- ✅ **Easy Setup** - Initialize AURA in 5 lines of code
- ✅ **Automatic Logging** - Every target tap logged with rich metadata
- ✅ **CouchDB Integration** - All data synced to server automatically
- ✅ **Counterbalancing** - `getSuggestedConditionOrder()` for unbiased results
- ✅ **Condition Management** - `setCondition()` for within-subjects designs
- ✅ **Offline-First** - Logs even without internet, syncs later
- ✅ **Material Design** - Beautiful, modern UI with animations

---

## 🚀 Quick Start

### Step 1: Add AURA to Your Project

Add JitPack repository to your **`settings.gradle.kts`**:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add Dependency

In your app's **`build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.1.1")
}
```

### Step 3: Add CouchDB Credentials

Create **`local.properties`** in your project root:

```properties
couchdb.user=your_username
couchdb.password=your_password
```

Add to **`build.gradle.kts`** (app module):

```kotlin
import java.util.Properties

android {
    // ... other config

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

### Step 4: Initialize AURA

```kotlin
val config = Aura.Config(
    context = applicationContext,
    experimentID = "Fitts_Law_Exp",
    userID = "participant_01",
    couchDbUrl = "https://couchdb.hci.uni-hannover.de",
    dbName = "aura",
    username = BuildConfig.COUCHDB_USER,
    password = BuildConfig.COUCHDB_PASSWORD,
    availableConditions = listOf("Small", "Medium", "Large")
)

Aura.setupExperiment(config)
```

### Step 5: Log Events

```kotlin
// Get counterbalanced order
val conditionOrder = Aura.getSuggestedConditionOrder()
// e.g., ["Large", "Small", "Medium"] for participant_01

// Set current condition
Aura.setCondition("Small")

// Log an event with rich data
Aura.logEvent("target_hit", mapOf(
    "trial" to 1,
    "reaction_time_ms" to 543,
    "target_size_dp" to 48,
    "index_of_difficulty" to 4.2
))
```

That's it! 🎉 AURA handles everything else:
- Local file storage (JSONL format)
- Automatic metadata (timestamps, device info)
- Background upload to CouchDB
- Retry logic for failed uploads
- Offline support

---

## 📂 What You Get

When you clone this branch, you get:

```
Aura/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/aura/
│   │   │   └── MainActivity.kt          # Complete Fitts' Law experiment
│   │   └── res/
│   │       └── layout/
│   │           └── activity_main.xml    # Material Design UI
│   └── build.gradle.kts                 # Ready-to-use config
├── aura/                                # AURA library source
└── README.md                            # This file
```

---

## 🎨 UI Preview

The app features a modern, card-based design:

1. **Header Card** (Purple) - Shows experiment info and current state
2. **Setup Card** (White) - Participant ID input and initialization
3. **Experiment Area** (White) - Interactive targets appear here
4. **Admin Controls** (White) - Condition navigation and logs

### Color Scheme

- Primary: `#6200EE` (Purple)
- Accent: `#03DAC5` (Teal)
- Background: `#F5F5F5` (Light Gray)
- Cards: White with elevation shadows

---

## 📊 Data Logged to CouchDB

Each experiment creates the following events:

### `experiment_started`
```json
{
  "event_name": "experiment_started",
  "participant_id": "7",
  "condition_order": "Small,Medium,Large",
  "trials_per_condition": 10,
  "device": "Pixel 6",
  "android_version": "13"
}
```

### `condition_started`
```json
{
  "event_name": "condition_started",
  "condition": "Small",
  "target_size_dp": 48,
  "condition_index": 0
}
```

### `target_hit` (per trial)
```json
{
  "event_name": "target_hit",
  "condition": "Small",
  "trial": 5,
  "reaction_time_ms": 543,
  "distance_px": 342.7,
  "target_size_dp": 48,
  "target_size_px": 144,
  "index_of_difficulty": 2.47,
  "accuracy": "hit"
}
```

### `condition_completed`
```json
{
  "event_name": "condition_completed",
  "condition": "Small",
  "avg_reaction_time_ms": 612.3,
  "accuracy_percent": 95.0,
  "total_trials": 10
}
```

### `experiment_completed`
```json
{
  "event_name": "experiment_completed",
  "total_conditions": 3,
  "total_trials": 30
}
```

All events automatically include:
- `experiment_id`: "Fitts_Law_Exp"
- `user_id`: Participant ID
- `timestamp`: Unix milliseconds
- `payload`: Custom event data

---

## 🔬 Understanding the Code

### Key AURA Functions Used

#### 1. Setup
```kotlin
Aura.setupExperiment(config)
```
Initializes AURA with your experiment parameters. Call this once at the start.

#### 2. Counterbalancing
```kotlin
val order = Aura.getSuggestedConditionOrder()
// Returns: ["Small", "Medium", "Large"] or ["Large", "Small", "Medium"]
```
Based on participant ID parity:
- **Even IDs** (2, 4, 6...): Original order
- **Odd IDs** (1, 3, 5...): Reversed order

This prevents order effects in within-subjects designs!

#### 3. Set Condition
```kotlin
Aura.setCondition("Small")
```
All subsequent events will be tagged with this condition until you change it.

#### 4. Log Events
```kotlin
Aura.logEvent("target_hit", mapOf(
    "trial" to 5,
    "reaction_time_ms" to 543
))
```
Logs an event with custom payload. AURA adds metadata automatically.

---

## 🎓 Fitts' Law Background

**Fitts' Law** is a predictive model in HCI that describes the time required to rapidly move to a target:

```
MT = a + b × log₂(D/W + 1)
```

Where:
- **MT** = Movement Time
- **D** = Distance to target
- **W** = Width of target
- **ID** = Index of Difficulty = log₂(D/W + 1)

This app calculates and logs the ID for each trial, allowing you to verify Fitts' Law with real data!

---

## 🛠️ Customization

### Change Number of Trials

```kotlin
private var totalTrialsPerCondition = 10  // Change this!
```

### Add More Conditions

```kotlin
private val conditions = listOf(
    TargetCondition("Tiny", 24),
    TargetCondition("Small", 48),
    TargetCondition("Medium", 96),
    TargetCondition("Large", 144),
    TargetCondition("Huge", 192)
)
```

### Modify Target Colors

```kotlin
private fun getRandomColor(): Int {
    val colors = listOf(
        "#2196F3",  // Blue
        "#FF5722",  // Red
        "#4CAF50",  // Green
        // Add your own!
    )
    return Color.parseColor(colors.random())
}
```

---

## 📖 Learn More

### AURA Documentation
- [Main Branch README](https://github.com/kollmeralex/Aura/blob/main/README.md)
- [Implementation Guide](https://github.com/kollmeralex/Aura/blob/main/GUIDE.md)
- [JitPack Page](https://jitpack.io/#kollmeralex/Aura)

### HCI Resources
- [Fitts' Law on Wikipedia](https://en.wikipedia.org/wiki/Fitts%27s_law)
- [ACM Digital Library](https://dl.acm.org/)

---

## 🤝 Contributing

Found a bug or have a suggestion? Open an issue or PR on the [main branch](https://github.com/kollmeralex/Aura)!

---

## 📄 License

This example is part of the AURA project. Use it freely for your research!

---

## 🙏 Credits

**AURA** - Accessibility User Research Analytics  
Developed at HCI Labor, Leibniz Universität Hannover

Built with ❤️ for HCI researchers

---

**Happy Experimenting! 🎉**
