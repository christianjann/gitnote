package io.github.christianjann.gitnotecje.ui.screen.app.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.christianjann.gitnotecje.MyApp
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.helper.FrontmatterParser
import io.github.christianjann.gitnotecje.manager.AssetManager
import io.github.christianjann.gitnotecje.manager.ExtensionType
import io.github.christianjann.gitnotecje.manager.GitManager
import io.github.christianjann.gitnotecje.manager.extensionType
import io.github.christianjann.gitnotecje.ui.component.AssetManagerDialog
import io.github.christianjann.gitnotecje.ui.component.EditTagsDialog
import io.github.christianjann.gitnotecje.ui.component.SimpleIcon
import io.github.christianjann.gitnotecje.ui.destination.EditParams
import io.github.christianjann.gitnotecje.ui.model.EditType
import io.github.christianjann.gitnotecje.ui.viewmodel.edit.MarkDownVM
import io.github.christianjann.gitnotecje.ui.viewmodel.edit.TextVM
import io.github.christianjann.gitnotecje.ui.viewmodel.edit.newEditViewModel
import io.github.christianjann.gitnotecje.ui.viewmodel.edit.newMarkDownVM
import kotlinx.coroutines.ExperimentalCoroutinesApi

private const val TAG = "EditScreen"



@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun EditScreen(
    editParams: EditParams,
    onFinished: () -> Unit,
) {

    val extension = editParams.fileExtension()

    val vm = when (extensionType(extension.text)) {
        ExtensionType.Text -> newEditViewModel(editParams)
        ExtensionType.Markdown -> newMarkDownVM(editParams)
        null -> throw Exception("file extension not supported, but present in the database?? $extension")
    }

    val assetManager = remember { MyApp.appModule.assetManager }
    val gitManager = remember { MyApp.appModule.gitManager }

    val showAssetManagerDialog = remember { mutableStateOf(false) }

    if (editParams is EditParams.Saved) {
        BackHandler {
            vm.shouldSaveWhenQuitting = false
            onFinished()
        }
    }

    val nameFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }

    // tricks to request focus only one time
    var lastId: Boolean by rememberSaveable { mutableStateOf(false) }
    if (!lastId) {
        lastId = true
        LaunchedEffect(null) {
            if (vm.editType == EditType.Create) {
                nameFocusRequester.requestFocus()
            }
        }
    }

    val isReadOnlyModeActive =
        !vm.shouldForceNotReadOnlyMode.value && vm.prefs.isReadOnlyModeActive.getAsState().value

    var hasPendingCheckboxChanges by remember { mutableStateOf(false) }
    var pendingCheckboxText by remember { mutableStateOf<String?>(null) }

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(15.dp)

            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vm.shouldSaveWhenQuitting = false
                            onFinished()
                        },
                    ) {
                        SimpleIcon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        )
                    }
                },
                title = {

                    TextField(
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
                        value = vm.name.value,
                        onValueChange = {
                            vm.name.value = it
                        },
                        readOnly = isReadOnlyModeActive,
                        singleLine = true,
                        placeholder = {
                            Text(text = stringResource(R.string.note_name))
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedTextColor = MaterialTheme.colorScheme.tertiary,
                            focusedContainerColor = backgroundColor,
                            unfocusedContainerColor = backgroundColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                textFocusRequester.requestFocus()
                            }
                        )
                    )
                },
                actions = {
                    IconButton(
                        onClick = { vm.startEditTags() },
                    ) {
                        SimpleIcon(
                            imageVector = Icons.Default.Tag,
                        )
                    }
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            vm.setReadOnlyMode(!isReadOnlyModeActive)
                        },
                    ) {
                        SimpleIcon(
                            imageVector = if (isReadOnlyModeActive) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.LockOpen
                            },
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // bug: https://issuetracker.google.com/issues/224005027
            //AnimatedVisibility(visible = currentNoteFolderRelativePath.isNotEmpty()) {
            if ((!isReadOnlyModeActive || hasPendingCheckboxChanges) && vm.name.value.text.isNotEmpty()) {
                FloatingActionButton(
                    modifier = Modifier
                        .padding(bottom = bottomBarHeight),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        if (hasPendingCheckboxChanges && pendingCheckboxText != null) {
                            // Apply checkbox changes and save
                            val newTextFieldValue = TextFieldValue(text = pendingCheckboxText!!)
                            vm.onValueChange(newTextFieldValue)
                            hasPendingCheckboxChanges = false
                            pendingCheckboxText = null
                        }
                        vm.save(onSuccess = onFinished)
                    }
                ) {
                    SimpleIcon(
                        imageVector = Icons.Default.Done,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            Box(
                modifier = Modifier.weight(1f)
            ) {

                val textContent = vm.content.value

                when (vm) {
                    is MarkDownVM -> {
                        MarkDownContent(
                            vm = vm,
                            textFocusRequester = textFocusRequester,
                            onFinished = onFinished,
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            textContent = textContent,
                            onCheckboxChangesPending = { hasChanges, modifiedText ->
                                hasPendingCheckboxChanges = hasChanges
                                pendingCheckboxText = modifiedText
                            }
                        )
                    }

                    else -> {
                        GenericTextField(
                            vm = vm,
                            textFocusRequester = textFocusRequester,
                            onFinished = onFinished,
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            textContent = textContent
                        )
                    }
                }
            }

            when (vm) {
                is MarkDownVM -> {
                    val textFormatExpanded =
                        rememberSaveable(isReadOnlyModeActive) { mutableStateOf(false) }

                    if (textFormatExpanded.value) {
                        TextFormatRow(vm = vm, textFormatExpanded = textFormatExpanded, onAsset = { showAssetManagerDialog.value = true })
                    } else {
                        DefaultRow(
                            vm = vm,
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            leftContent = {
                                SmallButton(
                                    onClick = {
                                        textFormatExpanded.value = true
                                    },
                                    enabled = !isReadOnlyModeActive,
                                    imageVector = Icons.Default.TextFormat,
                                    contentDescription = "text format"
                                )
                            }
                        )
                    }

                }

                else -> {
                    DefaultRow(
                        vm = vm,
                        isReadOnlyModeActive = isReadOnlyModeActive,
                    )
                }
            }
        }


    }

    val showEditTagsDialog by vm.showEditTagsDialog.collectAsState()
    val editTagsExpanded = remember { mutableStateOf(false) }
    
    // Sync the dialog state with ViewModel state
    LaunchedEffect(showEditTagsDialog) {
        editTagsExpanded.value = showEditTagsDialog
    }

    // Handle external dialog dismissal
    LaunchedEffect(editTagsExpanded.value) {
        if (!editTagsExpanded.value && showEditTagsDialog) {
            vm.cancelEditTags()
        }
    }
    
    if (showEditTagsDialog) {
        val allTags by vm.allTags.collectAsState()
        EditTagsDialog(
            expanded = editTagsExpanded,
            currentTags = FrontmatterParser.parseTags(vm.content.value.text),
            availableTags = allTags,
            onConfirm = { newTags -> vm.updateNoteTags(newTags) }
        )
    }

    if (vm is MarkDownVM) {
        AssetManagerDialog(
            showDialog = showAssetManagerDialog,
            assetManager = assetManager,
            repoPath = vm.prefs.repoPathBlocking(),
            gitManager = gitManager,
            onAssetSelected = { assetPath ->
                // Insert markdown link: ![alt](relative/path/to/assets/filename)
                val filename = assetPath.substringAfterLast('/')
                val altText = filename.substringBeforeLast('.')
                val noteDirectory = vm.previousNote.relativePath.substringBeforeLast('/', "")
                val relativeAssetsPath = if (noteDirectory.isEmpty()) {
                    "assets"
                } else {
                    "../".repeat(noteDirectory.split('/').size) + "assets"
                }
                val markdownLink = "![$altText]($relativeAssetsPath/$filename)"
                vm.insertText(markdownLink)
            }
        )
    }
}

@Composable
fun GenericTextField(
    vm: TextVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    isReadOnlyModeActive: Boolean = false,
    textContent: TextFieldValue,
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                textFocusRequester.requestFocus()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocusRequester),
                value = textContent,
                onValueChange = { vm.onValueChange(it) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { vm.save(onSuccess = onFinished) }
                ),
                readOnly = isReadOnlyModeActive
            )
            Spacer(modifier = Modifier.height(200.dp))
        }
    }
}