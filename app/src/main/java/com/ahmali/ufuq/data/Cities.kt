package com.ahmali.ufuq.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ahmali.ufuq.core.CityIndex
import java.util.concurrent.Executors

/** Loads the bundled city list once, off the main thread. */
object Cities {
    @Volatile
    private var index: CityIndex? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ufuq-cities").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun get(context: Context, callback: (CityIndex) -> Unit) {
        index?.let { callback(it); return }
        val app = context.applicationContext
        worker.execute {
            val idx = blocking(app)
            main.post { callback(idx) }
        }
    }

    /** Normalise search keys in the background so the first search is instant. */
    fun warmUpSearch(context: Context) {
        val app = context.applicationContext
        worker.execute { blocking(app).warmUp() }
    }

    fun runInBackground(task: () -> Unit) = worker.execute(task)

    @Synchronized
    fun blocking(context: Context): CityIndex {
        index?.let { return it }
        val loaded = try {
            context.assets.open("cities.tsv").use { CityIndex.load(it) }
        } catch (e: Exception) {
            CityIndex.empty() // asset missing in a dev build: search simply finds nothing
        }
        index = loaded
        return loaded
    }
}
