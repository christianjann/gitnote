package io.github.christianjann.gittasks.helper

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object FrontmatterParser {

    private val updatedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneId.systemDefault())
    
    // ISO format for due dates: "2024-08-07T11:28:29.715170"
    private val dueDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val completedRegex = Regex("completed\\?\\s*:\\s*\\w+", RegexOption.IGNORE_CASE)
    private val dueRegex = Regex("due\\s*:\\s*.+", RegexOption.IGNORE_CASE)

    fun parseDueDate(content: String): LocalDateTime? {
        val frontmatter = extractFrontmatter(content) ?: return null
        val dueLine = frontmatter.lines().find { dueRegex.containsMatchIn(it.trim()) } ?: return null
        val dueValue = dueLine.substringAfter(":").trim()
        return try {
            LocalDateTime.parse(dueValue, dueDateFormatter)
        } catch (e: DateTimeParseException) {
            // Try parsing just the date part if time is not present
            try {
                java.time.LocalDate.parse(dueValue).atStartOfDay()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    fun setDueDate(content: String, dueDate: LocalDateTime?): String {
        val lines = content.lines()
        val currentTime = updatedFormatter.format(Instant.now())

        if (lines.isNotEmpty() && lines[0].trim().startsWith("---")) {
            val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
            if (endIndex == -1) return content

            val frontmatterLines = lines.subList(1, endIndex + 1).toMutableList()
            val bodyLines = if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size) else emptyList()

            // Find existing due line
            val dueIndex = frontmatterLines.indexOfFirst { dueRegex.containsMatchIn(it.trim()) }

            if (dueDate != null) {
                val dueDateString = "due: ${dueDateFormatter.format(dueDate)}"
                if (dueIndex != -1) {
                    frontmatterLines[dueIndex] = dueDateString
                } else {
                    // Add due date after title or at the end
                    val insertIndex = frontmatterLines.indexOfFirst { it.trim().startsWith("title:") }.takeIf { it >= 0 }?.plus(1) ?: frontmatterLines.size
                    frontmatterLines.add(insertIndex, dueDateString)
                }
            } else {
                // Remove due date if present
                if (dueIndex != -1) {
                    frontmatterLines.removeAt(dueIndex)
                }
            }

            // Update updated timestamp
            val updatedIndex = frontmatterLines.indexOfFirst { it.trim().startsWith("updated:") }
            if (updatedIndex != -1) {
                frontmatterLines[updatedIndex] = "updated: $currentTime"
            }

            return (listOf("---") + frontmatterLines + listOf("---") + bodyLines).joinToString("\n")
        } else if (dueDate != null) {
            // No frontmatter, add it with due date
            val title = "title: ${lines.firstOrNull()?.take(50) ?: "Untitled"}"
            val newFrontmatter = listOf(
                title,
                "due: ${dueDateFormatter.format(dueDate)}",
                "updated: $currentTime",
                "created: $currentTime"
            )
            return (listOf("---") + newFrontmatter + listOf("---") + lines).joinToString("\n")
        }
        return content
    }

    fun parseCompleted(content: String): Boolean {
        val frontmatter = extractFrontmatter(content) ?: return false
        val completedLine = frontmatter.lines().find { completedRegex.containsMatchIn(it.trim()) } ?: return false
        return completedLine.trim().endsWith("yes")
    }

    fun parseCompletedOrNull(content: String): Boolean? {
        val frontmatter = extractFrontmatter(content) ?: return null
        val completedLine = frontmatter.lines().find { completedRegex.containsMatchIn(it.trim()) } ?: return null
        return completedLine.trim().endsWith("yes")
    }

    fun toggleCompleted(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("---")) return content

        val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
        if (endIndex == -1) return content

        val frontmatterLines = lines.subList(1, endIndex + 1)
        val bodyLines = if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size) else emptyList()

        val currentTime = updatedFormatter.format(Instant.now())

        var newFrontmatter = frontmatterLines.map { line ->
            when {
                completedRegex.containsMatchIn(line.trim()) -> {
                    val current = line.trim().endsWith("yes")
                    val newValue = if (current) "no" else "yes"
                    line.replace(completedRegex, "completed?: $newValue")
                }
                line.trim().startsWith("updated:") -> {
                    "updated: $currentTime"
                }
                else -> line
            }
        }

        // If no completed? field, add it
        val hasCompleted = newFrontmatter.any { completedRegex.containsMatchIn(it.trim()) }
        if (!hasCompleted) {
            // Find a good place to insert, e.g., after title or at the end
            val insertIndex = newFrontmatter.indexOfFirst { it.trim().startsWith("title:") }.takeIf { it >= 0 }?.plus(1) ?: newFrontmatter.size
            newFrontmatter = newFrontmatter.toMutableList().apply {
                add(insertIndex, "completed?: yes")
            }
        }

        return (listOf("---") + newFrontmatter + listOf("---") + bodyLines).joinToString("\n")
    }

    fun removeCompleted(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("---")) return content

        val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
        if (endIndex == -1) return content

        val frontmatterLines = lines.subList(1, endIndex + 1)
        val bodyLines = if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size) else emptyList()

        val currentTime = updatedFormatter.format(Instant.now())

        val newFrontmatter = frontmatterLines.map { line ->
            when {
                completedRegex.containsMatchIn(line.trim()) -> null // remove the line
                line.trim().startsWith("updated:") -> {
                    "updated: $currentTime"
                }
                else -> line
            }
        }.filterNotNull()

        return (listOf("---") + newFrontmatter + listOf("---") + bodyLines).joinToString("\n")
    }

    fun addCompleted(content: String): String {
        val lines = content.lines()
        val currentTime = updatedFormatter.format(Instant.now())

        if (lines.isNotEmpty() && lines[0].trim().startsWith("---")) {
            // Has frontmatter, add completed? if missing
            val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
            if (endIndex == -1) return content

            val frontmatterLines = lines.subList(1, endIndex + 1)
            val bodyLines = if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size) else emptyList()

            var newFrontmatter = frontmatterLines.map { line ->
                when {
                    line.trim().startsWith("updated:") -> {
                        "updated: $currentTime"
                    }
                    else -> line
                }
            }

            // If no completed? field, add it
            val hasCompleted = newFrontmatter.any { completedRegex.containsMatchIn(it.trim()) }
            if (!hasCompleted) {
                val insertIndex = newFrontmatter.indexOfFirst { it.trim().startsWith("title:") }.takeIf { it >= 0 }?.plus(1) ?: newFrontmatter.size
                newFrontmatter = newFrontmatter.toMutableList().apply {
                    add(insertIndex, "completed?: no")
                }
            }

            return (listOf("---") + newFrontmatter + listOf("---") + bodyLines).joinToString("\n")
        } else {
            // No frontmatter, add it
            val title = "title: ${lines.firstOrNull()?.take(50) ?: "Untitled"}" // guess title from first line
            val newFrontmatter = listOf(
                title,
                "updated: $currentTime",
                "created: $currentTime",
                "completed?: no"
            )
            return (listOf("---") + newFrontmatter + listOf("---") + lines).joinToString("\n")
        }
    }

    fun extractBody(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("---")) return content
        val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
        if (endIndex == -1) return content
        return if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size).joinToString("\n") else ""
    }

    fun parseTitle(content: String): String? {
        val frontmatter = extractFrontmatter(content) ?: return null
        val lines = frontmatter.lines()
        val titleLine = lines.find { it.trim().startsWith("title:") } ?: return null
        return titleLine.substringAfter("title:").trim()
    }

    private fun extractFrontmatter(content: String): String? {
        val lines = content.lines()
        if (lines.size < 3 || !lines[0].trim().startsWith("---")) return null
        val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
        if (endIndex == -1) return null
        return lines.subList(1, endIndex + 1).joinToString("\n")
    }

    fun parseTags(content: String): List<String> {
        val frontmatter = extractFrontmatter(content) ?: return emptyList()
        val lines = frontmatter.lines()
        val tagsIndex = lines.indexOfFirst { it.trim().startsWith("tags:") }
        if (tagsIndex == -1) return emptyList()

        val tags = mutableListOf<String>()
        for (i in tagsIndex + 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("- ")) {
                tags.add(line.substring(2).trim())
            } else if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) {
                // Next key, stop
                break
            }
        }
        return tags
    }

    fun updateTags(content: String, newTags: List<String>): String {
        val lines = content.lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("---")) {
            // No frontmatter, add it if we have tags
            if (newTags.isNotEmpty()) {
                val currentTime = updatedFormatter.format(Instant.now())
                val title = "title: ${lines.firstOrNull()?.take(50) ?: "Untitled"}"
                val tagsSection = if (newTags.isNotEmpty()) {
                    listOf("tags:") + newTags.map { "  - $it" }
                } else emptyList()
                val newFrontmatter = listOf(title, "updated: $currentTime", "created: $currentTime") + tagsSection
                return (listOf("---") + newFrontmatter + listOf("---") + lines).joinToString("\n")
            }
            return content
        }

        val endIndex = lines.drop(1).indexOfFirst { it.trim().startsWith("---") }
        if (endIndex == -1) return content

        val frontmatterLines = lines.subList(1, endIndex + 1).toMutableList()
        val bodyLines = if (endIndex + 2 < lines.size) lines.subList(endIndex + 2, lines.size) else emptyList()

        val currentTime = updatedFormatter.format(Instant.now())

        // Find existing tags section
        val tagsIndex = frontmatterLines.indexOfFirst { it.trim().startsWith("tags:") }

        // Remove existing tags section
        if (tagsIndex != -1) {
            var removeEnd = tagsIndex + 1
            while (removeEnd < frontmatterLines.size) {
                val line = frontmatterLines[removeEnd].trim()
                if (line.startsWith("- ") || line.isEmpty()) {
                    removeEnd++
                } else {
                    break
                }
            }
            for (i in (tagsIndex until removeEnd).reversed()) {
                frontmatterLines.removeAt(i)
            }
        }

        // Update updated timestamp
        val updatedIndex = frontmatterLines.indexOfFirst { it.trim().startsWith("updated:") }
        if (updatedIndex != -1) {
            frontmatterLines[updatedIndex] = "updated: $currentTime"
        }

        // Add new tags section if we have tags
        if (newTags.isNotEmpty()) {
            val insertIndex = frontmatterLines.indexOfFirst { it.trim().startsWith("title:") }.takeIf { it >= 0 }?.plus(1) ?: frontmatterLines.size
            val tagsSection = listOf("tags:") + newTags.map { "  - $it" }
            frontmatterLines.addAll(insertIndex, tagsSection)
        }

        return (listOf("---") + frontmatterLines + listOf("---") + bodyLines).joinToString("\n")
    }
}