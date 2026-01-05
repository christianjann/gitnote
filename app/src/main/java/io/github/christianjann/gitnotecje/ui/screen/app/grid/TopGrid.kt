package io.github.christianjann.gitnotecje.ui.screen.app.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import io.github.christianjann.gitnotecje.BuildConfig
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.manager.SyncState
import io.github.christianjann.gitnotecje.ui.component.CustomDropDown
import io.github.christianjann.gitnotecje.ui.component.CustomDropDownModel
import io.github.christianjann.gitnotecje.ui.component.SimpleIcon
import io.github.christianjann.gitnotecje.data.Language
import io.github.christianjann.gitnotecje.ui.model.NoteViewType
import io.github.christianjann.gitnotecje.ui.viewmodel.GridViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


private const val TAG = "TopGridScreen"

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun TopBar(
    vm: GridViewModel,
    padding: PaddingValues,
    offset: Float,
    selectedNotesNumber: Int,
    drawerState: DrawerState,
    onSettingsClick: () -> Unit,
    onShowGitLog: () -> Unit,
    onShowAssetManager: () -> Unit,
    onSelectLanguage: () -> Unit,
    searchFocusRequester: FocusRequester,
    onReloadDatabase: () -> Unit,
) {

    AnimatedContent(
        targetState = selectedNotesNumber == 0,
        label = "",
    ) { shouldShowSearchBar ->
        if (shouldShowSearchBar) {
            SearchBar(
                padding = padding,
                offset = offset,
                drawerState = drawerState,
                vm = vm,
                onSettingsClick = onSettingsClick,
                onShowGitLog = onShowGitLog,
                onShowAssetManager = onShowAssetManager,
                onSelectLanguage = onSelectLanguage,
                searchFocusRequester = searchFocusRequester,
                onReloadDatabase = onReloadDatabase,
            )
        } else {
            SelectableTopBar(
                padding = padding,
                vm = vm,
                selectedNotesNumber = selectedNotesNumber
            )
        }
    }
}


@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun SearchBar(
    padding: PaddingValues,
    offset: Float,
    drawerState: DrawerState,
    vm: GridViewModel,
    onSettingsClick: () -> Unit,
    onShowGitLog: () -> Unit,
    onShowAssetManager: () -> Unit,
    onSelectLanguage: () -> Unit,
    searchFocusRequester: FocusRequester,
    onReloadDatabase: () -> Unit,
) {


    val queryTextField = remember {
        mutableStateOf(
            TextFieldValue(
                text = vm.query.value,
                selection = TextRange(vm.query.value.length)
            )
        )
    }

    val focusManager = LocalFocusManager.current
    fun clearQuery() {
        queryTextField.value = TextFieldValue("")
        vm.clearQuery()
        focusManager.clearFocus()
    }

    val query = vm.query.collectAsState()
    val noteViewType = vm.prefs.noteViewType.getAsState()
    if (query.value.isNotEmpty()) {
        BackHandler {
            clearQuery()
        }
    }


    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(horizontal = 10.dp)
            .padding(top = 15.dp)
            .offset { IntOffset(x = 0, y = offset.roundToInt()) }
            .focusRequester(searchFocusRequester),
        value = queryTextField.value,
        onValueChange = {
            queryTextField.value = it
            vm.search(it.text)
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(100),
        placeholder = {
            Text(text = stringResource(R.string.search_in_notes))
        },
        singleLine = true,
        leadingIcon = {
            val scope = rememberCoroutineScope()

            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }
            ) {
                SimpleIcon(
                    imageVector = Icons.Rounded.Menu,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                val isEmpty = queryTextField.value.text.isEmpty()

                if (isEmpty) {
                    val syncState = vm.syncState.collectAsState()
                    SyncStateIcon(syncState.value) {
                        vm.consumeOkSyncState()
                    }
                }

                IconButton(
                    onClick = { vm.toggleViewType() }
                ) {
                    SimpleIcon(
                        imageVector = if (noteViewType.value == NoteViewType.Grid) {
                            Icons.AutoMirrored.Rounded.ViewList
                        } else {
                            Icons.Rounded.ViewModule
                        },
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(
                            if (noteViewType.value == NoteViewType.Grid) {
                                R.string.switch_to_list_view
                            } else {
                                R.string.switch_to_grid_view
                            }
                        )
                    )
                }

                if (isEmpty) {
                    Box {
                        val expanded = remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded.value = true }
                        ) {
                            SimpleIcon(
                                imageVector = Icons.Rounded.MoreVert,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val readOnlyMode = vm.prefs.isReadOnlyModeActive.getAsState().value
                        val debugFeaturesEnabled = vm.prefs.debugFeaturesEnabled.getAsState().value

                        @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
                        CustomDropDown(
                            expanded = expanded,
                            options = listOf(
                                CustomDropDownModel(
                                    text = stringResource(R.string.settings),
                                    onClick = onSettingsClick
                                ),
                                CustomDropDownModel(
                                    text = stringResource(R.string.select_language),
                                    onClick = onSelectLanguage
                                ),
                                CustomDropDownModel(
                                    text = stringResource(R.string.show_git_log),
                                    onClick = onShowGitLog
                                ),
                                CustomDropDownModel(
                                    text = stringResource(R.string.asset_manager),
                                    onClick = onShowAssetManager
                                ),
                                CustomDropDownModel(
                                    text = if (readOnlyMode) stringResource(
                                        R.string.read_only_mode_deactive
                                    ) else stringResource(R.string.read_only_mode_activate),
                                    onClick = {
                                        vm.viewModelScope.launch {
                                            vm.prefs.isReadOnlyModeActive.update(!readOnlyMode)
                                        }
                                    }
                                ),
                                if (BuildConfig.BUILD_TYPE != "release" || debugFeaturesEnabled) {
                                    CustomDropDownModel(
                                        text = stringResource(R.string.reload_database),
                                        onClick = onReloadDatabase
                                    )
                                } else null
                            )
                        )
                    }
                }

                if (!isEmpty) {
                    IconButton(
                        onClick = { clearQuery() }
                    ) {
                        SimpleIcon(
                            imageVector = Icons.Rounded.Close,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    )

}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun SelectableTopBar(
    padding: PaddingValues,
    vm: GridViewModel,
    selectedNotesNumber: Int
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp))
            .fillMaxWidth()
            .padding(top = padding.calculateTopPadding())
            .height(topBarHeight - 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current)
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        vm.unselectAllNotes()
                    }
                ) {
                    SimpleIcon(
                        imageVector = Icons.Rounded.Close,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = selectedNotesNumber.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    val expanded = remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            expanded.value = true
                        }
                    ) {
                        SimpleIcon(
                            imageVector = Icons.Rounded.MoreVert,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    CustomDropDown(
                        expanded = expanded,
                        options = listOf(
                            CustomDropDownModel(
                                text = pluralStringResource(
                                    R.plurals.delete_selected_notes,
                                    selectedNotesNumber
                                ),
                                onClick = { vm.deleteSelectedNotes() }
                            )
                        )
                    )
                }
            }
        }
    }
}


@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun SyncStateIcon(
    state: SyncState,
    onConsumeOkSyncState: () -> Unit
) {
    var showErrorDialog by remember { mutableStateOf(false) }
    var modifier: Modifier = Modifier

    if (state.isLoading()) {

        val infiniteTransition = rememberInfiniteTransition()
        val alpha = infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse
            )
        )

        modifier = modifier.alpha(alpha.value)
    }

    when (state) {
        is SyncState.Error -> {
            Icon(
                painter = painterResource(R.drawable.cloud_alert_24px),
                contentDescription = stringResource(R.string.sync_failed),
                modifier = modifier.clickable { showErrorDialog = true },
            )
        }

        is SyncState.Offline -> {
            Icon(
                painter = painterResource(R.drawable.cloud_alert_24px),
                contentDescription = stringResource(R.string.sync_offline_message),
                modifier = modifier.clickable { showErrorDialog = true },
            )
        }

        is SyncState.Ok -> {
            var visible by remember { mutableStateOf(!state.isConsumed) }

            LaunchedEffect(visible) {
                delay(1000)
                visible = false
                onConsumeOkSyncState()
            }

            AnimatedVisibility(
                visible = visible,
                exit = fadeOut(animationSpec = tween(durationMillis = 500))
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Sync Done",
                    modifier = modifier,
                )
            }
        }

        is SyncState.Pull -> Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = stringResource(R.string.sync_pulling_message),
            modifier = modifier.clickable { showErrorDialog = true },
        )

        is SyncState.Push -> Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = stringResource(R.string.sync_pushing_message),
            modifier = modifier.clickable { showErrorDialog = true },
        )

        is SyncState.Reloading -> Box(
            modifier = Modifier
                .size(24.dp)
                .clickable { showErrorDialog = true }
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }

        is SyncState.Opening -> Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = stringResource(R.string.opening_repository),
            modifier = modifier.clickable { showErrorDialog = true },
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(stringResource(R.string.sync_status_title)) },
            text = {
                Text(
                    when (state) {
                        is SyncState.Error -> stringResource(R.string.sync_failed)
                        is SyncState.Offline -> stringResource(R.string.sync_offline_message)
                        is SyncState.Pull -> stringResource(R.string.sync_pulling_message)
                        is SyncState.Push -> stringResource(R.string.sync_pushing_message)
                        is SyncState.Opening -> stringResource(R.string.opening_repository)
                        is SyncState.Reloading -> stringResource(R.string.reload_database)
                        else -> ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}


@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun LanguageSelectionDialog(
    showDialog: Boolean,
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.select_language))
            },
            text = {
                Column {
                    Language.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageSelected(language) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language == currentLanguage,
                                onClick = { onLanguageSelected(language) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (language) {
                                    Language.System -> stringResource(R.string.language_system)
                                    Language.English -> stringResource(R.string.language_english)
                                    Language.Czech -> stringResource(R.string.language_czech)
                                    Language.French -> stringResource(R.string.language_french)
                                    Language.PortugueseBrazilian -> stringResource(R.string.language_portuguese_brazilian)
                                    Language.Russian -> stringResource(R.string.language_russian)
                                    Language.Ukrainian -> stringResource(R.string.language_ukrainian)
                                    Language.German -> stringResource(R.string.language_german)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }
}
