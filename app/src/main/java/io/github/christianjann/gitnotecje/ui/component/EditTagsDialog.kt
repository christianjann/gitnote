package io.github.christianjann.gitnotecje.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.christianjann.gitnotecje.R

// Custom scrollbar implementation since VerticalScrollbar is not available in current Compose version
@Composable
private fun CustomVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val scrollbarWidth = 8.dp
    val scrollbarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .width(scrollbarWidth)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            val canvasHeight = size.height
            val contentHeight = scrollState.maxValue + scrollState.viewportSize
            val visibleHeight = scrollState.viewportSize

            if (contentHeight > visibleHeight) {
                val thumbHeight = (canvasHeight * visibleHeight / contentHeight).coerceAtLeast(20f)
                val maxThumbTravel = canvasHeight - thumbHeight
                val scrollProgress = if (scrollState.maxValue > 0) {
                    scrollState.value.toFloat() / scrollState.maxValue
                } else 0f
                val thumbY = scrollProgress * maxThumbTravel

                drawRoundRect(
                    color = scrollbarColor,
                    topLeft = Offset(0f, thumbY),
                    size = Size(scrollbarWidth.toPx(), thumbHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTagsDialog(
    expanded: MutableState<Boolean>,
    currentTags: List<String>,
    availableTags: List<String>,
    onConfirm: (List<String>) -> Unit
) {
    var selectedTags by remember { mutableStateOf(currentTags.toSet()) }

    // Update selectedTags when currentTags changes
    LaunchedEffect(currentTags) {
        selectedTags = currentTags.toSet()
    }

    val focusManager = LocalFocusManager.current

    BaseDialog(expanded = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .clickable { focusManager.clearFocus() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.edit_tags),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current tags section (top half)
            Text(
                text = stringResource(R.string.current_tags),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            val currentTagsScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(currentTagsScrollState)
                        .clickable(enabled = selectedTags.isEmpty()) { focusManager.clearFocus() }
                ) {
                    if (selectedTags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_tags_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.sorted().forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clickable {
                                                selectedTags = selectedTags - tag
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                CustomVerticalScrollbar(
                    scrollState = currentTagsScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Available tags section (bottom half)
            Text(
                text = stringResource(R.string.available_tags),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Search field for filtering/adding tags
            var searchText by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text(stringResource(R.string.search_or_add_tag)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            val tag = searchText.trim()
                            if (tag.isNotEmpty() && tag !in selectedTags) {
                                selectedTags = selectedTags + tag
                                searchText = ""
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            val unselectedTags = availableTags
                .filter { it !in selectedTags }
                .filter { searchText.isEmpty() || it.contains(searchText, ignoreCase = true) }
                .sorted()

            val availableTagsScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(availableTagsScrollState)
                        .clickable(enabled = unselectedTags.isEmpty()) { focusManager.clearFocus() }
                ) {
                    if (unselectedTags.isEmpty()) {
                        Text(
                            text = when {
                                availableTags.isEmpty() -> stringResource(R.string.no_available_tags)
                                searchText.isNotEmpty() && availableTags.any { it !in selectedTags } -> 
                                    stringResource(R.string.no_tags_match_search)
                                else -> stringResource(R.string.all_tags_selected)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            unselectedTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            selectedTags = selectedTags + tag
                                        }
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                CustomVerticalScrollbar(
                    scrollState = availableTagsScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        expanded.value = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        onConfirm(selectedTags.toList())
                        expanded.value = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}