# Field Intelligence Console — Roadmap

## Phase 1: Foundation (Completed)
- Offline-first architecture using Room.
- High-accuracy GPS capture via FusedLocationProvider.
- Action Matrix UI with 8 core intents.
- Pluggable Core Engine interface.
- Android Keystore AES-256-GCM API key management.

## Phase 2: Enhanced Engines (Next)
- **Local LLM Support:** Integrate with on-device models (e.g. MediaPipe / Gemini Nano) to perform AI analysis entirely offline without API keys.
- **Overpass API Engine:** Direct in-app parsing of OpenStreetMap data instead of launching an external browser query.
- **Custom Endpoints:** Allow users to specify custom base URLs for SolidWorks and OpenAI-compatible endpoints (e.g. LM Studio, Ollama).

## Phase 3: Plugin System
- Migrate the hardcoded Action Matrix buttons to a dynamic registry.
- Allow developers to build external APKs that register new buttons, intents, and engines into the Field Intelligence Console via Android Broadcasts or AIDL.

## Phase 4: Sync & Collaboration
- Optional remote backend sync for `FieldEntry` records.
- End-to-end encrypted team sharing of field scans.
