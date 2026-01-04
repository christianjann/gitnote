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

## User Quick Start

1. **Set up a Git repository**: Create a private Git repository on GitHub, GitLab, or your own server to store your notes
2. **Install GitNoteCJE**: Download and install the app from the [GitHub releases page](https://github.com/christianjann/gitnote/releases) or build it yourself following the developer instructions below
3. **Explore examples**: Check out the [examples](./doc/examples/) and copy them to your repository for inspiration
4. **Start taking notes**: Enjoy seamless markdown note-taking with Git synchronization!

## Developer Quick Start

1. **Install dependencies**: Rust, Android Studio, Android SDK, JDK
2. **Clone repository**: `git clone https://github.com/christianjann/gitnotecje.git`
3. **First Android Studio build**: Open the project in Android Studio and build it once to generate `.gradle/config.properties` with the correct JBR path
4. **Build Rust library**: `just rust-build`
5. **Build**: `just install` (builds and installs debug APK)
6. **Run**: Open the app and create/clone your first Git repository

For development outside Android Studio, a [justfile](justfile) provides convenient commands. See [just](https://github.com/casey/just) for usage details.

# Why

Because all apps which integrate with Git on Android either crash, are slow, are based on technologies I don't like (and are slow as hell) or don't have the features I need.

# Features

- [x] create/open/clone repositories
- [x] private repo support (SSH and HTTPS)
- [x] notes search (global and from specific folder)
- [x] grid view with customizable widths
- [x] list view
- [x] markdown edit view and full markdown rendering
- [x] clickable check boxes in markdown rendering
- [x] remote sync
- [x] time based sort
- [x] multi-language support
- [x] flexible tag display in grid and list view
- [x] tag-based filtering and organization
- [x] frontmatter metadata support
- [x] view the Git log
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
    <td align="center">
      <img src="assets/tag_editor.png" width="220" alt="Tag editor"/>
    </td>
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
    <td align="center">
      <strong>Tag Editor</strong><br>
      Edit and organize note tags with an intuitive interface
    </td>
  </tr>
</table>

_Supported Android versions: 11+_

_Supported Architecture: `arm64-v8a`, `x86_64`_

# Documentation

- [Detailed features documentation](./doc/features.md)
- [Building the app](./doc/building.md)
- [Markdown header format](./doc/design/markdown_header.md)
- [App architecture](./doc/design/app_architecture.md)
- [Contributing](./CONTRIBUTING.md)
- [Changelog](./CHANGELOG.md)

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
- Device memory storage is significantly slower than app memory (up to 200x). See [Storage Options and Performance](./doc/features.md#storage-options-and-performance) for details. Use app memory where possible.

# Future direction

- Move more of the core logic and data handling into the Rust part
- Maybe get rid of the database caching entirely and use a more thin layer on top of the Git abstraction
- Make it much faster
- Extend and cleanup frontmatter support (use title etc...)

# Attribution

This project is a fork of the original [GitNote](https://github.com/wiiznokes/gitnote) Android app by [wiiznokes](https://github.com/wiiznokes). It's an opinionated edition optimized to better suit personal note-taking requirements and workflow preferences, with improvements to performance, UI responsiveness, and feature enhancements.
