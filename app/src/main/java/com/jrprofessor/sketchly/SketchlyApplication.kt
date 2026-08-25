package com.jrprofessor.sketchly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Custom Application class.
 *
 * Responsibilities:
 * 1. Bootstrap Hilt DI graph ([HiltAndroidApp]).
 * 2. Configure WorkManager with [HiltWorkerFactory] so Workers can use @HiltWorker.
 * 3. Configure Crashlytics collection (M5: enabled in release).
 */
@HiltAndroidApp
class SketchlyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager reads this to create workers via HiltWorkerFactory,
     * enabling @AssistedInject / @HiltWorker in [SendSketchlyWorker] etc.
     * NOTE: Remove any WorkManager.initialize() calls — this replaces them.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ── Crashlytics (M5) ──
        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = true
            log("SketchlyApplication.onCreate — versionCode=1")
        }
    }
}
