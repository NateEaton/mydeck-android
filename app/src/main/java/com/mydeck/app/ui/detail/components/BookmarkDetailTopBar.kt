package com.mydeck.app.ui.detail.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    onShowHighlights: () -> Unit,
    onRefreshContent: () -> Unit,
    contentMode: ContentMode,
    onClickToggleRead: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickDeleteBookmark: (String) -> Unit,
    onArticleSearchActivate: () -> Unit,
    onClickOpenInBrowser: (String) -> Unit,
    onContentModeChange: (ContentMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    scrollState: ScrollState? = null,
    onScrollToTop: () -> Unit = {},
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
            scrollBehavior = scrollBehavior,
            title = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (scrollState != null && scrollState.value > 0) {
                                onScrollToTop()
                            }
                        }
                ) { }
            },
            navigationIcon = {
                IconButton(onClick = onClickBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            actions = {
                if ((uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO) &&
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
                            contentDescription = stringResource(R.string.action_find_in_page)
                        )
                    }
                }
                BookmarkDetailMenu(
                    uiState = uiState,
                    onClickToggleFavorite = onClickToggleFavorite,
                    onClickToggleArchive = onClickToggleArchive,
                    onClickToggleRead = onClickToggleRead,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickDeleteBookmark = onClickDeleteBookmark,
                    onShowDetails = onShowDetails,
                    onShowHighlights = onShowHighlights,
                    onRefreshContent = onRefreshContent,
                    onClickOpenInBrowser = onClickOpenInBrowser,
                    contentMode = contentMode,
                    onContentModeChange = onContentModeChange
                )
            }
        )
    }
}
