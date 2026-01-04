package io.github.christianjann.gitnotecje.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import io.github.christianjann.gitnotecje.ui.utils.conditional

@Composable
fun BaseDialog(
    expanded: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    verticalScrollEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    dialogContent: @Composable ColumnScope.(MutableState<Boolean>) -> Unit
) {
    if (expanded.value) {
        Dialog(
            onDismissRequest = {
                expanded.value = false
                // Don't call onDismiss here - let the UI handle it by observing expanded state changes
            }
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .imePadding()
                    .conditional(verticalScrollEnabled) {
                        verticalScroll(rememberScrollState())
                    },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 25.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    dialogContent(expanded)
                }
            }
        }
    }

}