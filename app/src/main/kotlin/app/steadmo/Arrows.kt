package app.steadmo

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import app.steadmo.core.HIGH_MMOL
import app.steadmo.core.LOW_MMOL
import app.steadmo.core.TrendArrow

@DrawableRes
fun TrendArrow?.drawable(): Int = when (this) {
    TrendArrow.DOUBLE_DOWN -> R.drawable.ic_trend_double_down
    TrendArrow.DOWN -> R.drawable.ic_trend_down
    TrendArrow.DOWN_SLIGHT -> R.drawable.ic_trend_down_slight
    TrendArrow.FLAT -> R.drawable.ic_trend_flat
    TrendArrow.UP_SLIGHT -> R.drawable.ic_trend_up_slight
    TrendArrow.UP -> R.drawable.ic_trend_up
    TrendArrow.DOUBLE_UP -> R.drawable.ic_trend_double_up
    null -> R.drawable.ic_trend_unknown
}

val LowColor = Color(0xFFE5484D)
val InRangeColor = Color(0xFF30A46C)
val HighColor = Color(0xFFE79D13)

fun rangeColor(mmol: Double): Color = when {
    mmol < LOW_MMOL -> LowColor
    mmol > HIGH_MMOL -> HighColor
    else -> InRangeColor
}
