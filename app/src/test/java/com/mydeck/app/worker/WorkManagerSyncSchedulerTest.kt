package com.mydeck.app.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WorkManagerSyncSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setup() {
        workManager = mockk(relaxed = true)
        scheduler = WorkManagerSyncScheduler(workManager)
    }

    @Test
    fun `scheduleBatchArticleLoad uses REPLACE policy when userInitiated is true`() {
        scheduler.scheduleBatchArticleLoad(wifiOnly = false, allowBatterySaver = true, userInitiated = true)

        val policySlot = slot<ExistingWorkPolicy>()
        verify {
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
    }

    @Test
    fun `scheduleBatchArticleLoad uses KEEP policy when userInitiated is false`() {
        scheduler.scheduleBatchArticleLoad(wifiOnly = false, allowBatterySaver = true, userInitiated = false)

        val policySlot = slot<ExistingWorkPolicy>()
        verify {
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ExistingWorkPolicy.KEEP, policySlot.captured)
    }

    @Test
    fun `scheduleBatchArticleLoad defaults userInitiated to false (KEEP)`() {
        scheduler.scheduleBatchArticleLoad(wifiOnly = true, allowBatterySaver = false)

        verify {
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }
}
