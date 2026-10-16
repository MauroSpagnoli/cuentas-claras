package app.cuentasclaras.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            // El `SyncWorker` real se construye con Hilt; aquí solo comprobamos
            // la programación, así que se sustituye por un worker de prueba.
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): Worker = NoOpWorker(appContext, workerParameters)
            })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @Test
    fun `encola un trabajo unico que exige conexion`() {
        SyncScheduler(context).schedule()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncScheduler.WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    private class NoOpWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : Worker(appContext, params) {
        override fun doWork(): Result = Result.success()
    }
}
