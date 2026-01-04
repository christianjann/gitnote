package io.github.christianjann.gitnotecje.manager

import android.util.Log
import io.github.christianjann.gitnotecje.MyApp
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.data.AppPreferences
import io.github.christianjann.gitnotecje.data.room.Note
import io.github.christianjann.gitnotecje.data.room.NoteFolder
import io.github.christianjann.gitnotecje.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

private const val TAG = "StorageManager"

sealed interface SyncState {

    data class Ok(val isConsumed: Boolean) : SyncState

    object Error : SyncState

    object Offline : SyncState

    object Pull : SyncState

    object Push : SyncState

    object Reloading : SyncState

    object Opening : SyncState

    fun isLoading(): Boolean {
        return this is Pull || this is Push || this is Reloading || this is Opening
    }
}

sealed class Progress {
    data object Timestamps : Progress()

    data class GeneratingDatabase(val path: String) : Progress()
}

class StorageManager {


    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val noteRepository: NoteRepository = MyApp.appModule.noteRepository

    private val uiHelper = MyApp.appModule.uiHelper

    private val gitManager: GitManager = MyApp.appModule.gitManager

    private val locker = Mutex()

    private val _syncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.Ok(true))
    val syncState: StateFlow<SyncState> = _syncState

    var onDatabaseUpdated: (suspend () -> Unit)? = null

    suspend fun setSyncState(state: SyncState) {
        _syncState.emit(state)
    }


    suspend fun updateDatabaseAndRepo(includeGitOperations: Boolean = true): Result<Unit> = locker.withLock {
        Log.d(TAG, "updateDatabaseAndRepo")

        val cred = prefs.cred()
        val remoteUrl = prefs.remoteUrl.get()
        val author = prefs.gitAuthor()

        var syncFailed = false

        gitManager.commitAll(
            author,
            "commit from gitnote to update the repo of the app"
        ).onFailure {
            uiHelper.makeToast(it.message)
        }

        if (includeGitOperations && remoteUrl.isNotEmpty()) {
            _syncState.emit(SyncState.Pull)
            gitManager.pull(cred, author).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast("${it.message}${uiHelper.getString(R.string.offline_hint)}")
                }
            }
        }

        if (includeGitOperations && remoteUrl.isNotEmpty()) {
            _syncState.emit(SyncState.Push)
            // todo: maybe async this call
            gitManager.push(cred).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast("${it.message}${uiHelper.getString(R.string.offline_hint)}")
                }
            }
        }

        if (!syncFailed) {
            _syncState.emit(SyncState.Ok(false))
        }

        updateDatabaseWithoutLocker()

        return success(Unit)
    }

    /**
     * Perform git pull and push operations in the background
     */
    fun performBackgroundGitOperations() {
        Log.d(TAG, "performBackgroundGitOperations called")
        CoroutineScope(Dispatchers.IO).launch {
            locker.withLock {
                val cred = prefs.cred()
                val author = prefs.gitAuthor()
                val remoteUrl = prefs.remoteUrl.get()
                var syncFailed = false

                Log.d(TAG, "Remote URL: $remoteUrl")

                if (remoteUrl.isNotEmpty()) {
                    Log.d(TAG, "Starting pull operation")
                    _syncState.emit(SyncState.Pull)
                    gitManager.pull(cred, author).onFailure {
                        Log.e(TAG, "Pull failed: ${it.message}")
                        syncFailed = true
                        _syncState.emit(SyncState.Offline)
                        // Don't show toast for background operations
                    }.onSuccess {
                        Log.d(TAG, "Pull succeeded")
                    }
                }

                if (remoteUrl.isNotEmpty()) {
                    Log.d(TAG, "Starting push operation")
                    _syncState.emit(SyncState.Push)
                    gitManager.push(cred).onFailure {
                        syncFailed = true
                        _syncState.emit(SyncState.Offline)
                        // Don't show toast for background operations
                    }
                }

                if (!syncFailed) {
                    _syncState.emit(SyncState.Ok(false))
                    // Update database after successful git operations
                    updateDatabaseIfNeeded()
                }
            }
        }
    }

    /**
     * Update the database with the last files
     * available of the fs, and update with the
     * head commit of the repo.
     *
     * /!\ Warning there can be pending file added to the database
     * that are not committed to the repo.
     * The caller must ensure that all files has been committed
     * to keep the database in sync with the remote repo
     */
    private suspend fun updateDatabaseWithoutLocker(
        force: Boolean = false,
        progressCb: ((Progress) -> Unit)? = null
    ): Result<Unit> {

        val fsCommit = gitManager.lastCommit()
        val databaseCommit = prefs.databaseCommit.get()

        Log.d(TAG, "fsCommit: $fsCommit, databaseCommit: $databaseCommit")
        if (fsCommit.isEmpty()) {
            Log.d(TAG, "Repository is in invalid state, skipping database update")
            return success(Unit)
        }
        if (!force && fsCommit == databaseCommit) {
            Log.d(TAG, "last commit is already loaded in data base")
            return success(Unit)
        }

        val repoPath = prefs.repoPath()
        Log.d(TAG, "repoPath = $repoPath")

        progressCb?.invoke(Progress.Timestamps)
        val timestamps = gitManager.getTimestamps().getOrThrow()

        noteRepository.clearAndInit(repoPath, timestamps, progressCb)
        prefs.databaseCommit.update(fsCommit)

        return success(Unit)
    }

    /**
     * See the documentation of [updateDatabaseWithoutLocker]
     */
    suspend fun updateDatabase(
        force: Boolean = false,
        progressCb: ((Progress) -> Unit)? = null
    ): Result<Unit> = locker.withLock {
        updateDatabaseWithoutLocker(force, progressCb)
    }

    /**
     * Check if the database is out of sync with the repository
     */
    suspend fun isDatabaseOutOfSync(): Boolean {
        return try {
            val fsCommit = gitManager.lastCommit()
            val databaseCommit = prefs.databaseCommit.get()
            fsCommit != databaseCommit
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database sync status: ${e.message}", e)
            false // Assume in sync if we can't check
        }
    }

    /**
     * Update database if it's out of sync, best effort
     */
    suspend fun updateDatabaseIfNeeded() {
        Log.d(TAG, "updateDatabaseIfNeeded called")
        try {
            if (isDatabaseOutOfSync()) {
                Log.d(TAG, "Database is out of sync, updating...")
                // Show sync indicator in top grid
                _syncState.emit(SyncState.Reloading)
                // Show user feedback that sync is happening
                withContext(Dispatchers.Main) {
                    uiHelper.makeToast(uiHelper.getString(R.string.syncing_repository))
                }
                updateDatabaseWithoutLocker().onFailure {
                    Log.e(TAG, "Failed to update database: ${it.message}")
                    _syncState.emit(SyncState.Error)
                    withContext(Dispatchers.Main) {
                        uiHelper.makeToast(uiHelper.getString(R.string.sync_failed))
                    }
                }.onSuccess {
                    _syncState.emit(SyncState.Ok(false))
                    withContext(Dispatchers.Main) {
                        uiHelper.makeToast(uiHelper.getString(R.string.sync_success))
                    }
                }
            } else {
                Log.d(TAG, "Database is already in sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database sync status: ${e.message}", e)
            _syncState.emit(SyncState.Error)
        }
    }

    /**
     * Best effort
     */
    suspend fun updateNote(new: Note, previous: Note, onUpdated: suspend () -> Unit = {}): Result<Unit> = locker.withLock {
        Log.d(TAG, "updateNote: previous = $previous")
        Log.d(TAG, "updateNote: new = $new")

        val result = update(
            commitMessage = "gitnote changed ${previous.relativePath}",
            onUpdated = onUpdated
        ) {
            noteRepository.removeNote(previous)
            noteRepository.insertNote(new)

            val rootPath = prefs.repoPath()
            val previousFile = previous.toFileFs(rootPath)

            // Check if the file exists before trying to delete it
            // If it was deleted remotely, this is fine - we just skip the delete
            if (previousFile.exist()) {
                previousFile.delete().onFailure {
                    val message =
                        uiHelper.getString(R.string.error_delete_file, previousFile.path, it.message)
                    Log.e(TAG, message)
                    uiHelper.makeToast(message)
                    return@update failure(Exception(message))
                }
            } else {
                Log.d(TAG, "Previous file ${previousFile.path} does not exist, skipping delete")
            }

            val newFile = new.toFileFs(rootPath)
            newFile.create().onFailure {
                val message = uiHelper.getString(R.string.error_create_file, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
                return@update failure(Exception(message))
            }

            newFile.write(new.content).onFailure {
                val message = uiHelper.getString(R.string.error_write_file, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
                return@update failure(Exception(message))
            }

            success(Unit)
        }

        // Call the global callback after database/git operations complete
        result.onSuccess {
            onDatabaseUpdated?.invoke()
        }

        return result

    }

    /**
     * Best effort
     */
    suspend fun createNote(note: Note): Result<Unit> = locker.withLock {
        Log.d(TAG, "createNote: $note")

        update(
            commitMessage = "gitnote created ${note.relativePath}"
        ) {
            noteRepository.insertNote(note)

            val file = note.toFileFs(prefs.repoPath())

            file.create().onFailure {
                val message = uiHelper.getString(R.string.error_create_file, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
            }
            file.write(note.content).onFailure {
                val message = uiHelper.getString(R.string.error_write_file, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
            }

            success(Unit)
        }
    }


    suspend fun deleteNote(note: Note): Result<Unit> = locker.withLock {

        Log.d(TAG, "deleteNote: $note")
        update(
            commitMessage = "gitnote deleted ${note.relativePath}"
        ) {
            noteRepository.removeNote(note)

            val file = note.toFileFs(prefs.repoPath())
            file.delete().onFailure {
                val message = uiHelper.getString(R.string.error_delete_file, file.path, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
            }
            success(Unit)
        }
    }

    suspend fun deleteNotes(notes: List<Note>): Result<Unit> = locker.withLock {
        Log.d(TAG, "deleteNotes: ${notes.size}")

        update(
            commitMessage = "gitnote deleted ${notes.size} notes"
        ) {
            // optimization because we only see the db state on screen
            notes.forEach { note ->
                noteRepository.removeNote(note)
            }

            val repoPath = prefs.repoPath()
            notes.forEach { note ->

                Log.d(TAG, "deleting $note")
                val file = note.toFileFs(repoPath)

                file.delete().onFailure {
                    val message =
                        uiHelper.getString(R.string.error_delete_file, file.path, it.message)
                    Log.e(TAG, message)
                    uiHelper.makeToast(message)
                }
            }
            success(Unit)
        }
    }

    suspend fun createNoteFolder(noteFolder: NoteFolder): Result<Unit> = locker.withLock {
        Log.d(TAG, "createNoteFolder: $noteFolder")

        update(
            commitMessage = "gitnote created folder ${noteFolder.relativePath}"
        ) {
            noteRepository.insertNoteFolder(noteFolder)

            val folder = noteFolder.toFolderFs(prefs.repoPath())
            folder.create().onFailure {
                val message = uiHelper.getString(R.string.error_create_folder, it.message)
                Log.e(TAG, message)
                uiHelper.makeToast(message)
            }

            success(Unit)
        }
    }

    suspend fun deleteNoteFolder(noteFolder: NoteFolder): Result<Unit> = locker.withLock {
        Log.d(TAG, "deleteNoteFolder: $noteFolder")

        update(
            commitMessage = "gitnote deleted folder ${noteFolder.relativePath}"
        ) {
            noteRepository.deleteNoteFolder(noteFolder)

            val folder = noteFolder.toFolderFs(prefs.repoPath())
            folder.delete().onFailure {
                val msg = uiHelper.getString(R.string.error_delete_folder, it.message)
                Log.e(TAG, msg)
                uiHelper.makeToast(msg)
            }

            success(Unit)
        }
    }

    suspend fun closeRepo() = locker.withLock {
        prefs.closeRepo()
        gitManager.closeRepo()
        noteRepository.clearDatabase()
    }


    private suspend fun <T> update(
        commitMessage: String,
        onUpdated: suspend () -> Unit = {},
        f: suspend () -> Result<T>
    ): Result<T> = withContext(Dispatchers.Default) {

        val cred = prefs.cred()
        val remoteUrl = prefs.remoteUrl.get()
        val author = prefs.gitAuthor()
        val backgroundGitOps = prefs.backgroundGitOperations.getBlocking()

        var syncFailed = false

        gitManager.commitAll(
            author,
            "commit from gitnote, before doing a change"
        ).onFailure {
            return@withContext failure(it)
        }

        // Only perform pull/push if background git operations are disabled
        if (!backgroundGitOps && remoteUrl.isNotEmpty()) {
            _syncState.emit(SyncState.Pull)
            gitManager.pull(cred, author).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
            }
        }

        val payload = f().fold(
            onFailure = {
                return@withContext failure(it)
            },
            onSuccess = {
                it
            }
        )

        withContext(Dispatchers.Main) {
            onUpdated()
        }

        gitManager.commitAll(author, commitMessage).onFailure {
            return@withContext failure(it)
        }

        // Only perform push if background git operations are disabled
        if (!backgroundGitOps && remoteUrl.isNotEmpty()) {
            _syncState.emit(SyncState.Push)
            gitManager.push(cred).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
            }
        }

        prefs.databaseCommit.update(gitManager.lastCommit())

        // If background git operations are enabled, perform them asynchronously
        if (backgroundGitOps) {
            performBackgroundGitOperations()
        } else if (!syncFailed) {
            _syncState.emit(SyncState.Ok(false))
        }

        return@withContext success(payload)
    }

    suspend fun getGitLog(limit: Int = 20): Result<List<GitLogEntry>> = withContext(Dispatchers.IO) {
        gitManager.getGitLog(limit)
    }

    suspend fun consumeOkSyncState() {
        _syncState.emit(SyncState.Ok(true))
    }
}
