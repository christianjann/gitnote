# GitNote Merge Strategy

## Overview

GitNote implements automatic merge conflict resolution for pull operations to provide a seamless user experience. When merge conflicts occur during synchronization, the system attempts to resolve them automatically rather than requiring manual intervention.

## Automatic Conflict Resolution

### When Conflicts Occur

Merge conflicts happen when:
- The same file has been modified both locally and remotely
- Changes cannot be merged cleanly using Git's automatic merge algorithms

### Resolution Strategy

GitNote uses a **three-pass automatic resolution strategy** with clear precedence rules:

#### Pass 1: Prefer Local Changes ("Our" Version)
- For each conflicted file, if the local version exists, use it
- This preserves user modifications and maintains local intent
- Rationale: Local changes are typically more recent and user-intended

#### Pass 2: Use Remote Changes ("Their" Version)
- For remaining conflicts where no local version exists, use the remote version
- This ensures remote changes are incorporated when local changes are absent
- Rationale: Remote changes should not be lost if they don't conflict with local work

#### Pass 3: Fallback to Common Ancestor
- For any remaining conflicts, use the common ancestor version
- This is a conservative fallback to avoid data loss
- Rationale: Better to revert to a known good state than leave unresolved conflicts

### Implementation Details

#### Conflict Detection
- Uses `repo.merge()` to set up merge state and detect conflicts
- Conflicts are identified through Git's index conflict markers

#### Resolution Process
1. **Setup**: Initialize merge state with `repo.merge()`
2. **Detection**: Check for conflicts using `repo.index().has_conflicts()`
3. **Resolution Loop**:
   - Retrieve conflict information for each conflicted file
   - Apply resolution strategy in order of precedence
   - Write resolved content to working directory
   - Update Git index with resolved file
   - Remove conflict markers
4. **Validation**: Ensure no conflicts remain
5. **Commit**: Create merge commit with resolved tree
6. **Cleanup**: Update working directory to match merge commit

#### Error Handling
- If automatic resolution fails for any conflict, the entire merge is aborted
- Repository is reset to pre-merge state to maintain consistency
- No partial merges are allowed to prevent data corruption

## User Experience

### Seamless Operation
- Users continue working normally without interruption
- Conflicts are resolved transparently in the background
- No manual conflict resolution required

### Conflict Resolution Bias
- **Local-first approach**: User changes are preserved when possible
- **Conservative fallback**: Reverts to safe state rather than leaving conflicts
- **Deterministic behavior**: Same conflicts always resolve the same way

### Failure Scenarios
- If automatic resolution cannot resolve all conflicts, the pull operation fails
- Repository remains in a clean state
- User can retry or resolve conflicts manually if needed

### Technical Implementation

#### Path Resolution
When resolving conflicts, file paths from Git index entries are relative to the repository root. The implementation uses `repo.workdir()` to construct absolute paths for file operations, ensuring compatibility regardless of the current working directory.

#### Directory Creation
Parent directories are automatically created for resolved files to handle cases where the directory structure doesn't exist in the working directory.

#### Code Location
- Primary implementation: `app/src/main/rust/src/libgit2/merge.rs`
- Test coverage: `app/src/main/rust/src/libgit2/test.rs::test_pull_conflict_resolution`

### Key Functions
- `normal_merge()`: Main merge orchestration
- `do_merge()`: High-level merge coordination
- `pull()`: Entry point for pull operations

### Dependencies
- `git2` crate for Git operations
- Custom error handling and logging
- JNI integration for Android app

## Testing

The merge strategy is validated through comprehensive tests that cover:
- Basic conflict resolution scenarios
- Multi-pass resolution logic
- Error handling and cleanup
- Integration with pull operations
- Repository state consistency

See `test_pull_conflict_resolution` for detailed test coverage.

## Future Considerations

### Potential Enhancements
- **User preferences**: Allow users to choose resolution strategies
- **Conflict notification**: Inform users when automatic resolution occurs
- **Manual override**: Provide UI for manual conflict resolution when auto-resolution fails
- **Conflict history**: Track and learn from resolution patterns

### Limitations
- Only handles text-based conflicts
- No support for binary file conflicts
- Conservative approach may lose some changes in edge cases