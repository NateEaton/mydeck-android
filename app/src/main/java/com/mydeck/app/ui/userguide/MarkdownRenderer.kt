package com.mydeck.app.ui.userguide

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import org.commonmark.node.*

@Composable
fun rememberMarkwon(onSectionNavigate: (fileName: String) -> Unit): Markwon {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        buildMarkwon(context, colorScheme, onSectionNavigate)
    }
}

fun buildMarkwon(
    context: Context,
    colorScheme: ColorScheme,
    onSectionNavigate: (fileName: String) -> Unit
): Markwon {
    val linkColor = colorScheme.primary.toArgb()
    val textColor = colorScheme.onSurface.toArgb()

    return Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(
            ImagesPlugin.create { plugin ->
                plugin.addSchemeHandler(FileSchemeHandler.createWithAssets(context))
            }
        )
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                // Use larger font sizes that match the reader view
                builder
                    // Heading sizes - make them more prominent but not excessive
                    .headingTextSizeMultipliers(
                        floatArrayOf(1.6f, 1.35f, 1.17f, 1.0f, 0.87f, 0.75f)
                    )
                    // Link color
                    .linkColor(linkColor)
                    // Code block styling
                    .codeBlockTextColor(colorScheme.onSurface.toArgb())
                    .codeBlockBackgroundColor(colorScheme.surfaceVariant.toArgb())
                    // Inline code styling
                    .codeTextColor(colorScheme.onSurface.toArgb())
                    .codeBackgroundColor(colorScheme.surfaceVariant.toArgb())
                    // Quote styling
                    .blockQuoteColor(colorScheme.outline.toArgb())
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    if (link.endsWith(".md")) {
                        val fileName = link.removePrefix("./")
                        onSectionNavigate(fileName)
                    }
                }
            }

        })
        .build()
}

fun applyMarkwonColors(textView: TextView, colorScheme: ColorScheme) {
    textView.setTextColor(colorScheme.onSurface.toArgb())
    textView.setLinkTextColor(colorScheme.primary.toArgb())
    // Set base font size to match bodyLarge (16sp) - keep it simple
    textView.textSize = 16f
}
