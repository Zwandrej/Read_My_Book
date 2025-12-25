# Read My Book

Version: 0.2.0

Offline EPUB reader with Text-to-Speech for Android (Boox friendly).

## Features
- Import EPUB via Storage Access Framework
- Parse EPUB spine and table of contents (nav.xhtml or toc.ncx)
- Chapter list screen with selectable chapters
- Text-to-Speech reading with sentence preview and controls
- Resume position (chapter + sentence index)

## Version History
### 0.2.0
- Move TTS playback into a foreground service for background reading
- Add notification permissions for Android 13+

### 0.1.1
- Improve HTML to text cleanup for chapter content

### 0.1.0
- Initial app flow with start screen and chapter list
- EPUB TOC parsing for human-readable chapter titles
- TTS reading with sentence preview and resume position

## Planned Next
- Better TTS voices (voice picker, offline voice checks)
- Notification playback controls (Read/Stop/Next/Prev)
- Persist TTS rate and voice settings
- Resume screen polish (show saved position clearly)
- Multi-book library with last 5 recent books
- Resume cards per book (title, chapter, sentence)
- Bookmark/save position per book
- Chapter search and filter
- Sleep timer (15/30/60 minutes)
- Skip by paragraph or sentence count
- "Now Reading" header with chapter title
- Export/import reading state (backup)

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
