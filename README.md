# Read My Book

Version: 0.1.0

Offline EPUB reader with Text-to-Speech for Android (Boox friendly).

## Features
- Import EPUB via Storage Access Framework
- Parse EPUB spine and table of contents (nav.xhtml or toc.ncx)
- Chapter list screen with selectable chapters
- Text-to-Speech reading with sentence preview and controls
- Resume position (chapter + sentence index)

## Version History
### 0.1.0
- Initial app flow with start screen and chapter list
- EPUB TOC parsing for human-readable chapter titles
- TTS reading with sentence preview and resume position

## Build
```bash
./gradlew assembleDebug
```

## Test
```bash
./gradlew test
```

## Run
- Open the project in Android Studio
- Click Run ▶ on a device or emulator
