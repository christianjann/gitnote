package io.github.christianjann.gitnotecje.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import io.github.christianjann.gitnotecje.MyApp
import io.github.christianjann.gitnotecje.data.AppPreferences
import io.github.christianjann.gitnotecje.data.StorageConfig
import io.github.christianjann.gitnotecje.data.platform.NodeFs
import io.github.christianjann.gitnotecje.helper.StoragePermissionHelper
import io.github.christianjann.gitnotecje.helper.UiHelper
import io.github.christianjann.gitnotecje.manager.GitException
import io.github.christianjann.gitnotecje.manager.GitExceptionType
import io.github.christianjann.gitnotecje.ui.model.StorageConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val gitManager = MyApp.appModule.gitManager
    val uiHelper: UiHelper = MyApp.appModule.uiHelper

    private val storageManager = MyApp.appModule.storageManager

    private var syncJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }


    suspend fun tryInit(): Boolean {

        if (!prefs.isInit.get()) {
            return false
        }

        val storageConfig = when (prefs.storageConfig.get()) {
            StorageConfig.App -> {
                StorageConfiguration.App
            }

            StorageConfig.Device -> {
                if (!StoragePermissionHelper.isPermissionGranted()) {
                    return false
                }
                val repoPath = try {
                    prefs.repoPath()
                } catch (_: Exception) {
                    return false
                }
                StorageConfiguration.Device(repoPath)
            }
        }

        if (!NodeFs.Folder.fromPath(storageConfig.repoPath()).exist()) {
            return false
        }

        val openResult = gitManager.openRepo(storageConfig.repoPath())
        if (openResult.isFailure) {
            val exception = openResult.exceptionOrNull()
            if (exception !is GitException || exception.type != GitExceptionType.RepoAlreadyInit) {
                return false
            }
            // If already initialized, continue
        }
        prefs.applyGitAuthorDefaults(null, gitManager.currentSignature())

        // Perform initial database sync after repository is opened
        // This ensures database is up to date when the app starts
        if (syncJob?.isActive != true) {
            syncJob = CoroutineScope(Dispatchers.IO).launch {
                val lastSyncTime = prefs.lastDatabaseSyncTime.get()
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSyncTime

                Log.d(TAG, "Sync check: lastSyncTime=$lastSyncTime, currentTime=$currentTime, timeSinceLastSync=$timeSinceLastSync")

                // Always perform sync on app start to ensure data is up to date
                // Users expect the app to be current when they open it
                Log.d(TAG, "Starting sync operations on app start")
                
                // Perform background git operations to sync with remote
                // This will automatically update the database after completion
                val backgroundGitOps = prefs.backgroundGitOperations.getBlocking()
                Log.d(TAG, "Background git ops enabled: $backgroundGitOps")
                if (backgroundGitOps) {
                    storageManager.performBackgroundGitOperations()
                } else {
                    // If background git ops are disabled, still update database
                    storageManager.updateDatabaseIfNeeded()
                }
                
                prefs.lastDatabaseSyncTime.update(currentTime)
            }
        }

        return true
    }

}
