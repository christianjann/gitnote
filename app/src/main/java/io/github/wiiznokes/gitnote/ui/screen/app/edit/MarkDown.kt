package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.github.wiiznokes.gitnote.helper.FrontmatterParser
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.MarkDownVM

@Composable
fun MarkdownWithClickableCheckboxes(
    markdown: String,
    modifier: Modifier = Modifier,
    onCheckboxClick: (originalLine: String, isChecked: Boolean) -> Unit
) {
    val lines = markdown.lines()
    
    Column(modifier = modifier) {
        var currentMarkdownBlock = ""
        
        lines.forEachIndexed { index, line ->
            val checkboxPattern = Regex("""^(\s*)- \[([ xX])\] (.+)$""")
            val match = checkboxPattern.find(line)
            
            if (match != null) {
                // If we have accumulated markdown, render it first
                if (currentMarkdownBlock.isNotEmpty()) {
                    MarkdownText(
                        markdown = currentMarkdownBlock,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    currentMarkdownBlock = ""
                }
                
                // Render the checkbox line
                val indent = match.groupValues[1]
                val checkboxState = match.groupValues[2].lowercase() == "x"
                val text = match.groupValues[3]
                
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(start = (indent.length * 8).dp)
                ) {
                    Checkbox(
                        checked = checkboxState,
                        onCheckedChange = { isChecked ->
                            onCheckboxClick(line, isChecked)
                        },
                        enabled = true
                    )
                    Text(
                        text = text,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                // Accumulate non-checkbox lines
                currentMarkdownBlock += line + "\n"
            }
        }
        
        // Render any remaining markdown
        if (currentMarkdownBlock.isNotEmpty()) {
            MarkdownText(
                markdown = currentMarkdownBlock.trimEnd(),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun MarkDownContent(
    vm: MarkDownVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    isReadOnlyModeActive: Boolean,
    textContent: TextFieldValue,
    onCheckboxChangesPending: (hasChanges: Boolean, modifiedText: String?) -> Unit = { _, _ -> },
) {
    if (isReadOnlyModeActive) {
        var modifiedText by remember { mutableStateOf(textContent.text) }
        var hasUnsavedChanges by remember { mutableStateOf(false) }
        
        // Notify parent about pending changes
        LaunchedEffect(hasUnsavedChanges, modifiedText) {
            onCheckboxChangesPending(hasUnsavedChanges, if (hasUnsavedChanges) modifiedText else null)
        }
        
        val bodyText = FrontmatterParser.extractBody(modifiedText)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            MarkdownWithClickableCheckboxes(
                markdown = bodyText,
                modifier = Modifier.padding(15.dp),
                onCheckboxClick = { originalLine, isChecked ->
                    // Find and replace the line in the body text
                    val lines = bodyText.lines().toMutableList()
                    val lineIndex = lines.indexOf(originalLine)
                    if (lineIndex != -1) {
                        val checkboxPattern = Regex("""^(\s*)- \[([ xX])\] (.+)$""")
                        val match = checkboxPattern.find(originalLine)
                        if (match != null) {
                            val indent = match.groupValues[1]
                            val text = match.groupValues[3]
                            val newCheckbox = if (isChecked) "[X]" else "[ ]"
                            lines[lineIndex] = "$indent- $newCheckbox $text"
                            
                            val newBodyText = lines.joinToString("\n")
                            // Reconstruct the full text with frontmatter
                            val originalLines = modifiedText.lines()
                            if (originalLines.isNotEmpty() && originalLines[0].trim().startsWith("---")) {
                                val endIndex = originalLines.drop(1).indexOfFirst { it.trim().startsWith("---") }
                                if (endIndex != -1) {
                                    val frontmatterLines = originalLines.subList(0, endIndex + 2)
                                    modifiedText = frontmatterLines.joinToString("\n") + "\n" + newBodyText
                                } else {
                                    modifiedText = newBodyText
                                }
                            } else {
                                modifiedText = newBodyText
                            }
                            hasUnsavedChanges = true
                        }
                    }
                }
            )
        }
    } else {
        GenericTextField(
            vm = vm,
            textFocusRequester = textFocusRequester,
            onFinished = onFinished,
            textContent = textContent
        )
    }
}


@Composable
fun TextFormatRow(
    vm: MarkDownVM,
    modifier: Modifier = Modifier,
    textFormatExpanded: MutableState<Boolean>
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeight)
            .scrollable(rememberScrollState(initial = 0), orientation = Orientation.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SmallButton(
            onClick = { vm.onTitle() },
            imageVector = Icons.Default.Title,
            contentDescription = "title"
        )

        SmallButton(
            onClick = { vm.onBold() },
            imageVector = Icons.Default.FormatBold,
            contentDescription = "bold"
        )
        SmallButton(
            onClick = { vm.onItalic() },
            imageVector = Icons.Default.FormatItalic,
            contentDescription = "italic"
        )

        SmallSeparator()

        SmallButton(
            onClick = { vm.onLink() },
            imageVector = Icons.Default.Link,
            contentDescription = "link"
        )

        SmallButton(
            onClick = { vm.onCode() },
            imageVector = Icons.Default.Code,
            contentDescription = "code"
        )
        SmallButton(
            onClick = { vm.onQuote() },
            imageVector = Icons.Default.FormatQuote,
            contentDescription = "quote"
        )

        SmallSeparator()

        SmallButton(
            onClick = { vm.onUnorderedList() },
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = "unordered list"
        )
        SmallButton(
            onClick = { vm.onNumberedList() },
            imageVector = Icons.Default.FormatListNumbered,
            contentDescription = "list number"
        )
        SmallButton(
            onClick = { vm.onTaskList() },
            imageVector = Icons.Default.Checklist,
            contentDescription = "checklist"
        )


        SmallSeparator()

        SmallButton(
            onClick = {
                textFormatExpanded.value = false
            },
            imageVector = Icons.Default.Close,
            contentDescription = "close"
        )
    }
}