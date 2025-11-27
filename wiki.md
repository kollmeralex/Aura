====== Gruppe 2: A(ccessibility) U(ser) R(esearch) A(nalytics) -AURA ======

# **Projekttitel: Generische Logging-Komponente für Prototypen**

**Autor**: [[alex.kollmer@stud.uni-hannover.de|Alex Kollmer]] [[hevend.hussein@stud.uni-hannover.de|Hevend Hussein]]

**Betreuer**: [[lukas.koehler@hci.uni-hannover.de|Lukas Köhler]]

**Bearbeitungszeit**: 23.10.2025 - 15.01.2026

**Git-Repository**: 

**Folien Abschlusspräsentation**: 

---------

**1. Worum geht es in dem Projekt? (Projektbeschreibung)**

Motivation und Problemstellung
In der experimentellen Mensch-Computer-Interaktion (HCI) ist die präzise Erfassung von Nutzerinteraktionen die Grundlage für die Validierung von Hypothesen und die Evaluation von Prototypen. Entwickler und Forschende, die Nutzerstudien durchführen, stehen jedoch oft vor der Herausforderung, Logging-Funktionalitäten manuell in ihre Prototypen zu implementieren. Dieser Prozess ist nicht nur zeitaufwendig, sondern auch fehleranfällig und führt häufig zu inkonsistenten Datenformaten über verschiedene Projekte hinweg. Es fehlt ein standardisiertes, leicht zu integrierbares Werkzeug, das den gesamten Prozess der Datenerfassung von der Sitzungssteuerung bis zur Datenspeicherung abstrahiert.

Die Lösung: Die AURA-Bibliothek
Dieses Projekt zielt auf die Konzeption und Entwicklung von **AURA (Accessibility User Research Analytics)** ab – einer leichtgewichtigen und flexiblen Logging-Bibliothek für Android-Anwendungen. AURA soll es Forschenden und Entwicklern ermöglichen, mit minimalem Aufwand ein robustes und standardisiertes Logging in ihre Studien-Prototypen zu integrieren.

Der Kernvorteil von AURA liegt nicht darin, *was* geloggt wird (dies definiert der Forscher selbst im Code), sondern *wie* es geloggt wird. Die Bibliothek nimmt dem Entwickler die Komplexität der Sitzungsverwaltung, des Metadaten-Managements und der zuverlässigen Datenspeicherung ab.

Funktionsumfang und Architektur der AURA-Bibliothek
AURA wird als einfach zu importierendes Modul konzipiert, das eine klare und intuitive API (Application Programming Interface) bereitstellt. Der geplante Funktionsumfang umfasst:

**Kernfunktionen:**

*   **Sitzungs- und Experiment-Management:**
    *   `AURA.setupExperiment(config: Object)`: Eine initiale Funktion, um das Experiment zu konfigurieren. Hier werden Metadaten wie die `ExperimentID` und die Anzahl der `conditions` (z.B. 2 für eine A/B-Studie) festgelegt.
   

*   **Flexible Event-Erfassung:**
    *   `AURA.logEvent(eventName: String, data: Object)`: Die zentrale Funktion der Bibliothek. Der Entwickler kann an jeder beliebigen Stelle im Code einen Event loggen, ihm einen eindeutigen Namen geben (z.B. `"button_clicked"`, `"task_completed"`) und ein beliebiges JSON-Objekt mit kontextspezifischen Daten anhängen (z.B. `{ "button_id": "submit", "time_to_complete_ms": 1234 }`).

*   **Automatische Metadaten-Anreicherung:**
    *   Jedes mit `logEvent` erfasste Ereignis wird von AURA automatisch mit essenziellen Metadaten angereichert. Dazu gehören:
        *   Ein präziser **Zeitstempel** (Unix-Timestamp in Millisekunden).
        *   Die bei der Konfiguration festgelegte **UserID**, **ExperimentID** und **Condition**.
        *   Der **Paketname der App**.
        *   Eine eindeutige **Event-ID**.

*   **Zuverlässige Datenspeicherung:**
    *   Alle erfassten Events einer Sitzung werden lokal auf dem Gerät in einer klar strukturierten **JSON-Datei** gespeichert. Dies garantiert, dass keine Daten bei Netzwerkproblemen oder App-Abstürzen verloren gehen.
    *   Die Dateinamen werden systematisch generiert (z.B. `ExperimentID_UserID_Condition_Timestamp.json`), um eine einfache Zuordnung zu gewährleisten.

Demonstration und Anwendungsfall
Um den Nutzen und die einfache Anwendung der AURA-Bibliothek zu demonstrieren, wird im Rahmen des Projekts ein Prototyp für eine experimentelle Studie entwickelt. Dieser Prototyp wird zwei einfache Experimente implementieren, in denen AURA integriert wird:

*   **Experiment 1 (Fitts' Law):** Eine klassische HCI-Studie, bei der Probanden auf unterschiedlich große und weit entfernte Ziele klicken müssen. AURA wird hier genutzt, um Klick-Positionen, Ziel-Koordinaten und die Zeit zwischen den Klicks zu loggen. Wir werden zwei Bedingungen testen (z.B. kleine vs. große Ziele) und zeigen, wie AURA die Datenerfassung für die Auswertung vereinfacht.
*   **Experiment 2 (UI-Vergleich):** Ein A/B-Test, bei dem zwei verschiedene Layouts für eine Aufgabe verglichen werden (Condition 1 vs. Condition 2). AURA wird verwendet, um die benötigte Zeit, die Anzahl der Klicks und eventuelle Fehler pro Bedingung zu erfassen.

In der Abschlusspräsentation wird die Code-Integration von AURA in diesen Prototypen live gezeigt, um zu verdeutlichen, wie wenige Zeilen Code nötig sind, um ein voll funktionsfähiges Experimenten-Logging aufzusetzen.

Projektziel
Das finale Ergebnis ist eine gut dokumentierte, wiederverwendbare Logging-Bibliothek (AURA), die zukünftigen Studierenden und Forschungsprojekten als Grundlage für ihre eigenen Nutzerstudien dienen kann. Ergänzt wird dies durch einen lauffähigen Prototypen, der als Best-Practice-Beispiel für die Integration und den Nutzen der Bibliothek dient.


---------
**2. Vorgehensweise (Technischer Ansatz)**

Der technische Ansatz des Projekts gliedert sich in zwei Kernbereiche: die Entwicklung der Android-Bibliothek als einfach integrierbare Komponente und die Konfiguration der serverseitigen Datenspeicherung mit CouchDB.

a) Android-Komponente (AURA-Bibliothek)

Die AURA-Bibliothek wird als eigenständiges Android-Modul in Kotlin entwickelt. Der Fokus liegt darauf, die Integration für Forschende und Entwickler so einfach wie möglich zu gestalten.

**Distribution und Integration:**

**Methode 1: JitPack**
Die AURA-Bibliothek wird über JitPack veröffentlicht, einen Build-Service für Git-Repositories. Dies ermöglicht eine einfache Integration über Gradle ohne manuelle Dateiverwaltung.

Integration in ein Projekt:
1.  JitPack-Repository zur `settings.gradle.kts` hinzufügen:
    ```kotlin
    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
            maven { url = uri("https://jitpack.io") }
        }
    }
    ```

2.  Abhängigkeit zur `build.gradle.kts` (oder `libs.versions.toml`) der App hinzufügen:
    ```kotlin
    dependencies {
        implementation("com.gitlab.kollmeralex:aura:1.0.9")
    }
    ```

**Methode 2: Lokale .aar-Datei (Notlösung)**
Falls JitPack nicht verfügbar ist, kann die Bibliothek auch als lokale `.aar`-Datei eingebunden werden:
1.  Die `.aar`-Datei in das `libs`-Verzeichnis des Zielprojekts kopieren
2.  In der `build.gradle.kts`-Datei der App deklarieren:
    ```kotlin
    dependencies {
        implementation(files("libs/aura-logging-library.aar"))
    }
    ```

Diese Methode eignet sich für Offline-Entwicklung oder wenn keine externe Abhängigkeit gewünscht ist.

b) Server-Komponente (CouchDB)

Als Backend für die Online-Speicherung der gesammelten Log-Daten wird **Apache CouchDB** eingesetzt. Diese NoSQL-Datenbank wurde aufgrund ihrer spezifischen Eigenschaften ausgewählt, die ideal zu den Anforderungen des Projekts passen.


---------

**3. Related Work (Verwandte Arbeiten)**

Die Entwicklung einer Logging-Bibliothek ist primär eine ingenieurtechnische Aufgabe, die darauf abzielt, Forschungspraktiken zu unterstützen. Daher gibt es keine spezifische Forschungsliteratur, die sich exakt mit der Konzeption eines solchen Tools für HCI-Studien befasst. Die wissenschaftliche Grundlage für dieses Projekt stammt stattdessen aus der allgemeinen Forschung zum Thema Software-Logging. Diese lässt sich im Wesentlichen in zwei Bereiche gliedern: erstens, die Untersuchung von Logging-Praktiken, insbesondere **welche Daten und Metadaten** erfasst werden sollten, und zweitens, die Analyse von **Herausforderungen und Risiken**, die mit dem Logging verbunden sind.

**Logging-Praktiken und die Wichtigkeit von Metadaten**
Ein zentraler Forschungsschwerpunkt ist die Frage, wie und was Entwickler loggen. Studien zeigen, dass Logging-Praktiken in mobilen Anwendungen oft inkonsistent und weniger verbreitet sind, was zu Problemen bei der späteren Analyse führt. Der entscheidende Faktor für eine systematische Auswertung ist die Erfassung von strukturierten Daten und kontextbezogenen **Metadaten**. Anstatt unstrukturierter Textnachrichten, die für eine maschinelle Verarbeitung ungeeignet sind, ermöglicht ein standardisiertes Format (wie JSON) die direkte Analyse. Metadaten wie ein **Zeitstempel**, eine **UserID** oder die **Experiment-Condition** geben den reinen Interaktionsdaten erst ihren wissenschaftlichen Kontext und machen sie vergleich- und gruppierbar. Die AURA-Bibliothek adressiert diese Erkenntnis, indem sie die Erfassung dieser essenziellen Metadaten automatisiert und standardisiert.

[[https://ink.library.smu.edu.sg/cgi/viewcontent.cgi?article=5498&context=sis_research]]

**Herausforderungen: Sicherheit, Performance und Analyse**
Die Forschung beleuchtet auch die mit dem Logging verbundenen Risiken. Ein wesentliches Problem ist die **Sicherheit und der Datenschutz**, da durch unachtsames Logging sensible Nutzerdaten offengelegt werden können (Datenlecks). Ein weiterer Aspekt ist die **Energie- und System-Performance**. Obwohl der Einfluss des Loggens meist als gering eingestuft wird, kann er bei sehr hoher Frequenz, wie sie in interaktiven Studien auftreten kann, relevant werden. Schließlich existiert eine Vielzahl von Tools zur **automatisierten Log-Analyse**, deren Effektivität jedoch oft durch fehlende Standardisierung der Log-Formate begrenzt wird. AURA begegnet diesen Herausforderungen durch eine reine On-Device-Speicherung zur besseren Datenkontrolle, eine effiziente lokale Pufferung zur Minimierung der Performance-Last und die Bereitstellung eines konsistenten Datenformats zur Vereinfachung der späteren Analyse.
[[https://link.springer.com/article/10.1007/s10664-019-09687-9]]
---------

**4. Zeitplan (Wochenplan)**

**Bearbeitungszeit**: 23.10.2025 - 15.01.2026

| Woche | Datum | Geplante Aufgaben | Tatsächlicher Status |
| **01** | 23.10. - 29.10. | **Kick-off & Einarbeitung:** Projektdefinition finalisieren, Einarbeitung in die Erstellung von Android-Bibliotheken (`.aar`) und die Grundlagen von CouchDB. | DONE |
| **02** | 30.10. - 05.11. | **Literaturrecherche:** Analyse von existierenden Logging-Frameworks (Related Work) und Best Practices. | DONE |
| **03** | 06.11. - 12.11. | **Architektur & Design:** Detaillierte Systemarchitektur entwerfen, API-Design (`setupExperiment`, `logEvent`) und JSON-Datenmodell finalisieren. | DONE |
| **04** | 13.11. - 19.11. | **Setup & Prototyping:** Projekt-Setup (App-Modul + Bibliotheks-Modul), erste API-Implementierung, Testaufrufe loggen Events nach Logcat. | DONE |
| **05** | 20.11. - 26.11. | **Implementierung (Kern I):** Kernlogik entwickeln: `logEvent`-Funktion mit automatischer Anreicherung von Metadaten (Timestamp, IDs etc.). | DONE |
| **06** | 27.11. - 03.12. | **Implementierung (Kern II):** Lokale Speicherung implementieren: Gesammelte Events werden in eine JSON-Datei auf dem Gerät geschrieben. | IN PROGRESS |
| **07** | 04.12. - 10.12. | **Implementierung (Demo-App):** Fitts' Law Experiment mit **Counterbalancing** (Rechtshändig vs. Linkshändig). Implementierung der Logik, die basierend auf Vor-Daten die nächste Condition wählt. | NOT DONE |
| **08** | 11.12. - 17.12. | **Datenbank-Integration I:** CouchDB-Server aufsetzen, Netzwerk-Schicht in der Bibliothek implementieren (z.B. mit Ktor/Retrofit). | DONE |
| **09** | 18.12. - 24.12. | **Datenbank-Integration II (Bidirektional):** Implementierung des **Rückkanals** (Server -> App). Abrufen von User-Daten für Counterbalancing-Entscheidungen. | NOT DONE |
| **10** | 25.12. - 31.12. | *(Weihnachtspause / Puffer)* | |
| **11** | 01.01. - 07.01. | **Test & Verfeinerung:** Umfassende Tests der Bibliothek im Demo-Prototypen (inkl. Offline-Szenarien), Debugging und Erstellung der finalen `.aar`-Datei. | NOT DONE |
| **12** | 08.01. - 14.01. | **Dokumentation & Bericht:** Verfassen der API-Dokumentation und einer Integrationsanleitung. Ausarbeitung des Abschlussberichts. | NOT DONE |
| **13** | 15.01.2026 | **Abschluss:** Finale Code-Einreichung, Vorbereitung und Halten der Abschlusspräsentation. | NOT DONE |

---------

**5. Anleitung: Wie unsere Logging-App genutzt wird**

---------

**6. Features & Limitationen**

**Features:**
*   **Easy Setup:** Einzeilige Initialisierung (`Aura.setupExperiment`).
*   **Auto-Metadaten:** Automatische Erfassung von Zeitstempel, UserID, etc.
*   **Offline-First:** Lokale Speicherung (JSON) als Fallback (Work in Progress).
*   **CouchDB Sync:** Automatische Synchronisierung mit dem Backend.
*   **NEU: Bidirektionaler Datenfluss:** Abrufen von User-Status vom Server ("Welche Conditions hat User X schon absolviert?").
*   **NEU: Counterbalancing-Support:** Unterstützung bei der Zuweisung von Experiment-Konditionen (z.B. AB/BA Testing), um Reihenfolgeneffekte zu vermeiden.

**Limitationen:**
*   Aktuell nur für Android.
*   Benötigt Netzwerkverbindung für den initialen Status-Abgleich (für Counterbalancing).

---------

**7. Projektergebnisse (Deliverables)**
