package com.mydeck.app.ui.detail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mydeck.app.R
import com.mydeck.app.ui.detail.BookmarkDetailViewModel
import com.mydeck.app.ui.detail.TypographyUtils

@Composable
fun BookmarkDetailHeader(
    modifier: Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickOpenUrl: (String) -> Unit,
    onTitleChanged: ((String) -> Unit)? = null
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(uiState.bookmark.title) }
    val focusRequester = remember { FocusRequester() }
    var hasFocusedOnce by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) {
            hasFocusedOnce = false
            focusRequester.requestFocus()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isEditingTitle) {
                onTitleChanged?.invoke(editedTitle)
                isEditingTitle = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val fontFamily = TypographyUtils.getFontFamily(uiState.typographySettings.fontFamily)
                if (isEditingTitle) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { newValue: String -> editedTitle = newValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasFocusedOnce = true
                                } else if (hasFocusedOnce && isEditingTitle) {
                                    onTitleChanged?.invoke(editedTitle)
                                    isEditingTitle = false
                                }
                            },
                        label = { Text(stringResource(R.string.edit_title)) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = fontFamily),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onTitleChanged?.invoke(editedTitle)
                            isEditingTitle = false
                        })
                    )
                } else {
                    Text(
                        text = uiState.bookmark.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = fontFamily),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editedTitle = uiState.bookmark.title
                                hasFocusedOnce = false
                                isEditingTitle = true
                            }
                    )
                }
            }
        }
    }
}
