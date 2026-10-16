package app.cuentasclaras.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Trabajo en segundo plano que drena el outbox. Delega toda la lógica en
 * [OutboxDrainer] para que el drenado sea testeable sin WorkManager.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drainer: OutboxDrainer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (drainer.drain()) {
        OutboxDrainer.Outcome.COMPLETE -> Result.success()
        OutboxDrainer.Outcome.RETRY -> Result.retry()
    }
}
