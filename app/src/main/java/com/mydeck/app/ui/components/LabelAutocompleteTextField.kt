package com.mydeck.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mydeck.app.R

@Composable
fun LabelAutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onLabelSelected: (String) -> Unit,
    existingLabels: List<String>,
    currentLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(value, existingLabels, currentLabels) {
        if (value.isBlank()) emptyList()
        else existingLabels
            .filter { it.contains(value.trim(), ignoreCase = true) && !currentLabels.contains(it) }
            .take(5)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.detail_label_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (value.isNotBlank()) {
                    onLabelSelected(value.trim())
                }
            }),
            textStyle = MaterialTheme.typography.bodySmall
        )

        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    suggestions.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onLabelSelected(label) }
                        )
                    }
                }
            }
        }
    }
}
