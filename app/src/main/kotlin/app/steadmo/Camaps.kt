package app.steadmo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Opens CamAPS FX. Steadmo never doses: every bolus, Boost and Ease-off is set
 * and confirmed in CamAPS FX (SPEC.md §2, D2).
 */
object Camaps {
    private val packages = listOf(
        "com.camdiab.fx_alert.mmoll",
        "com.camdiab.fx_alert.mgdl",
        "com.camdiab.fx_alert.hx.mmoll",
        "com.camdiab.fx_alert.hx.mgdl",
    )

    fun open(context: Context, seenPackage: String?): Boolean {
        val candidates = listOfNotNull(seenPackage) + packages
        val intent = candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            Log.w(SteadmoApp.TAG, "CamAPS FX isn't installed, or isn't visible to Steadmo")
            Toast.makeText(context, "CamAPS FX isn't installed", Toast.LENGTH_SHORT).show()
            return false
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Copies [grams] so it can be pasted into CamAPS FX's meal entry, then opens it. */
    fun openForMeal(context: Context, seenPackage: String?, grams: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Carbs", grams.toString()))
        if (open(context, seenPackage)) {
            Toast.makeText(context, "$grams g copied. Enter it in CamAPS FX.", Toast.LENGTH_SHORT).show()
        }
    }
}
