# Field Intelligence Console

**One tap. Instant awareness. Minimal cognitive load.**

Field Intelligence Console is an offline-first Android application designed to provide rapid location awareness through a modular Action Matrix. It reduces the need to switch between multiple apps while working in the field by orchestrating trusted data sources and AI services around a single, highly accurate GPS scan.

## Features

- **One-Tap Scan:** Instantly capture your coordinates, accuracy, and timestamp.
- **Action Matrix:** Eight distinct action modules (Info Scan, History, Navigation, AI Analysis, Risk/Safety, Logistics, Financial, Emergency Exit).
- **Offline First:** All scans and observations are stored locally in a Room database.
- **Pluggable Core Code Engine:** Securely connect external API providers (OpenAI, Google Maps/Earth, SolidWorks, Autodesk APS) via the Android Keystore to run deep, contextual AI analysis directly within the app.
- **Intent Fallback:** If no API keys are configured, the app gracefully degrades to intent-based sharing, allowing you to use your existing apps (e.g. ChatGPT, Google Maps) with pre-built structured prompts.
- **High-Contrast UI:** Built with Jetpack Compose using Material Design 3, optimised for one-handed outdoor operation.

## Architecture

Built using modern Android architecture principles:
- **MVVM** (Model-View-ViewModel)
- **Jetpack Compose** for UI
- **Kotlin Coroutines & StateFlow** for asynchronous reactive programming
- **Room Database** for local persistence
- **Hilt** for Dependency Injection
- **Strategy Pattern** for the pluggable Core Engine

See `docs/ARCHITECTURE.md` for a deep dive into the system design.

## Building the Project

1. Clone or extract the repository.
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Sync Gradle.
4. Build and run on a physical device or emulator (API 26+).

> **Note:** The project compiles immediately. To enable the in-app AI features, you must supply your own API keys in the app's Settings screen.

## License

This project is open-source software licensed under the MIT License. See `LICENSE` for details.
