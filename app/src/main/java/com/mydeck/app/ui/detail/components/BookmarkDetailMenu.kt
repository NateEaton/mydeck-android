package com.mydeck.app.ui.detail.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.mydeck.app.R
import com.mydeck.app.ui.detail.BookmarkDetailViewModel
import com.mydeck.app.ui.detail.ContentMode

@Composable
fun BookmarkDetailMenu(
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickToggleFavorite: (String, Boolean) -> Unit,
    onClickToggleArchive: (String, Boolean) -> Unit,
    onClickToggleRead: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickDeleteBookmark: (String) -> Unit,
    onShowDetails: () -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    contentMode: ContentMode = ContentMode.READER,
    onContentModeChange: (ContentMode) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 1. Add to Favorites / Remove from Favorites
            DropdownMenuItem(
                text = {
                    Text(
                        if (uiState.bookmark.isFavorite) stringResource(R.string.action_remove_from_favorites)
                        else stringResource(R.string.action_add_to_favorites)
                    )
                },
                onClick = {
                    onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        if (uiState.bookmark.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null
                    )
                }
            )

            // 2. Add to Archive / Remove from Archive
            DropdownMenuItem(
                text = {
                    Text(
                        if (uiState.bookmark.isArchived) stringResource(R.string.action_remove_from_archive)
                        else stringResource(R.string.action_add_to_archive)
                    )
                },
                onClick = {
                    onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        if (uiState.bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = null
                    )
                }
            )

            // 3. Is Read (moved under archive)
            DropdownMenuItem(
                text = {
                    Text(
                        if (uiState.bookmark.isRead) stringResource(R.string.action_mark_unread)
                        else stringResource(R.string.action_mark_read)
                    )
                },
                onClick = {
                    onClickToggleRead(uiState.bookmark.bookmarkId, !uiState.bookmark.isRead)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        if (uiState.bookmark.isRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null
                    )
                }
            )

            if (uiState.bookmark.hasContent) {
                // Thin light divider between read and View original
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // 4. View Article / View Original (article and video types)
                if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (contentMode == ContentMode.READER) stringResource(R.string.action_view_original)
                                else stringResource(R.string.action_view_article)
                            )
                        },
                        onClick = {
                            onContentModeChange(if (contentMode == ContentMode.READER) ContentMode.ORIGINAL else ContentMode.READER)
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(
                                if (contentMode == ContentMode.READER) Icons.Default.Language else Icons.Outlined.Description,
                                contentDescription = null
                            )
                        }
                    )
                } else if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (contentMode == ContentMode.READER) stringResource(R.string.action_view_original)
                                else stringResource(R.string.action_view_video)
                            )
                        },
                        onClick = {
                            onContentModeChange(if (contentMode == ContentMode.READER) ContentMode.ORIGINAL else ContentMode.READER)
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(
                                if (contentMode == ContentMode.READER) Icons.Default.Language else Icons.Default.Movie,
                                contentDescription = null
                            )
                        }
                    )
                } else if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (contentMode == ContentMode.READER) stringResource(R.string.action_view_original)
                                else stringResource(R.string.action_view_photograph)
                            )
                        },
                        onClick = {
                            onContentModeChange(if (contentMode == ContentMode.READER) ContentMode.ORIGINAL else ContentMode.READER)
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(
                                if (contentMode == ContentMode.READER) Icons.Default.Language else Icons.Default.Image,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            // Share Link
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                onClick = {
                    onClickShareBookmark(uiState.bookmark.url)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                }
            )

            // 5. Details (changed from Bookmark Details)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_dialog_title)) },
                onClick = {
                    onShowDetails()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                }
            )

            // Thin light divider between Details and Delete
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // 6. Delete
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    onClickDeleteBookmark(uiState.bookmark.bookmarkId)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            )
        }
    }
}
