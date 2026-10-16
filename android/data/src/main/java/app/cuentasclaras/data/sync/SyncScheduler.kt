package app.cuentasclaras.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encola el drenado del outbox con WorkManager. La petición exige conexión y,
 * si falla de forma transitoria, reintenta con backoff exponencial.
 *
 * Se usa trabajo único y `APPEND_OR_REPLACE` para no cancelar una sincronización
 * en curso, pero asegurando que una mutación nueva siempre acabe procesándose.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        /** Nombre del trabajo único que drena el outbox. */
        const val WORK_NAME = "cuentas-claras-outbox-sync"

        /** Backoff inicial; WorkManager exige un mínimo de 10 segundos. */
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
