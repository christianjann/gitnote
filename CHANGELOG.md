# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [26.01.17]

### Added

- Favorites Feature:
  - New "Favorites" mode in navigation drawer (cycle: Folders → Tags → Favorites)
  - Long-press on a folder or tag to add/remove from favorites
  - Favorites mode shows quick access to favorite folders and tags
  - Favorites stored in `favorites.md` file at repository root (synced via Git)
  - Star icon indicates favorited items in Folders and Tags modes
- Drawer State Persistence:
  - App remembers the last drawer mode (Folders/Tags/Favorites) across restarts
  - Selected tag is now persisted and restored when reopening the app
- Network Sync Settings:
  - New "Offline mode" toggle - completely disables all network operations (sync, push, pull)
  - New "Sync only on WiFi" option - prevents sync over mobile data
  - Optional "Sync on specific WiFi only" - requires location permission to detect WiFi network name
  - Users can select current network or manually enter a WiFi network name (SSID)
  - Location permission is only requested when enabling specific WiFi feature (opt-in)

### Changed

- Editor: Moved Tags and Due Date buttons from top bar to FAB area for more space to view/edit note filename
- Drawer: Moved Create New Folder button from top bar to FAB area for more space to view the current path

### Fixed

- Tag Filtering: Fixed tag filter to only match YAML frontmatter tags, not markdown list items in note content
- Due View: Notes are now properly sorted by due date when "Due view ignores all filters" is disabled
- Due View: Completed tasks now sort to the bottom (same behavior as List view)

## [26.01.16]

### Added

- Multi-Select Move: Move multiple notes at once
  - Select multiple notes and choose "Move selected notes" from the menu
  - Navigate to destination folder using the drawer
  - All selected notes are moved in a single Git commit
- Due Date Feature: Set and track due dates for notes
  - New "Due" view mode (cycle through Grid → List → Due with the view toggle button)
  - Due view shows only notes with due dates set, sorted by due date
  - Set due date from note context menu ("Set due date" option)
  - Set due date from editor/preview mode (calendar button in top bar)
  - Due dates displayed in list view with color coding (red if overdue, primary color if upcoming)
  - Due dates stored in frontmatter as ISO format (e.g., `due: 2024-08-07T11:28:29`)
  - New setting: "Due view ignores all filters" (enabled by default) - shows all notes with due dates from all folders

### Fixed

- Fixed two times 150 in NoteMinWidth menu
- Cursor Position When Tapping Below Text: Tapping below the last line in the text editor now moves the cursor to the end of the text instead of the beginning
- Markdown Preview: Text can now be selected and copied in read-only markdown view
- Markdown Preview: Improved checkbox alignment so checkboxes align with the first line of text
- Markdown Preview: Save button now appears when tags or due date is changed in preview mode

### Changed

- Asset Manager: Improved usability
  - Full filenames now displayed instead of truncated text
  - Added scrollbar for easier navigation when many assets are present
  - Added image preview thumbnails for image files (PNG, JPG, GIF, WebP, BMP, SVG)
- Navigation Drawer: Improved folder navigation buttons
  - Added separate "up" button (arrow icon) to navigate to parent folder
  - "Enter" button (return icon) now selects current folder and closes drawer
  - Added bottom padding to prevent folder items from being hidden by buttons

## [26.01.15]

### Added

- **Export Repository as ZIP**: New backup feature in settings
  - Export entire repository including all notes and assets to a ZIP file
  - Save to device storage or share via email, cloud services, etc.
  - Especially useful for app memory repositories without remote sync
  - Timestamped filenames for easy backup organization

### Fixed

- **Move Note in Tag Mode**: Drawer now automatically switches to folder mode when moving a note
  - Previously, users couldn't select a destination folder if the drawer was in tag browsing mode
  - The drawer now switches to folder view and clears tag selection when initiating a move

## [26.01.14]

### Changed

- **GitHub OAuth Implementation**: Updated OAuth flow for improved compatibility
  - New GitHub OAuth application with updated client credentials
  - Added explicit `redirect_uri` parameter to ensure proper mobile browser handling
  - Enhanced OAuth callback handling for better 2FA support

### Added

- **GitHub Authentication Documentation**: Comprehensive guide covering all authentication methods
  - Detailed OAuth setup with security and privacy explanations
  - SSH key authentication (automatic and manual setup)
  - Personal access token authentication for HTTPS cloning
  - Deploy key configuration for repository-specific access
  - Included authentication flow screenshots and troubleshooting guide

## [26.01.13]

### Changed

- **App Rebrand**: Renamed application from GitNoteCJE to GitTasks
  - Updated all internal references, themes, and configurations
  - Modified default Git author name from "gitnote" to "gittasks"
- **Frontmatter Title Preference**: Changed `preferFrontmatterTitle` setting to be enabled by default
  - Notes with frontmatter metadata will now use the title from frontmatter by default
  - Users can still disable this in settings if they prefer filename-based titles

## [26.01.12]

### Fixed

- **Repository Initialization**: Fixed empty repository handling to ensure proper git operations
  - Local repositories now automatically created with initial welcome commit
  - Welcome message (`welcome.md`) greets new users and explains basic functionality
  - Eliminates "repository in invalid state" errors when creating first note
  - Empty repository checks now use appropriate logging (debug/info/warning) for better diagnostics
  - Git log and other query functions handle empty repositories gracefully

## [26.01.11]

### Fixed

- **Text Field Focus and Scrolling**: Fixed text editor cursor visibility and interaction issues
  - Cursor now appears when tapping anywhere in the text editing area, not just the first line
  - Added virtual scrolling capability to scroll up even when text doesn't fill the screen
  - 200dp bottom spacer allows better visibility of text at the bottom during editing
  - Clickable wrapper around text field ensures focus is properly requested on any tap
  - Applied to both plain text and markdown editing modes

## [26.01.10]

### Fixed

- Full internationalization support with translations in Czech, German, French, Portuguese (Brazil), Russian, and Ukrainian
- Fixed discard functionality to properly remove untracked imported assets

## [26.01.9]

### Added

- **Asset Manager**: Initial implementation of asset management system for notes
  - Import images and files from device storage into the repository's assets folder
  - Export assets to device storage for sharing or backup
  - Delete assets with confirmation dialogs
  - Git integration: assets are committed, synced, and can be discarded/reverted
  - Automatic relative path calculation for markdown image links from note location to assets
  - Asset insertion button in markdown editor formatting toolbar (accessible via vertical scroll)
  - Supports various image formats and file types
  - Future expansion possible for different asset locations and generic link insertion

- **Vertical Scrolling in Text Editor**: Enhanced text editing experience
  - Text fields now support vertical scrolling for better navigation in long notes
  - Extra bottom padding (200dp) allows scrolling up even when text doesn't fill the screen
  - Improved context visibility while editing by allowing upward scrolling
  - Applied to both plain text and markdown editing modes

- **Git Operation Timeouts**: Cherry-picked from original GitTasks - Added 7-second timeout for remote git operations (push and pull)
  - Prevents operations from hanging indefinitely on slow or unresponsive servers
  - Improves app responsiveness and user experience on unreliable networks
  - Applied globally through libgit2 configuration during initialization

- **Frontmatter Title Preference**: Added user setting to prefer frontmatter titles over filenames
  - New "Prefer frontmatter title" toggle in settings (disabled by default - opt-in)
  - When enabled, note titles in list and grid views use the `title` field from frontmatter instead of filename
  - Falls back to filename if no frontmatter title is present
  - Allows special characters in note titles (colons, slashes, etc.) that are forbidden in filesystem paths
  - Improves note organization and readability for users who maintain frontmatter metadata

### Fixed

- **Text Selection Handle Positioning**: Fixed incorrect positioning of text selection handles in Compose BOM 2025.12.01
  - Text selection handles were displayed above the cursor instead of below where they belong
  - Reverted Compose BOM version to 2025.09.00 to restore correct handle positioning
  - Affects text input fields in the editor, file name field, and search bar

- **Frontmatter Timestamp Localization**: Fixed frontmatter timestamps to use local time instead of UTC
  - Updated timestamps in note frontmatter now display in the user's local timezone
  - Database timestamps remain unchanged and continue to use UTC internally
  - Improves user experience by showing familiar local time in note metadata

## [26.01.8]

### Fixed

- **Screen Rotation Stability**: Fixed `RepoNotInit` errors during device screen rotation
  - Added proper shutdown sequence in MainActivity.onDestroy() to cancel background operations before closing repository
  - StorageManager now cancels all queued git operations and waits for completion during shutdown
  - Prevents background git operations from accessing closed repository during activity recreation
  - Repository state is properly maintained across activity lifecycle events
  - Expensive sync operations now only run once per app session, not on every screen rotation
  - Eliminates synchronization failures caused by premature repository closure

- **Git Operation Queue and Debouncing**: Implemented a unified queue system for all git operations to prevent data races and ensure proper debouncing
  - Commits are queued and executed immediately when it's their turn
  - Pull operations are queued with configurable debouncing delay (default 5 seconds) to prevent excessive syncs during rapid editing
  - Pull operations are immediate for app startup and manual refresh, but delayed for note changes
  - Ensures at least one pull/push operation after the last commit to keep remote repository in sync
  - Prevents conflicts between simultaneous commit and pull operations
  - Consolidates multiple commit operations into single commits with detailed change logs
  - Fixed logic issue where pull operations were processed immediately due to queue processing in finally block
  - Implemented scheduled pull mechanism using coroutines to properly delay queue addition
- **Data Race Prevention**: Eliminated potential data races during rapid consecutive changes
  - All git operations (commits and pulls) now use a unified queue system
  - Operations execute sequentially to prevent conflicts
  - Background sync operations wait for queued operations to complete
  - Improves performance and reduces server load during intensive editing sessions
- **Database Sync Stability**: Improved database synchronization logic to prevent unnecessary updates
  - Database commit hash is only updated after successful pulls that bring remote changes
  - Prevents database reloading when background sync only pushes local commits
  - Eliminates UI flickering during rapid consecutive note changes
- **Privacy Protection**: Enhanced logging to protect user privacy in production
  - Note content is only logged when debug features are enabled
  - Production logs only show note paths to prevent accidental content exposure
  - Applied to updateNote, createNote, deleteNote, and TextVM operations

## [26.01.7]

### Added

- Enhanced Background Git Operations: Improved asynchronous git synchronization
  - Descriptive commit messages showing changed files for background sync
  - Multiple file changes consolidated with detailed commit bodies
  - Maintains clear git history while preventing UI blocking

### Fixed

- Database Synchronization: Fixed background git operations causing unnecessary database updates
  - Background operations now only update database when pull operations actually bring in remote changes
  - Prevents excessive database reloading when no remote changes occurred
  - Improved logging for database sync state comparisons
  - Reduces UI interruptions during background synchronization

## [26.01.6]

### Added

- Repository Opening Indicator: Added visual feedback in the top grid when the Git repository is being opened on app startup
  - Shows folder open icon with pulsing animation during repository initialization
  - Provides clear indication that the app is loading and accessing the repository
  - Differentiates from other sync states with unique visual styling
- Centralized State Management: Implemented single source of truth for note data across all ViewModels
  - Created NoteRepository as centralized data access layer with reactive Flows
  - All database operations now go through NoteRepository for consistent data access
  - Automatic UI refresh when database updates through Flow emissions
  - Eliminates stale data issues during sync operations across ViewModels
- Background Git Operations Delay: Added configurable delay for background git synchronization
  - Default 5-second delay to batch multiple rapid changes into single operations
  - Prevents UI blocking during successive note edits
  - Configurable in Settings > Git > Background git delay
- Performance Optimization: Dedicated thread pool for git operations
  - Custom dispatcher prevents git I/O from blocking other app operations
  - Eliminates UI freezing during pull/push operations
  - Improved responsiveness during rapid note edits
- Concurrent Operations: Note updates no longer blocked during git synchronization
  - Removed unnecessary locking that prevented concurrent database operations
  - Users can continue editing notes while background git operations run
  - Maintains data consistency through proper serialization at the git level
- Two-Job Background System: Implemented intelligent background job management
  - Maximum one executing job and one waiting job to handle rapid successive changes
  - Waiting job automatically promotes to executing when current job completes
  - Eliminates UI blocking during multiple quick edits
- Storage Performance Warnings: Added comprehensive performance documentation
  - Device memory operations are up to 200x slower than app memory
  - Clear warnings in setup UI when selecting device storage
  - Performance comparison table in documentation

### Performance

- App Launch Time: Optimized repository synchronization timing for faster release build startup
- Background Git Operations: Eliminated UI blocking during rapid successive changes
  - Two-job system prevents multiple concurrent background operations
  - Configurable batching delay reduces unnecessary git operations

### Fixed

- Multi-Select Mode Performance: Optimized paging flow caching to maintain fast UI updates
  - Refined caching strategy to avoid performance degradation from over-caching
  - UI remains responsive when entering/exiting multi-select mode

## [26.01.5]

### Fixed

- Multi-Select Mode Crash: Fixed crash when entering multi-select mode by clicking "select multiple notes"
  - Resolved "Attempt to collect twice" error with granular paging flow caching
  - App no longer crashes when entering selection mode

## [26.01.4]

### Added

- Tag Editing Interface: Added intuitive tag editing dialog accessible from note context menus
  - Edit tags directly from grid and list views via long-press menu
  - Real-time synchronization ensures tag editor always shows current tags
  - Search functionality within the dialog to filter available tags
  - Keyboard-friendly interface with proper focus management
  - Supports all languages with complete translations
- Cloud Sync Error States: Added clickable error icons that show detailed status dialogs
  - Tap sync error or offline icons to view detailed status information
  - Improves accessibility and user understanding of sync issues

### Fixed

- Drawer Action Bar Visibility: Fixed issue where drawer action bar became invisible when main view was scrolled down
  - Separated scroll behaviors for main content and drawer to prevent interference
  - Main view scrolling now works independently of drawer state
  - Drawer maintains its own scroll state and action bar remains visible

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
  - Git author name defaults to "gittasks" when empty
  - Git author email defaults to "gittasks@localhost" when empty

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
