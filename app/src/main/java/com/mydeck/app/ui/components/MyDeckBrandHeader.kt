package com.mydeck.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextOverflow
import com.mydeck.app.R

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp

@Composable
fun MyDeckBrandHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = 128.dp // 80% of 160dp
) {
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 1.2f )

    val fontHeight = with(LocalDensity.current) { titleStyle.fontSize.toDp() }
    val spacing = fontHeight * 0.8f // 80% of font height

    Row(
        modifier = modifier.offset(x = -spacing), // Shift left by spacing amount
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_brand_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(iconSize)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.offset(x = (-20).dp)
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            )

            Text(
                text = stringResource(R.string.app_name),
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
