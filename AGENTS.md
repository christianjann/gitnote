# GitTasks Agent Instructions

GitTasks is a Git-based note-taking and task management Android app. Notes are stored as Markdown files with YAML frontmatter, synced via Git repositories.

**Tech Stack**: Kotlin, Jetpack Compose, Room database, Rust (JNI for Git operations), Gradle

## Quick Reference

| Command              | Purpose                                   |
| -------------------- | ----------------------------------------- |
| `just build`         | Build debug APK                           |
| `just install`       | Build and install to device               |
| `just rust-build`    | Compile Rust library (after Rust changes) |
| `just release-build` | Build release APK                         |
| `just fmt`           | Ensure all files are formatted properly   |

## Code Structure

```
app/src/main/java/io/github/christianjann/gittasks/
├── ui/
│   ├── screen/        # Composable screens (Grid, Editor, Settings)
│   ├── viewmodel/     # ViewModels (GridViewModel, TextVM, etc.)
│   ├── component/     # Reusable UI components
│   └── model/         # UI state models (GridNote, SortOrder, etc.)
├── data/
│   ├── room/          # Room database (Note, NoteFolder, Dao)
│   └── AppPreferences # DataStore preferences
├── helper/            # Utilities (FrontmatterParser, etc.)
└── manager/           # Business logic (StorageManager, GitManager)

app/src/main/rust/src/ # Rust/JNI code for Git operations
doc/                   # Design docs and feature documentation
```

## When Making Changes

**Always**:

1. Follow the [app architecture](doc/design/app_architecture.md)
2. Update `CHANGELOG.md` under `[Unreleased]` section
3. Update `doc/features.md` if adding user-facing features
4. Run `just build` to verify compilation
5. When adding new strings to [strings.xml](app/src/main/res/values/strings.xml) also update the translations

**Frontmatter format** (see [doc/design/markdown_header.md](doc/design/markdown_header.md)):

```yaml
---
title: Note Title
completed?: yes|no
due: 2026-01-25T14:00:00
tags:
  - tag1
---
```

## Architecture Patterns

- **MVVM**: Screens observe ViewModels via `StateFlow`/`collectAsState()`
- **Repository pattern**: `NoteRepository` abstracts Room database access
- **Managers**: `StorageManager` handles file I/O + Git commits, `GitManager` for Git operations
- **Parsing**: `FrontmatterParser` for all YAML frontmatter operations

## Build Prerequisites

- Android SDK/NDK, Java JDK, Rust toolchain with Android targets, `just` command runner
- JAVA_HOME configured in `.gradle/config.properties`

**Manual Gradle** (if not using `just`):

```bash
JAVA_HOME=$(grep '^java.home=' .gradle/config.properties | cut -d'=' -f2) ./gradlew <command>
```

## Troubleshooting

- Rust targets: `rustup target add aarch64-linux-android x86_64-linux-android`
- Verify JAVA_HOME in `.gradle/config.properties`
- For device issues: check ADB connection and authorization
