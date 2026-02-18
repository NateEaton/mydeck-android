package com.mydeck.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.mydeck.app.ui.detail.BookmarkDetailHost
import com.mydeck.app.ui.detail.BookmarkDetailViewModel

@Composable
fun BookmarkDetailPaneHost(
    bookmarkId: String,
    showOriginal: Boolean,
    onNavigateBack: () -> Unit
) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel(key = "detail_pane")
    LaunchedEffect(bookmarkId) { viewModel.loadBookmark(bookmarkId) }
    BookmarkDetailHost(viewModel, showOriginal, onNavigateBack)
}
