package app.steadmo

import android.app.Notification
import android.content.Context
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView

/**
 * What a CamAPS FX notification shows, flattened to text. "My diabetes info"
 * is a custom layout, so the values are in its views, not its extras; the
 * views are inflated and walked in layout order. Images are described (id,
 * rotation, a hash of the pixels) so the trend arrow CamAPS draws can be
 * mapped later (TODO.md).
 */
data class NotificationDump(
    val postTime: Long,
    val packageName: String,
    val channelId: String?,
    /** The channel's importance as the user set it, from the listener's ranking; null if unknown. */
    val channelImportance: Int? = null,
    val viewTexts: List<String>,
    val extrasTexts: List<String>,
    val images: List<String>,
) {
    fun describe(): String = buildString {
        appendLine("package: $packageName")
        appendLine("channel: $channelId (importance $channelImportance)")
        appendLine("views: ${viewTexts.joinToString(" | ")}")
        appendLine("extras: ${extrasTexts.joinToString(" | ")}")
        images.forEach { appendLine("image: $it") }
    }.trimEnd()

    companion object {
        fun of(context: Context, sbn: StatusBarNotification, channelImportance: Int? = null): NotificationDump {
            val notification = sbn.notification
            val texts = mutableListOf<String>()
            val images = mutableListOf<String>()
            @Suppress("DEPRECATION") // The only way to read a custom layout's content.
            val views = notification.contentView ?: notification.bigContentView
            if (views != null) walk(context, views, texts, images)
            return NotificationDump(
                postTime = sbn.postTime,
                packageName = sbn.packageName,
                channelId = notification.channelId,
                channelImportance = channelImportance,
                viewTexts = texts,
                extrasTexts = extrasTexts(notification),
                images = images,
            )
        }

        private fun walk(context: Context, views: RemoteViews, texts: MutableList<String>, images: MutableList<String>) {
            val root = try {
                views.apply(context, FrameLayout(context))
            } catch (e: RuntimeException) {
                // A layout we can't inflate leaves the extras to go on; say so in the log.
                Log.w(SteadmoApp.TAG, "Couldn't inflate the CamAPS FX notification layout", e)
                return
            }
            visit(root, texts, images)
        }

        private fun visit(view: View, texts: MutableList<String>, images: MutableList<String>) {
            if (view.visibility != View.VISIBLE) return
            when (view) {
                is TextView -> view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it }
                is ImageView -> images += describeImage(view)
                is ViewGroup -> for (i in 0 until view.childCount) visit(view.getChildAt(i), texts, images)
            }
        }

        private fun describeImage(view: ImageView): String {
            val name = if (view.id == View.NO_ID) {
                "no-id"
            } else {
                try {
                    view.resources.getResourceEntryName(view.id)
                } catch (e: android.content.res.Resources.NotFoundException) {
                    Log.d(SteadmoApp.TAG, "Image view id ${view.id} has no resource name", e)
                    "id-${view.id}"
                }
            }
            val hash = view.drawable?.let { drawable ->
                val bitmap = createBitmap(16, 16)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, 16, 16)
                drawable.draw(canvas)
                val pixels = IntArray(16 * 16)
                bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16)
                bitmap.recycle()
                Integer.toHexString(pixels.contentHashCode())
            } ?: "empty"
            return "$name rotation=${view.rotation} description=${view.contentDescription} pixels=$hash"
        }

        private fun extrasTexts(notification: Notification): List<String> {
            val extras = notification.extras ?: return emptyList()
            val keys = listOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_INFO_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
            )
            val texts = keys.mapNotNull { extras.getCharSequence(it)?.toString() }
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
            return (texts + lines).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
    }
}
