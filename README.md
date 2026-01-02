<div align="center">

<h1>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/logo_wide_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/logo_wide_light.svg">
  <img alt="GitNote" width="50%" src="assets/logo_wide_light.svg">
</picture>
</h1>

</div>

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/wiiznokes/gitnote.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/wiiznokes/gitnote/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.wiiznokes.gitnote?logo=f-droid&label=F-Droid&cacheSeconds=3600)](https://f-droid.org/packages/io.github.wiiznokes.gitnote)
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/io.github.wiiznokes.gitnote)](https://apt.izzysoft.de/fdroid/index/apk/io.github.wiiznokes.gitnote)

Android note app which integrate Git. You can use this app with other desktop editors.

## Why

Because all apps which integrate git on Android either separate the note title from the name of the file or use old UI/UX frameworks

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
- [x] git log viewer
- [x] folder display mode options

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

_Supported Android versions: 11 to 16_

_Supported Architecture: `arm64-v8a`, `x86_64`_

## Documentation

- [Detailed Features](./doc/features.md)
- [Design Document](./doc/design/markdown_header.md)

# Build

[See](./BUILD.md).

# Current limitation

- Android does not differentiate case for file name, so if you have a folder named `A` and another folder named `a`, `a` will not be displayed.
- Conflict will make the app crash

## Contributing

See [this file](./CONTRIBUTING.md).
