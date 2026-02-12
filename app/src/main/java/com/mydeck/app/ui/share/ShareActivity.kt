package com.mydeck.app.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import com.mydeck.app.MainActivity
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.ui.list.AddBookmarkSheet
import com.mydeck.app.ui.list.SaveAction
import com.mydeck.app.ui.list.SheetMode
import com.mydeck.app.ui.theme.MyDeckTheme
import com.mydeck.app.util.extractUrlAndTitle
import com.mydeck.app.util.isValidUrl
import com.mydeck.app.util.MAX_TITLE_LENGTH
import com.mydeck.app.worker.CreateBookmarkWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var bookmarkRepository: BookmarkRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = extractSharedText()
        if (sharedText == null) {
            Toast.makeText(this, getString(R.string.not_valid_url), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            val themeString = settingsDataStore.themeFlow
            val themeState = remember { mutableStateOf(Theme.SYSTEM) }

            LaunchedEffect(Unit) {
                themeString.collect { value ->
                    themeState.value = value?.let {
                        try { Theme.valueOf(it) } catch (_: Exception) { Theme.SYSTEM }
                    } ?: Theme.SYSTEM
                }
            }

            val themeValue = when (themeState.value) {
                Theme.SYSTEM -> if (isSystemInDarkTheme()) Theme.DARK else Theme.LIGHT
                else -> themeState.value
            }

            MyDeckTheme(theme = themeValue) {
                ShareBookmarkContent(
                    initialUrl = sharedText.first,
                    initialTitle = sharedText.second,
                    onAction = { action, url, title, labels ->
                        handleAction(action, url, title, labels)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun extractSharedText(): Pair<String, String>? {
        if (intent?.action != Intent.ACTION_SEND || intent?.type != "text/plain") {
            return null
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) return null

        val parsed = text.extractUrlAndTitle()
        return if (parsed != null && parsed.url.isValidUrl()) {
            Pair(parsed.url, parsed.title?.take(MAX_TITLE_LENGTH) ?: "")
        } else {
            null
        }
    }

    private fun handleAction(
        action: SaveAction,
        url: String,
        title: String,
        labels: List<String>
    ) {
        when (action) {
            SaveAction.ADD -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = false
                )
                finish()
            }
            SaveAction.ARCHIVE -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = true
                )
                finish()
            }
            SaveAction.VIEW -> {
                handleViewAction(url, title, labels)
            }
        }
    }

    private fun handleViewAction(url: String, title: String, labels: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bookmarkId = bookmarkRepository.createBookmark(
                    title = title,
                    url = url,
                    labels = labels
                )
                val intent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("navigateToBookmarkDetail", bookmarkId)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Timber.e(e, "Failed to create bookmark for View action")
                Toast.makeText(
                    this@ShareActivity,
                    getString(R.string.create_bookmark_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBookmarkContent(
    initialUrl: String,
    initialTitle: String,
    onAction: (SaveAction, String, String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf(initialTitle) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val urlError: Int? = if (!url.isValidUrl() && url.isNotEmpty()) {
        R.string.account_settings_url_error
    } else {
        null
    }
    val isCreateEnabled = url.isValidUrl()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            AddBookmarkSheet(
                url = url,
                title = title,
                urlError = urlError,
                isCreateEnabled = isCreateEnabled,
                labels = labels,
                onUrlChange = { url = it },
                onTitleChange = { title = it },
                onLabelsChange = { labels = it },
                onCreateBookmark = { },
                mode = SheetMode.SHARE_INTENT,
                onAction = { action ->
                    if (action == SaveAction.VIEW) {
                        isLoading = true
                    }
                    onAction(action, url, title, labels)
                },
                onInteraction = { }
            )
        }
    }
}
