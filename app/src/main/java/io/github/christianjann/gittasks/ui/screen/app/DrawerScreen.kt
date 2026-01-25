package io.github.christianjann.gittasks.ui.screen.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.room.Embedded
import io.github.christianjann.gittasks.BuildConfig
import io.github.christianjann.gittasks.MyApp
import io.github.christianjann.gittasks.R
import io.github.christianjann.gittasks.manager.SyncState
import io.github.christianjann.gittasks.data.room.Note
import io.github.christianjann.gittasks.data.room.NoteFolder
import io.github.christianjann.gittasks.helper.Favorites
import io.github.christianjann.gittasks.ui.component.CustomDropDown
import io.github.christianjann.gittasks.ui.component.CustomDropDownModel
import io.github.christianjann.gittasks.ui.component.GetStringDialog
import io.github.christianjann.gittasks.ui.component.SimpleIcon
import io.github.christianjann.gittasks.ui.component.SimpleSpacer
import io.github.christianjann.gittasks.ui.model.DrawerMode
import io.github.christianjann.gittasks.ui.theme.IconDefaultSize
import io.github.christianjann.gittasks.ui.theme.LocalSpaces
import io.github.christianjann.gittasks.utils.getParentPath
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect


private const val TAG = "DrawerScreen"

data class DrawerFolderModel(
    @Embedded val noteFolder: NoteFolder,
    val noteCount: Int,
    val hasChildren: Boolean,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowNFoldersNavigation(
    currentPath: String,
    openFolder: (String) -> Unit,
    createNoteFolder: (relativeParentPath: String, name: String) -> Boolean,
    drawerMode: DrawerMode,
    onCycleMode: () -> Unit,
    noteBeingMoved: Note?,
    notesBeingMovedCount: Int = 0,
    onMoveNoteToFolder: (String) -> Unit,
    onCancelMove: () -> Unit,
    onHomeClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val isMovingNotes = noteBeingMoved != null || notesBeingMovedCount > 0
    val containers = if (currentPath.isEmpty()) emptyList() else currentPath.split('/')

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(
                onClick = onHomeClick
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                )
            }
        },
        title = {
            LazyRow(
                modifier = Modifier
            ) {

                itemsIndexed(containers) { index, item ->

                    if (index != 0) Text(
                        text = "/",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    Text(
                        modifier = Modifier
                            .clickable {
                                val containersPart = containers.slice(0..index).iterator()
                                var path = ""

                                for (folder in containersPart) {
                                    path += if (containersPart.hasNext()) {
                                        "$folder/"
                                    } else {
                                        folder
                                    }
                                }
                                openFolder(path)
                            },
                        text = item,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }
            }
        },
        actions = {
            if (isMovingNotes) {
                IconButton(onClick = { onMoveNoteToFolder(currentPath) }) {
                    SimpleIcon(
                        imageVector = Icons.Filled.Check
                    )
                }
                IconButton(onClick = onCancelMove) {
                    SimpleIcon(
                        imageVector = Icons.Filled.Close
                    )
                }
            } else {
                // Create folder button - only shown in Folders mode
                if (drawerMode == DrawerMode.Folders) {
                    val showCreateNewFolder = rememberSaveable {
                        mutableStateOf(false)
                    }

                    IconButton(onClick = {
                        showCreateNewFolder.value = true
                    }) {
                        SimpleIcon(
                            imageVector = Icons.Filled.CreateNewFolder
                        )
                    }

                    GetStringDialog(
                        expanded = showCreateNewFolder,
                        label = stringResource(R.string.new_folder_label),
                        actionText = stringResource(R.string.create_new_folder),
                        unExpandedOnValidation = false
                    ) {
                        if (createNoteFolder(currentPath, it)) {
                            showCreateNewFolder.value = false
                        }
                    }
                }

                // Mode cycle button - cycles through Folders -> Tags -> Favorites -> Folders
                IconButton(onClick = onCycleMode) {
                    SimpleIcon(
                        imageVector = when (drawerMode) {
                            DrawerMode.Folders -> Icons.Filled.Folder
                            DrawerMode.Tags -> Icons.Rounded.Tag
                            DrawerMode.Favorites -> Icons.Filled.Star
                        }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DrawerScreen(
    drawerState: DrawerState,
    currentNoteFolderRelativePath: String,
    drawerFolders: List<DrawerFolderModel>,
    openFolder: (String) -> Unit,
    deleteFolder: (NoteFolder) -> Unit,
    createNoteFolder: (relativeParentPath: String, name: String) -> Boolean,
    allTags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    noteBeingMoved: Note?,
    notesBeingMovedCount: Int = 0,
    onMoveNoteToFolder: (String) -> Unit,
    onCancelMove: () -> Unit,
    syncState: SyncState,
    favorites: Favorites,
    onLoadFavorites: () -> Unit,
    onAddFolderToFavorites: (String) -> Unit,
    onRemoveFolderFromFavorites: (String) -> Unit,
    onAddTagToFavorites: (String) -> Unit,
    onRemoveTagFromFavorites: (String) -> Unit,
    drawerMode: DrawerMode,
    onDrawerModeChange: (DrawerMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
) {

    val isMovingNotes = noteBeingMoved != null || notesBeingMovedCount > 0

    // Switch to folder mode when starting to move a note
    LaunchedEffect(noteBeingMoved, notesBeingMovedCount) {
        if (isMovingNotes && drawerMode != DrawerMode.Folders) {
            onDrawerModeChange(DrawerMode.Folders)
            onTagSelected(null)
        }
    }

    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen || currentNoteFolderRelativePath.isNotEmpty()) {
        if (currentNoteFolderRelativePath.isEmpty()) {
            scope.launch { drawerState.close() }
        } else {
            openFolder(getParentPath(currentNoteFolderRelativePath))
        }
    }

    // Auto-close drawer when navigating to a leaf folder (no subfolders)
    LaunchedEffect(drawerFolders) {
        if (currentNoteFolderRelativePath.isNotEmpty() && drawerFolders.isEmpty() && !isMovingNotes && drawerMode == DrawerMode.Folders) {
            scope.launch { drawerState.close() }
        }
    }

    Scaffold(
        topBar = {
            RowNFoldersNavigation(
                currentPath = currentNoteFolderRelativePath,
                openFolder = openFolder,
                createNoteFolder = createNoteFolder,
                drawerMode = drawerMode,
                onCycleMode = { 
                    val newMode = when (drawerMode) {
                        DrawerMode.Folders -> DrawerMode.Tags
                        DrawerMode.Tags -> DrawerMode.Favorites
                        DrawerMode.Favorites -> DrawerMode.Folders
                    }
                    onDrawerModeChange(newMode)
                    if (newMode == DrawerMode.Favorites) {
                        onLoadFavorites()
                    }
                    if (newMode == DrawerMode.Folders) {
                        onTagSelected(null)
                    }
                },
                noteBeingMoved = noteBeingMoved,
                notesBeingMovedCount = notesBeingMovedCount,
                onMoveNoteToFolder = onMoveNoteToFolder,
                onCancelMove = onCancelMove,
                onHomeClick = {
                    openFolder("")
                    // Keep drawer open to show folders
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {

            // bug: https://issuetracker.google.com/issues/224005027
            //AnimatedVisibility(visible = currentNoteFolderRelativePath.isNotEmpty()) {
            if (currentNoteFolderRelativePath.isNotEmpty() && drawerMode == DrawerMode.Folders) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Up button - navigate to parent folder
                    FloatingActionButton(
                        modifier = Modifier,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(20.dp),
                        onClick = {
                            openFolder(getParentPath(currentNoteFolderRelativePath))
                        }
                    ) {
                        SimpleIcon(
                            imageVector = Icons.Filled.ArrowUpward,
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    // Enter/Accept button - select current folder and close drawer
                    FloatingActionButton(
                        modifier = Modifier,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(20.dp),
                        onClick = {
                            openFolder(currentNoteFolderRelativePath)
                            scope.launch { drawerState.close() }
                        }
                    ) {
                        SimpleIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->


        val listState = rememberLazyListState()

        LazyColumn(
            modifier = Modifier
                .padding(paddingValues = paddingValues),
            state = listState,
            // Add bottom padding to prevent items from being hidden by FAB buttons
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)
        ) {

            when (drawerMode) {
                DrawerMode.Tags -> {
                item {
                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(LocalSpaces.current.smallPadding)
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.all_notes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onTagSelected(null)
                                scope.launch { drawerState.close() }
                            }
                            .padding(LocalSpaces.current.smallPadding),
                        color = if (selectedTag == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                items(allTags) { tag ->
                    val dropDownExpanded = remember { mutableStateOf(false) }
                    val clickPosition = remember { mutableStateOf(Offset.Zero) }
                    val isFavorite = tag in favorites.tags

                    Box {
                        CustomDropDown(
                            expanded = dropDownExpanded,
                            shape = MaterialTheme.shapes.medium,
                            options = listOf(
                                CustomDropDownModel(
                                    text = stringResource(
                                        if (isFavorite) R.string.remove_from_favorites 
                                        else R.string.add_to_favorites
                                    ),
                                    onClick = {
                                        if (isFavorite) {
                                            onRemoveTagFromFavorites(tag)
                                        } else {
                                            onAddTagToFavorites(tag)
                                        }
                                    }
                                ),
                            ),
                            clickPosition = clickPosition
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onLongClick = { dropDownExpanded.value = true },
                                onClick = {
                                    onTagSelected(tag)
                                    scope.launch { drawerState.close() }
                                }
                            )
                            .pointerInteropFilter {
                                clickPosition.value = Offset(it.x, it.y)
                                false
                            }
                            .padding(LocalSpaces.current.smallPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag,
                            color = if (selectedTag == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isFavorite) {
                            SimpleIcon(
                                imageVector = Icons.Filled.Star,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    }
                }

                DrawerMode.Favorites -> {
                    item {
                        Text(
                            text = stringResource(R.string.favorites),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(LocalSpaces.current.smallPadding)
                        )
                    }

                    if (favorites.folders.isEmpty() && favorites.tags.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LocalSpaces.current.smallPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.no_favorites),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_favorites_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Favorite folders section
                    if (favorites.folders.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.favorite_folders),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = LocalSpaces.current.smallPadding,
                                    top = LocalSpaces.current.smallPadding,
                                    bottom = 4.dp
                                )
                            )
                        }

                        items(favorites.folders) { folderPath ->
                            val dropDownExpanded = remember { mutableStateOf(false) }
                            val clickPosition = remember { mutableStateOf(Offset.Zero) }

                            Box {
                                CustomDropDown(
                                    expanded = dropDownExpanded,
                                    shape = MaterialTheme.shapes.medium,
                                    options = listOf(
                                        CustomDropDownModel(
                                            text = stringResource(R.string.remove_from_favorites),
                                            onClick = { onRemoveFolderFromFavorites(folderPath) }
                                        ),
                                    ),
                                    clickPosition = clickPosition
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onLongClick = { dropDownExpanded.value = true },
                                        onClick = {
                                            onTagSelected(null) // Clear tag filter when opening favorite folder
                                            openFolder(folderPath)
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                    .pointerInteropFilter {
                                        clickPosition.value = Offset(it.x, it.y)
                                        false
                                    }
                                    .padding(LocalSpaces.current.smallPadding),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SimpleIcon(
                                    modifier = Modifier.size(IconDefaultSize),
                                    imageVector = Icons.Filled.Folder
                                )
                                SimpleSpacer(width = LocalSpaces.current.smallPadding)
                                Text(
                                    text = folderPath.ifEmpty { "/" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Favorite tags section
                    if (favorites.tags.isNotEmpty()) {
                        item {
                            if (favorites.folders.isNotEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.favorite_tags),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = LocalSpaces.current.smallPadding,
                                    top = if (favorites.folders.isEmpty()) LocalSpaces.current.smallPadding else 0.dp,
                                    bottom = 4.dp
                                )
                            )
                        }

                        items(favorites.tags) { tag ->
                            val dropDownExpanded = remember { mutableStateOf(false) }
                            val clickPosition = remember { mutableStateOf(Offset.Zero) }

                            Box {
                                CustomDropDown(
                                    expanded = dropDownExpanded,
                                    shape = MaterialTheme.shapes.medium,
                                    options = listOf(
                                        CustomDropDownModel(
                                            text = stringResource(R.string.remove_from_favorites),
                                            onClick = { onRemoveTagFromFavorites(tag) }
                                        ),
                                    ),
                                    clickPosition = clickPosition
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onLongClick = { dropDownExpanded.value = true },
                                        onClick = {
                                            onTagSelected(tag)
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                    .pointerInteropFilter {
                                        clickPosition.value = Offset(it.x, it.y)
                                        false
                                    }
                                    .padding(LocalSpaces.current.smallPadding),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SimpleIcon(
                                    modifier = Modifier.size(IconDefaultSize),
                                    imageVector = Icons.Rounded.Tag
                                )
                                SimpleSpacer(width = LocalSpaces.current.smallPadding)
                                Text(
                                    text = tag,
                                    color = if (selectedTag == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                DrawerMode.Folders -> {
                val displayItems = mutableListOf<Any>()
                if (drawerFolders.isEmpty() && (syncState is SyncState.Pull || syncState is SyncState.Push)) {
                    displayItems.add("sync")
                }
                if (drawerFolders.isNotEmpty()) {
                    displayItems.addAll(drawerFolders)
                } else if (!(syncState is SyncState.Pull || syncState is SyncState.Push)) {
                    displayItems.add("fallback")
                }
                items(displayItems, key = {
                    when (it) {
                        "sync" -> "sync"
                        "fallback" -> "fallback"
                        else -> (it as DrawerFolderModel).noteFolder.id
                    }
                }) { item ->
                    when (item) {
                        "sync" -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LocalSpaces.current.smallPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.syncing_repository),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        "fallback" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LocalSpaces.current.smallPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No folders found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Go to Home",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        openFolder("")
                                    }
                                )
                            }
                        }
                        is DrawerFolderModel -> {
                            val drawerNoteFolder = item as DrawerFolderModel
                            val folderPath = drawerNoteFolder.noteFolder.relativePath
                            val isFavorite = folderPath in favorites.folders
                            Box {
                                val dropDownExpanded = remember {
                                    mutableStateOf(false)
                                }

                                val clickPosition = remember {
                                    mutableStateOf(Offset.Zero)
                                }

                                // need this box for clickPosition
                                Box {
                                    CustomDropDown(
                                        expanded = dropDownExpanded,
                                        shape = MaterialTheme.shapes.medium,
                                        options = listOf(
                                            CustomDropDownModel(
                                                text = stringResource(
                                                    if (isFavorite) R.string.remove_from_favorites 
                                                    else R.string.add_to_favorites
                                                ),
                                                onClick = {
                                                    if (isFavorite) {
                                                        onRemoveFolderFromFavorites(folderPath)
                                                    } else {
                                                        onAddFolderToFavorites(folderPath)
                                                    }
                                                }
                                            ),
                                            CustomDropDownModel(
                                                text = stringResource(R.string.delete_this_folder),
                                                onClick = {
                                                    deleteFolder(drawerNoteFolder.noteFolder)
                                                }
                                            ),
                                        ),
                                        clickPosition = clickPosition
                                    )
                                }

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onLongClick = {
                                                    dropDownExpanded.value = true
                                                },
                                                onClick = {
                                                    openFolder(drawerNoteFolder.noteFolder.relativePath)
                                                    if (!drawerNoteFolder.hasChildren && !isMovingNotes) {
                                                        scope.launch { drawerState.close() }
                                                    }
                                                }
                                            )
                                            .pointerInteropFilter {
                                                clickPosition.value = Offset(it.x, it.y)
                                                false
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {

                                            Text(
                                                text = drawerNoteFolder.noteCount.toString(),
                                                modifier = Modifier
                                                    .padding(LocalSpaces.current.smallPadding)
                                            )


                                            Row(
                                                modifier = Modifier
                                                    .padding(LocalSpaces.current.smallPadding),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                SimpleIcon(
                                                    modifier = Modifier
                                                        .size(IconDefaultSize),
                                                        imageVector = Icons.Filled.Folder
                                                )

                                                SimpleSpacer(width = LocalSpaces.current.smallPadding)

                                                Text(
                                                    text = drawerNoteFolder.noteFolder.fullName(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )

                                                if (isFavorite) {
                                                    SimpleSpacer(width = 4.dp)
                                                    SimpleIcon(
                                                        imageVector = Icons.Filled.Star,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
