package app.steadmo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Release builds stay offline (SPEC.md §5): no update check runs. */
@Suppress("UNUSED_PARAMETER")
open class UpdateChecker(scope: CoroutineScope) {
    open val updateAvailable: StateFlow<Boolean> = MutableStateFlow(false)
    open fun onAppOpen() = Unit
    open fun skipNextOpen() = Unit
    open fun check() = Unit
}
