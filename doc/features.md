# GitNote Features

GitNote is a Git-based note-taking app for Android that integrates seamlessly with Git repositories for version control and synchronization.

## Core Features

### Git Integration

- **Repository Management**: Clone, pull, and push notes to Git repositories.
- **Version Control**: Automatic commits on note changes, with conflict resolution.
- **Automatic Merge Conflict Resolution**: Seamless handling of merge conflicts during synchronization with intelligent three-pass resolution strategy.
- **SSH Support**: Secure authentication using SSH keys.
- **Git Log Viewer**: Access commit history directly in the app with formatted timestamps, author information, and commit messages.
- **External Change Detection**: Automatically detects and syncs changes made outside the app.
- **Background Git Operations**: Intelligent background synchronization with configurable batching.

### Background Git Operations

GitNote features a sophisticated background synchronization system that prevents UI blocking during rapid successive changes while ensuring data consistency.

#### How It Works

- **Two-Job System**: Maintains at most one executing job and one waiting job
  - Executing job performs actual git operations (pull/push)
  - Waiting job queues up when executing job is busy
  - Waiting job automatically promotes to executing when current job completes
- **Configurable Delay**: Default 5-second delay batches multiple rapid changes
  - Reduces unnecessary git operations during quick successive edits
  - Configurable in Settings > Git > Background git delay
- **UI Responsiveness**: All git operations run in background, never blocking the UI
- **Performance Optimization**: Dedicated thread pool prevents git I/O from interfering with other app operations
- **Concurrent Editing**: Note updates work seamlessly during background synchronization
- **Smart Batching**: Multiple changes within the delay window are consolidated

#### Usage Scenarios

- **Rapid Editing**: Making multiple changes quickly (checkbox toggles, text edits) gets batched
- **Successive Updates**: No UI freezing during intensive note-taking sessions
- **Network Optimization**: Reduces network requests by batching operations

#### Configuration

- **Background Git Operations**: Enable/disable background sync (enabled by default)
- **Background Git Delay**: Set delay in seconds (0 = immediate, default = 5)

### Automatic Merge Conflict Resolution

GitNote features intelligent automatic merge conflict resolution that handles conflicts seamlessly during synchronization, eliminating the need for manual conflict resolution.

#### How It Works

- **Three-Pass Resolution Strategy**: When conflicts occur during pull operations, GitNote applies a deterministic three-pass approach:
  1. **Local Priority**: Uses your local changes when available
  2. **Remote Fallback**: Uses remote changes if no local version exists
  3. **Ancestor Safety**: Falls back to common ancestor version to prevent data loss
- **Transparent Operation**: Conflicts are resolved automatically in the background without user intervention
- **Visual Feedback**: Loading indicators show when resolution is in progress
- **Safe Fallback**: If automatic resolution fails, operations are aborted to maintain data integrity

#### Conflict Scenarios Handled

- **Text File Conflicts**: Automatic resolution for conflicting changes in note files
- **File Presence Conflicts**: Handles cases where files exist locally but not remotely, or vice versa
- **Concurrent Edits**: Resolves conflicts when the same note is modified both locally and remotely

#### User Experience

- **Seamless Sync**: Pull operations complete successfully even with conflicts
- **No Manual Intervention**: Users continue working without interruption
- **Progress Indication**: Spinner in action bar shows when conflict resolution is active
- **Reliable Results**: Same conflicts always resolve to the same outcome for consistency

#### Technical Details

- **Repository-Aware**: Path resolution works regardless of current working directory
- **Author Attribution**: Merge commits include proper author information
- **Index Management**: Git index is properly updated after resolution
- **Error Handling**: Graceful degradation if automatic resolution is impossible

### Note Management

- **Markdown Support**: Full Markdown rendering with syntax highlighting and image display.
- **Frontmatter Metadata**: YAML headers for titles, timestamps, completion status, tags, and authors.
- **File-Based Storage**: Notes are stored as Markdown files in the repository.

### Storage Options and Performance

GitNote supports two storage locations with dramatically different performance characteristics:

| Operation          | App Memory | Device Memory    |
| ------------------ | ---------- | ---------------- |
| Opening Repository | <1 second  | ~7 seconds       |
| Checkbox Toggle    | Instant    | Up to 20 seconds |
| Note Editing       | Instant    | Up to 20 seconds |
| File Operations    | Instant    | Up to 20 seconds |

**⚠️ Performance Warning**: Device memory operations are up to 200x slower than app memory operations. Always prefer app memory unless you have insufficient storage space in the app's allocated memory.

**Storage Location Selection**:

- **App Memory** (Recommended): Faster performance, suitable for most users
- **Device Memory**: Slower but allows access to the full device storage when app memory is insufficient

### Tag Filtering

GitNote supports filtering notes by tags defined in the frontmatter, allowing users to quickly find related notes.

#### How It Works

- **Tag Parsing**: Tags are extracted from the `tags:` field in YAML frontmatter.
- **Drawer Navigation**: Switch between folder and tag browsing modes in the navigation drawer.
- **Tag Selection**: Click on tags in the drawer to filter notes, or select "All notes" to show everything.
- **Real-time Filtering**: Notes are filtered instantly when a tag is selected, showing only matching notes.
- **Folder Context Preservation**: When using tag filtering, the current folder context is preserved in the navigation drawer for better user awareness.

#### Usage Tips

- Add tags to notes using YAML frontmatter: `tags: - tag1 - tag2`
- Use the toggle button in the drawer to switch between folder and tag modes.
- Tag filtering works in both grid and list views.
- Tags are case-sensitive and must match exactly.

#### Example Workflow

1. Add tags to a note's frontmatter:
   ```
   ---
   title: Meeting Notes
   tags:
     - meeting
     - planning
     - work
   ---
   ```
2. Open the navigation drawer and click the tag icon to switch to tag mode.
3. Click on "meeting" to show only notes tagged with "meeting".
4. Click "All notes" to return to showing all notes.

#### Auto-Closing Drawer

To provide a focused view of filtered results, the navigation drawer automatically closes in certain scenarios:

- **Tag Selection**: When you select a tag (including "All notes"), the drawer closes immediately, allowing you to view the filtered notes without obstruction.
- **Final Folder Navigation**: When navigating to a folder that has no subfolders (a "leaf" folder) during normal browsing, the drawer closes to let you focus on the notes in that folder.
- **Mode Switching**: Switching between tag and folder modes clears the respective filters (tags when switching to folder mode, folder when switching to tag mode) to prevent confusion.

This behavior ensures a smooth user experience by reducing the need for manual drawer management while browsing and filtering notes. Note that when moving notes, the drawer remains open during folder navigation to allow selecting the destination folder.

### Tag Editing

GitNote provides an intuitive tag editing interface that allows you to manage tags directly from the note grid and list views, with real-time synchronization to ensure the tag editor always shows current tags.

#### How It Works

- **Tag Editor Dialog**: Access tag editing through the note actions menu (long-press a note and select "Edit tags").
- **Current Tags Display**: The dialog shows currently assigned tags for the selected note.
- **Available Tags List**: Browse and select from all tags used across your repository.
- **Search Functionality**: Filter available tags using the search box within the dialog.

#### Usage Tips

- Long-press any note in grid or list view to access the "Edit tags" option.
- The dialog shows tags in two sections: current tags (assigned to this note) and available tags (used elsewhere).
- Use the search box to quickly find specific tags from the available list.
- Changes are applied immediately when you save the dialog.
- The tag editor reflects any recent tag changes made to the note.

#### Example Workflow

1. Long-press a note in the grid or list view.
2. Select "Edit tags" from the context menu.
3. View current tags assigned to the note in the "Current tags" section.
4. Browse or search available tags in the "Available tags" section.
5. Select/deselect tags as needed.
6. Tap "Save" to apply changes.
7. Reopen the tag editor anytime to see updated tags reflecting your changes.

#### State Synchronization

The tag editor maintains perfect synchronization with the note's current state:

- **Immediate Updates**: Tag changes are reflected instantly in the list/grid view.
- **Persistent State**: Reopening the tag editor shows the latest tags, not stale data.
- **Cross-Session Consistency**: Tag state persists correctly across app sessions and screen rotations.

### Advanced Filtering Options

GitNote provides configurable filtering options that give users more control over how tag filtering and search behave across folder boundaries.

#### Tag Filtering Ignores Folders

When enabled (default), tag filtering searches across all folders in the repository instead of limiting results to the currently selected folder. This provides a global view of tagged notes regardless of their location in the folder hierarchy.

##### How It Works

- **Global Search**: When filtering by a tag, notes with that tag are shown from all folders, not just the current folder.
- **Setting Location**: Configure via Settings > Filtering > "Tag filtering ignores folders"
- **Default Behavior**: Enabled by default for broader tag discovery.
- **Folder Context**: The current folder context is still preserved in the navigation drawer for user awareness.

##### Usage Tips

- Enable this setting to find all notes with a specific tag across your entire repository.
- Disable it to limit tag filtering to the currently selected folder only.
- Particularly useful for cross-project tags or when organizing notes by topic rather than location.

##### Example Workflow

1. Enable "Tag filtering ignores folders" in Settings > Filtering
2. Navigate to any folder in your repository
3. Switch to tag mode in the navigation drawer
4. Select a tag - notes with that tag appear from all folders, not just the current one
5. The drawer shows your current folder context for reference

#### Search Ignores All Filters

When enabled (default), search operations show results from all folders in the repository, ignoring current folder and subfolder settings. This ensures comprehensive search results across your entire note collection.

##### How It Works

- **Global Search**: Search queries return results from all folders regardless of current folder context.
- **Setting Location**: Configure via Settings > Filtering > "Search ignores all filters"
- **Default Behavior**: Enabled by default for complete search coverage.
- **Filter Override**: Bypasses folder display mode and current folder restrictions during search.

##### Usage Tips

- Enable this setting to find any note in your repository through search.
- Disable it to limit search results to the current folder and its subfolders (respecting folder display mode).
- Ideal for finding notes when you don't remember their exact location.

##### Example Workflow

1. Enable "Search ignores all filters" in Settings > Filtering
2. Navigate to a specific folder
3. Use the search function - results appear from all folders in the repository
4. Tap on any result to navigate to that note's location

### Folder Display Mode

GitNote allows users to control whether notes from subfolders are included when browsing a folder, providing flexible folder navigation options.

#### How It Works

- **Current Folder Only**: Shows only notes directly in the selected folder, excluding notes from subfolders.
- **Include Subfolders**: Shows notes from the selected folder and all its subfolders (default behavior).
- **Setting Location**: Configure this option in Settings > Appearance > Folder display mode.
- **Real-time Updates**: Changes take effect immediately when switching folders or updating the setting.

#### Usage Tips

- Use "Current folder only" for focused work in specific folders without subfolder distractions.
- Use "Include subfolders" (default) to see all related notes in a folder hierarchy.
- The setting applies to both grid and list views.
- Search functionality respects the current folder display mode setting.

#### Example Workflow

1. Navigate to a folder containing both direct notes and subfolders with notes.
2. Open Settings > Appearance and select "Folder display mode".
3. Choose "Current folder only" to see only notes directly in the current folder.
4. Switch back to "Include subfolders" to see all notes in the folder hierarchy.

#### Folder Structure Example

```
📁 Project/
├── 📄 project-overview.md
├── 📁 planning/
│   ├── 📄 timeline.md
│   └── 📄 budget.md
└── 📁 implementation/
    └── 📄 code-structure.md
```

- **Current folder only** in "Project/": Shows only `project-overview.md`
- **Include subfolders** in "Project/": Shows all 4 notes from the entire hierarchy

### User Interface

- **Grid and List Views**: Switch between grid and list layouts for notes.
- **Search and Sort**: Search notes by content, sort by date, title, etc.
- **Dark Mode**: Automatic theme switching.
- **Note Actions Menu**: Long-press notes to access options like delete, multi-select, convert between notes and tasks, and move notes.
- **Minimal Note Width**: Adjustable minimum width for notes in grid view (100dp, 150dp, 200dp, 250dp).
- **Offline Indicator**: Persistent icon showing when the app is offline and cannot sync.
- **Tag Display Mode**: Control whether tags are shown in grid view, list view, both, or neither.
- **Reload Spinner**: Visual progress indicator during database reload operations.
- **Scrollbars**: Custom scroll indicators for grid and list views to improve navigation in long note lists (opt-in setting).

### Reload Spinner

GitNote provides visual feedback during long-running database reload operations to improve user experience.

#### How It Works

- **Menu Access**: Available in the debug menu (three-dot menu → "Reload Database") when debug features are enabled.
- **Visual Indicator**: Shows a spinning circular progress indicator in the action bar next to the view toggle button.
- **Automatic Management**: The spinner appears when the operation starts and disappears when it completes or fails.
- **Non-blocking**: The spinner provides feedback without blocking user interaction with other parts of the app.

#### Usage Tips

- The reload spinner appears during database synchronization operations that may take several seconds.
- Provides immediate visual confirmation that the reload operation has started.
- Helps users understand that the app is actively working on their request.
- Available only when debug features are enabled in the app settings.

#### Key Benefits

- **User Feedback**: Clear indication that a long-running operation is in progress.
- **Improved UX**: Reduces user uncertainty during database operations.
- **Professional Feel**: Provides the same level of feedback as other modern applications.

### Scrollbars

GitNote provides custom scroll indicators for both grid and list views to improve navigation when browsing through long lists of notes. This feature is opt-in and can be enabled in the settings.

#### How It Works

- **Settings Toggle**: Enable/disable via Settings → User Interface → "Show scrollbars"
- **Automatic Display**: Scrollbars appear on the right edge of scrollable content when there are more items than can fit on screen and the setting is enabled
- **Visual Design**: Semi-transparent rounded rectangles that match the app's Material 3 theme.
- **Dynamic Sizing**: The scrollbar thumb size reflects the proportion of visible items to total items.
- **Position Indication**: The scrollbar thumb position shows the current scroll position within the list.

#### Implementation Details

- **Custom Canvas Drawing**: Implemented using Compose Canvas for precise control over appearance and behavior.
- **Theme Integration**: Uses MaterialTheme colors with appropriate alpha transparency.
- **Performance Optimized**: Only renders when scrolling is necessary (total items > visible items).
- **Responsive**: Updates in real-time as the user scrolls through content.

#### Usage Tips

- Scrollbars provide visual feedback about list length and current position.
- Particularly useful when browsing large collections of notes.
- The scrollbar thumb size gives a quick sense of how much content is available.
- Available in both grid and list view modes.

#### Key Benefits

- **Better Navigation**: Users can quickly assess list length and current position.
- **Improved UX**: Reduces the need to scroll blindly through long lists.
- **Visual Consistency**: Matches modern app design patterns.
- **Accessibility**: Provides additional visual cues for content navigation.

### Tag Display Mode

GitNote allows users to control tag visibility across different view modes and provides improved readability for notes with multiple tags.

#### How It Works

- **Display Control**: Choose whether tags are shown in grid view, list view, both views, or neither view.
- **Setting Location**: Configure via Settings > Appearance > Tag display mode.
- **Multi-line Wrapping**: In list view, tags wrap to multiple lines for better readability when notes have many tags.
- **Real-time Updates**: Changes take effect immediately when switching between grid and list views.

#### Usage Tips

- Use "Grid only" to show tags only in grid view for compact list browsing.
- Use "List only" to show tags only in list view for detailed grid browsing.
- Use "Both" to show tags in both views for maximum visibility.
- Use "None" to hide tags entirely for a cleaner interface.
- Multi-line wrapping in list view ensures all tags are visible without truncation.

#### Example

With multi-line wrapping enabled in list view:

```
📄 My Project Note
🏷️ feature, android, kotlin, ui, backend
```

Without wrapping (previous behavior):

```
📄 My Project Note
🏷️ feature, android, kotlin...
```

### Read-Only Mode

GitNote features a read-only mode that transforms the note editing experience into a clean, distraction-free markdown preview with interactive elements.

#### How It Works

- **Menu Toggle**: Access via the three-dot menu in the main grid view ("Activate read only mode" / "Deactivate read only mode").
- **Markdown Rendering**: Instead of showing a text editor, notes display as fully rendered markdown with proper formatting, images, and styling.
- **Interactive Checkboxes**: Task checkboxes (`- [ ]` and `- [X]`) become clickable, allowing you to toggle completion status without editing the raw text.
- **Frontmatter Hiding**: YAML frontmatter is automatically hidden to provide a clean, distraction-free reading experience.
- **Image Display**: Markdown images are properly rendered and displayed.
- **Persistent Setting**: The read-only mode preference is saved and persists across app sessions.

#### Usage Tips

- Use read-only mode for reviewing and reading notes without accidental edits.
- Perfect for task management - toggle checkboxes in rendered view for quick updates.
- Ideal for sharing screen content or presenting notes.
- Combine with dark/light theme switching for optimal reading comfort.
- The mode applies to all note editing screens throughout the app.

#### Example Workflow

1. Open GitNote and access the three-dot menu in the main grid view.
2. Select "Activate read only mode" to switch to preview mode.
3. Open any note to see it rendered as formatted markdown.
4. Click on task checkboxes to toggle completion status.
5. Return to the menu and select "Deactivate read only mode" to return to normal editing.

#### Key Benefits

- **Clean Reading**: Focus on content without frontmatter or raw markdown syntax.
- **Quick Task Updates**: Toggle checkboxes without opening the text editor.
- **Accidental Edit Prevention**: Perfect for reviewing notes you don't want to modify.
- **Presentation Mode**: Clean, formatted display for sharing or presenting notes.

### Git Log Viewer

GitNote provides direct access to the git commit history of your notes repository, allowing you to track changes and see the evolution of your notes over time.

#### How It Works

- **Menu Access**: Available in the main menu (three-dot menu) in the grid view
- **Commit Information**: Displays commit message, author, date, and abbreviated hash for each commit.
- **Loading Indicator**: Shows a progress indicator while retrieving git log data.
- **Formatted Timestamps**: Dates are displayed in readable "YYYY-MM-DD HH:MM:SS" format.
- **Scrollable List**: All commits are shown in a scrollable list within a dialog.

#### Usage Tips

- Access the git log to understand what changes were made and when.
- Useful for tracking note modifications, additions, and deletions over time.
- The loading indicator appears on first access due to repository initialization.
- Subsequent accesses are faster as the repository stays cached in memory.

## Completion Checkbox Feature

GitNote now supports marking notes as completed using a checkbox in the UI, tied to the `completed?` field in the frontmatter.

### How It Works

- **Checkbox Display**: A checkbox appears next to the note title if the note has frontmatter.
- **Toggling**: Click the checkbox to toggle between `completed?: yes` and `completed?: no`.
- **Automatic Updates**: Toggling updates the `updated` timestamp and saves the changes to the file and Git repository.
- **Visual Feedback**: Completed notes can be visually distinguished (future feature).
- **List View Icons**: In list view, tasks show a checkbox icon while notes show a document icon.

### Usage Tips

- Add frontmatter to notes to enable the checkbox.
- Use completion for tasks, reminders, or project tracking.
- The checkbox is read-only in display; editing requires toggling in the app.
- Changes are committed to Git automatically.
- In list view, the icon type (checkbox vs document) indicates whether the item is a task or note.

### Example Workflow

1. Create a note with frontmatter:
   ```
   ---
   title: Finish Report
   completed?: no
   ---
   ```
2. Open the note in GitNote; a checkbox appears.
3. Click the checkbox to mark as complete; the file updates to `completed?: yes`.
4. Sync with Git to persist changes across devices.

### Interactive Checkboxes in Markdown

GitNote supports interactive checkboxes directly within rendered markdown content, allowing you to toggle task completion without switching to edit mode.

#### How It Works

- **Rendered Mode**: In read-only markdown view, `- [ ]` and `- [X]` patterns become interactive checkboxes.
- **Visual Components**: Plain markdown text is replaced with Material Design checkbox components.
- **Local Editing**: Tapping checkboxes updates the display immediately but doesn't save until confirmed.
- **Accept Button**: An accept button appears when changes are made, allowing you to save all modifications.
- **Git Integration**: Confirmed changes are committed to the repository with proper version control.

#### Usage Tips

- Perfect for task lists and checklists within note content.
- Changes are local until you tap the accept button.
- Use in read-only mode for quick task management.
- Supports indented checkboxes for nested task hierarchies.
- All checkbox changes in a session are saved together.
- Images in markdown are rendered in read-only mode.
- Support for remote images (HTTP/HTTPS URLs) and local repository files.
- Relative image paths are resolved from the note's directory in the repository.
- Absolute paths within the repository (starting with /) are also supported.

#### Example Workflow

1. Create a note with checkbox markdown:
   ```
   ## Project Tasks
   - [ ] Design mockups
   - [ ] Implement backend
   - [ ] Write documentation
   ```
2. Switch to read-only mode to see rendered markdown.
3. Tap checkboxes to mark tasks complete - they toggle visually.
4. Tap the accept button to save all changes to the file and Git.

## Task Sorting in List View

Completed tasks are automatically sorted to the end of the list view to keep active tasks visible at the top.

### How It Works

- **Automatic Sorting**: In list view, completed tasks (with `completed?: yes`) appear after incomplete tasks.
- **Primary Sort**: Tasks are first sorted by completion status, then by the selected sort order (date, title, etc.).
- **Grid View**: This sorting only applies to list view; grid view maintains the standard sort order.
- **Real-time Updates**: Sorting updates immediately when task completion status changes.

### Usage Tips

- Use list view to keep your active tasks at the top of the list.
- Completed tasks remain accessible but don't clutter the active task list.
- This feature works automatically for all notes with completion status.
- Switch to grid view if you prefer a different organization.

### Example

In list view, with sort by "Most recent":

```
[ ] Active Task 1 (today)
[ ] Active Task 2 (yesterday)
[x] Completed Task 1 (last week)
[x] Completed Task 2 (last month)
```

## Floating Action Button (FAB)

The FAB provides quick access to create new notes and tasks directly.

### How It Works

- **Expandable Menu**: Long-press or tap the FAB to expand creation options.
- **Create Note**: Creates a new regular note with document icon.
- **Create Task**: Creates a new task with checkbox icon and `completed?: no` in frontmatter.
- **Search**: Quick access to the search functionality.
- **Smart Defaults**: New items inherit the current folder and use query text as title if available.

### Usage Tips

- Use the document icon to create regular notes.
- Use the checkbox icon to create tasks that need completion tracking.
- The FAB respects the current folder context for new items.
- Search option focuses the search bar for quick note finding.

### Example Workflow

1. Navigate to the desired folder.
2. Tap the FAB to expand options.
3. Select the checkbox icon to create a new task.
4. The task appears immediately with completion checkbox.

## Move Note Feature

GitNote allows moving notes between folders using an intuitive drag-and-drop style interface.

### How It Works

- **Initiate Move**: Long-press a note and select "Move note" from the menu.
- **Drawer Navigation**: The navigation drawer opens in move mode, allowing folder browsing.
- **Folder Selection**: Navigate through folders without closing the drawer.
- **Confirm Move**: Click the check icon ("Insert here") to move the note to the current folder.
- **Cancel Move**: Click the close icon to abort the move operation.
- **Automatic Close**: The drawer closes automatically after a successful move.

### Usage Tips

- Use the drawer to browse and select destination folders.
- The drawer stays open during folder navigation in move mode.
- Move operations update the note's file path and commit changes to Git.
- Cancel at any time without making changes.

### Example Workflow

1. Long-press a note in the current folder.
2. Select "Move note" from the menu.
3. Use the drawer to navigate to the target folder.
4. Click the check icon to complete the move.
5. The drawer closes, showing the note in its new location.

## Convert to Task/Note Feature

GitNote allows quick conversion between regular notes and task-like notes via the long-press menu.

### How It Works

- **Long-Press Menu**: Access additional options by long-pressing a note.
- **Convert to Task**: Adds the `completed?: no` field to the frontmatter. If no frontmatter exists, creates one with title, updated, created, and completed fields.
- **Convert to Note**: Removes the `completed?:` field from the frontmatter, converting the task back to a regular note.
- **Automatic Updates**: Conversion updates the `updated` timestamp and saves changes to the file and Git repository.

### Usage Tips

- Use "Convert to Task" for notes that represent actionable items.
- Use "Convert to Note" to remove task tracking from a note.
- The menu shows the appropriate option based on the note's current state (task or note).
- Converted notes immediately show/hide the completion checkbox in the UI.

### Example Workflow

1. Long-press a regular note.
2. Select "Convert to Task"; frontmatter is added with `completed?: no`.
3. The note now displays a checkbox for completion tracking.
4. To revert, long-press again and select "Convert to Note"; the `completed?` field is removed.

## Frontmatter Hiding (Read-Only Mode Feature)

As part of GitNote's read-only mode, YAML frontmatter is automatically hidden when viewing Markdown notes to provide a clean, distraction-free reading experience.

### How It Works

- **Automatic Detection**: The app detects YAML frontmatter enclosed in `---` markers at the beginning of Markdown files.
- **Content Extraction**: In view mode, only the content after the frontmatter is rendered as Markdown.
- **Edit Mode Visibility**: Frontmatter remains visible and editable when switching to edit mode.
- **No Data Loss**: Frontmatter is preserved in the file; it's only hidden during rendering.

### Usage Tips

- Frontmatter is completely hidden in read-only view, showing only the actual note content.
- Switch to edit mode to see and modify frontmatter fields.
- This feature works automatically for all Markdown notes with valid frontmatter.
- Non-Markdown files and notes without frontmatter display normally.

### Example

A note file containing:

```
---
title: My Note
completed?: yes
---

# Main Content
This is the actual note content that users see.
```

Will display in view mode as:

> # Main Content
>
> This is the actual note content that users see.

## Show Full Title in List View

GitNote allows long note titles to wrap to multiple lines in list view instead of being truncated with ellipsis.

### How It Works

- **Setting Toggle**: Enable "Show full title in list view" in Settings > Appearance
- **Multi-line Display**: When enabled, note titles wrap naturally to multiple lines
- **Single-line Display**: When disabled, titles are truncated with "..." after one line
- **List View Only**: This setting only affects the list view, not the grid view

### Usage Tips

- Enable this setting if you have long note titles that are being cut off
- The setting provides better readability for notes with descriptive titles
- Disable it to keep a more compact list view with consistent row heights
- Changes take effect immediately without restarting the app

### Example

With the setting **disabled** (default):

```
My Very Long Note Title That Gets...
```

With the setting **enabled**:

```
My Very Long Note Title That Gets
Cut Off At The End Of The Line
```

## Frontmatter Title Display

GitNote allows users to display frontmatter titles instead of filenames in note cards, providing more meaningful and descriptive note titles.

### How It Works

- **Opt-in Setting**: Enable "Prefer frontmatter titles" in Settings > Appearance (disabled by default)
- **Title Priority**: When enabled, uses the `title:` field from YAML frontmatter; falls back to filename if no title exists
- **Fallback Behavior**: If frontmatter title is empty or missing, displays the filename without extension
- **Performance Optimized**: Titles are parsed on-demand with minimal performance impact

### Usage Tips

- Enable this setting if your notes use descriptive frontmatter titles
- Particularly useful for notes with generic filenames but meaningful titles
- The setting applies to both grid and list views
- Frontmatter titles provide better context when browsing notes
- Allows special characters in titles (colons, slashes, quotes) that are forbidden in filesystem paths

### Example

For a note file named `meeting-2024-01-15.md` with frontmatter:

```yaml
---
title: "Q1 Planning Meeting: Product Roadmap Discussion / Sprint Review"
tags: - meeting - planning - q1
---
```

- **With setting disabled**: Shows "meeting-2024-01-15"
- **With setting enabled**: Shows "Q1 Planning Meeting: Product Roadmap Discussion / Sprint Review"

This allows titles with special characters like colons (:) and slashes (/) that cannot be used in filenames.

### Technical Details

- **Parsing**: Extracts title from YAML frontmatter using regex-based parsing
- **Caching**: No additional caching needed due to lightweight parsing operations
- **Compatibility**: Works with existing frontmatter structure (title, tags, completed, etc.)
- **Special Characters**: Frontmatter titles can contain characters forbidden in filenames (colons, slashes, quotes, etc.)

## Background Git Operations

GitNote features a sophisticated background synchronization system that prevents UI blocking during rapid successive changes while ensuring data consistency.

### How It Works

- **Two-Job System**: Maintains at most one executing job and one waiting job
  - Executing job performs actual git operations (pull/push) after configurable delay
  - Waiting job queues up when executing job is busy
  - Waiting job automatically promotes to executing when current job completes
- **Configurable Delay**: Default 5-second delay batches multiple rapid changes
  - Reduces unnecessary git operations during quick successive edits
  - Set to 0 for immediate operations
- **UI Responsiveness**: All git operations run in background, never blocking the UI
- **Smart Batching**: Multiple changes within the delay window are consolidated

### Configuration

- **Setting Toggle**: Enable "Background git operations" in Settings > Git (enabled by default)
- **Delay Setting**: Configure "Background git delay" in Settings > Git (default: 5 seconds)

### Usage Tips

- Enable background operations for uninterrupted note editing during intensive sessions
- The 5-second delay is ideal for batching rapid changes (checkbox toggles, quick edits)
- Set delay to 0 if you prefer immediate sync but still want background processing
- Background operations don't show error toasts to avoid interrupting workflow
- Applies to all note operations: creating, editing, deleting, converting, moving

### Example Workflow

1. Open Settings > Git and enable "Background git operations"
2. Set "Background git delay" to 5 seconds (or your preferred delay)
3. Edit notes rapidly - operations are batched and don't block UI
4. Check sync status indicators to monitor background operations
5. Continue working without waiting for git synchronization
