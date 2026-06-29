package com.mydeck.app.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.ui.components.FilterControls
import com.mydeck.app.ui.components.rememberFilterEditorState

/**
 * Unified collection editor: a name field on top of the shared [FilterControls]. Used for creating
 * (FAB on the Collections screen, or main-list "Save as Collection") and editing a collection.
 *
 * Entry points pass the appropriate [heading]/[initialName]/[initialFilter]. When [onDelete] is
 * non-null (edit mode) a Delete action is shown alongside Save. Save is disabled until the name is
 * non-blank and the filter has no validation error. Rename is supported via the name field.
 */
// PORT: "MyDeck" brand never appears here; the editor reuses generic filter controls.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionEditorSheet(
    heading: String,
    initialName: String,
    initialFilter: FilterFormState,
    labels: Map<String, Int>,
    onSave: (name: String, filter: FilterFormState) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initialName) { mutableStateOf(initialName) }
    val filterState = rememberFilterEditorState(initialFilter)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.collection_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            FilterControls(
                state = filterState,
                labels = labels,
                // Collections can't persist the device-local "Downloaded" state or the reading-time /
                // word-count filters, so those controls are hidden here to avoid silent data loss.
                includeLocalOnlyFilters = false,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Text(stringResource(R.string.collection_delete_action)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(name.trim(), filterState.toFilterFormState()) },
                    enabled = name.isNotBlank() && !filterState.hasValidationError,
                ) { Text(stringResource(R.string.collection_editor_save)) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
