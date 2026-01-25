package io.github.christianjann.gittasks.helper

import android.util.Log
import io.github.christianjann.gittasks.MyApp
import io.github.christianjann.gittasks.data.room.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

private const val TAG = "FavoritesManager"
const val FAVORITES_FILENAME = "favorites.md"

/**
 * Represents a favorite item which can be either a folder or a tag.
 */
sealed class FavoriteItem {
    data class Folder(val path: String) : FavoriteItem()
    data class Tag(val name: String) : FavoriteItem()
}

/**
 * Data class holding all favorites.
 */
data class Favorites(
    val folders: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * Manages reading and writing favorites to a favorites.md file in the repository root.
 * Uses StorageManager for persistence like any other note.
 * 
 * File format (human-readable markdown):
 * ```markdown
 * ---
 * title: Favorites
 * ---
 * 
 * ## Folders
 * 
 * - path/to/folder1
 * - path/to/folder2
 * 
 * ## Tags
 * 
 * - tag1
 * - tag2
 * ```
 */
class FavoritesManager {
    
    private val storageManager get() = MyApp.appModule.storageManager
    private val noteRepository get() = MyApp.appModule.noteRepository
    
    /**
     * Load favorites from the favorites.md note.
     * Returns empty Favorites if the file doesn't exist or can't be parsed.
     */
    suspend fun loadFavorites(): Favorites = withContext(Dispatchers.IO) {
        try {
            // Try to get the note from the database first
            val note = noteRepository.getNoteByRelativePath(FAVORITES_FILENAME).firstOrNull()
            if (note != null) {
                Log.d(TAG, "Loaded favorites from database")
                return@withContext parseFavorites(note.content)
            }
            
            Log.d(TAG, "Favorites note not found in database")
            Favorites()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites: ${e.message}", e)
            Favorites()
        }
    }
    
    /**
     * Save favorites using StorageManager like any other note.
     */
    suspend fun saveFavorites(favorites: Favorites): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val content = formatFavorites(favorites)
            val currentTime = System.currentTimeMillis()
            
            // Check if the note already exists
            val existingNote = noteRepository.getNoteByRelativePath(FAVORITES_FILENAME).firstOrNull()
            
            if (existingNote != null) {
                // Update existing note via StorageManager
                val updatedNote = existingNote.copy(
                    content = content,
                    lastModifiedTimeMillis = currentTime
                )
                val result = storageManager.updateNote(updatedNote, existingNote)
                result.onFailure { e ->
                    Log.e(TAG, "Failed to update favorites: ${e.message}", e)
                    return@withContext Result.failure(e)
                }
                Log.d(TAG, "Updated favorites note via StorageManager")
            } else {
                // Create new note via StorageManager
                val newNote = Note.new(relativePath = FAVORITES_FILENAME).copy(
                    content = content,
                    lastModifiedTimeMillis = currentTime
                )
                val result = storageManager.createNote(newNote)
                result.onFailure { e ->
                    Log.e(TAG, "Failed to create favorites: ${e.message}", e)
                    return@withContext Result.failure(e)
                }
                Log.d(TAG, "Created favorites note via StorageManager")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorites: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add a folder to favorites.
     */
    suspend fun addFolderToFavorites(path: String): Result<Favorites> {
        val current = loadFavorites()
        if (path in current.folders) {
            return Result.success(current) // Already a favorite
        }
        val updated = current.copy(folders = current.folders + path)
        return saveFavorites(updated).map { updated }
    }
    
    /**
     * Remove a folder from favorites.
     */
    suspend fun removeFolderFromFavorites(path: String): Result<Favorites> {
        val current = loadFavorites()
        val updated = current.copy(folders = current.folders - path)
        return saveFavorites(updated).map { updated }
    }
    
    /**
     * Add a tag to favorites.
     */
    suspend fun addTagToFavorites(name: String): Result<Favorites> {
        val current = loadFavorites()
        if (name in current.tags) {
            return Result.success(current) // Already a favorite
        }
        val updated = current.copy(tags = current.tags + name)
        return saveFavorites(updated).map { updated }
    }
    
    /**
     * Remove a tag from favorites.
     */
    suspend fun removeTagFromFavorites(name: String): Result<Favorites> {
        val current = loadFavorites()
        val updated = current.copy(tags = current.tags - name)
        return saveFavorites(updated).map { updated }
    }
    
    /**
     * Check if a folder is a favorite.
     */
    fun isFolderFavorite(favorites: Favorites, path: String): Boolean {
        return path in favorites.folders
    }
    
    /**
     * Check if a tag is a favorite.
     */
    fun isTagFavorite(favorites: Favorites, name: String): Boolean {
        return name in favorites.tags
    }
    
    /**
     * Parse favorites from markdown content.
     * Reads lists from ## Folders and ## Tags sections in the body.
     */
    private fun parseFavorites(content: String): Favorites {
        val folders = mutableListOf<String>()
        val tags = mutableListOf<String>()
        
        val lines = content.lines()
        var currentSection: String? = null
        var inFrontmatter = false
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Skip frontmatter
            if (trimmed == "---") {
                inFrontmatter = !inFrontmatter
                continue
            }
            if (inFrontmatter) continue
            
            // Detect section headers
            when {
                trimmed.equals("## Folders", ignoreCase = true) || 
                trimmed.equals("## Favorite Folders", ignoreCase = true) -> {
                    currentSection = "folders"
                }
                trimmed.equals("## Tags", ignoreCase = true) || 
                trimmed.equals("## Favorite Tags", ignoreCase = true) -> {
                    currentSection = "tags"
                }
                trimmed.startsWith("## ") -> {
                    // Another section, stop parsing current
                    currentSection = null
                }
                trimmed.startsWith("- ") && currentSection != null -> {
                    val value = trimmed.removePrefix("- ").trim()
                    if (value.isNotEmpty()) {
                        when (currentSection) {
                            "folders" -> {
                                // Convert "/" back to empty string for root folder
                                val folderPath = if (value == "/") "" else value
                                folders.add(folderPath)
                            }
                            "tags" -> tags.add(value)
                        }
                    }
                }
                trimmed.startsWith("* ") && currentSection != null -> {
                    // Also support asterisk lists
                    val value = trimmed.removePrefix("* ").trim()
                    if (value.isNotEmpty()) {
                        when (currentSection) {
                            "folders" -> {
                                val folderPath = if (value == "/") "" else value
                                folders.add(folderPath)
                            }
                            "tags" -> tags.add(value)
                        }
                    }
                }
            }
        }
        
        return Favorites(folders = folders, tags = tags)
    }
    
    /**
     * Format favorites as a human-readable markdown file.
     */
    private fun formatFavorites(favorites: Favorites): String {
        val sb = StringBuilder()
        
        // Simple frontmatter with just a title
        sb.appendLine("---")
        sb.appendLine("title: Favorites")
        sb.appendLine("---")
        sb.appendLine()
        
        // Folders section
        sb.appendLine("## Folders")
        sb.appendLine()
        if (favorites.folders.isEmpty()) {
            sb.appendLine("*No favorite folders yet.*")
        } else {
            for (folder in favorites.folders) {
                val displayName = folder.ifEmpty { "/" }
                sb.appendLine("- $displayName")
            }
        }
        sb.appendLine()
        
        // Tags section
        sb.appendLine("## Tags")
        sb.appendLine()
        if (favorites.tags.isEmpty()) {
            sb.appendLine("*No favorite tags yet.*")
        } else {
            for (tag in favorites.tags) {
                sb.appendLine("- $tag")
            }
        }
        
        return sb.toString()
    }
}
