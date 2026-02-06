package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.policy.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.policy.DateRangeParams
import com.mydeck.app.domain.policy.PolicyReason
import com.mydeck.app.worker.DateRangeContentSyncWorker
import timber.log.Timber
import javax.inject.Inject
import android.content.Context

/**
 * Use case for triggering date range content sync
 */
class DateRangeContentSyncUseCase @Inject constructor(
    private val contentSyncPolicyEvaluator: ContentSyncPolicyEvaluator,
    private val context: Context
) {
    
    sealed class UseCaseResult<out DataType : Any> {
        data class Success<out DataType : Any>(val dataType: DataType) : UseCaseResult<DataType>()
        data class Error(val exception: Throwable) : UseCaseResult<Nothing>()
        data class PolicyBlocked(val reason: PolicyReason) : UseCaseResult<Nothing>()
    }
    
    /**
     * Execute date range content sync
     */
    suspend fun execute(): UseCaseResult<Unit> {
        try {
            // Check policy first
            val policyResult = contentSyncPolicyEvaluator.evaluatePolicy()
            if (!policyResult.allowed) {
                Timber.w("Date range content sync blocked by policy: ${policyResult.reason}")
                return UseCaseResult.PolicyBlocked(policyResult.reason)
            }
            
            // Check date range is configured
            val dateRange = contentSyncPolicyEvaluator.getDateRangeParams()
            if (dateRange.fromDate == null || dateRange.toDate == null) {
                return UseCaseResult.Error(
                    IllegalStateException("Date range not properly configured")
                )
            }
            
            // Enqueue the worker
            DateRangeContentSyncWorker.enqueue(context)
            
            Timber.d("Date range content sync enqueued successfully")
            return UseCaseResult.Success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Error executing date range content sync")
            return UseCaseResult.Error(e)
        }
    }
    
    /**
     * Get current date range configuration
     */
    suspend fun getDateRange(): DateRangeParams {
        return contentSyncPolicyEvaluator.getDateRangeParams()
    }
    
    /**
     * Set date range configuration
     */
    suspend fun setDateRange(fromDate: kotlinx.datetime.LocalDate?, toDate: kotlinx.datetime.LocalDate?) {
        contentSyncPolicyEvaluator.setDateRangeParams(DateRangeParams(fromDate, toDate))
        Timber.d("Date range updated: $fromDate to $toDate")
    }
    
    /**
     * Check if date range is properly configured
     */
    suspend fun isDateRangeConfigured(): Boolean {
        val dateRange = contentSyncPolicyEvaluator.getDateRangeParams()
        return dateRange.fromDate != null && dateRange.toDate != null
    }
}
