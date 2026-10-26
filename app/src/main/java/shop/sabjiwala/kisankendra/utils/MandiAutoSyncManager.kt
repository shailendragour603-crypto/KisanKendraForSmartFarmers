package shop.sabjiwala.kisankendra.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.MandiRepository
import shop.sabjiwala.kisankendra.model.MandiRecord
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MandiSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                MandiAutoSyncManager.performAutoSyncIfEligible(appContext)
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }
}

object MandiAutoSyncManager {

    private const val WORK_NAME = "mandi_auto_sync_work"
    private const val API_KEY = "579b464db66ec23bdd000001f0acdf7b0b4943fe5b9998c1304ba55f"
    private const val SYNC_INTERVAL_MINUTES = 30L

    fun schedulePeriodicSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Enqueue immediate one-time background worker
            val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<MandiSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(oneTimeRequest)

            // 2. Enqueue periodic background worker every 30 minutes
            val syncRequest = PeriodicWorkRequestBuilder<MandiSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isWithinSyncTimeWindow(): Boolean {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(tz)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        val startMinutes = 12 * 60 + 30 // 12:30 PM IST
        val endMinutes = 21 * 60        // 9:00 PM IST (Final sync)

        return currentMinutes in startMinutes..endMinutes
    }

    suspend fun performAutoSyncIfEligible(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isWithinSyncTimeWindow()) {
            return@withContext false
        }

        val firestore = FirebaseFirestore.getInstance()
        val nowMs = System.currentTimeMillis()

        // Check if Firestore was already updated within the last 30 minutes
        try {
            val doc = firestore.collection("mandi_cache").document("latest").get().await()
            if (doc.exists()) {
                val updatedAt = doc.getTimestamp("updatedAt")
                if (updatedAt != null) {
                    val lastUpdatedMs = updatedAt.toDate().time
                    val diffMinutes = (nowMs - lastUpdatedMs) / (1000 * 60)
                    if (diffMinutes in 0..25) { // Only skip if updated within the last 0 to 25 minutes
                        return@withContext false
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fetch live Mandi rates from API (using Indian IP of device) and update same Firestore documents
        return@withContext try {
            val repository = MandiRepository()
            val liveRecords = repository.fetchMandiRatesFromApi(apiKey = API_KEY, limit = 2500)
            if (liveRecords.isNotEmpty()) {
                val previousRecords = repository.getPreviousDayRecords(liveRecords)
                val recordsWithDiff = calculatePriceDifferences(liveRecords, previousRecords)
                repository.saveMandiRatesToFirestore(recordsWithDiff)

                // Save locally on device as well
                MandiCacheHelper.saveMandiStateLocally(context, "latest_cache", recordsWithDiff)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun calculatePriceDifferences(
        currentList: List<MandiRecord>,
        previousList: List<MandiRecord>
    ): List<MandiRecord> {
        val previousPriceMap = previousList.associateBy { record ->
            "${record.state.orEmpty().trim()}_${record.market.orEmpty().trim()}_${record.commodity.orEmpty().trim()}_${record.variety.orEmpty().trim()}".lowercase()
        }

        return currentList.map { current ->
            val key = "${current.state.orEmpty().trim()}_${current.market.orEmpty().trim()}_${current.commodity.orEmpty().trim()}_${current.variety.orEmpty().trim()}".lowercase()
            val previousRecord = previousPriceMap[key]

            val currentPrice = current.maxPrice?.toDoubleOrNull() ?: current.modalPrice?.toDoubleOrNull() ?: 0.0
            val previousPrice = previousRecord?.maxPrice?.toDoubleOrNull() ?: previousRecord?.modalPrice?.toDoubleOrNull()

            val computedDiff = if (previousPrice != null && previousPrice > 0.0) {
                currentPrice - previousPrice
            } else {
                null
            }

            current.apply {
                priceDiff = computedDiff
            }
        }
    }
}
