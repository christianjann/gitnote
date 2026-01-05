package io.github.christianjann.gitnotecje.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.christianjann.gitnotecje.data.AppPreferences
import io.github.christianjann.gitnotecje.data.platform.FileSystem
import io.github.christianjann.gitnotecje.data.platform.NodeFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

private const val TAG = "AssetManager"

class AssetManager(
    private val context: Context,
    private val prefs: AppPreferences
) {

    private fun getWorkingDirectory(repoPath: String): String {
        val repoFile = File(repoPath)
        return if (repoFile.name == ".git") {
            repoFile.parent ?: repoPath
        } else {
            repoPath
        }
    }

    /**
     * Import a file from Android's file system to the repository's asset directory
     */
    suspend fun importAsset(uri: Uri, filename: String, repoPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workingDir = getWorkingDirectory(repoPath)
            val assetDir = prefs.assetDirectory.get()
            val assetPath = "$assetDir/$filename"
            val assetFile = File(workingDir, assetPath)

            // Create asset directory if it doesn't exist
            val assetDirFile = File(workingDir, assetDir)
            if (!assetDirFile.exists()) {
                assetDirFile.mkdirs()
            }

            // Copy file from URI to asset directory
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(assetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext failure(Exception("Could not open input stream for URI"))

            Log.d(TAG, "Imported asset: ${assetFile.absolutePath}")

            success(assetPath)
        } catch (e: Exception) {
            failure(e)
        }
    }

    /**
     * Export an asset from the repository to Android's file system
     */
    suspend fun exportAsset(assetPath: String, destinationUri: Uri, repoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val workingDir = getWorkingDirectory(repoPath)
            val assetFile = File(workingDir, assetPath)

            if (!assetFile.exists()) {
                return@withContext failure(Exception("Asset file does not exist: ${assetFile.absolutePath}"))
            }

            if (assetFile.length() == 0L) {
                return@withContext failure(Exception("Asset file is empty: ${assetFile.absolutePath}"))
            }

            // Copy file to destination URI
            FileInputStream(assetFile).use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                } ?: return@withContext failure(Exception("Could not open output stream for destination URI"))
            }

            Log.d(TAG, "Exported asset: ${assetFile.absolutePath}")

            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    /**
     * Get all assets in the asset directory
     */
    suspend fun listAssets(repoPath: String): Result<List<NodeFs>> = withContext(Dispatchers.IO) {
        try {
            val workingDir = getWorkingDirectory(repoPath)
            val assetDir = prefs.assetDirectory.get()
            val assetDirFile = File(workingDir, assetDir)

            if (!assetDirFile.exists()) {
                return@withContext success(emptyList())
            }

            val assets = mutableListOf<NodeFs>()
            assetDirFile.listFiles()?.forEach { file ->
                if (file.isFile) {
                    assets.add(NodeFs.File.fromPath(workingDir, "$assetDir/${file.name}"))
                }
            }

            success(assets)
        } catch (e: Exception) {
            failure(e)
        }
    }

    /**
     * Delete an asset from the repository
     */
    suspend fun deleteAsset(assetPath: String, repoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val workingDir = getWorkingDirectory(repoPath)
            val assetFile = File(workingDir, assetPath)
            if (assetFile.exists()) {
                val deleted = assetFile.delete()
                Log.d(TAG, "Deleted asset: ${assetFile.absolutePath}")
                success(Unit)
            } else {
                Log.w(TAG, "Asset file does not exist: ${assetFile.absolutePath}")
                failure(Exception("Asset file does not exist: ${assetFile.absolutePath}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting asset: ${assetPath}", e)
            failure(e)
        }
    }

    /**
     * Get the relative path for an asset reference in markdown
     */
    fun getMarkdownAssetPath(filename: String): String {
        val assetDir = prefs.assetDirectory.getBlocking()
        return "$assetDir/$filename"
    }
}