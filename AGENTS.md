# Repository Guidelines

## Project Structure & Module Organization
This repository currently contains planning and contributor guidance only. The intended Android app layout (per `PROJECT.md`) is:
- `app/src/main/java/.../ui/` for Compose screens
- `app/src/main/java/.../tts/` for TextToSpeech playback and chunking
- `app/src/main/java/.../epub/` for EPUB import/parsing
- `app/src/main/java/.../storage/` for persistence (Room/DataStore)
- `app/src/main/java/.../util/` for shared helpers

When the Android project is generated, keep modules small and aligned to these folders.
Currently, most logic lives in `app/src/main/java/com/ajz/ereader/MainActivity.kt` and should be split into the folders above as milestones progress.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` to build a debug APK
- `./gradlew test` for unit tests
- `./gradlew connectedAndroidTest` for device/emulator tests

## Coding Style & Naming Conventions
Follow Kotlin and Jetpack Compose conventions:
- Indentation: 2 or 4 spaces, consistent with Android Studio defaults.
- Naming: `PascalCase` for classes (e.g., `Book`, `PlaybackState`), `camelCase` for functions and properties.
- Keep files short; consider splitting when files exceed ~250 lines.
- Use brief comments only when a concept is non-obvious.

## Testing Guidelines
Testing framework and coverage rules are not set yet. When tests are added:
- Prefer JUnit for unit tests and AndroidX Test for instrumentation.
- Name tests after behavior, e.g., `TtsPlayer_skipsForwardBySentence()`.
- Document how to run the tests alongside the Gradle tasks above.

## Commit & Pull Request Guidelines
This repo has no Git history and no commit conventions yet. If you initialize Git:
- Use clear, imperative commit messages (e.g., "Add EPUB import screen").
- PRs should include a brief description, linked issue (if any), and screenshots for UI changes.

## Agent-Specific Instructions
Follow `agent.md` for the pair-coding protocol and milestone flow. The project plan and milestones live in `PROJECT.md` and should be treated as the source of truth.
