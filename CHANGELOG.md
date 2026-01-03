# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [26.01.3]

### Fixed

- App Crash on Screen Rotation: Fixed crashes that occurred when rotating the device screen
- Database Sync Optimization: Improved synchronization performance and reliability
  - Faster app startup with background sync operations
  - Prevents excessive syncing during screen rotation
- User Feedback for Repo Opening: Added loading indicator when opening repositories
- Empty State Guidance: Added helpful hint when the notes list is empty, guiding users to open the drawer and select a folder to get started
- Timestamp Calculation Performance: Dramatically improved database resync performance from minutes to seconds

## [26.01.2]

### Added

- Empty Repository Support: Git wrapper now handles cloning repositories with no commits or missing main/master branches
  - Automatically creates initial commit when cloning empty repositories
  - Creates main branch if neither main nor master branches exist
  - Ensures repositories are always in a usable state after cloning

### Changed

- Default Settings: Updated default preferences for new users
  - Scrollbars now enabled by default for better navigation
  - Minimum note width changed to 150dp (from 200dp)
  - Remember last opened folder enabled by default
  - Full titles shown in list view by default
  - Tags visible in both grid and list views by default
  - Subfolders excluded by default in folder browsing
  - Background Git operations enabled by default for smoother workflow

### Fixed

- Git Authentication Errors: Improved error handling for clone and push operations with specific guidance for different Git hosting providers
  - Added provider-specific error messages for GitHub, Bitbucket, GitLab, Azure DevOps, and AWS CodeCommit
  - Fixed "too many redirects or authentication replays; class=Http (34)" errors by providing clear instructions to use Personal Access Tokens/App Passwords instead of passwords
  - Enhanced user experience when authentication fails during Git operations
- Git Author Defaults: Added default email fallback to prevent commit failures when creating new local repositories
  - Git author name defaults to "gitnote" when empty
  - Git author email defaults to "gitnote@localhost" when empty

## [26.01.1]

### Added

- Advanced Filtering Options: New settings for more flexible note filtering
  - "Tag filtering ignores folders" (enabled by default): When filtering by tags, search across all folders instead of limiting to current folder
  - "Search ignores all filters" (enabled by default): When searching, show results from all folders regardless of current folder and subfolder settings
- Folder Context in Tag Mode: When using tag filtering, the current folder context is preserved in the navigation drawer for better user awareness

### Improved

- Tag Display: Tags now wrap to multiple lines in list view for better readability when notes have many tags

## [26.01]

### Added

- Task Management: Support for marking notes as tasks with completion checkboxes
  - YAML frontmatter parsing for `completed?` field
  - Toggleable checkboxes in grid and list views
  - Convert notes to tasks and vice versa via long-press menu
- Frontmatter Hiding: Automatically hide YAML frontmatter in read-only view mode for clean Markdown rendering
- List View Improvements: Option to show full note titles with line wrapping instead of truncation
- Tag Display Mode: Control tag visibility in grid and list views
  - Settings option with 4 modes: None, List Only, Grid Only, Both
  - YAML frontmatter parsing for `tags:` field with list support
  - Tag chips with Material 3 styling in note cards
- Folder Display Mode: Control whether subfolder notes are included when browsing folders
  - Toggle setting: Include subfolders on/off
  - Real-time folder filtering in both grid and list views
  - Default behavior maintains existing subfolder inclusion
- Background Git Operations: Option to perform git pull/push operations in background
  - Toggle setting to avoid blocking UI during refresh operations
  - Useful when working alone and app syncs at startup
  - Maintains sync status without UI interruptions
- Tag Filtering: Browse and filter notes by tags in the navigation drawer
  - Automatic tag extraction from all notes' frontmatter
  - Tag-based navigation with drawer auto-close for focused viewing
  - Real-time filtering in both grid and list views
- Move Notes: Drag-and-drop style note relocation between folders
  - Long-press menu option to initiate move mode
  - Drawer navigation for folder selection during move operations
  - Automatic file relocation and Git commit
- Enhanced Floating Action Button: Expandable menu for quick note/task creation
  - Create regular notes or tasks with completion tracking
  - Quick search access
  - Context-aware folder inheritance for new items
- Git Log Viewer: Access git commit history directly in the app
  - View commit messages, authors, dates, and hashes
  - Loading indicator during git log retrieval
  - Formatted timestamps for better readability
  - Available in the main menu
- Debug Features Toggle: Option to enable debug features in release builds
  - Settings option in About section to enable debug tools
  - Makes reload database button available when needed for troubleshooting
- Interactive Checkboxes: Clickable checkboxes in rendered markdown mode
  - Tap checkboxes in read-only mode to toggle completion status
  - Material Design checkbox components replace plain markdown text
  - Accept button appears to save changes without switching to edit mode
  - Seamless task management in rendered markdown view
- Image Support: Display images in rendered markdown mode
  - Support for remote images via HTTP/HTTPS URLs
  - Support for local repository files via relative paths (resolved from the note's directory)
  - PNG/JPG/SVG images load correctly with proper path resolution
- Git Synchronization Improvements: Better handling of external repository changes
  - Automatic database synchronization when external commits are detected
  - Improved error handling for notes modified or deleted remotely
  - Database index automatically updates when app detects external changes
- Reload Spinner: Visual indicator in action bar during database reload operations
  - Shows spinning progress indicator when "Reload Database" is selected from menu
  - Provides immediate feedback for long-running database operations
  - Automatically disappears when operation completes or fails
- Automatic Merge Conflict Resolution: Intelligent three-pass conflict resolution during synchronization
  - Local-first resolution strategy preserves user changes
  - Visual feedback with loading spinner during resolution
  - Seamless background operation without user intervention
  - Repository-aware path resolution for cross-platform compatibility
- Scrollbars: Added opt-in custom scroll indicators to grid and list views for better navigation in long note lists

### Changed

- Improved note title display logic to respect "show full path" settings consistently

## [25.12]

### Changed

- new markdown rendering lib

### Added

- load keys from devices

## [25.11]

### Changed

- optimization for big repo
  - use fts in libsql
  - use a padger (only load notes visibles)

### Added

- cloning page
- pat authentification
- default path for new notes

## [25.08]

### Changed

- new rust backend for libgit2
- ssh support and strong github integration (list repos, create a new one)

## [25.07]

### Added

- markdown dedicated editor

## [25.07]

### Added

- izzy support

## [25.06]

### Added

- read only mode with markdown rendering

## [25.05]

### Added

- f-droid support

## [24.08]

- delete folder (#42)
- remove fuzzy for searching. Use substring instead

### Changed

- kotlin 2.0

### Fixed

- provider dd
