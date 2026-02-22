package com.mydeck.app.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydeck.app.R
import com.mydeck.app.ui.components.MyDeckBrandHeader
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun DeviceAuthorizationDialog(
    userCode: String,
    verificationUri: String,
    verificationUriComplete: String?,
    expiresAt: Long,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(expiresAt)) }

    // Update countdown timer
    LaunchedEffect(expiresAt) {
        while (timeRemaining > 0) {
            delay(1.seconds)
            timeRemaining = calculateTimeRemaining(expiresAt)
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(R.string.oauth_device_auth_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.oauth_device_auth_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Step 1: URL
                Text(
                    text = stringResource(R.string.oauth_device_auth_step1),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = verificationUri,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    copyToClipboard(context, verificationUri)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.oauth_device_auth_copy_url))
                            }

                            if (verificationUriComplete != null) {
                                Button(
                                    onClick = {
                                        openInBrowser(context, verificationUriComplete)
                                    }
                                ) {
                                    Text(stringResource(R.string.oauth_device_auth_open_browser))
                                }
                            }
                        }
                    }
                }

                // Step 2: User Code
                Text(
                    text = stringResource(R.string.oauth_device_auth_step2),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = userCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Button(
                            onClick = {
                                copyToClipboard(context, userCode)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.oauth_device_auth_copy_code))
                        }
                    }
                }

                // Status and Timer
                when {
                    timeRemaining > 0 -> {
                        Text(
                            text = stringResource(R.string.oauth_device_auth_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        
                        Text(
                            text = stringResource(R.string.oauth_device_auth_expires_in, formatTime(timeRemaining)),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.oauth_device_auth_expired),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun calculateTimeRemaining(expiresAt: Long): Long {
    val remaining = expiresAt - System.currentTimeMillis()
    return maxOf(0, remaining / 1000) // Convert to seconds
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OAuth Code", text)
    clipboard.setPrimaryClip(clip)
}

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/**
 * Full-screen version of the device authorization UI.
 * Unlike the AlertDialog version, this survives app backgrounding
 * (e.g. when the user taps "Open in Browser" and switches away).
 */
@Composable
fun DeviceAuthorizationScreen(
    userCode: String,
    verificationUri: String,
    verificationUriComplete: String?,
    expiresAt: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(expiresAt)) }

    LaunchedEffect(expiresAt) {
        while (timeRemaining > 0) {
            delay(1.seconds)
            timeRemaining = calculateTimeRemaining(expiresAt)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(96.dp))

        MyDeckBrandHeader()

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.oauth_device_auth_instructions),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1: URL
        Text(
            text = stringResource(R.string.oauth_device_auth_step1),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = verificationUri,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { copyToClipboard(context, verificationUri) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.oauth_device_auth_copy_url))
                    }
                    if (verificationUriComplete != null) {
                        Button(onClick = { openInBrowser(context, verificationUriComplete) }) {
                            Text(stringResource(R.string.oauth_device_auth_open_browser))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: User Code
        Text(
            text = stringResource(R.string.oauth_device_auth_step2),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = userCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(onClick = { copyToClipboard(context, userCode) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.oauth_device_auth_copy_code))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Status and Timer
        when {
            timeRemaining > 0 -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.oauth_device_auth_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.oauth_device_auth_expires_in, formatTime(timeRemaining)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.oauth_device_auth_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

    }
}
