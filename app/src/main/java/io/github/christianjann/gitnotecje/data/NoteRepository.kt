package io.github.christianjann.gitnotecje.data

import androidx.paging.PagingSource
import io.github.christianjann.gitnotecje.data.room.Note
import io.github.christianjann.gitnotecje.data.room.NoteFolder
import io.github.christianjann.gitnotecje.data.room.RepoDatabase
import io.github.christianjann.gitnotecje.ui.model.GridNote
import io.github.christianjann.gitnotecje.ui.model.SortOrder
import io.github.christianjann.gitnotecje.ui.screen.app.DrawerFolderModel
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val database: RepoDatabase) {

    fun getAllNotes(): Flow<List<Note>> = database.repoDatabaseDao.allNotes()

    fun getNoteByRelativePath(relativePath: String): Flow<Note?> = database.repoDatabaseDao.getNoteByRelativePath(relativePath)

    fun getGridNotes(
        currentNoteFolderRelativePath: String,
        sortOrder: SortOrder,
        includeSubfolders: Boolean,
        tag: String? = null,
        tagIgnoresFolders: Boolean = false,
    ): PagingSource<Int, GridNote> = database.repoDatabaseDao.gridNotes(
        currentNoteFolderRelativePath,
        sortOrder,
        includeSubfolders,
        tag,
        tagIgnoresFolders
    )

    fun getGridNotesWithQuery(
        currentNoteFolderRelativePath: String,
        sortOrder: SortOrder,
        query: String,
        includeSubfolders: Boolean,
        tag: String? = null,
        tagIgnoresFolders: Boolean = false,
        searchIgnoresFilters: Boolean = false,
    ): PagingSource<Int, GridNote> = database.repoDatabaseDao.gridNotesWithQuery(
        currentNoteFolderRelativePath,
        sortOrder,
        query,
        includeSubfolders,
        tag,
        tagIgnoresFolders,
        searchIgnoresFilters
    )

    fun getDrawerFolders(
        currentNoteFolderRelativePath: String,
        sortOrder: SortOrder,
    ): Flow<List<DrawerFolderModel>> = database.repoDatabaseDao.drawerFolders(
        currentNoteFolderRelativePath,
        sortOrder
    )

    suspend fun insertNote(note: Note) = database.repoDatabaseDao.insertNote(note)

    suspend fun removeNote(note: Note) = database.repoDatabaseDao.removeNote(note)

    suspend fun clearAndInit(
        rootPath: String,
        timestamps: HashMap<String, Long>,
        progressCb: ((io.github.christianjann.gitnotecje.manager.Progress) -> Unit)? = null
    ) = database.repoDatabaseDao.clearAndInit(rootPath, timestamps, progressCb)

    suspend fun insertNoteFolder(noteFolder: NoteFolder) = database.repoDatabaseDao.insertNoteFolder(noteFolder)

    suspend fun deleteNoteFolder(noteFolder: NoteFolder) = database.repoDatabaseDao.deleteNoteFolder(noteFolder)

    suspend fun isNoteExist(relativePath: String): Boolean = database.repoDatabaseDao.isNoteExist(relativePath)

    fun clearDatabase() = database.repoDatabaseDao.clearDatabase()
}