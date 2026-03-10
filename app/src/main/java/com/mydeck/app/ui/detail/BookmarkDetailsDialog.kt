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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mydeck.app.BuildConfig
import com.mydeck.app.R
import com.mydeck.app.ui.list.LabelPickerBottomSheet
import com.mydeck.app.ui.list.LabelPickerMode
import java.net.URI
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarkDetailsDialog(
    bookmark: BookmarkDetailViewModel.Bookmark,
    onDismissRequest: () -> Unit,
    onLabelsUpdate: (List<String>) -> Unit = {},
    existingLabels: List<String> = emptyList(),
    onExportDebugJson: () -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onRefreshContent: () -> Unit = {},
    canRefreshContent: Boolean = false,
    onEditMetadata: () -> Unit = {}
) {
    var labels by remember { mutableStateOf(bookmark.labels.toMutableList()) }

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

            EditableTitleRow(
                title = bookmark.title,
                onClick = onEditMetadata
            )

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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            MetadataFieldWithIcon(
                icon = Icons.Filled.Download,
                value = bookmark.createdDate,
                contentDescription = stringResource(R.string.detail_saved_date)
            )

            // Published date
            bookmark.publishedDate?.let { publishedDate ->
                MetadataFieldWithIcon(
                    icon = Icons.Filled.Event,
                    value = stringResource(R.string.detail_published_prefix, publishedDate),
                    contentDescription = stringResource(R.string.detail_published_date)
                )
            }

            // Authors
            val filteredAuthors = bookmark.authors.filter { 
                it.isNotBlank() && it != "false" && it != "null"
            }
            if (filteredAuthors.isNotEmpty()) {
                MetadataFieldWithIcon(
                    icon = Icons.Filled.Person,
                    value = stringResource(R.string.detail_by_author, filteredAuthors.joinToString(", ")),
                    contentDescription = stringResource(R.string.detail_author)
                )
            }

            val rootUrl = remember(bookmark.url) { bookmark.url.toRootUrl() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onClickOpenInBrowser(rootUrl) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = rootUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                )
            }

            bookmark.readingTime?.let { time ->
                if (time > 0) {
                    MetadataFieldWithIcon(
                        icon = Icons.Filled.Schedule,
                        value = stringResource(R.string.detail_about_minutes_read, time),
                        contentDescription = stringResource(R.string.detail_reading_time)
                    )
                }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Labels Section
            LabelsSection(
                labels = labels,
                existingLabels = existingLabels,
                onSetLabels = { updated ->
                    labels = updated.toMutableList()
                    onLabelsUpdate(labels)
                },
                onRemoveLabel = { label ->
                    labels = labels.filter { it != label }.toMutableList()
                    onLabelsUpdate(labels)
                }
            )

            if (canRefreshContent) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRefreshContent),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.action_refresh_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Debug Info Section (only show in debug builds)
            if (BuildConfig.DEBUG && bookmark.debugInfo.isNotBlank()) {
                DebugInfoSection(
                    debugInfo = bookmark.debugInfo,
                    onExportDebugJson = onExportDebugJson
                )
            }
        }
    }
}

@Composable
private fun EditableTitleRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.action_edit_metadata),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
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
    existingLabels: List<String> = emptyList(),
    onSetLabels: (List<String>) -> Unit,
    onRemoveLabel: (String) -> Unit
) {
    var showLabelPicker by remember { mutableStateOf(false) }
    val labelOptions = remember(existingLabels, labels) {
        (existingLabels + labels).distinct().associateWith { 0 }
    }

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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLabelPicker = true },
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (labels.isEmpty()) {
                        stringResource(R.string.add_labels)
                    } else {
                        stringResource(R.string.edit_labels)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (labels.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.labels_selected_count, labels.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showLabelPicker) {
            LabelPickerBottomSheet(
                labels = labelOptions,
                mode = LabelPickerMode.MultiSelect(
                    initialSelection = labels.toSet(),
                    onDone = { selectedLabels ->
                        val updatedLabels =
                            labels.filter { selectedLabels.contains(it) } +
                                selectedLabels.filterNot { labels.contains(it) }.sorted()
                        onSetLabels(updatedLabels)
                    }
                ),
                onDismiss = { showLabelPicker = false }
            )
        }
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
                    contentDescription = stringResource(R.string.remove_label),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DebugInfoSection(
    debugInfo: String,
    onExportDebugJson: () -> Unit = {}
) {
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
                // Export JSON button
                IconButton(
                    onClick = onExportDebugJson
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Export debug JSON",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

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
        publishedDateInput = "01/12/2024",
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
        textDirection = "ltr",
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
        onLabelsUpdate = {},
        onEditMetadata = {}
    )
}

private fun String.toRootUrl(): String {
    return try {
        val uri = URI(this)
        if (uri.scheme != null && uri.host != null) {
            "${uri.scheme}://${uri.host}"
        } else {
            this
        }
    } catch (_: Exception) {
        this
    }
}
