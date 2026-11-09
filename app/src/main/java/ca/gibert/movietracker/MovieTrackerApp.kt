package ca.gibert.movietracker

import android.app.Application
import ca.gibert.movietracker.data.AppContainer
import io.sentry.android.core.SentryAndroid

class MovieTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initSentry()
        container = AppContainer(this)
    }

    // Sentry only runs when a DSN was compiled in (see app/build.gradle.kts).
    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.environment = BuildConfig.BUILD_TYPE
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            // Never attach IP addresses, user identifiers, or request bodies.
            options.isSendDefaultPii = false
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
        }
    }
}
