package app.steadmo.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.steadmo.Camaps
import app.steadmo.Disclaimer
import app.steadmo.MainActivity
import app.steadmo.core.SteadmoLog
import app.steadmo.repository

/**
 * The widget's buttons land here, with no UI of its own, because a widget
 * can't copy to the clipboard or start CamAPS FX itself. Until the
 * disclaimer is accepted, every button opens Steadmo instead.
 */
class WidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.data?.pathSegments.orEmpty()
        SteadmoLog.event("widget: %s", path.joinToString("/"))
        val seen = repository.state.value.camapsPackage
        when {
            !Disclaimer.accepted(this) -> startActivity(Intent(this, MainActivity::class.java))
            path.firstOrNull() == "meal" -> {
                val grams = path.getOrNull(1)?.toIntOrNull()
                if (grams != null) Camaps.openForMeal(this, seen, grams) else Camaps.open(this, seen)
            }
            path.firstOrNull() == "camaps" -> Camaps.open(this, seen)
            else -> startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    companion object {
        const val SCHEME = "steadmo"
    }
}
