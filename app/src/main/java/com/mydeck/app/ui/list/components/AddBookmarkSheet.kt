package com.mydeck.app.ui.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.list.SaveAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookmarkBottomSheet(
    url: String,
    title: String,
    labels: List<String>,
    urlError: String? = null,
    isCreateEnabled: Boolean,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit,
    onAction: (SaveAction) -> Unit, // Kept for consistency if needed, though simpler callback might suffice
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AddBookmarkSheetContent(
            url = url,
            title = title,
            urlError = urlError,
            isCreateEnabled = isCreateEnabled,
            labels = labels,
            onUrlChange = onUrlChange,
            onTitleChange = onTitleChange,
            onLabelsChange = onLabelsChange,
            onCreateBookmark = onCreateBookmark,
            onAction = onAction
        )
    }
}

@Composable
private fun AddBookmarkSheetContent(
    url: String,
    title: String,
    urlError: String? = null,
    isCreateEnabled: Boolean,
    labels: List<String>,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit,
    onAction: (SaveAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp) // Extra padding for bottom sheet
    ) {
        Text(
            text = stringResource(R.string.add_bookmark),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.url)) },
            modifier = Modifier.fillMaxWidth(),
            isError = urlError != null,
            supportingText = urlError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
             keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = labels.joinToString(", "),
            onValueChange = { input -> 
                // Store as-is without splitting - will be committed on Done action
                onLabelsChange(if (input.isBlank()) emptyList() else listOf(input))
            },
            label = { Text(stringResource(R.string.bookmark_labels)) },
             supportingText = { Text("Press Done to add label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
             keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
             keyboardActions = KeyboardActions(
                onDone = {
                    if (isCreateEnabled) onCreateBookmark()
                }
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreateBookmark,
            enabled = isCreateEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.save))
        }
    }
}
