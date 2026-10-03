# Architecture Overview

Field Intelligence Console follows the official Android Architecture Guidelines, enforcing a strict unidirectional data flow (UDF) and a clear separation of concerns.

## 1. UI Layer (Jetpack Compose)
- **State Holders:** `MainViewModel`, `AiAnalysisViewModel`, `EngineSettingsViewModel`.
- **UI State:** Exposed exclusively as immutable `StateFlow` objects.
- **Composables:** Stateless UI components (`MainScreen`, `ActionMatrixButton`, `ObservationDialog`) that emit events back to the ViewModels.

## 2. Data Layer (Room)
- **Entity:** `FieldEntry` is the single source of truth for a scan.
- **DAO:** `FieldEntryDao` exposes suspend functions for one-shot reads/writes and `Flow` for reactive lists.
- **Repository:** `FieldEntryRepository` abstracts the local database, preparing the app for future remote sync capabilities.

## 3. Core Engine Layer (Strategy Pattern)
The application supports a pluggable AI/GIS backend:
- `CoreEngine` interface defines the contract.
- `CoreEngineFactory` selects the best engine at runtime based on configured keys.
- `ApiKeyStore` manages AES-256-GCM encrypted key storage via the Android Keystore.
- Implementations include `OpenAIEngine`, `GoogleEngine`, `SolidWorksEngine`, and `AutodeskEngine`.
- **Fallback:** `NoOpEngine` is used when no keys are present, routing tasks to `IntentDispatcher`.

## 4. Intent Dispatcher
A centralised router for all external Android intents. If the app cannot perform a task internally (or if the user prefers external apps), `IntentDispatcher` builds the appropriate `Intent` (e.g. `geo:`, `google.navigation:`, or a web fallback) and launches it.

## 5. Dependency Injection (Hilt)
All major components are injected via Dagger Hilt.
- `AppModule` provides singletons for the database, DAOs, LocationManager, and API Key Store.
- ViewModels use constructor injection.
