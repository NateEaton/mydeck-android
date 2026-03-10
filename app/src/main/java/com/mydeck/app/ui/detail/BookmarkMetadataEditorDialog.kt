package com.mydeck.app.ui.detail

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.mydeck.app.R
import com.mydeck.app.domain.model.BookmarkMetadataUpdate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp

private const val TextDirectionAuto = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkMetadataEditorDialog(
    bookmark: BookmarkDetailViewModel.Bookmark,
    onDismissRequest: () -> Unit,
    onSave: (BookmarkMetadataUpdate) -> Unit
) {
    var title by remember(bookmark.bookmarkId, bookmark.title) { mutableStateOf(bookmark.title) }
    var description by remember(bookmark.bookmarkId, bookmark.description) { mutableStateOf(bookmark.description) }
    var siteName by remember(bookmark.bookmarkId, bookmark.siteName) { mutableStateOf(bookmark.siteName) }
    var authors by remember(bookmark.bookmarkId, bookmark.authors) {
        mutableStateOf(bookmark.authors.joinToString(separator = "\n"))
    }
    var published by remember(bookmark.bookmarkId, bookmark.publishedDateInput) {
        mutableStateOf(parsePublishedDate(bookmark.publishedDateInput.orEmpty()))
    }
    var lang by remember(bookmark.bookmarkId, bookmark.lang) { mutableStateOf(bookmark.lang) }
    var textDirection by remember(bookmark.bookmarkId, bookmark.textDirection) {
        mutableStateOf(bookmark.textDirection.ifBlank { TextDirectionAuto })
    }
    var textDirectionExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val descriptionFocusRequester = remember { FocusRequester() }
    val siteNameFocusRequester = remember { FocusRequester() }
    val authorsFocusRequester = remember { FocusRequester() }
    val languageFocusRequester = remember { FocusRequester() }

    val textDirectionOptions = listOf(
        TextDirectionAuto to stringResource(R.string.metadata_text_direction_auto),
        "ltr" to stringResource(R.string.metadata_text_direction_ltr),
        "rtl" to stringResource(R.string.metadata_text_direction_rtl)
    )

    fun openPublishedDatePicker() {
        focusManager.clearFocus(force = true)
        val initialDate = published?.toLocalDate() ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val localDate = LocalDate.of(year, monthOfYear + 1, dayOfMonth)
                val instant = localDate
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                published = Instant.fromEpochMilliseconds(instant.toEpochMilli())
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).apply {
            setOnDismissListener { focusManager.clearFocus(force = true) }
        }.show()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.action_edit_metadata)) },
                    actions = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            onSave(
                                BookmarkMetadataUpdate(
                                    title = title.trim(),
                                    description = description.trim(),
                                    siteName = siteName.trim(),
                                    authors = authors
                                        .lines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                    published = published,
                                    lang = lang.trim(),
                                    textDirection = textDirection.takeIf { it.isNotBlank() }
                                )
                            )
                            onDismissRequest()
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { descriptionFocusRequester.requestFocus() }
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester),
                    label = { Text(stringResource(R.string.detail_description)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { siteNameFocusRequester.requestFocus() }
                    )
                )

                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(siteNameFocusRequester),
                    label = { Text(stringResource(R.string.detail_site_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { authorsFocusRequester.requestFocus() }
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = authors,
                        onValueChange = { authors = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(authorsFocusRequester),
                        label = { Text(stringResource(R.string.detail_authors)) },
                        minLines = 3
                    )
                    Text(
                        text = stringResource(R.string.metadata_one_value_per_line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val publishedDateText = published?.let(::formatPublishedDate).orEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    OutlinedTextField(
                        value = publishedDateText,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.detail_published_date)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Event,
                                contentDescription = null
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = ::openPublishedDatePicker)
                    )
                }

                OutlinedTextField(
                    value = lang,
                    onValueChange = { lang = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(languageFocusRequester),
                    label = { Text(stringResource(R.string.detail_language)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    )
                )

                ExposedDropdownMenuBox(
                    expanded = textDirectionExpanded,
                    onExpandedChange = { textDirectionExpanded = !textDirectionExpanded }
                ) {
                    OutlinedTextField(
                        value = textDirectionOptions.first { it.first == textDirection }.second,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.detail_text_direction)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = textDirectionExpanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = textDirectionExpanded,
                        onDismissRequest = { textDirectionExpanded = false }
                    ) {
                        textDirectionOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    textDirection = value
                                    textDirectionExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parsePublishedDate(input: String): Instant? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val formatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    )

    formatters.forEach { formatter ->
        try {
            val localDate = LocalDate.parse(trimmed, formatter)
            val instant = localDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
            return Instant.fromEpochMilliseconds(instant.toEpochMilli())
        } catch (_: DateTimeParseException) {
            // Try the next supported format.
        }
    }

    return null
}

private fun Instant.toLocalDate(): LocalDate {
    return java.time.Instant
        .ofEpochMilli(toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun formatPublishedDate(instant: Instant): String {
    val localDate = instant.toLocalDate()
    return DateTimeFormatter.ofPattern("MM/dd/yyyy").format(localDate)
}
