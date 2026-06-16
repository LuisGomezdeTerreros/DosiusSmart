<p align="center">
  <img src="app/src/main/res/drawable/logo_dosius.png" alt="DosiusSmart logo" width="180"/>
</p>
# DosiusSmart

**Intelligent glycemic management system for insulin-dependent diabetics.**

Final Degree Project (Trabajo de Fin de Grado) — Software Engineering, Universidad de Sevilla.

> **Status: Active development.** Core features are implemented and functional. Several modules (AI assistant, statistics, insulin recommendations) are planned but not yet built.

---

## Overview

DosiusSmart is an Android application that centralises the daily management of Type 1 (and insulin-dependent Type 2) diabetes. It pulls continuous glucose monitor (CGM) data from Abbott LibreLinkUp, lets users log food, insulin, and exercise events in a diary, and will eventually provide AI-driven dosage recommendations and meal analysis.

The application is being developed as a TFG at the University of Seville, focusing on integrating real medical data pipelines with a clean, modern Android architecture.

---

## Features

### Implemented

| Feature | Description |
|---|---|
| **Live glucose dashboard** | Current glucose reading pulled from Abbott LibreLinkUp, colour-coded by range (hypoglycaemia / normal / hyperglycaemia) with trend arrows |
| **6-hour glucose chart** | Scrollable Vico line chart plotted from cached CGM history |
| **Background glucose sync** | WorkManager job that periodically fetches new readings and persists them in Room |
| **Food database** | Full CRUD for food items with nutritional data (carbs/100 g, glycaemic index, ingredients), category grouping, search and filter |
| **Food detail view** | Detailed view of a food item with usage history |
| **Diary entry screen** | Log food intake, insulin dose, and/or exercise in a single timestamped entry; each section is optional |
| **Entries on dashboard** | Past diary entries listed below the glucose chart, grouped by day |
| **Settings screen** | LibreLinkUp credentials storage via DataStore |

### Planned / In Progress

- **AI chat assistant** — conversational food logging and glucose analysis via an LLM (architecture designed, not yet implemented)
- **Insulin dosage recommendations** — rule-based + model-assisted carbohydrate-to-insulin calculator
- **Statistics screen** — time-in-range, HbA1c estimate, trend analysis over configurable periods
- **Meal recognition** — photo-based carbohydrate estimation
- **Notifications** — hypo/hyper alerts derived from CGM stream

---

## Architecture

The project follows **MVVM** with a strict three-layer structure:

```
presentation/   Compose screens, ViewModels, UiState, navigation, theme
domain/         Data models (@Entity), GlucoseRepository interface
data/           Repository implementations, DAOs, Room DB, Retrofit API, DI modules
```

Key architectural decisions made deliberately for this project scope:

- **No separate entity classes** — domain models carry `@Entity` annotations directly; `Converters.kt` handles complex types.
- **No use case layer** — ViewModels call repositories directly. Adds no value for a solo CRUD project without unit tests.
- **Repository interface only for `GlucoseRepository`** — because it has two implementations (`LibreLinkUpRepository` + `MockGlucoseRepository`). All other repositories are concrete classes injected directly.
- **`fallbackToDestructiveMigration()`** — development environment; no production data is preserved across schema changes.

---

## Tech Stack

| Category | Library / Tool |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, Kotlin Coroutines, StateFlow |
| Dependency injection | Hilt 2.51.1 (KSP) |
| Local persistence | Room 2.6.1 |
| Network | Retrofit 2 + OkHttp + Kotlinx Serialization |
| Charts | Vico `compose-m3:2.0.0` |
| Background work | WorkManager 2.9.1 |
| Date/time | `kotlinx-datetime:0.6.1` |
| Credential storage | DataStore Preferences |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 36 |

---

## Project Structure

```
app/src/main/java/com/dosius/smart/
├── domain/
│   ├── model/          # GlucoseReading, GlucoseTrend, Entry, Food, Exercise, Ingredient
│   └── repository/     # GlucoseRepository interface
├── data/
│   ├── di/             # Hilt modules (DatabaseModule, NetworkModule, RepositoryModule)
│   ├── local/
│   │   ├── dao/        # EntryDao, FoodReadingDao, GlucoseReadingDao, ExerciseDao
│   │   └── database/   # DosiusDatabase, Converters
│   ├── preferences/    # CredentialPreferences (DataStore)
│   ├── remote/         # LibreLinkUpApi, DTOs, GlucoseReadingMapper
│   ├── repository/     # LibreLinkUpRepository, MockGlucoseRepository,
│   │                   # EntryRepository, ExerciseRepository, DatabaseSeeder
│   └── worker/         # GlucoseRefreshWorker
└── presentation/
    ├── navigation/     # AppNavHost, Screen (sealed class)
    ├── dashboard/      # DashboardScreen, DashboardViewModel, DashboardUiState
    ├── entry/          # AddEntryScreen, AddEntryViewModel
    ├── database/       # DatabaseScreen, AddFoodScreen, FoodDetailScreen + ViewModels
    ├── settings/       # SettingsScreen, SettingsViewModel
    └── theme/          # Color, Type, Theme
```

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- A LibreLinkUp account linked to a FreeStyle Libre sensor (optional — the app falls back to mock data)

### Running the app

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Gradle sync complete.
4. Run on an emulator (API 26+) or a physical device.

To use live CGM data, enter your LibreLinkUp email and password in the **Settings** screen. The app will authenticate and begin syncing readings automatically.

Without credentials the app displays mock glucose data so the UI can be explored without hardware.

---

## CGM Integration

DosiusSmart connects to **Abbott's LibreLinkUp API** (`api.libreview.io`) — the same backend used by the official LibreLinkUp companion app. Authentication uses email/password; the session token is stored encrypted in DataStore. Readings are cached in Room so the dashboard remains functional offline.

> This integration is unofficial and not affiliated with Abbott Diabetes Care.

---

## Roadmap

```
[x] Glucose dashboard + LibreLinkUp integration
[x] Background glucose sync (WorkManager)
[x] Food database (CRUD)
[x] Diary entry logging (food, insulin, exercise)
[ ] Statistics & time-in-range analysis
[ ] AI chat assistant (food logging, glucose Q&A)
[ ] Insulin dosage recommendation engine
[ ] Meal photo recognition
[ ] Hypo/hyper alert notifications
[ ] Polish & accessibility pass
```

---

## Academic Context

This project is submitted as the Final Degree Project (TFG) for the Software Engineering degree at the **Universidad de Sevilla**. It is a solo development project built incrementally over the course of one academic year.

The technical scope covers:

- Integration with a real-world continuous glucose monitor data pipeline
- Local-first persistence with offline support
- Modern Android architecture patterns (MVVM, Compose, Hilt, Coroutines)
- Planned AI-assisted features (LLM tool-calling for dietary analysis)

---

## License

This project is developed for academic purposes. All rights reserved.
