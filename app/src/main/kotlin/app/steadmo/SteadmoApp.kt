package app.steadmo

import android.app.Application
import android.util.Log

class SteadmoApp : Application() {
    lateinit var repository: GlucoseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "started")
        repository = GlucoseRepository(this, StatusNotifier(this))
    }

    companion object {
        const val TAG = "Steadmo"
    }
}

val android.content.Context.repository: GlucoseRepository
    get() = (applicationContext as SteadmoApp).repository
