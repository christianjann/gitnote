package io.github.christianjann.gitnotecje.ui.screen.app.grid

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import io.github.christianjann.gitnotecje.MainActivity
import io.github.christianjann.gitnotecje.data.Language
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.data.room.Note
import io.github.christianjann.gitnotecje.helper.FrontmatterParser
import io.github.christianjann.gitnotecje.ui.component.CustomDropDown
import io.github.christianjann.gitnotecje.ui.component.CustomDropDownModel
import io.github.christianjann.gitnotecje.ui.component.EditTagsDialog
import io.github.christianjann.gitnotecje.ui.model.EditType
import io.github.christianjann.gitnotecje.ui.model.FileExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.christianjann.gitnotecje.ui.model.GridNote
import io.github.christianjann.gitnotecje.ui.model.NoteViewType
import io.github.christianjann.gitnotecje.ui.model.TagDisplayMode
import io.github.christianjann.gitnotecje.ui.screen.app.DrawerScreen
import io.github.christianjann.gitnotecje.ui.viewmodel.GridViewModel
import io.github.christianjann.gitnotecje.manager.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi


@OptIn(ExperimentalCoroutinesApi::class)
private const val TAG = "GridScreen"

private const val maxOffset = -500f
internal val topBarHeight = 80.dp

internal val topSpacerHeight = topBarHeight + 40.dp + 15.dp

// Custom scrollbar implementation since VerticalScrollbar is not available in current Compose version
@Composable
private fun CustomVerticalScrollbar(
    scrollState: LazyStaggeredGridState,
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

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(
    onSettingsClick: () -> Unit,
    onEditClick: (Note, EditType) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {

    val vm: GridViewModel = viewModel()

    val tagDisplayMode by vm.prefs.tagDisplayMode.getAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val drawerScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            DrawerScreen(
                drawerState = drawerState,
                currentNoteFolderRelativePath = vm.currentNoteFolderRelativePath.collectAsState().value,
                drawerFolders = vm.drawerFolders.collectAsState().value,
                openFolder = vm::openFolder,
                deleteFolder = vm::deleteFolder,
                createNoteFolder = vm::createNoteFolder,
                allTags = vm.allTags.collectAsState().value,
                selectedTag = vm.selectedTag.collectAsState<String?>().value,
                onTagSelected = { vm.selectTag(it) },
                noteBeingMoved = vm.noteBeingMoved.collectAsState().value,
                onMoveNoteToFolder = { vm.moveNoteToFolder(it) },
                onCancelMove = { vm.cancelMoveNote() },
                syncState = vm.syncState.collectAsState().value,
                scrollBehavior = drawerScrollBehavior,
            )
        }
    }) {

        val selectedNotes by vm.selectedNotes.collectAsState()

        val noteBeingMoved by vm.noteBeingMoved.collectAsState()

        val wasMoving = remember { mutableStateOf(false) }

        LaunchedEffect(noteBeingMoved) {
            if (noteBeingMoved == null && wasMoving.value) {
                drawerState.close()
            }
            wasMoving.value = noteBeingMoved != null
        }

        LaunchedEffect(noteBeingMoved) {
            if (noteBeingMoved != null) {
                drawerState.open()
            }
        }

        if (selectedNotes.isNotEmpty()) {
            BackHandler {
                vm.unselectAllNotes()
            }
        }

        val noteViewType by vm.prefs.noteViewType.getAsState()

        val searchFocusRequester = remember { FocusRequester() }

        val fabExpanded = remember {
            mutableStateOf(false)
        }

        val offset = remember { mutableFloatStateOf(0f) }

        val showLanguageDialog = remember { mutableStateOf(false) }
        val currentLanguage by vm.prefs.language.getAsState()

        val nestedScrollConnection = rememberNestedScrollConnection(
            offset = offset,
            fabExpanded = fabExpanded,
        )

        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeContent,
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {

                if (selectedNotes.isEmpty()) {
                    FloatingActionButtons(
                        vm = vm,
                        offset = offset.floatValue,
                        onEditClick = onEditClick,
                        searchFocusRequester = searchFocusRequester,
                        expanded = fabExpanded,
                    )
                }

            }) { padding ->

            GridView(
                vm = vm,
                onEditClick = onEditClick,
                selectedNotes = selectedNotes,
                nestedScrollConnection = nestedScrollConnection,
                padding = padding,
                noteViewType = noteViewType,
                tagDisplayMode = tagDisplayMode,
            )

            TopBar(
                vm = vm,
                offset = offset.floatValue,
                selectedNotesNumber = selectedNotes.size,
                drawerState = drawerState,
                onSettingsClick = onSettingsClick,
                onShowGitLog = {
                    vm.showGitLog()
                },
                onSelectLanguage = {
                    showLanguageDialog.value = true
                },
                searchFocusRequester = searchFocusRequester,
                padding = padding,
                onReloadDatabase = {
                    vm.reloadDatabase()
                }
            )

        }

        // Git Log Dialog
        val showGitLogDialog by vm.showGitLogDialog.collectAsState()
        val gitLogEntries by vm.gitLogEntries.collectAsState()
        val isGitLogLoading by vm.isGitLogLoading.collectAsState()

        if (showGitLogDialog) {
            AlertDialog(
                onDismissRequest = { vm.hideGitLogDialog() },
                title = { Text("Git Log") },
                text = {
                    if (isGitLogLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn {
                            items(count = gitLogEntries.size) { index ->
                                val entry = gitLogEntries[index]
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Author: ${entry.author}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Date: ${entry.date}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Hash: ${entry.hash.take(8)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.hideGitLogDialog() }) {
                        Text("Close")
                    }
                }
            )
        }

        // Language Selection Dialog
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        LanguageSelectionDialog(
            showDialog = showLanguageDialog.value,
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    vm.prefs.language.update(language)
                    (context as? MainActivity)?.changeLanguage(language)
                }
                showLanguageDialog.value = false
            },
            onDismiss = {
                showLanguageDialog.value = false
            }
        )

        // Edit Tags Dialog
        val showEditTagsDialog by vm.showEditTagsDialog.collectAsState()
        val noteBeingEditedTags by vm.noteBeingEditedTags.collectAsState()
        val allTags by vm.allTags.collectAsState()
        val currentTagsMap by vm.currentTags.collectAsState()

        val editTagsExpanded = remember { mutableStateOf(false) }
        
        // Sync dialog state
        LaunchedEffect(showEditTagsDialog) {
            editTagsExpanded.value = showEditTagsDialog
        }

        // Handle external dialog dismissal
        LaunchedEffect(editTagsExpanded.value) {
            if (!editTagsExpanded.value && showEditTagsDialog) {
                vm.cancelEditTags()
            }
        }

        if (showEditTagsDialog && noteBeingEditedTags != null) {
            EditTagsDialog(
                expanded = editTagsExpanded,
                currentTags = currentTagsMap[noteBeingEditedTags!!.id] ?: FrontmatterParser.parseTags(noteBeingEditedTags!!.content),
                availableTags = allTags,
                onConfirm = { newTags ->
                    vm.updateNoteTags(noteBeingEditedTags!!, newTags)
                }
            )
        }
    }
}


@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
private fun GridView(
    vm: GridViewModel,
    nestedScrollConnection: NestedScrollConnection,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: List<Note>,
    padding: PaddingValues,
    noteViewType: NoteViewType,
    tagDisplayMode: TagDisplayMode,
) {
    val gridNotes = vm.gridNotes.collectAsLazyPagingItems<GridNote>()
    val query = vm.query.collectAsState()

    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, {
        Log.d(TAG, "pull refresh")
        vm.refresh()
    })

    val showFullPathOfNotes = vm.prefs.showFullPathOfNotes.getAsState()
    val showFullTitleInListView = vm.prefs.showFullTitleInListView.getAsState()
    val preferFrontmatterTitle = vm.prefs.preferFrontmatterTitle.getAsState()
    val showScrollbars = vm.prefs.showScrollbars.getAsState()

    Box {

        // Scrollbars added to both grid and list views for better UX

        val commonModifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
            .nestedScroll(nestedScrollConnection)

        val currentTagDisplayMode = tagDisplayMode

        when (noteViewType) {
            NoteViewType.Grid -> {
                val gridState = rememberLazyStaggeredGridState()

                LaunchedEffect(query.value) {
                    gridState.animateScrollToItem(index = 0)
                }

                GridNotesView(
                    gridNotes = gridNotes,
                    gridState = gridState,
                    modifier = commonModifier,
                    selectedNotes = selectedNotes,
                    showFullPathOfNotes = showFullPathOfNotes.value,
                    preferFrontmatterTitle = preferFrontmatterTitle.value,
                    tagDisplayMode = currentTagDisplayMode,
                    noteViewType = noteViewType,
                    onEditClick = onEditClick,
                    vm = vm,
                    showScrollbars = showScrollbars.value,
                )
            }

            NoteViewType.List -> {
                val listState = rememberLazyListState()

                LaunchedEffect(query.value) {
                    listState.animateScrollToItem(index = 0)
                }

                NoteListView(
                    gridNotes = gridNotes,
                    listState = listState,
                    modifier = commonModifier,
                    selectedNotes = selectedNotes,
                    showFullPathOfNotes = showFullPathOfNotes.value,
                    showFullTitleInListView = showFullTitleInListView.value,
                    preferFrontmatterTitle = preferFrontmatterTitle.value,
                    tagDisplayMode = currentTagDisplayMode,
                    noteViewType = noteViewType,
                    onEditClick = onEditClick,
                    vm = vm,
                    showScrollbars = showScrollbars.value,
                )
            }
        }

        // fix me: https://stackoverflow.com/questions/74594418/pullrefreshindicator-overlaps-with-scrollabletabrow
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topBarHeight + padding.calculateTopPadding()),
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            scale = true
        )
    }

}


@Composable
private fun GridNotesView(
    gridNotes: LazyPagingItems<GridNote>,
    gridState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    selectedNotes: List<Note>,
    showFullPathOfNotes: Boolean,
    preferFrontmatterTitle: Boolean,
    tagDisplayMode: TagDisplayMode,
    noteViewType: NoteViewType,
    onEditClick: (Note, EditType) -> Unit,
    vm: GridViewModel,
    showScrollbars: Boolean,
) {


    val noteMinWidth = vm.prefs.noteMinWidth.getAsState()
    val showFullNoteHeight = vm.prefs.showFullNoteHeight.getAsState()

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
                    textAlign = TextAlign.Center
                )
            }
        } else {

            LazyVerticalStaggeredGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 3.dp),
                columns = StaggeredGridCells.Adaptive(noteMinWidth.value.size.dp),
                state = gridState
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
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
                        Log.d(TAG, "Recomposing grid item for note ${gridNote.note.id} with content hash ${gridNote.note.content.hashCode()}")
                        NoteCard(
                            gridNote = gridNote,
                            vm = vm,
                            onEditClick = onEditClick,
                            selectedNotes = selectedNotes,
                            showFullPathOfNotes = showFullPathOfNotes,
                            showFullNoteHeight = showFullNoteHeight.value,
                            preferFrontmatterTitle = preferFrontmatterTitle,
                            tagDisplayMode = tagDisplayMode,
                            noteViewType = noteViewType,
                            modifier = Modifier.padding(3.dp)
                        )
                    } else {
                        // Placeholder for loading item
                        Card(
                            modifier = Modifier.padding(3.dp).height(100.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Loading...")
                            }
                        }
                    }
                }

                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(modifier = Modifier.height(topBarHeight + 10.dp))
                }
            }

            if (showScrollbars) {
                CustomVerticalScrollbar(
                    scrollState = gridState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    gridNote: GridNote,
    vm: GridViewModel,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: List<Note>,
    showFullPathOfNotes: Boolean,
    showFullNoteHeight: Boolean,
    preferFrontmatterTitle: Boolean,
    tagDisplayMode: TagDisplayMode,
    noteViewType: NoteViewType,
    modifier: Modifier = Modifier,
) {
    val dropDownExpanded = remember {
        mutableStateOf(false)
    }

    val clickPosition = remember {
        mutableStateOf(Offset.Zero)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (dropDownExpanded.value) {
            BorderStroke(
                width = 2.dp, color = MaterialTheme.colorScheme.primary
            )
        } else if (gridNote.selected) {
            BorderStroke(
                width = 2.dp, color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1000.dp)
            )
        },
        modifier = modifier
            .sizeIn(
                maxHeight = if (showFullNoteHeight) Dp.Unspecified else 500.dp
            )
            .combinedClickable(onLongClick = {
                dropDownExpanded.value = true
            }, onClick = {
                if (selectedNotes.isEmpty()) {
                    onEditClick(
                        gridNote.note, EditType.Update
                    )
                } else {
                    vm.selectNote(
                        gridNote.note, add = !gridNote.selected
                    )
                }
            })
            .pointerInteropFilter {
                clickPosition.value = Offset(it.x, it.y)
                false
            },
    ) {
        Box {

            NoteActionsDropdown(
                vm = vm,
                gridNote = gridNote,
                selectedNotes = selectedNotes,
                dropDownExpanded = dropDownExpanded,
                clickPosition = clickPosition
            )

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                val title = if (showFullPathOfNotes || !gridNote.isUnique) {
                    gridNote.note.relativePath
                } else {
                    if (preferFrontmatterTitle) {
                        FrontmatterParser.parseTitle(gridNote.note.content)?.takeIf { it.isNotBlank() } ?: gridNote.note.nameWithoutExtension()
                    } else {
                        gridNote.note.nameWithoutExtension()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    if (gridNote.completed != null) {
                        Checkbox(
                            checked = gridNote.completed!!,
                            onCheckedChange = { vm.toggleCompleted(gridNote.note) },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = title,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.tertiary
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
                    val currentTagsMap = vm.currentTags.collectAsState<Map<Int, List<String>>>()
                    val currentTags = currentTagsMap.value[gridNote.note.id] ?: FrontmatterParser.parseTags(gridNote.note.content)
                    if (currentTags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            currentTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (gridNote.note.fileExtension() is FileExtension.Md) {
//                                MarkdownText(
//                                    markdown = gridNote.note.content,
//                                    disableLinkMovementMethod = true,
//                                    isTextSelectable = false,
//                                    onLinkClicked = { }
//                                )
                    Text(
                        text = FrontmatterParser.extractBody(gridNote.note.content),
                        modifier = Modifier,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = gridNote.note.content,
                        modifier = Modifier,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoteActionsDropdown(
    vm: GridViewModel,
    gridNote: GridNote,
    selectedNotes: List<Note>,
    dropDownExpanded: MutableState<Boolean>,
    clickPosition: MutableState<Offset>,
) {

    // need this box for clickPosition
    Box {
        CustomDropDown(
            expanded = dropDownExpanded,
            shape = MaterialTheme.shapes.medium,
            options = listOf(
                CustomDropDownModel(
                    text = stringResource(R.string.delete_this_note),
                    onClick = { vm.deleteNote(gridNote.note) }),
                if (selectedNotes.isEmpty()) CustomDropDownModel(
                    text = stringResource(R.string.select_multiple_notes),
                    onClick = { vm.selectNote(gridNote.note, true) }) else null,
                CustomDropDownModel(
                    text = stringResource(R.string.move_note),
                    onClick = { vm.startMoveNote(gridNote.note) }),
                if (gridNote.completed != null) CustomDropDownModel(
                    text = stringResource(R.string.convert_to_note),
                    onClick = { vm.convertToNote(gridNote.note) }) else CustomDropDownModel(
                    text = stringResource(R.string.convert_to_task),
                    onClick = { vm.convertToTask(gridNote.note) }),
                CustomDropDownModel(
                    text = stringResource(R.string.edit_tags),
                    onClick = { vm.startEditTags(gridNote.note) }),
            ),
            clickPosition = clickPosition
        )
    }
}

// https://stackoverflow.com/questions/73079388/android-jetpack-compose-keyboard-not-close
// https://medium.com/@debdut.saha.1/top-app-bar-animation-using-nestedscrollconnection-like-facebook-jetpack-compose-b446c109ee52
// todo: fix scroll is blocked when the full size of the grid is the screen,
//  the stretching will cause tbe offset to not change
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberNestedScrollConnection(
    offset: MutableFloatState,
    fabExpanded: MutableState<Boolean>,
    scrollBehavior: TopAppBarScrollBehavior? = null,
): NestedScrollConnection {


    val keyboardController = LocalSoftwareKeyboardController.current

    return remember {
        var shouldBlock = false

        val fabConnection = object : NestedScrollConnection {
            fun calculateOffset(delta: Float): Offset {
                offset.floatValue = (offset.floatValue + delta).coerceIn(maxOffset, 0f)
                //Log.d(TAG, "calculateOffset(newOffset: ${offset.floatValue}, delta: $delta)")
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                //Log.d(TAG, "onPreScroll(available: ${available.y})")
                if (!shouldBlock) keyboardController?.hide()

                fabExpanded.value = false

                return calculateOffset(available.y)
            }

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                //Log.d(TAG, "onPostScroll(consumed: ${consumed.y}, available: ${available.y})")
                return calculateOffset(available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                shouldBlock = true
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldBlock = false
                return super.onPostFling(consumed, available)
            }

        }

        // Combine FAB connection with TopAppBar scroll behavior connection
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Call TopAppBar connection first (if exists)
                val topBarConsumed = scrollBehavior?.nestedScrollConnection?.onPreScroll(available, source) ?: Offset.Zero
                // Then call FAB connection with remaining
                val fabConsumed = fabConnection.onPreScroll(available - topBarConsumed, source)
                return topBarConsumed + fabConsumed
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Call TopAppBar connection first (if exists)
                val topBarConsumed = scrollBehavior?.nestedScrollConnection?.onPostScroll(consumed, available, source) ?: Offset.Zero
                // Then call FAB connection with remaining
                val fabConsumed = fabConnection.onPostScroll(consumed, available - topBarConsumed, source)
                return topBarConsumed + fabConsumed
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Call TopAppBar connection first (if exists)
                val topBarConsumed = scrollBehavior?.nestedScrollConnection?.onPreFling(available) ?: Velocity.Zero
                // Then call FAB connection with remaining
                val fabConsumed = fabConnection.onPreFling(available - topBarConsumed)
                return topBarConsumed + fabConsumed
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Call TopAppBar connection first (if exists)
                val topBarConsumed = scrollBehavior?.nestedScrollConnection?.onPostFling(consumed, available) ?: Velocity.Zero
                // Then call FAB connection with remaining
                val fabConsumed = fabConnection.onPostFling(consumed, available - topBarConsumed)
                return topBarConsumed + fabConsumed
            }
        }
    }
}
