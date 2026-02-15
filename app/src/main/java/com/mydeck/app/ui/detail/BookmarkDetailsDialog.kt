package com.mydeck.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mydeck.app.BuildConfig
import com.mydeck.app.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarkDetailsDialog(
    bookmark: BookmarkDetailViewModel.Bookmark,
    onDismissRequest: () -> Unit,
    onLabelsUpdate: (List<String>) -> Unit = {}
) {
    var labels by remember { mutableStateOf(bookmark.labels.toMutableList()) }
    var newLabelInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_dialog_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            if (bookmark.thumbnailSrc.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bookmark.thumbnailSrc)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // Site and Publication Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bookmark.iconSrc.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(bookmark.iconSrc)
                            .crossfade(true)
                            .build(),
                        contentDescription = "site icon",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = bookmark.siteName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Saved date
            MetadataFieldWithIcon(
                icon = Icons.Filled.Download,
                value = bookmark.createdDate,
                contentDescription = stringResource(R.string.detail_saved_date)
            )

            // Published date
            bookmark.publishedDate?.let { publishedDate ->
                MetadataFieldWithIcon(
                    icon = Icons.Filled.Publish,
                    value = publishedDate,
                    contentDescription = stringResource(R.string.detail_published_date)
                )
            }

            // Reading time
            bookmark.readingTime?.let { time ->
                if (time > 0) {
                    MetadataFieldWithIcon(
                        icon = Icons.Filled.Schedule,
                        value = stringResource(R.string.detail_about, "$time ${stringResource(R.string.detail_minutes_short)}"),
                        contentDescription = stringResource(R.string.detail_reading_time)
                    )
                }
            }

            // Authors
            val filteredAuthors = bookmark.authors.filter { 
                it.isNotBlank() && it != "false" && it != "null"
            }
            if (filteredAuthors.isNotEmpty()) {
                MetadataFieldWithIcon(
                    icon = Icons.Filled.Person,
                    value = filteredAuthors.joinToString(", "),
                    contentDescription = stringResource(R.string.detail_author)
                )
            }

            // Word count
            bookmark.wordCount?.let { count ->
                if (count > 0) {
                    MetadataFieldWithIcon(
                        icon = Icons.Filled.Subject,
                        value = stringResource(R.string.detail_words, count),
                        contentDescription = stringResource(R.string.detail_word_count)
                    )
                }
            }

            // Language (only show if different from system language)
            val systemLanguage = Locale.getDefault().language
            if (bookmark.lang.isNotBlank() &&
                bookmark.lang != "Unknown" &&
                bookmark.lang != systemLanguage) {
                val languageDisplayName = try {
                    Locale.forLanguageTag(bookmark.lang).displayLanguage
                } catch (e: Exception) {
                    bookmark.lang
                }
                MetadataFieldWithIcon(
                    icon = Icons.Filled.Language,
                    value = languageDisplayName,
                    contentDescription = stringResource(R.string.detail_language)
                )
            }

            // Description
            if (bookmark.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Labels Section
            LabelsSection(
                labels = labels,
                newLabelInput = newLabelInput,
                onNewLabelChange = { newLabelInput = it },
                onAddLabel = {
                    if (newLabelInput.isNotBlank()) {
                        val trimmedLabel = newLabelInput.trim()
                        if (trimmedLabel.isNotEmpty() && !labels.contains(trimmedLabel)) {
                            labels.add(trimmedLabel)
                            newLabelInput = ""
                            keyboardController?.hide()
                            onLabelsUpdate(labels)
                        }
                    }
                },
                onRemoveLabel = { label ->
                    labels.remove(label)
                    onLabelsUpdate(labels)
                }
            )

            // Debug Info Section (only show in debug builds)
            if (BuildConfig.DEBUG && bookmark.debugInfo.isNotBlank()) {
                DebugInfoSection(debugInfo = bookmark.debugInfo)
            }
        }
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetadataFieldWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    contentDescription: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsSection(
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

        // Existing labels
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

        // Input field for new label
        OutlinedTextField(
            value = newLabelInput,
            onValueChange = onNewLabelChange,
            placeholder = { Text(stringResource(R.string.detail_label_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onAddLabel() }
            ),
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
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    contentDescription = "Remove label",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DebugInfoSection(debugInfo: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with title and action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Debug Info",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row {
                // Copy button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(debugInfo))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy debug info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Share button
                IconButton(
                    onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, debugInfo)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Debug Info")
                        context.startActivity(shareIntent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share debug info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = debugInfo,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkDetailsDialogPreview() {
    val sampleBookmark = BookmarkDetailViewModel.Bookmark(
        bookmarkId = "1",
        createdDate = "2024-01-15",
        publishedDate = "2024-01-12",
        url = "https://example.com",
        title = "Sample Bookmark",
        siteName = "Example",
        authors = listOf("John Doe", "Jane Smith"),
        imgSrc = "",
        iconSrc = "",
        thumbnailSrc = "",
        isFavorite = false,
        isArchived = false,
        isRead = false,
        type = BookmarkDetailViewModel.Bookmark.Type.ARTICLE,
        articleContent = null,
        embed = null,
        lang = "English",
        wordCount = 1500,
        readingTime = 7,
        description = "This is a sample description for the bookmark",
        labels = listOf("tech", "android", "kotlin"),
        readProgress = 0,
        hasContent = true
    )

    BookmarkDetailsDialog(
        bookmark = sampleBookmark,
        onDismissRequest = {},
        onLabelsUpdate = {}
    )
}
