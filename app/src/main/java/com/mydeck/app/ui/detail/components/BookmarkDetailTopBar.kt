package com.mydeck.app.ui.detail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mydeck.app.R
import com.mydeck.app.ui.detail.BookmarkDetailViewModel
import com.mydeck.app.ui.detail.ContentMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailTopBar(
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState,
    onArticleSearchQueryChange: (String) -> Unit,
    onArticleSearchPrevious: () -> Unit,
    onArticleSearchNext: () -> Unit,
    onArticleSearchDeactivate: () -> Unit,
    onClickBack: () -> Unit,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickToggleFavorite: (String, Boolean) -> Unit,
    onClickToggleArchive: (String, Boolean) -> Unit,
    onShowTypographyPanel: () -> Unit,
    onShowDetails: () -> Unit,
    contentMode: ContentMode,
    onClickToggleRead: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickDeleteBookmark: (String) -> Unit,
    onArticleSearchActivate: () -> Unit,
    onClickOpenInBrowser: (String) -> Unit,
    onContentModeChange: (ContentMode) -> Unit
) {
    if (articleSearchState.isActive) {
        ArticleSearchBar(
            query = articleSearchState.query,
            currentMatch = articleSearchState.currentMatch,
            totalMatches = articleSearchState.totalMatches,
            onQueryChange = onArticleSearchQueryChange,
            onPreviousMatch = onArticleSearchPrevious,
            onNextMatch = onArticleSearchNext,
            onClose = onArticleSearchDeactivate
        )
    } else {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onClickBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                }) {
                    Icon(
                        imageVector = if (uiState.bookmark.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite)
                    )
                }
                IconButton(onClick = {
                    onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                }) {
                    Icon(
                        imageVector = if (uiState.bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = stringResource(R.string.action_archive)
                    )
                }
                if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE &&
                    contentMode == ContentMode.READER) {
                    IconButton(onClick = { onShowTypographyPanel() }) {
                        Icon(
                            imageVector = Icons.Outlined.FormatSize,
                            contentDescription = stringResource(R.string.action_typography_settings)
                        )
                    }
                    IconButton(onClick = { onArticleSearchActivate() }) {
                        Icon(
                            imageVector = Icons.Outlined.FindInPage,
                            contentDescription = stringResource(R.string.action_search_in_article)
                        )
                    }
                }
                BookmarkDetailMenu(
                    uiState = uiState,
                    onClickToggleRead = onClickToggleRead,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickDeleteBookmark = onClickDeleteBookmark,
                    onShowDetails = onShowDetails,
                    onClickOpenInBrowser = onClickOpenInBrowser,
                    contentMode = contentMode,
                    onContentModeChange = onContentModeChange
                )
            }
        )
    }
}
