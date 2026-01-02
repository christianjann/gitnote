<div align="center">

<h1>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/logo_wide_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/logo_wide_light.svg">
  <img alt="GitNoteCJE" width="50%" src="assets/logo_wide_light.svg">
</picture>
</h1>

</div>

<!-- [![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/christianjann/gitnotecje.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/christianjann/gitnotecje/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.christianjann.gitnotecje?logo=f-droid&label=F-Droid&cacheSeconds=3600)](https://f-droid.org/packages/io.github.christianjann.gitnotecje)
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/io.github.christianjann.gitnotecje)](https://apt.izzysoft.de/fdroid/index/apk/io.github.christianjann.gitnotecje) -->

Android note-taking app with Git integration. A personal fork of GitNote, optimized for efficient markdown-based note management with advanced features like tag filtering, Git log viewing, and seamless synchronization.

## Quick Start

1. **Install dependencies**: Rust, Android SDK, JDK
2. **Clone repository**: `git clone https://github.com/christianjann/gitnotecje.git`
3. **First Android Studio build**: Open the project in Android Studio and build it once to generate `.gradle/config.properties` with the correct JBR path
4. **Build**: `just install` (builds and installs debug APK)
5. **Run**: Open the app and create/clone your first Git repository

For development outside Android Studio, a [justfile](justfile) provides convenient commands. See [just](https://github.com/casey/just) for usage details.

# Why

Because all apps which integrate with Git on Android either crash, are slow, are based on technologies I don't like (and are slow as hell) or don't have the features I need.

# Features

- [x] create/open/clone repositories
- [x] notes search (global and from specific folder)
- [x] grid view with customizable widths
- [x] tree view
- [x] edit view
- [x] private repo (SSH and HTTPS)
- [x] remote sync
- [x] time based sort
- [x] multi-language support
- [x] flexible tag display in grid view
- [x] tag-based filtering and organization
- [x] full markdown rendering
- [x] frontmatter metadata support
- [x] Git log viewer
- [x] folder display mode options
- [x] automatic merge conflict resolution

<table align="center">
  <tr>
    <td align="center">
      <img src="assets/grid.png" width="180" alt="Grid view with flexible tag display"/>
    </td>
    <td align="center">
      <img src="assets/list.png" width="200" alt="List view"/>
    </td>
    <td align="center">
      <img src="assets/tags.png" width="180" alt="Tag filtering interface"/>
    </td>
    <td align="center">
      <img src="assets/drawer.png" width="180" alt="Drawer navigation"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <strong>Grid View</strong><br>
      Browse notes in a customizable grid layout with flexible tag display
    </td>
    <td align="center">
      <strong>List View</strong><br>
      Navigate through notes in a traditional list format
    </td>
    <td align="center">
      <strong>Tag Filtering</strong><br>
      Filter and organize notes using tags for better organization
    </td>
    <td align="center">
      <strong>Navigation Drawer</strong><br>
      Access repositories, settings, and navigation options
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="assets/edit.png" width="220" alt="Edit screen"/>
    </td>
    <td align="center">
      <img src="assets/rendered.png" width="220" alt="Markdown rendering"/>
    </td>
    <td align="center">
      <img src="assets/git_log.png" width="220" alt="Git log viewer"/>
    </td>
    <td align="center"></td>
  </tr>
  <tr>
    <td align="center">
      <strong>Edit Screen</strong><br>
      Full-featured markdown editor with syntax highlighting
    </td>
    <td align="center">
      <strong>Markdown Rendering</strong><br>
      Preview rendered markdown with full formatting support
    </td>
    <td align="center">
      <strong>Git Log Viewer</strong><br>
      View commit history and track changes to your notes
    </td>
    <td align="center"></td>
  </tr>
</table>

_Supported Android versions: 11+_

_Supported Architecture: `arm64-v8a`, `x86_64`_

# Documentation

- [Detailed features documentation](./doc/features.md)
- [Building the app](./BUILD.md)
- [Markdown header format](./doc/design/markdown_header.md)
- [Contributing](./CONTRIBUTING.md)

# Build System

This project uses a custom build system based on [just](https://github.com/casey/just) for task automation. Key commands:

- `just build` - Build debug APK
- `just install` - Build and install debug APK
- `just rust-build` - Build Rust native library
- `just fmt` - Format all code (Kotlin + Rust)
- `just test` - Run Rust unit tests
- `just test-integration` - Run Git integration tests (requires git, located in `app/src/main/rust/tests/`)
- `just clean-build` - Clean and rebuild

See [building.md](doc/building.md) for detailed build instructions.

# Current limitation

- Android does not differentiate case for file name, so if you have a folder named `A` and another folder named `a`, `a` will not be displayed.
- Resync and reload of the database when there was an remote change by some other Git client takes forever
- Sometimes the app is misbehaving, the only thing that helps is a reload of the database via the debug menu
- Opening a local repository in device memory that was previously a remote cloned repo seems not to work, re-clone it

# Future direction
- Move more of the core logic and data handling into the Rust part
- Maybe get rid of the database caching entirely and use a more thin layer on top of the Git abstraction
- Make it much faster

# Attribution

This project is a fork of the original [GitNote](https://github.com/wiiznokes/gitnote) Android app by [wiiznokes](https://github.com/wiiznokes). It's an opinionated edition optimized to better suit personal note-taking requirements and workflow preferences, with improvements to performance, UI responsiveness, and feature enhancements.
