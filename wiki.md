====== Gruppe 2: A(ccessibility) U(ser) R(esearch) A(nalytics) – AURA ======

**Projekttitel:** Generische Logging-Komponente für Prototypen

**Autoren:** [[alex.kollmer@stud.uni-hannover.de|Alex Kollmer]], [[hevend.hussein@stud.uni-hannover.de|Hevend Hussein]]

**Betreuer:** [[lukas.koehler@hci.uni-hannover.de|Lukas Köhler]]

**Bearbeitungszeit:** 23.10.2025 – 15.01.2026

**Git-Repository:** [[https://github.com/kollmeralex/Aura]]

**Beispiel-Implementierung (Fitts' Law):** [[https://github.com/kollmeralex/Aura/tree/fittslaws]]

**JitPack:** [[https://jitpack.io/#kollmeralex/Aura]]

---

===== 1. Projektbeschreibung =====

==== Motivation und Problemstellung ====

In der experimentellen Mensch-Computer-Interaktion (HCI) ist die präzise Erfassung von Nutzerinteraktionen die Grundlage für die Validierung von Hypothesen und die Evaluation von Prototypen. Entwickler und Forschende, die Nutzerstudien durchführen, stehen jedoch oft vor der Herausforderung, Logging-Funktionalitäten manuell in ihre Prototypen zu implementieren. Dieser Prozess ist zeitaufwendig, fehleranfällig und führt häufig zu inkonsistenten Datenformaten über verschiedene Projekte hinweg.

==== Die Lösung: AURA ====

**AURA (Android User Research Analytics)** ist eine leichtgewichtige Logging-Bibliothek für Android-Anwendungen. Sie ermöglicht Forschenden und Entwicklern, mit minimalem Aufwand ein robustes und standardisiertes Logging in ihre Studien-Prototypen zu integrieren.

Der Kernvorteil von AURA liegt nicht darin, //was// geloggt wird (dies definiert der Forscher selbst), sondern //wie// es geloggt wird. Die Bibliothek abstrahiert die Komplexität der Sitzungsverwaltung, des Metadaten-Managements, der Datenspeicherung und des Counterbalancings.

==== Kernfunktionen ====

- **Experiment-Management:** Konfiguration von ExperimentID, UserID und Conditions
- **Flexible Event-Erfassung:** Beliebige Events mit JSON-Payloads loggen
- **Automatische Metadaten:** Zeitstempel, IDs und Condition werden automatisch angehängt
- **Duale Speicherung:** Lokale JSONL-Dateien + CouchDB-Synchronisierung
- **Counterbalancing:** Integrierte Unterstützung für Within-Subject Designs
- **ID-Validierung:** Prüfung ob Teilnehmer-ID bereits verwendet wurde

---

===== 2. Technischer Ansatz =====

==== Android-Komponente ====

Die AURA-Bibliothek ist als eigenständiges Android-Modul in Kotlin entwickelt und wird über JitPack distribuiert.

**Integration in ein Projekt:**

1. JitPack-Repository zur ''settings.gradle.kts'' hinzufügen:

<code kotlin>
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
</code>

2. Abhängigkeit zur ''build.gradle.kts'' hinzufügen:

<code kotlin>
dependencies {
    implementation("com.github.kollmeralex:Aura:v1.4.0")
}
</code>

==== Server-Komponente (CouchDB) ====

Als Backend wird **Apache CouchDB** eingesetzt. Die NoSQL-Datenbank eignet sich aufgrund ihrer JSON-basierten Dokumentenstruktur und der HTTP-API ideal für das Speichern von Log-Daten. AURA kommuniziert mit CouchDB über Retrofit und nutzt Mango-Queries zum Abfragen von Teilnehmerdaten.

==== Architektur ====

<code>
┌─────────────────┐      ┌─────────────────┐
│   Android App   │      │    CouchDB      │
│                 │      │                 │
│  ┌───────────┐  │      │  ┌───────────┐  │
│  │   AURA    │◄─┼──────┼─►│  Database │  │
│  │  Library  │  │ HTTP │  │   (aura)  │  │
│  └───────────┘  │      │  └───────────┘  │
│        │        │      │                 │
│        ▼        │      └─────────────────┘
│  ┌───────────┐  │
│  │   Local   │  │
│  │   JSONL   │  │
│  └───────────┘  │
└─────────────────┘
</code>

---

===== 3. Counterbalancing =====

Ein zentrales Feature von AURA ist die integrierte Counterbalancing-Unterstützung. In Within-Subject Designs absolvieren alle Teilnehmer alle Bedingungen, was zu Reihenfolgeeffekten führen kann. AURA bietet fünf Modi, um diese systematisch zu kontrollieren.

==== Verfügbare Modi ====

^ Modus ^ Beschreibung ^ Anwendungsfall ^
| ''LATIN_SQUARE'' | Balancierte Rotation der Bedingungen | Standard für Within-Subject Designs |
| ''FULL_PERMUTATION'' | Alle n! möglichen Reihenfolgen | Kleine Anzahl an Bedingungen (≤4) |
| ''RANDOM'' | Zufällige Reihenfolge pro Teilnehmer | Wenn Reihenfolge keine Rolle spielt |
| ''CUSTOM'' | Feste Reihenfolge definieren | Spezifische Anforderungen |
| ''LEGACY'' | Einfache odd/even Umkehrung | Abwärtskompatibilität |

==== Latin Square ====

Für n Bedingungen werden n Gruppen erstellt. Jede Bedingung erscheint an jeder Position gleich oft. Teilnehmer werden via ''participantIndex % n'' den Gruppen zugewiesen.

<code kotlin>
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.LATIN_SQUARE
)
</code>

Beispiel mit 3 Bedingungen (A, B, C):
<code>
Gruppe 0: A → B → C
Gruppe 1: B → C → A
Gruppe 2: C → A → B
</code>

==== Full Permutation ====

Generiert alle n! möglichen Reihenfolgen. Achtung: Nur für kleine n praktikabel (3 Bedingungen = 6 Gruppen, 4 = 24 Gruppen).

<code kotlin>
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.FULL_PERMUTATION
)
</code>

==== Random ====

Zufällige Reihenfolge für jeden Teilnehmer. Der Seed basiert auf der Teilnehmer-ID, sodass die gleiche ID immer die gleiche Reihenfolge ergibt (reproduzierbar).

<code kotlin>
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.RANDOM
)
</code>

==== Custom ====

Definiert eine feste Reihenfolge für alle oder individuelle Reihenfolgen pro Teilnehmer-ID.

<code kotlin>
// Gleiche Reihenfolge für alle
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.CUSTOM,
    customOrders = mapOf("default" to listOf("Medium", "Small", "Large"))
)

// Individuelle Reihenfolgen
counterbalanceConfig = Aura.CounterbalanceConfig(
mode = Aura.CounterbalanceMode.CUSTOM,
customOrders = mapOf(
"P01" to listOf("A", "B", "C"),
"P02" to listOf("C", "B", "A")
)
)
</code>

==== Legacy ====

Einfache Umkehrung basierend auf gerader/ungerader Teilnehmer-ID (nur 2 Gruppen).

<code kotlin>
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.LEGACY
)
</code>

==== Start-/End-Bedingung ====

Unabhängig vom Modus kann eine feste Start- oder Endbedingung definiert werden:

<code kotlin>
counterbalanceConfig = Aura.CounterbalanceConfig(
    mode = Aura.CounterbalanceMode.LATIN_SQUARE,
    startCondition = "Training",
    endCondition = "Questionnaire"
)
</code>

---

===== 4. ID-Validierung =====

AURA prüft vor dem Start eines Experiments, ob die eingegebene Teilnehmer-ID bereits in der Datenbank existiert. Dies verhindert versehentliche Doppelungen und Datenverlust.

==== Funktionsweise ====

Die Methode ''checkUserIdExists()'' fragt via Mango-Query alle existierenden UserIDs von CouchDB ab:

<code kotlin>
val existingIds = Aura.checkUserIdExists(userId)
</code>

Falls die ID bereits existiert, zeigt die Demo-App einen Warndialog mit:

- Liste aller bereits verwendeten IDs
- Vorgeschlagene nächste ID (höchste numerische ID + 1)
- Möglichkeit, trotzdem fortzufahren oder eine andere ID zu wählen

---

===== 5. API-Verwendung =====

==== Experiment initialisieren ====

<code kotlin>
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
</code>

==== Counterbalanced Order abrufen ====

<code kotlin>
val result = Aura.getCounterbalancedOrder()
val conditionOrder = result.conditionOrder  // z.B. ["Medium", "Large", "Small"]
val groupIndex = result.groupIndex          // z.B. 1
</code>

==== Condition setzen ====

<code kotlin>
Aura.setCondition("Small")
</code>

==== Events loggen ====

<code kotlin>
Aura.logEvent("target_hit", mapOf(
    "trial" to 5,
    "reaction_time_ms" to 543,
    "distance_px" to 342.7,
    "target_size_dp" to 48,
    "index_of_difficulty" to 2.47
))
</code>

==== Event-Datenstruktur ====

Jedes Event wird automatisch mit Metadaten angereichert:

<code json>
{
  "experimentID": "Fitts_Law_Exp",
  "userID": "P07",
  "condition": "Small",
  "eventName": "target_hit",
  "timestamp": 1736438400000,
  "payload": {
    "trial": 5,
    "reaction_time_ms": 543,
    "distance_px": 342.7
  }
}
</code>

---

===== 6. Demo-Anwendung: Fitts' Law Experiment =====

Als Demonstration wurde ein Fitts' Law Experiment implementiert. Teilnehmer klicken auf Targets unterschiedlicher Größe, wobei Reaktionszeit und Distanz gemessen werden.

**Bedingungen:**

- Small: 48dp
- Medium: 96dp
- Large: 144dp

**Trials pro Condition:** 10

**Geloggte Events:**

- ''experiment_started''
- ''condition_started''
- ''target_hit'' (pro Trial)
- ''condition_completed''
- ''experiment_completed''

Die vollständige Implementierung ist im Branch ''fittslaws'' verfügbar: [[https://github.com/kollmeralex/Aura/tree/fittslaws]]

---

===== 7. Related Work =====

Die wissenschaftliche Grundlage für dieses Projekt stammt aus der Forschung zum Thema Software-Logging:

**Logging-Praktiken und Metadaten**

Studien zeigen, dass Logging-Praktiken in mobilen Anwendungen oft inkonsistent sind. Der entscheidende Faktor für eine systematische Auswertung ist die Erfassung von strukturierten Daten und kontextbezogenen Metadaten. AURA adressiert dies durch automatisierte Metadaten-Erfassung und ein standardisiertes JSON-Format.

[[https://ink.library.smu.edu.sg/cgi/viewcontent.cgi?article=5498&context=sis_research]]

**Herausforderungen: Sicherheit und Performance**

Wesentliche Probleme beim Logging sind Datenschutz und Performance-Einfluss. AURA begegnet diesen durch lokale Speicherung zur Datenkontrolle und effiziente Pufferung zur Minimierung der Performance-Last.

[[https://link.springer.com/article/10.1007/s10664-019-09687-9]]

---

===== 8. Features & Limitationen =====

==== Features ====

- Einfache Initialisierung mit einer Konfiguration
- Automatische Metadaten-Erfassung (Zeitstempel, IDs, Condition)
- Offline-First: Lokale JSONL-Speicherung als Fallback
- CouchDB-Synchronisierung
- Bidirektionaler Datenfluss (Server → App für Counterbalancing)
- 5 Counterbalancing-Modi
- ID-Validierung mit Vorschlag für nächste ID
- Start-/End-Bedingung definierbar

==== Limitationen ====

- Aktuell nur für Android
- Benötigt Netzwerkverbindung für initialen Counterbalancing-Status

---

===== 9. Zeitplan =====

**Bearbeitungszeit:** 23.10.2025 – 15.01.2026

^ Woche ^ Datum ^ Aufgaben ^ Status ^
| 01 | 23.10. – 29.10. | Kick-off, Einarbeitung Android-Bibliotheken, CouchDB | DONE |
| 02 | 30.10. – 05.11. | Literaturrecherche, Related Work | DONE |
| 03 | 06.11. – 12.11. | Architektur, API-Design, Datenmodell | DONE |
| 04 | 13.11. – 19.11. | Projekt-Setup, erste API-Implementierung | DONE |
| 05 | 20.11. – 26.11. | logEvent mit Metadaten-Anreicherung | DONE |
| 06 | 27.11. – 03.12. | Lokale JSONL-Speicherung | DONE |
| 07 | 04.12. – 10.12. | Demo-App: Fitts' Law mit Counterbalancing | DONE |
| 08 | 11.12. – 17.12. | CouchDB-Integration, Netzwerk-Schicht | DONE |
| 09 | 18.12. – 24.12. | Bidirektionaler Datenfluss, Counterbalancing-Modi | DONE |
| 10 | 25.12. – 31.12. | Weihnachtspause | – |
| 11 | 01.01. – 07.01. | Tests, Debugging, ID-Validierung | DONE |
| 12 | 08.01. – 14.01. | Dokumentation, README, Wiki | IN PROGRESS |
| 13 | 15.01.2026 | Abschlusspräsentation | – |

---

===== 10. Projektergebnisse =====

- **AURA Library v1.4.0** – Veröffentlicht auf JitPack
- **Demo-App** – Fitts' Law Experiment mit vollständiger AURA-Integration
- **Dokumentation** – README mit Installationsanleitung und API-Referenz
- **Öffentliches Repository** – [[https://github.com/kollmeralex/Aura]]
