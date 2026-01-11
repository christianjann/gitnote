package io.github.christianjann.gittasks.ui.viewmodel


import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.christianjann.gittasks.MyApp
import io.github.christianjann.gittasks.R
import io.github.christianjann.gittasks.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsViewModel : ViewModel() {

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val storageManager = MyApp.appModule.storageManager
    val uiHelper = MyApp.appModule.uiHelper
    private val context = MyApp.appModule.context

    fun update(f: suspend () -> Unit) {
        viewModelScope.launch {
            f()
        }
    }

    fun closeRepo() {
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.closeRepo()
        }
    }

    fun reloadDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            uiHelper.makeToast(uiHelper.getString(R.string.reloading_database))
            val res = storageManager.updateDatabase(force = true)
            res.onFailure {
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast("${uiHelper.getString(R.string.failed_reload)}: $it")
                }
            }
            res.onSuccess {
                if (prefs.debugFeaturesEnabled.getBlocking()) {
                    uiHelper.makeToast(uiHelper.getString(R.string.success_reload))
                }
            }
        }
    }

    fun exportRepoAsZip(destinationUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            uiHelper.makeToast(uiHelper.getString(R.string.exporting_repository))
            try {
                val repoPath = prefs.repoPath()
                val repoDir = File(repoPath)
                
                // Handle .git bare repo case - get parent directory
                val workingDir = if (repoDir.name == ".git") {
                    repoDir.parentFile ?: repoDir
                } else {
                    repoDir
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            zipDirectory(workingDir, workingDir.name, zipOut)
                        }
                    } ?: throw Exception("Could not open output stream")
                }

                uiHelper.makeToast(uiHelper.getString(R.string.export_success))
            } catch (e: Exception) {
                uiHelper.makeToast("${uiHelper.getString(R.string.export_failed)}: ${e.message}")
            }
        }
    }

    private fun zipDirectory(folder: File, parentFolder: String, zipOut: ZipOutputStream) {
        val files = folder.listFiles() ?: return
        
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(file, "$parentFolder/${file.name}", zipOut)
            } else {
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry("$parentFolder/${file.name}")
                    zipOut.putNextEntry(zipEntry)
                    
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zipOut.write(buffer, 0, length)
                    }
                    zipOut.closeEntry()
                }
            }
        }
    }
}