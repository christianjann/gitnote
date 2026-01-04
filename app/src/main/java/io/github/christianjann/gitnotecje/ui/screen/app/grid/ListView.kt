package io.github.christianjann.gitnotecje.ui.screen.app.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.data.room.Note
import io.github.christianjann.gitnotecje.ui.model.EditType
import io.github.christianjann.gitnotecje.ui.model.GridNote
import io.github.christianjann.gitnotecje.helper.FrontmatterParser
import io.github.christianjann.gitnotecje.ui.model.NoteViewType
import io.github.christianjann.gitnotecje.ui.model.TagDisplayMode
import io.github.christianjann.gitnotecje.ui.viewmodel.GridViewModel
import java.text.DateFormat
import java.util.Date
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
private const val TAG = "ListView"

// Custom scrollbar implementation since VerticalScrollbar is not available in current Compose version
@Composable
private fun CustomVerticalScrollbar(
    scrollState: LazyListState,
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
            val totalItems = scrollState.layoutInfo.totalItemsCount.toFloat()
            val visibleItems = scrollState.layoutInfo.visibleItemsInfo.size.toFloat()

            if (totalItems > visibleItems) {
                val thumbHeight = (canvasHeight * visibleItems / totalItems).coerceAtLeast(20f)
                val maxThumbTravel = canvasHeight - thumbHeight
                val scrollProgress = if (totalItems - visibleItems > 0) {
                    scrollState.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems)
                } else 0f
                val thumbY = scrollProgress * maxThumbTravel

                drawRoundRect(
                    color = scrollbarColor,
                    topLeft = Offset(0f, thumbY),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@Composable
internal fun NoteListView(
    gridNotes: LazyPagingItems<GridNote>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    selectedNotes: List<Note>,
    showFullPathOfNotes: Boolean,
    showFullTitleInListView: Boolean,
    tagDisplayMode: TagDisplayMode,
    noteViewType: NoteViewType,
    onEditClick: (Note, EditType) -> Unit,
    vm: GridViewModel,
    showScrollbars: Boolean,
) {

    Box(modifier = modifier) {
        if (gridNotes.itemCount == 0) {
            // Empty state hint
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.no_notes_found),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_state_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Spacer(modifier = Modifier.height(topSpacerHeight))
                }

                items(
                    count = gridNotes.itemCount,
                    key = { index -> 
                        val note = gridNotes[index]?.note
                        if (note != null) {
                            "${note.id}_${note.content.hashCode()}"
                        } else {
                            index.toString()
                        }
                    }
                ) { index ->
                    val gridNote = gridNotes[index]
                    if (gridNote != null) {
                        //Log.d(TAG, "Recomposing list item for note ${gridNote.note.id} with content hash ${gridNote.note.content.hashCode()}")
                        NoteListRow(
                            gridNote = gridNote,
                            vm = vm,
                            onEditClick = onEditClick,
                            selectedNotes = selectedNotes,
                            showFullPathOfNotes = showFullPathOfNotes,
                            showFullTitleInListView = showFullTitleInListView,
                            tagDisplayMode = tagDisplayMode,
                            noteViewType = noteViewType,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(topBarHeight + 10.dp))
                }
            }

            if (showScrollbars) {
                CustomVerticalScrollbar(
                    scrollState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun NoteListRow(
    gridNote: GridNote,
    vm: GridViewModel,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: List<Note>,
    showFullPathOfNotes: Boolean,
    showFullTitleInListView: Boolean,
    tagDisplayMode: TagDisplayMode,
    noteViewType: NoteViewType,
) {
    val dropDownExpanded = remember { mutableStateOf(false) }
    val clickPosition = remember { mutableStateOf(Offset.Zero) }

    val formattedDate = remember(gridNote.note.lastModifiedTimeMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(gridNote.note.lastModifiedTimeMillis))
    }

    val title = if (showFullPathOfNotes || !gridNote.isUnique) {
        gridNote.note.relativePath
    } else {
        gridNote.note.nameWithoutExtension()
    }

    val rowBackground =
        if (gridNote.selected) MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onLongClick = { dropDownExpanded.value = true },
                onClick = {
                    if (selectedNotes.isEmpty()) {
                        onEditClick(gridNote.note, EditType.Update)
                    } else {
                        vm.selectNote(gridNote.note, add = !gridNote.selected)
                    }
                }
            )
            .pointerInteropFilter {
                clickPosition.value = Offset(it.x, it.y)
                false
            }
    ) {
        Box {
            NoteActionsDropdown(
                vm = vm,
                gridNote = gridNote,
                selectedNotes = selectedNotes,
                dropDownExpanded = dropDownExpanded,
                clickPosition = clickPosition
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(24.dp)) {
                    if (gridNote.completed != null) {
                        Checkbox(
                            checked = gridNote.completed!!,
                            onCheckedChange = { vm.toggleCompleted(gridNote.note) },
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showFullTitleInListView) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Display tags if enabled for current view
                    val shouldShowTags = when (tagDisplayMode) {
                        TagDisplayMode.None -> false
                        TagDisplayMode.ListOnly -> noteViewType == NoteViewType.List
                        TagDisplayMode.GridOnly -> noteViewType == NoteViewType.Grid
                        TagDisplayMode.Both -> true
                    }

                    if (shouldShowTags) {
                        val currentTagsMap = vm.currentTags.collectAsState()
                        val currentTags = currentTagsMap.value[gridNote.note.id] ?: FrontmatterParser.parseTags(gridNote.note.content)
                        if (currentTags.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentTags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = formattedDate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(80.dp)
        )
    }
}