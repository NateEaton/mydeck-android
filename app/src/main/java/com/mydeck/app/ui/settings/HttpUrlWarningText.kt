package com.mydeck.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.util.openUrlInCustomTab

private const val TAILSCALE_SERVE_URL = "https://tailscale.com/docs/features/tailscale-serve"
private const val READECK_PROXY_DOCS_URL = "https://readeck.org/en/docs/configuration"

@Composable
fun HttpUrlWarningText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Text(text = stringResource(R.string.account_settings_url_http_warning))
        TextButton(
            onClick = { openUrlInCustomTab(context, TAILSCALE_SERVE_URL) },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(R.string.http_migration_tailscale_link))
        }
        TextButton(
            onClick = { openUrlInCustomTab(context, READECK_PROXY_DOCS_URL) },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(R.string.http_migration_readeck_proxy_link))
        }
    }
}
