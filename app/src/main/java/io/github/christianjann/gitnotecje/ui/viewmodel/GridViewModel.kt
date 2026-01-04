package io.github.christianjann.gitnotecje.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.christianjann.gitnotecje.MyApp
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.data.AppPreferences
import io.github.christianjann.gitnotecje.data.room.Note
import io.github.christianjann.gitnotecje.data.room.NoteFolder
import io.github.christianjann.gitnotecje.data.room.RepoDatabase
import io.github.christianjann.gitnotecje.helper.FrontmatterParser
import io.github.christianjann.gitnotecje.helper.NameValidation
import io.github.christianjann.gitnotecje.manager.GitLogEntry
import io.github.christianjann.gitnotecje.manager.StorageManager
import io.github.christianjann.gitnotecje.manager.SyncState
import io.github.christianjann.gitnotecje.ui.model.FileExtension
import io.github.christianjann.gitnotecje.ui.model.GridNote
import io.github.christianjann.gitnotecje.ui.model.NoteViewType
import io.github.christianjann.gitnotecje.ui.model.SortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GridViewModel : ViewModel() {

    companion object {
        private const val TAG = "GridViewModel"
    }


    private val storageManager: StorageManager = MyApp.appModule.storageManager

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val db: RepoDatabase = MyApp.appModule.repoDatabase
    private val dao = db.repoDatabaseDao
    val uiHelper = MyApp.appModule.uiHelper

    init {
        // Database sync is now handled by MainViewModel after repository initialization
        // to avoid race conditions during Activity recreation
        
        // Set up global refresh callback for database updates
        storageManager.onDatabaseUpdated = {
            Log.d(TAG, "onDatabaseUpdated callback triggered, refreshing UI")
            _refreshTrigger.value = _refreshTrigger.value + 1
            _refreshSignal.value = _refreshSignal.value + 1
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger

    private val _refreshSignal = MutableStateFlow(0)
    val refreshSignal: StateFlow<Int> = _refreshSignal

    private val refreshCounter = MutableStateFlow(0)

    val syncState = storageManager.syncState

    fun consumeOkSyncState() {
        viewModelScope.launch {
            storageManager.consumeOkSyncState()
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()


    private val _currentNoteFolderRelativePath = MutableStateFlow(
        if (prefs.rememberLastOpenedFolder.getBlocking()) {
            prefs.lastOpenedFolder.getBlocking()
        } else {
            ""
        }
    )
    val currentNoteFolderRelativePath: StateFlow<String>
        get() = _currentNoteFolderRelativePath.asStateFlow()


    private val _selectedNotes: MutableStateFlow<List<Note>> = MutableStateFlow(emptyList())

    val selectedNotes: StateFlow<List<Note>>
        get() = _selectedNotes.asStateFlow()

    private val _showGitLogDialog = MutableStateFlow(false)
    val showGitLogDialog: StateFlow<Boolean> = _showGitLogDialog.asStateFlow()

    private val _gitLogEntries = MutableStateFlow<List<GitLogEntry>>(emptyList())
    val gitLogEntries: StateFlow<List<GitLogEntry>> = _gitLogEntries.asStateFlow()

    private val _isGitLogLoading = MutableStateFlow(false)
    val isGitLogLoading: StateFlow<Boolean> = _isGitLogLoading.asStateFlow()


    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _noteBeingMoved = MutableStateFlow<Note?>(null)
    val noteBeingMoved: StateFlow<Note?> = _noteBeingMoved.asStateFlow()

    private val _showEditTagsDialog = MutableStateFlow(false)
    val showEditTagsDialog: StateFlow<Boolean> = _showEditTagsDialog.asStateFlow()

    private val _noteBeingEditedTags = MutableStateFlow<Note?>(null)
    val noteBeingEditedTags: StateFlow<Note?> = _noteBeingEditedTags.asStateFlow()

    // Direct tag state for immediate UI updates
    private val _currentTags = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val currentTags: StateFlow<Map<Int, List<String>>> = _currentTags.asStateFlow()

    fun startMoveNote(note: Note) {
        viewModelScope.launch {
            _noteBeingMoved.emit(note)
        }
    }

    fun selectTag(tag: String?) {
        viewModelScope.launch {
            _selectedTag.emit(tag)
        }
    }

    fun cancelMoveNote() {
        viewModelScope.launch {
            _noteBeingMoved.emit(null)
        }
    }

    fun startEditTags(note: Note) {
        viewModelScope.launch {
            _noteBeingEditedTags.emit(note)
            _showEditTagsDialog.emit(true)
        }
    }

    fun cancelEditTags() {
        viewModelScope.launch {
            _showEditTagsDialog.emit(false)
            _noteBeingEditedTags.emit(null)
        }
    }

    // Get current tags for a note - checks direct state first, then parses from content
    fun getCurrentTagsForNote(note: Note): List<String> {
        return _currentTags.value[note.id] ?: FrontmatterParser.parseTags(note.content)
    }

    fun updateNoteTags(note: Note, newTags: List<String>) {
        viewModelScope.launch {
            Log.d(TAG, "updateNoteTags: updating note ${note.id} with tags $newTags")

            // Update UI immediately with new tags
            _currentTags.value = _currentTags.value + (note.id to newTags)

            val updatedContent = FrontmatterParser.updateTags(note.content, newTags)
            val updatedNote = note.copy(
                content = updatedContent,
                lastModifiedTimeMillis = System.currentTimeMillis()
            )
            val result = storageManager.updateNote(updatedNote, note) {
                // Wait a bit to ensure database transaction completes
                kotlinx.coroutines.delay(50)
                // Note: Keep the direct tag state since it matches the updated content
            }
            result.onFailure { e ->
                // Revert the UI update on failure
                _currentTags.value = _currentTags.value - note.id
                uiHelper.makeToast("Failed to update note tags: $e")
            }
            cancelEditTags()
        }
    }

    fun moveNoteToFolder(folderRelativePath: String) {
        val note = noteBeingMoved.value ?: return
        viewModelScope.launch {
            val newRelativePath = "$folderRelativePath/${note.fullName()}"
            val newNote = note.copy(relativePath = newRelativePath)
            val result = storageManager.updateNote(newNote, note) {
                // Refresh is handled by Room invalidation
            }
            result.onFailure { e ->
                uiHelper.makeToast("Failed to move note: $e")
            }
            _noteBeingMoved.emit(null)
        }
    }


    init {
        Log.d(TAG, "init")
    }

    suspend fun refreshSelectedNotes() {
        selectedNotes.value.filter { selectedNote ->
            dao.isNoteExist(selectedNote.relativePath)
        }.let { newSelectedNotes ->
            _selectedNotes.emit(newSelectedNotes)
        }
    }

    fun refresh() {
        CoroutineScope(Dispatchers.IO).launch {
            _isRefreshing.emit(true)
            val backgroundGitOps = prefs.backgroundGitOperations.getBlocking()
            storageManager.updateDatabaseAndRepo(includeGitOperations = !backgroundGitOps)
            if (backgroundGitOps) {
                storageManager.performBackgroundGitOperations()
            }
            refreshSelectedNotes()
            _isRefreshing.emit(false)
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _query.emit(query)
        }
    }

    fun clearQuery() {
        viewModelScope.launch {
            _query.emit("")
        }
    }

    fun openFolder(relativePath: String) {
        viewModelScope.launch {
            _currentNoteFolderRelativePath.emit(relativePath)
            prefs.lastOpenedFolder.update(relativePath)
        }
    }

    fun createNoteFolder(relativeParentPath: String, name: String): Boolean {
        if (!NameValidation.check(name)) {
            uiHelper.makeToast(uiHelper.getString(R.string.error_invalid_name))
            return false
        }

        val relativePath = "$relativeParentPath/$name"

        val noteFolder = NoteFolder.new(
            relativePath = relativePath
        )

        if (noteFolder.toFolderFs(prefs.repoPathBlocking()).exist()) {
            uiHelper.makeToast(uiHelper.getString(R.string.error_folder_already_exist))
            return false
        }

        CoroutineScope(Dispatchers.IO).launch {
            storageManager.createNoteFolder(noteFolder)
        }

        return true
    }


    /**
     * @param add true if the note must be selected, false otherwise
     */
    fun selectNote(note: Note, add: Boolean) = viewModelScope.launch {
        if (add) {
            selectedNotes.value.plus(note)
        } else {
            selectedNotes.value.minus(note)
        }.let {
            _selectedNotes.emit(it)
        }
    }

    fun unselectAllNotes() = viewModelScope.launch {
        _selectedNotes.emit(emptyList())
    }

    fun toggleViewType() {
        viewModelScope.launch {
            val next = if (prefs.noteViewType.get() == NoteViewType.Grid) {
                NoteViewType.List
            } else {
                NoteViewType.Grid
            }
            prefs.noteViewType.update(next)
        }
    }

    fun deleteSelectedNotes() {
        CoroutineScope(Dispatchers.IO).launch {
            val currentSelectedNotes = selectedNotes.value
            unselectAllNotes()
            storageManager.deleteNotes(currentSelectedNotes)
        }
    }

    fun deleteNote(note: Note) {
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.deleteNote(note)
        }
    }

    fun deleteFolder(noteFolder: NoteFolder) {
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.deleteNoteFolder(noteFolder)
        }
    }


    fun defaultNewNote(): Note {

        val defaultName = query.value.let {
            if (NameValidation.check(it)) {
                it
            } else ""
        }

        val defaultExtension = FileExtension.match(prefs.defaultExtension.getBlocking())
        val defaultFullName = "$defaultName.${defaultExtension.text}"

        val currentNoteFolderRelativePath = currentNoteFolderRelativePath.value

        val parent = if (currentNoteFolderRelativePath == "") {
            prefs.defaultPathForNewNote.getBlocking()
        } else currentNoteFolderRelativePath

        return Note.new(
            relativePath = "$parent/$defaultFullName",
        )
    }

    fun defaultNewTask(): Note {
        val note = defaultNewNote()
        val content = FrontmatterParser.addCompleted(note.content)
        return note.copy(content = content)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingFlow = combine(
        prefs.includeSubfolders.getFlow(),
        prefs.tagIgnoresFolders.getFlow(),
        prefs.searchIgnoresFilters.getFlow()
    ) { includeSubfolders, tagIgnoresFolders, searchIgnoresFilters ->
        Triple(includeSubfolders, tagIgnoresFolders, searchIgnoresFilters)
    }.flatMapLatest { (includeSubfolders, tagIgnoresFolders, searchIgnoresFilters) ->
        combine(
            currentNoteFolderRelativePath,
            prefs.sortOrder.getFlow(),
            query,
            selectedTag,
            refreshTrigger
        ) { currentNoteFolderRelativePath, sortOrder, query, selectedTag, refreshTriggerValue ->
            Log.d(TAG, "pagingFlow: creating new Pager, refreshTrigger = $refreshTriggerValue")
            Pager(
                config = PagingConfig(pageSize = 50),
                pagingSourceFactory = {
                    Log.d(TAG, "pagingFlow: creating new paging source, refreshTrigger = $refreshTriggerValue")
                    if (query.isEmpty()) {
                        dao.gridNotes(currentNoteFolderRelativePath, sortOrder, includeSubfolders, selectedTag, tagIgnoresFolders)
                    } else {
                        dao.gridNotesWithQuery(currentNoteFolderRelativePath, sortOrder, query, includeSubfolders, selectedTag, tagIgnoresFolders, searchIgnoresFilters)
                    }
                }
            ).flow.cachedIn(viewModelScope)
        }
    }.flatMapLatest { it }

    val gridNotes = combine(refreshTrigger, refreshSignal) { _, _ -> Unit }.flatMapLatest { _ ->
        combine(
            pagingFlow,
            selectedNotes
        ) { pagingData: PagingData<GridNote>, selectedNotes: List<Note> ->
            Log.d(TAG, "gridNotes combine: creating new PagingData, selectedNotes count = ${selectedNotes.size}")
            pagingData.map { gridNote ->
                val updatedGridNote = gridNote.copy(
                    selected = selectedNotes.contains(gridNote.note),
                    completed = FrontmatterParser.parseCompletedOrNull(gridNote.note.content)
                )
                Log.d(TAG, "gridNotes map: note ${gridNote.note.id}, content hash = ${gridNote.note.content.hashCode()}, tags = ${FrontmatterParser.parseTags(gridNote.note.content)}")
                updatedGridNote
            }
        }
    }.flowOn(Dispatchers.Main)

    val allTags = dao.allNotes().flowMap { notes: List<Note> ->
        notes.flatMap { note: Note ->
            FrontmatterParser.parseTags(note.content)
        }.distinct().sorted()
    }.stateIn(
        CoroutineScope(Dispatchers.Main), SharingStarted.WhileSubscribed(), emptyList()
    )

    // todo: use pager
    @OptIn(ExperimentalCoroutinesApi::class)
    val drawerFolders = combine(
        currentNoteFolderRelativePath,
        prefs.sortOrderFolder.getFlow(),
    ) { currentNoteFolderRelativePath, sortOrder ->
        Pair(currentNoteFolderRelativePath, sortOrder)
    }.flatMapLatest { pair ->
        val (currentNoteFolderRelativePath, sortOrder) = pair
        dao.drawerFolders(currentNoteFolderRelativePath, sortOrder)
    }.stateIn(
        CoroutineScope(Dispatchers.Main), SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun reloadDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.setSyncState(SyncState.Reloading)
            try {
                uiHelper.makeToast(uiHelper.getString(R.string.reloading_database))
                val res = storageManager.updateDatabase(force = true)
                res.onFailure {
                    uiHelper.makeToast("${uiHelper.getString(R.string.failed_reload)}: $it")
                }
                res.onSuccess {
                    uiHelper.makeToast(uiHelper.getString(R.string.success_reload))
                }
            } finally {
                storageManager.setSyncState(SyncState.Ok(false))
            }
        }
    }

    fun toggleCompleted(note: Note) {
        viewModelScope.launch {
            val newContent = FrontmatterParser.toggleCompleted(note.content)
            val newNote = note.copy(
                content = newContent,
                lastModifiedTimeMillis = System.currentTimeMillis()
            )
            val result = storageManager.updateNote(newNote, note) {
                // Refresh is handled by Room invalidation
            }
            result.onFailure {
                uiHelper.makeToast("Failed to update note: $it")
            }
        }
    }

    fun convertToTask(note: Note) {
        viewModelScope.launch {
            val newContent = FrontmatterParser.addCompleted(note.content)
            val newNote = note.copy(
                content = newContent,
                lastModifiedTimeMillis = System.currentTimeMillis()
            )
            val result = storageManager.updateNote(newNote, note) {
                // Refresh is handled by Room invalidation
            }
            result.onFailure {
                uiHelper.makeToast("Failed to convert note: $it")
            }
        }
    }

    fun convertToNote(note: Note) {
        viewModelScope.launch {
            val newContent = FrontmatterParser.removeCompleted(note.content)
            val newNote = note.copy(
                content = newContent,
                lastModifiedTimeMillis = System.currentTimeMillis()
            )
            val result = storageManager.updateNote(newNote, note) {
                // Refresh is handled by Room invalidation
            }
            result.onFailure {
                uiHelper.makeToast("Failed to convert note: $it")
            }
        }
    }

    fun showGitLog() {
        if (_isGitLogLoading.value) return
        viewModelScope.launch {
            _isGitLogLoading.value = true
            storageManager.getGitLog().onSuccess { entries ->
                _gitLogEntries.value = entries
                _showGitLogDialog.value = true
            }.onFailure {
                uiHelper.makeToast("Failed to load git log: $it")
            }.also {
                _isGitLogLoading.value = false
            }
        }
    }

    fun hideGitLogDialog() {
        _showGitLogDialog.value = false
        _gitLogEntries.value = emptyList()
    }
}
