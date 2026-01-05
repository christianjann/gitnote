package io.github.christianjann.gitnotecje.ui.component
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.christianjann.gitnotecje.R
import io.github.christianjann.gitnotecje.data.platform.NodeFs
import io.github.christianjann.gitnotecje.manager.AssetManager
import io.github.christianjann.gitnotecje.manager.GitManager
import kotlinx.coroutines.launch

@Composable
fun AssetManagerDialog(
    showDialog: MutableState<Boolean>,
    assetManager: AssetManager,
    repoPath: String,
    gitManager: GitManager,
    onAssetSelected: ((String) -> Unit)? = null // Callback when user selects an asset for markdown insertion
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assets by remember { mutableStateOf<List<NodeFs>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedAssetUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAssetForExport by remember { mutableStateOf<NodeFs?>(null) }
    var hasChanges by remember { mutableStateOf(false) }

    // File picker for importing assets
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedAssetUri = it }
    }

    // Handle import when URI is selected
    LaunchedEffect(selectedAssetUri) {
        selectedAssetUri?.let { uri ->
            isLoading = true
            // Get the actual filename from the URI
            val filename = getFilenameFromUri(context, uri) ?: "imported_asset_${System.currentTimeMillis()}"
            assetManager.importAsset(uri, filename, repoPath).onSuccess { assetPath ->
                hasChanges = true
                // Refresh the asset list
                assetManager.listAssets(repoPath).onSuccess { assetList ->
                    assets = assetList
                }.onFailure {
                    // Handle error - could show a toast or snackbar
                }
            }.onFailure {
                // Handle error - could show a toast or snackbar
            }
            isLoading = false
            selectedAssetUri = null // Reset
        }
    }

    // File picker for exporting assets
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destinationUri ->
            selectedAssetForExport?.let { asset ->
                scope.launch {
                    isLoading = true
                    assetManager.exportAsset(asset.fullName, destinationUri, repoPath).onSuccess {
                        // Export successful
                    }.onFailure {
                        // Handle error
                    }
                    isLoading = false
                    selectedAssetForExport = null
                }
            }
        }
    }

    LaunchedEffect(showDialog.value) {
        if (showDialog.value) {
            isLoading = true
            assetManager.listAssets(repoPath).onSuccess { assetList ->
                assets = assetList
            }.onFailure {
                // Handle error
            }
            gitManager.hasChanges().onSuccess { changes ->
                hasChanges = changes
            }.onFailure {
                Log.e("AssetManager", "Failed to check changes")
                hasChanges = false
            }
            isLoading = false
        }
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("Asset Manager") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { importLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Asset")
                        }
                    }

                    // Asset list
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (assets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    "No assets found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(assets) { asset ->
                                AssetItem(
                                    asset = asset,
                                    onSelect = { onAssetSelected?.invoke(asset.fullName) },
                                    onDelete = {
                                        scope.launch {
                                            assetManager.deleteAsset(asset.fullName, repoPath).onSuccess {
                                                hasChanges = true
                                                // Refresh the list
                                                assetManager.listAssets(repoPath).onSuccess { assetList ->
                                                    assets = assetList
                                                }
                                            }.onFailure { error ->
                                                // TODO: Show error message to user
                                                Log.e("AssetManager", "Delete failed: ${error.message}")
                                            }
                                        }
                                    },
                                    onExport = {
                                        selectedAssetForExport = asset
                                        val filename = asset.fullName.substringAfterLast('/')
                                        exportLauncher.launch(filename)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = if (hasChanges) {
                {
                    TextButton(onClick = {
                        // Discard changes
                        scope.launch {
                            gitManager.checkoutPath("assets").onSuccess {
                                hasChanges = false
                                // Refresh the asset list
                                assetManager.listAssets(repoPath).onSuccess { assetList ->
                                    assets = assetList
                                }.onFailure {
                                    Log.e("AssetManager", "Failed to refresh assets after discard")
                                }
                                showDialog.value = false
                            }.onFailure {
                                Log.e("AssetManager", "Checkout failed: ${it.message}")
                                // TODO: Show error
                            }
                        }
                    }) {
                        Text("Discard")
                    }
                }
            } else null,
            confirmButton = if (hasChanges) {
                {
                    TextButton(onClick = {
                        // Commit changes
                        scope.launch {
                            // Get current signature
                            val signature = gitManager.currentSignature()
                            if (signature != null) {
                                gitManager.commitAll(signature, "Update assets").onSuccess {
                                    // Trigger sync (push/pull)
                                    gitManager.sync(null).onSuccess {
                                        hasChanges = false
                                        // Refresh the asset list
                                        assetManager.listAssets(repoPath).onSuccess { assetList ->
                                            assets = assetList
                                        }.onFailure {
                                            Log.e("AssetManager", "Failed to refresh assets after commit")
                                        }
                                        showDialog.value = false
                                    }.onFailure {
                                        Log.e("AssetManager", "Sync failed: ${it.message}")
                                        hasChanges = false // Changes are committed even if sync fails
                                        // Refresh the asset list
                                        assetManager.listAssets(repoPath).onSuccess { assetList ->
                                            assets = assetList
                                        }.onFailure {
                                            Log.e("AssetManager", "Failed to refresh assets after sync failure")
                                        }
                                        showDialog.value = false // Still close on sync failure
                                    }
                                }.onFailure {
                                    Log.e("AssetManager", "Commit failed: ${it.message}")
                                    // TODO: Show error
                                }
                            } else {
                                Log.e("AssetManager", "No signature available")
                                // TODO: Handle no signature
                                showDialog.value = false
                            }
                        }
                    }) {
                        Text("Commit")
                    }
                }
            } else {
                {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

private fun getFilenameFromUri(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val displayNameIndex = cursor.getColumnIndex("_display_name")
            if (displayNameIndex >= 0) {
                cursor.getString(displayNameIndex)
            } else {
                null
            }
        } else {
            null
        }
    }
}

@Composable
private fun AssetItem(
    asset: NodeFs,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (asset is NodeFs.File) {
                    Text(
                        text = "${asset.fileSize()} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onExport) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Export asset",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete asset",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}