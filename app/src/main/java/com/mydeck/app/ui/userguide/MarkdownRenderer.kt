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
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler

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
                builder
                    .headingTextSizeMultipliers(
                        floatArrayOf(1.6f, 1.35f, 1.17f, 1.0f, 0.87f, 0.75f)
                    )
                    .headingBreakHeight(0)
                    .linkColor(linkColor)
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
}
