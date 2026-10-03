# Contributing to Field Intelligence Console

We welcome contributions from the community. Please follow these guidelines to ensure a smooth review process.

## Adding a New Core Engine Provider

The Core Engine architecture is designed to be extensible via the Strategy pattern. To add a new provider (e.g. Anthropic, Mapbox, or a custom internal API):

1. **Add to Enum:** Add your provider to `ApiProvider.kt`.
2. **Implement Engine:** Create a new class implementing the `CoreEngine` interface (e.g. `AnthropicEngine.kt`).
3. **Update Factory:** Register your engine in `CoreEngineFactory.kt` priority list.
4. **No UI Changes Required:** The Settings screen will automatically generate a secure input card for your new provider, and the `AiAnalysisViewModel` will automatically use it if it holds the highest priority.

## Adding a New Action Matrix Button

If you wish to add a new coloured action button to the matrix:

1. Define a new colour in `Theme.kt`.
2. Add the button definition to the `buttons` list in `MainScreen.kt`.
3. Add the corresponding routing logic to the `handleAction` function.
4. If the action requires an external app, add the intent building logic to `IntentDispatcher.kt`.

## Pull Request Process

1. Fork the repository and create your feature branch.
2. Ensure your code compiles and follows the existing Kotlin style guidelines.
3. Write or update unit tests where applicable.
4. Submit a Pull Request with a clear description of the problem solved or feature added.
