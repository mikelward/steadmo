package app.steadmo

import app.steadmo.core.SteadmoLog

/**
 * The Application under Robolectric: the debug log records, but without the
 * file sink's crash handler (which would replace the test runner's) and
 * without the binder calls for process exits.
 */
class TestSteadmoApp : SteadmoApp() {
    var testUpdateChecker: UpdateChecker? = null

    override val updateChecker: UpdateChecker
        get() = testUpdateChecker ?: super.updateChecker

    override fun installDiagnosticLog() {
        SteadmoLog.clearSinks()
    }

    override fun logProcessExits() = Unit

    override fun createUpdateChecker(): UpdateChecker = object : UpdateChecker(appScope) {
        override fun check() = Unit
    }
}
