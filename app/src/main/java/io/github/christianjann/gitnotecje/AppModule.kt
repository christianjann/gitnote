package io.github.christianjann.gitnotecje

import android.content.Context
import io.github.christianjann.gitnotecje.data.AppPreferences
import io.github.christianjann.gitnotecje.data.NoteRepository
import io.github.christianjann.gitnotecje.data.room.RepoDatabase
import io.github.christianjann.gitnotecje.helper.UiHelper
import io.github.christianjann.gitnotecje.manager.GitManager
import io.github.christianjann.gitnotecje.manager.StorageManager


interface AppModule {
    val repoDatabase: RepoDatabase
    val noteRepository: NoteRepository
    val uiHelper: UiHelper
    val storageManager: StorageManager
    val gitManager: GitManager
    val appPreferences: AppPreferences
    val context: Context

}

class AppModuleImpl(
    override val context: Context
) : AppModule {

    override val repoDatabase: RepoDatabase by lazy {
        RepoDatabase.buildDatabase(context)
    }

    override val noteRepository: NoteRepository by lazy {
        NoteRepository(repoDatabase)
    }

    override val uiHelper: UiHelper by lazy {
        UiHelper(context)
    }
    override val storageManager: StorageManager by lazy {
        StorageManager()
    }
    override val gitManager: GitManager by lazy {
        GitManager()
    }
    override val appPreferences: AppPreferences by lazy {
        AppPreferences(context)
    }
}