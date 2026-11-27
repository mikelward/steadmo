package app.steadmo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.steadmo.core.SteadmoLog
import app.steadmo.widget.WidgetActionActivity

/**
 * Long-press shortcuts on Steadmo's launcher icon: the meal presets and Boost.
 * They go through the same UI-less activity as the widget's buttons, so they
 * copy the grams, open CamAPS FX, and (with CamAPS FX shortcuts on) go
 * straight to its screen; before the disclaimer is accepted they open Steadmo.
 */
object LauncherShortcuts {
    private data class Spec(val id: String, val label: String, val path: String, val icon: Int)

    private val specs = listOf(
        Spec("meal_20", "20 g", "meal/20", R.drawable.ic_shortcut_meal),
        Spec("meal_40", "40 g", "meal/40", R.drawable.ic_shortcut_meal),
        Spec("meal_45", "45 g", "meal/45", R.drawable.ic_shortcut_meal),
        Spec("boost", "Boost", "camaps/boost", R.drawable.ic_shortcut_boost),
    )

    fun publish(context: Context) {
        val shortcuts = specs.mapIndexed { rank, spec ->
            ShortcutInfoCompat.Builder(context, spec.id)
                .setShortLabel(spec.label)
                .setLongLabel(if (spec.path.startsWith("meal/")) "Meal, ${spec.label}" else spec.label)
                .setIcon(IconCompat.createWithResource(context, spec.icon))
                .setRank(rank)
                .setIntent(
                    Intent(context, WidgetActionActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("${WidgetActionActivity.SCHEME}://widget/${spec.path}")),
                )
                .build()
        }
        val ok = ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        SteadmoLog.event("shortcuts: launcher shortcuts %s", if (ok) "published" else "rate-limited; kept the old ones")
    }
}
