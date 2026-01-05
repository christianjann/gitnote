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
import io.github.christianjann.gitnotecje.manager.SyncState
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
    private var initialSyncCompleted = false

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

        // Note: Repo opening is deferred to startSync() to speed up app startup

        return true
    }

    fun startSync() {
        Log.i(TAG, "startSync called")
        if (syncJob?.isActive != true) {
            syncJob = CoroutineScope(Dispatchers.IO).launch { //maybe set to Dispatchers.Default
                // Set opening state
                storageManager.setSyncState(SyncState.Opening)
                
                // Open the repository first (only if not already open)
                val storageConfig = when (prefs.storageConfig.get()) {
                    StorageConfig.App -> {
                        StorageConfiguration.App
                    }

                    StorageConfig.Device -> {
                        StorageConfiguration.Device(prefs.repoPath())
                    }
                }

                if (!gitManager.isRepositoryInitialized()) {
                    Log.i(TAG, "About to open repo: ${storageConfig.repoPath()}")
                    val openResult = gitManager.openRepo(storageConfig.repoPath())
                    if (openResult.isFailure) {
                        val exception = openResult.exceptionOrNull()
                        Log.e(TAG, "Failed to open repo: $exception")
                        storageManager.setSyncState(SyncState.Error)
                        return@launch
                    }
                    Log.i(TAG, "Repo opened successfully")
                } else {
                    Log.i(TAG, "Repo already open, skipping open operation")
                }
                storageManager.setSyncState(SyncState.Ok(false))
                prefs.applyGitAuthorDefaults(null, gitManager.currentSignature())

                val lastSyncTime = prefs.lastDatabaseSyncTime.get()
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSyncTime

                Log.i(TAG, "Sync check: lastSyncTime=$lastSyncTime, currentTime=$currentTime, timeSinceLastSync=$timeSinceLastSync")

                // Always perform sync on app start to ensure data is up to date
                // Users expect the app to be current when they open it
                Log.i(TAG, "Starting sync operations on app start")
                
                // Perform background git operations to sync with remote
                // This will automatically update the database after completion
                // Only perform these expensive operations on the first app start, not on screen rotations
                if (!initialSyncCompleted) {
                    val backgroundGitOps = prefs.backgroundGitOperations.getBlocking()
                    Log.i(TAG, "Background git ops enabled: $backgroundGitOps")
                    if (backgroundGitOps) {
                        storageManager.performBackgroundGitOperations(immediate = true)
                    } else {
                        // If background git ops are disabled, still update database
                        storageManager.updateDatabaseIfNeeded()
                    }
                    
                    initialSyncCompleted = true
                } else {
                    Log.i(TAG, "Skipping background sync operations - already completed for this app session")
                }
                
                prefs.lastDatabaseSyncTime.update(currentTime)
            }
        }
    }

}
