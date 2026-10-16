package app.cuentasclaras

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.cuentasclaras.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Aplicación Hilt. Implementa [Configuration.Provider] para que WorkManager use
 * una `HiltWorkerFactory` y pueda inyectar dependencias en `SyncWorker`.
 */
@HiltAndroidApp
class CuentasClarasApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Drena las mutaciones que quedaran pendientes de una sesión anterior.
        syncScheduler.schedule()
    }
}
