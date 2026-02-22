package com.mydeck.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mydeck.app.R

@Composable
fun MyDeckBrandHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = 84.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(iconSize)
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(iconSize)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        )

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
