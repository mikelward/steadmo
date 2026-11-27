package app.steadmo.core

import com.mikelward.androidlog.DebugLog

/**
 * Steadmo's debug log (mikelward/androidlog). Lines stay on the device; they
 * leave only in a bug report the user chooses to share (SPEC.md §6).
 */
object SteadmoLog : DebugLog()
