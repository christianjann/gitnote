package io.github.christianjann.gittasks.manager

import android.util.Log
import io.github.christianjann.gittasks.MyApp
import io.github.christianjann.gittasks.R
import io.github.christianjann.gittasks.data.AppPreferences
import io.github.christianjann.gittasks.data.room.Note
import io.github.christianjann.gittasks.data.room.NoteFolder
import io.github.christianjann.gittasks.data.NoteRepository
import io.github.christianjann.gittasks.helper.NetworkHelper
import io.github.christianjann.gittasks.ui.model.Cred
import io.github.christianjann.gittasks.ui.model.GitAuthor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val networkHelper: NetworkHelper = NetworkHelper(MyApp.appModule.context)

    private val locker = Mutex()
    private var executingGitJob: kotlinx.coroutines.Job? = null
    private var waitingGitJob: kotlinx.coroutines.Job? = null
    private var lastBackgroundSyncTime: Long = 0

    // Unified queue for git operations
    private val gitOperationQueue = mutableListOf<GitOperation>()
    private var currentOperationJob: kotlinx.coroutines.Job? = null
    private var lastPullRequestTime: Long = 0
    private var scheduledPull: GitOperation.Pull? = null
    private var scheduledPullTimer: kotlinx.coroutines.Job? = null

    // Track if we're in app initialization to prevent cancelling critical operations
    private var isInitializing = false

    sealed class GitOperation {
        data class Commit(val author: GitAuthor, val commitMessages: MutableList<String>) : GitOperation()
        data class Pull(val cred: Cred?, val author: GitAuthor) : GitOperation()
    }


    private val _syncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.Ok(true))
    val syncState: StateFlow<SyncState> = _syncState

    var onDatabaseUpdated: (suspend () -> Unit)? = null

    suspend fun setSyncState(state: SyncState) {
        _syncState.emit(state)
    }

    /**
     * Check if network sync is allowed based on offline mode and WiFi settings.
     * Returns a pair of (allowed, reason) where reason is a string resource ID if not allowed.
     */
    private fun isSyncAllowed(): Pair<Boolean, Int?> {
        // Check offline mode first
        if (prefs.offlineMode.getBlocking()) {
            return Pair(false, R.string.sync_skipped_offline_mode)
        }

        val syncOnlyOnWifi = prefs.syncOnlyOnWifi.getBlocking()
        val syncOnSpecificWifi = prefs.syncOnSpecificWifi.getBlocking()
        val syncWifiSsid = prefs.syncWifiSsid.getBlocking()

        if (!syncOnlyOnWifi) {
            return Pair(true, null)
        }

        if (!networkHelper.isOnWifi()) {
            return Pair(false, R.string.sync_skipped_not_on_wifi)
        }

        if (syncOnSpecificWifi && syncWifiSsid.isNotEmpty()) {
            val currentSsid = networkHelper.getCurrentWifiSsid()
            if (currentSsid != syncWifiSsid) {
                return Pair(false, R.string.sync_skipped_wrong_wifi)
            }
        }

        return Pair(true, null)
    }


    suspend fun updateDatabaseAndRepo(includeGitOperations: Boolean = true): Result<Unit> = locker.withLock {
        Log.d(TAG, "updateDatabaseAndRepo")

        val cred = prefs.cred()
        val remoteUrl = prefs.remoteUrl.get()
        val author = prefs.gitAuthor()

        var syncFailed = false

        gitManager.commitAll(
            author,
            "commit from gittasks to update the repo of the app"
        ).onFailure {
            uiHelper.makeToast(it.message)
        }

        // Check WiFi conditions before syncing
        val (syncAllowed, syncBlockedReason) = isSyncAllowed()
        val shouldSync = includeGitOperations && remoteUrl.isNotEmpty() && syncAllowed
        
        if (includeGitOperations && remoteUrl.isNotEmpty() && !syncAllowed) {
            Log.d(TAG, "Sync blocked by WiFi settings")
            syncBlockedReason?.let {
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast(uiHelper.getString(it))
                }
            }
        }

        if (shouldSync) {
            _syncState.emit(SyncState.Pull)
            gitManager.pull(cred, author).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast("${it.message}${uiHelper.getString(R.string.offline_hint)}")
                }
            }
        }

        if (shouldSync) {
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
     * Queues a pull operation with debouncing. Commits are handled through queueCommit().
     */
    suspend fun performBackgroundGitOperations(immediate: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val debounceMs = if (immediate) 0L else prefs.backgroundGitDelaySeconds.getBlocking() * 1000L

        Log.d(TAG, "performBackgroundGitOperations called, immediate=$immediate")

        // Queue pull operation with debouncing
        queuePullOperation(currentTime, debounceMs)
    }

    /**
     * Queues a commit operation immediately (no debouncing)
     */
    suspend fun queueCommit(author: GitAuthor, commitMessage: String) {
        Log.d(TAG, "queueCommit: $commitMessage")
        val cred = prefs.cred()
        val remoteUrl = prefs.remoteUrl.getBlocking()
        val backgroundGitOps = prefs.backgroundGitOperations.getBlocking()

        synchronized(gitOperationQueue) {
            // Check if there's already a commit operation queued - if so, add to its message list
            val existingCommitIndex = gitOperationQueue.indexOfFirst { it is GitOperation.Commit }
            if (existingCommitIndex >= 0) {
                // Add to existing commit operation
                val existingOp = gitOperationQueue[existingCommitIndex] as GitOperation.Commit
                existingOp.commitMessages.add(commitMessage)
                Log.d(TAG, "Added message to existing commit operation, now has ${existingOp.commitMessages.size} messages")
            } else {
                // Create new commit operation with message list
                gitOperationQueue.add(GitOperation.Commit(author, mutableListOf(commitMessage)))
            }
        }
        processQueue()

        // If background git operations are enabled, queue pull operation with delay
        if (backgroundGitOps && remoteUrl.isNotEmpty()) {
            performBackgroundGitOperations()
        }
    }



    private suspend fun queuePullOperation(currentTime: Long, debounceMs: Long) {
        val cred = prefs.cred()
        val author = prefs.gitAuthor()

        if (debounceMs == 0L) {
            // Immediate pull
            synchronized(gitOperationQueue) {
                // Cancel any existing scheduled pull
                scheduledPullTimer?.cancel()
                scheduledPullTimer = null
                scheduledPull = null

                // Remove any existing pull operations
                gitOperationQueue.removeAll { it is GitOperation.Pull }

                // Add immediate pull
                gitOperationQueue.add(GitOperation.Pull(cred, author))
                processQueue()
            }
        } else {
            // Scheduled pull with debouncing
            synchronized(gitOperationQueue) {
                // Cancel any existing scheduled pull
                scheduledPullTimer?.cancel()
                scheduledPullTimer = null
                scheduledPull = null

                // Always apply the full debounce delay for pull operations
                Log.d(TAG, "Queuing pull operation with debounce delay: ${debounceMs}ms")
                scheduledPull = GitOperation.Pull(cred, author)

                // Schedule processing after the debounce delay
                scheduledPullTimer = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                    delay(debounceMs)
                    synchronized(gitOperationQueue) {
                        scheduledPull?.let { pull ->
                            gitOperationQueue.add(pull)
                            scheduledPull = null
                            processQueue()
                        }
                    }
                }

                lastPullRequestTime = currentTime
            }
        }
    }

    private fun processQueue() {
        synchronized(gitOperationQueue) {
            if (gitOperationQueue.isEmpty() || currentOperationJob?.isActive == true) {
                return
            }

            val operation = gitOperationQueue.removeAt(0)
            currentOperationJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (operation) {
                        is GitOperation.Commit -> performQueuedCommit(operation.author, operation.commitMessages)
                        is GitOperation.Pull -> performQueuedPull(operation.cred, operation.author)
                    }
                } finally {
                    currentOperationJob = null
                    // Process next operation if any
                    processQueue()
                }
            }
        }
    }

    private suspend fun waitForQueueCompletion() {
        // Wait for any currently executing operation to complete
        currentOperationJob?.join()
        // Also wait for any queued operations to complete
        while (true) {
            synchronized(gitOperationQueue) {
                if (gitOperationQueue.isEmpty() && currentOperationJob?.isActive != true) {
                    break
                }
            }
            delay(10) // Small delay to avoid busy waiting
        }
    }

    private suspend fun performQueuedCommit(author: GitAuthor, commitMessages: List<String>) {
        val commitMessage = createConsolidatedCommitMessage(commitMessages)
        Log.d(TAG, "performQueuedCommit: committing ${commitMessages.size} operations with message: '$commitMessage'")
        gitManager.commitAll(author, commitMessage).onFailure {
            Log.e(TAG, "Queued commit failed: ${it.message}")
        }
    }

    private fun createConsolidatedCommitMessage(commitMessages: List<String>): String {
        return when (commitMessages.size) {
            0 -> "gittasks changes"
            1 -> commitMessages.first()
            else -> {
                val subject = "gittasks changes (${commitMessages.size} operations)"
                val body = commitMessages.joinToString("\n") { "- $it" }
                "$subject\n\n$body"
            }
        }
    }

    private suspend fun performQueuedPull(cred: Cred?, author: GitAuthor) {
        val remoteUrl = prefs.remoteUrl.get()
        if (remoteUrl.isEmpty()) return

        // Check WiFi conditions before syncing
        val (syncAllowed, syncBlockedReason) = isSyncAllowed()
        if (!syncAllowed) {
            Log.d(TAG, "performQueuedPull: Sync blocked by WiFi settings")
            syncBlockedReason?.let {
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast(uiHelper.getString(it))
                }
            }
            return
        }

        try {
            Log.d(TAG, "performQueuedPull: starting queued pull")

            val commitBeforePull = gitManager.lastCommit()

            // Pull
            _syncState.emit(SyncState.Pull)
            gitManager.pull(cred, author).onFailure {
                Log.e(TAG, "Queued pull failed: ${it.message}")
                _syncState.emit(SyncState.Offline)
                return
            }

            val commitAfterPull = gitManager.lastCommit()
            val pullChangedHead = commitAfterPull != commitBeforePull
            Log.d(TAG, "Queued pull succeeded, changed HEAD: $pullChangedHead")

            // Push after successful pull
            _syncState.emit(SyncState.Push)
            gitManager.push(cred).onFailure {
                Log.e(TAG, "Queued push failed: ${it.message}")
                _syncState.emit(SyncState.Offline)
                return
            }

            _syncState.emit(SyncState.Ok(false))

            // Update database if pull brought in remote changes
            if (pullChangedHead) {
                Log.d(TAG, "Queued pull brought remote changes, updating database")
                updateDatabaseIfNeeded()
            } else {
                // No remote changes, just ensure database commit is up to date with current HEAD
                val currentHead = gitManager.lastCommit()
                val databaseCommit = prefs.databaseCommit.get()
                if (currentHead != databaseCommit) {
                    Log.d(TAG, "Updating database commit hash to current HEAD: $currentHead")
                    prefs.databaseCommit.update(currentHead)
                }
            }
        } finally {
            // Operation complete
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
            val outOfSync = fsCommit != databaseCommit
            Log.d(TAG, "isDatabaseOutOfSync: fsCommit=$fsCommit, databaseCommit=$databaseCommit, outOfSync=$outOfSync")
            outOfSync
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
            val fsCommit = gitManager.lastCommit()
            val databaseCommit = prefs.databaseCommit.get()
            
            Log.d(TAG, "updateDatabaseIfNeeded: fsCommit=$fsCommit, databaseCommit=$databaseCommit")
            
            if (fsCommit == databaseCommit) {
                Log.d(TAG, "Database is already in sync with current HEAD, skipping update")
                return
            }
            
            Log.d(TAG, "Database needs updating, starting update...")
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
                Log.d(TAG, "Database update successful")
                _syncState.emit(SyncState.Ok(false))
                withContext(Dispatchers.Main) {
                    uiHelper.makeToast(uiHelper.getString(R.string.sync_success))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating database: ${e.message}", e)
            _syncState.emit(SyncState.Error)
        }
    }

    /**
     * Best effort
     */
    suspend fun updateNote(new: Note, previous: Note, onUpdated: suspend () -> Unit = {}): Result<Unit> = locker.withLock {
        if (prefs.debugFeaturesEnabled.getBlocking()) {
            Log.d(TAG, "updateNote: previous = $previous")
            Log.d(TAG, "updateNote: new = $new")
        } else {
            Log.d(TAG, "updateNote: ${previous.relativePath}")
        }

        val result = update(
            commitMessage = "gittasks changed ${previous.relativePath}",
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
        if (prefs.debugFeaturesEnabled.getBlocking()) {
            Log.d(TAG, "createNote: $note")
        } else {
            Log.d(TAG, "createNote: ${note.relativePath}")
        }

        update(
            commitMessage = "gittasks created ${note.relativePath}"
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

        if (prefs.debugFeaturesEnabled.getBlocking()) {
            Log.d(TAG, "deleteNote: $note")
        } else {
            Log.d(TAG, "deleteNote: ${note.relativePath}")
        }
        update(
            commitMessage = "gittasks deleted ${note.relativePath}"
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
            commitMessage = "gittasks deleted ${notes.size} notes"
        ) {
            // optimization because we only see the db state on screen
            notes.forEach { note ->
                noteRepository.removeNote(note)
            }

            val repoPath = prefs.repoPath()
            notes.forEach { note ->

                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    Log.d(TAG, "deleting $note")
                } else {
                    Log.d(TAG, "deleting ${note.relativePath}")
                }
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

    suspend fun moveNotes(notes: List<Note>, folderRelativePath: String): Result<Unit> = locker.withLock {
        Log.d(TAG, "moveNotes: ${notes.size} notes to $folderRelativePath")

        update(
            commitMessage = "gittasks moved ${notes.size} notes to $folderRelativePath"
        ) {
            val repoPath = prefs.repoPath()
            
            notes.forEach { note ->
                val newRelativePath = "$folderRelativePath/${note.fullName()}"
                val newNote = note.copy(relativePath = newRelativePath)
                
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    Log.d(TAG, "moving $note to $newRelativePath")
                } else {
                    Log.d(TAG, "moving ${note.relativePath} to $newRelativePath")
                }
                
                noteRepository.removeNote(note)
                noteRepository.insertNote(newNote)
                
                val previousFile = note.toFileFs(repoPath)
                
                if (previousFile.exist()) {
                    previousFile.delete().onFailure {
                        val message = uiHelper.getString(R.string.error_delete_file, previousFile.path, it.message)
                        Log.e(TAG, message)
                        uiHelper.makeToast(message)
                    }
                }
                
                val newFile = newNote.toFileFs(repoPath)
                newFile.create().onFailure {
                    val message = uiHelper.getString(R.string.error_create_file, it.message)
                    Log.e(TAG, message)
                    uiHelper.makeToast(message)
                }
                
                newFile.write(newNote.content).onFailure {
                    val message = uiHelper.getString(R.string.error_write_file, it.message)
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
            commitMessage = "gittasks created folder ${noteFolder.relativePath}"
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
            commitMessage = "gittasks deleted folder ${noteFolder.relativePath}"
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

        Log.d(TAG, "update: backgroundGitOps = $backgroundGitOps, remoteUrl = $remoteUrl")

        var syncFailed = false

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

        // Queue commit operation (will execute when it's its turn in the queue)
        queueCommit(author, commitMessage)

        // Handle pull/push based on background git ops setting
        if (!backgroundGitOps && remoteUrl.isNotEmpty()) {
            // For synchronous mode, wait for any queued operations to complete first
            waitForQueueCompletion()

            _syncState.emit(SyncState.Pull)
            gitManager.pull(cred, author).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
            }.onSuccess {
                // Update database commit after successful pull
                prefs.databaseCommit.update(gitManager.lastCommit())
            }
        }

        if (!backgroundGitOps && remoteUrl.isNotEmpty()) {
            _syncState.emit(SyncState.Push)
            gitManager.push(cred).onFailure {
                syncFailed = true
                _syncState.emit(SyncState.Offline)
            }
        }

        // Background git operations are now handled in queueCommit
        if (!syncFailed) {
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

    fun setInitializing(initializing: Boolean) {
        isInitializing = initializing
        Log.d(TAG, "Initialization mode set to: $initializing")
    }

    suspend fun shutdown() {
        Log.d(TAG, "Shutting down StorageManager, isInitializing=$isInitializing")
        
        // Cancel any scheduled pull timer
        scheduledPullTimer?.cancel()
        scheduledPullTimer = null
        
        // Cancel any waiting git job
        waitingGitJob?.cancel()
        waitingGitJob = null
        
        // Cancel any executing git job
        executingGitJob?.cancel()
        executingGitJob = null
        
        // Handle current operation based on initialization state
        if (isInitializing) {
            // During initialization, wait for critical operations to complete
            Log.d(TAG, "Waiting for initialization operations to complete...")
            waitForQueueCompletion()
        } else {
            // Outside initialization, cancel current operation
            currentOperationJob?.cancel()
            waitForQueueCompletion()
        }
        
        Log.d(TAG, "StorageManager shutdown complete")
    }
}
