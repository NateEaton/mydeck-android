package com.mydeck.app.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import com.mydeck.app.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBookmarkSheet(
    url: String,
    title: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit,
    onArchiveBookmark: () -> Unit,
    onDismiss: () -> Unit
) {
    var newLabelInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.add_link),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = url,
            onValueChange = { onUrlChange(it) },
            isError = urlError != null,
            label = { Text(stringResource(id = R.string.url)) },
            supportingText = {
                urlError?.let { Text(text = stringResource(it)) }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = title,
            onValueChange = { onTitleChange(it) },
            label = { Text(stringResource(id = R.string.title)) },
            modifier = Modifier.fillMaxWidth()
        )

        CreateBookmarkLabelsSection(
            labels = labels,
            newLabelInput = newLabelInput,
            onNewLabelChange = { newLabelInput = it },
            onAddLabel = {
                if (newLabelInput.isNotBlank()) {
                    val newLabels = newLabelInput.split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !labels.contains(it) }
                    if (newLabels.isNotEmpty()) {
                        onLabelsChange(labels + newLabels)
                    }
                    newLabelInput = ""
                    keyboardController?.hide()
                }
            },
            onRemoveLabel = { label ->
                onLabelsChange(labels.filter { it != label })
            }
        )

        Spacer(modifier = Modifier.size(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    commitPendingLabels(newLabelInput, labels, onLabelsChange)
                    newLabelInput = ""
                    onArchiveBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.action_archive_bookmark))
            }
            Button(
                onClick = {
                    commitPendingLabels(newLabelInput, labels, onLabelsChange)
                    newLabelInput = ""
                    onCreateBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.add))
            }
        }
    }
}

private fun commitPendingLabels(
    newLabelInput: String,
    labels: List<String>,
    onLabelsChange: (List<String>) -> Unit
) {
    if (newLabelInput.isBlank()) return
    val newLabels = newLabelInput.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() && !labels.contains(it) }
    if (newLabels.isNotEmpty()) {
        onLabelsChange(labels + newLabels)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBookmarkLabelsSection(
    labels: List<String>,
    newLabelInput: String,
    onNewLabelChange: (String) -> Unit,
    onAddLabel: () -> Unit,
    onRemoveLabel: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_labels),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEach { label ->
                    LabelChip(
                        label = label,
                        onRemove = { onRemoveLabel(label) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = newLabelInput,
            onValueChange = onNewLabelChange,
            placeholder = { Text(stringResource(R.string.detail_label_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddLabel() }),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LabelChip(
    label: String,
    onRemove: () -> Unit = {}
) {
    Card(
        modifier = Modifier.padding(4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove_label),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
