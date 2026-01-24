package io.github.christianjann.gittasks.ui.screen.app.edit

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import coil.ImageLoader
import coil.Coil
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.github.christianjann.gittasks.helper.FrontmatterParser
import io.github.christianjann.gittasks.ui.viewmodel.edit.MarkDownVM

@Composable
fun MarkdownWithClickableCheckboxes(
    markdown: String,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    onCheckboxClick: (originalLine: String, isChecked: Boolean) -> Unit
) {
    val lines = markdown.lines()
    
    Column(modifier = modifier) {
        var currentMarkdownBlock = ""
        
        lines.forEachIndexed { index, line ->
            val checkboxPattern = Regex("""^(\s*)- \[([ xX])\] (.+)$""")
            val imagePattern = Regex("""^!\[([^\]]*)\]\(([^)]+)\)$""")
            val checkboxMatch = checkboxPattern.find(line)
            val imageMatch = imagePattern.find(line)
            
            if (checkboxMatch != null) {
                // If we have accumulated markdown, render it first
                if (currentMarkdownBlock.isNotEmpty()) {
                    RenderMarkdownBlock(currentMarkdownBlock, imageLoader)
                    currentMarkdownBlock = ""
                }
                
                // Render the checkbox line
                val indent = checkboxMatch.groupValues[1]
                val checkboxState = checkboxMatch.groupValues[2].lowercase() == "x"
                val text = checkboxMatch.groupValues[3]
                
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
            } else if (imageMatch != null) {
                // If we have accumulated markdown, render it first
                if (currentMarkdownBlock.isNotEmpty()) {
                    RenderMarkdownBlock(currentMarkdownBlock, imageLoader)
                    currentMarkdownBlock = ""
                }
                
                // Render the image
                val altText = imageMatch.groupValues[1]
                val imageUrl = imageMatch.groupValues[2]
                
                AsyncImage(
                    model = imageUrl,
                    contentDescription = altText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    imageLoader = imageLoader ?: Coil.imageLoader(LocalContext.current)
                )
            } else {
                // Accumulate non-checkbox, non-image lines
                currentMarkdownBlock += line + "\n"
            }
        }
        
        // Render any remaining markdown
        if (currentMarkdownBlock.isNotEmpty()) {
            RenderMarkdownBlock(currentMarkdownBlock.trimEnd(), imageLoader)
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    markdown: String,
    imageLoader: ImageLoader?
) {
    MarkdownText(
        markdown = markdown,
        modifier = Modifier.padding(vertical = 2.dp),
        imageLoader = imageLoader,
        isTextSelectable = true
    )
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
        
        // Create ImageLoader for markdown images
        val context = LocalContext.current
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(
                        SvgDecoder.Factory(
                            useViewBoundsAsIntrinsicSize = true
                        )
                    )
                }
                .build()
        }
        
        // Set as default ImageLoader for Coil
        LaunchedEffect(imageLoader) {
            Coil.setImageLoader(imageLoader)
        }
        
        // Notify parent about pending changes
        LaunchedEffect(hasUnsavedChanges, modifiedText) {
            onCheckboxChangesPending(hasUnsavedChanges, if (hasUnsavedChanges) modifiedText else null)
        }
        
        val bodyText = FrontmatterParser.extractBody(modifiedText)
        
        // Get repository path and note directory for image resolution
        val repoPath by remember { mutableStateOf(vm.prefs.repoPathBlocking()) }
        val noteDirectory = remember(vm.previousNote.relativePath) {
            vm.previousNote.relativePath.substringBeforeLast('/', "")
        }
        
        // Pre-process markdown to convert relative image paths to file:// URIs
        val processedBodyText = bodyText.replace(Regex("""!\[([^\]]*)\]\(([^)]+)\)""")) { match ->
            val altText = match.groupValues[1]
            val imagePath = match.groupValues[2]
            
            // If it's already an absolute URL or file:// URI, leave it as is
            val processedPath = if (imagePath.startsWith("http://") || 
                                   imagePath.startsWith("https://") || 
                                   imagePath.startsWith("file://")) {
                imagePath
            } else {
                // Resolve relative path from the note's directory
                val resolvedPath = if (imagePath.startsWith("/")) {
                    // Absolute path within repo
                    "$repoPath$imagePath"
                } else {
                    // Relative path from note's directory
                    if (noteDirectory.isEmpty()) {
                        "$repoPath/$imagePath"
                    } else {
                        "$repoPath/$noteDirectory/$imagePath"
                    }
                }
                "file://$resolvedPath"
            }
            
            "![$altText]($processedPath)"
        }
        
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownWithClickableCheckboxes(
                    markdown = processedBodyText,
                    modifier = Modifier.padding(15.dp),
                    imageLoader = imageLoader,
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
    textFormatExpanded: MutableState<Boolean>,
    onAsset: () -> Unit = {}
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeight)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
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
            onClick = onAsset,
            imageVector = Icons.Default.Image,
            contentDescription = "asset"
        )

        SmallButton(
            onClick = {
                textFormatExpanded.value = false
            },
            imageVector = Icons.Default.Close,
            contentDescription = "close"
        )
    }
}