package shop.sabjiwala.kisankendra

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import shop.sabjiwala.kisankendra.api.MandiApiService
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.model.parseMandiRecordFromMap
import shop.sabjiwala.kisankendra.utils.MandiCacheHelper
import shop.sabjiwala.kisankendra.utils.MandiPriceHistoryHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MandiRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private var memoryCache: List<MandiRecord>? = null
        private val snapshotCache = mutableMapOf<String, List<MandiRecord>>()

        fun clearCache() {
            memoryCache = null
            snapshotCache.clear()
        }

        fun getCleanStateKey(stateName: String?): String {
            if (stateName.isNullOrBlank()) return "unknown_state"
            return stateName.lowercase().trim().replace("\\s+".toRegex(), "_")
        }
    }

    private val api: MandiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.data.gov.in/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MandiApiService::class.java)
    }

    suspend fun fetchMandiRatesFromApi(apiKey: String, limit: Int = 1000): List<MandiRecord> = withContext(Dispatchers.IO) {
        val allRecords = mutableListOf<MandiRecord>()
        var currentOffset = 0
        val maxPages = 50 // Up to 50 pages x 1,000 = 50,000 records max to capture expanding datasets

        var pageIndex = 0
        while (pageIndex < maxPages) {
            pageIndex++
            try {
                val response = api.getMandiPrices(
                    apiKey = apiKey,
                    limit = limit,
                    offset = currentOffset
                )
                val batch = response.records ?: emptyList()
                if (batch.isEmpty()) break

                allRecords.addAll(batch)
                if (batch.size < limit) break // Reached final page

                currentOffset += limit
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        allRecords
    }

    suspend fun fetchMandiRatesFromFirestore(context: Context? = null, forceRefresh: Boolean = false): List<MandiRecord> = withContext(Dispatchers.IO) {
        if (forceRefresh) {
            memoryCache = null
        } else if (context != null) {
            val local = MandiCacheHelper.getLocalMandiState(context, "latest_cache")
            if (!local.isNullOrEmpty()) {
                memoryCache = local
                return@withContext local
            } else {
                memoryCache = null // Wipes RAM cache if disk cache expired/crossed 1:30 PM boundary
            }
        } else if (!memoryCache.isNullOrEmpty()) {
            return@withContext memoryCache!!
        }
        return@withContext try {
            val doc = firestore.collection("mandi_cache").document("latest").get().await()
            if (doc.exists()) {
                val rawList = doc.get("records") as? List<Map<String, Any>>
                if (!rawList.isNullOrEmpty()) {
                    val list = rawList.mapNotNull { map ->
                        parseMandiRecordFromMap(map)
                    }
                    memoryCache = list
                    if (context != null) {
                        MandiCacheHelper.saveMandiStateLocally(context, "latest_cache", list)
                    }
                    list
                } else emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            memoryCache ?: emptyList()
        }
    }

    suspend fun saveMandiRatesToFirestore(records: List<MandiRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        memoryCache = records
        try {
            // Before overwriting latest cache, check if existing cache has a prior date and save as snapshot
            try {
                val existingDoc = firestore.collection("mandi_cache").document("latest").get().await()
                if (existingDoc.exists()) {
                    val rawList = existingDoc.get("records") as? List<Map<String, Any>>
                    if (!rawList.isNullOrEmpty()) {
                        val existingRecords = rawList.mapNotNull { parseMandiRecordFromMap(it) }
                        val existingDate = existingRecords.firstOrNull()?.arrivalDate.orEmpty()
                        val newDate = records.firstOrNull()?.arrivalDate.orEmpty()
                        if (existingDate.isNotBlank() && (newDate.isBlank() || existingDate != newDate)) {
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                            val df = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                            cal.add(Calendar.DAY_OF_YEAR, -1)
                            val yestStr = df.format(cal.time)
                            saveDailySnapshotToFirestore(yestStr, existingRecords)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 1. Group records by standardized clean state key and save to 'mandi_by_state'
            val groupedByState = records.groupBy { 
                getCleanStateKey(it.state) 
            }

            for ((stateKey, stateRecords) in groupedByState) {
                if (stateKey.isNotBlank()) {
                    val serializedStateRecords = stateRecords.map { record ->
                        hashMapOf(
                            "state" to record.state,
                            "district" to record.district,
                            "market" to record.market,
                            "commodity" to record.commodity,
                            "variety" to record.variety,
                            "minPrice" to record.minPrice,
                            "maxPrice" to record.maxPrice,
                            "modalPrice" to record.modalPrice,
                            "arrivalDate" to record.arrivalDate,
                            "priceDiff" to record.priceDiff
                        )
                    }
                    val stateData = hashMapOf(
                        "stateKey" to stateKey,
                        "updatedAt" to Timestamp.now(),
                        "records" to serializedStateRecords
                    )
                    firestore.collection("mandi_by_state").document(stateKey).set(stateData, SetOptions.merge()).await()
                }
            }

            // 2. Also write legacy summary payload (up to 2500) to 'mandi_cache/latest' for backward compatibility
            val legacySerialized = records.take(2500).map { record ->
                hashMapOf(
                    "state" to record.state,
                    "district" to record.district,
                    "market" to record.market,
                    "commodity" to record.commodity,
                    "variety" to record.variety,
                    "minPrice" to record.minPrice,
                    "maxPrice" to record.maxPrice,
                    "modalPrice" to record.modalPrice,
                    "arrivalDate" to record.arrivalDate,
                    "priceDiff" to record.priceDiff
                )
            }
            val legacyData = hashMapOf(
                "updatedAt" to Timestamp.now(),
                "records" to legacySerialized
            )
            firestore.collection("mandi_cache").document("latest").set(legacyData, SetOptions.merge()).await()

            MandiPriceHistoryHelper.saveDailyPricesToFirestore(records)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchMandiRatesByStateFromFirestore(stateName: String, context: Context? = null, forceRefresh: Boolean = false): List<MandiRecord> = withContext(Dispatchers.IO) {
        if (stateName.isBlank() || stateName.equals("All States", ignoreCase = true) || stateName.equals("⭐ Saved", ignoreCase = true)) {
            return@withContext fetchMandiRatesFromFirestore(context, forceRefresh)
        }
        val cleanStateKey = getCleanStateKey(stateName)

        if (!forceRefresh && context != null) {
            val local = MandiCacheHelper.getLocalMandiState(context, cleanStateKey)
            if (!local.isNullOrEmpty()) {
                return@withContext local
            }
        }

        // 1. Try loading from Firestore 'mandi_by_state/{cleanStateKey}'
        try {
            val doc = firestore.collection("mandi_by_state").document(cleanStateKey).get().await()
            if (doc.exists()) {
                val rawList = doc.get("records") as? List<Map<String, Any>>
                if (!rawList.isNullOrEmpty()) {
                    val list = rawList.mapNotNull { parseMandiRecordFromMap(it) }
                    if (list.isNotEmpty()) {
                        if (context != null) {
                            MandiCacheHelper.saveMandiStateLocally(context, cleanStateKey, list)
                        }
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Automatic Fail-Safe: If state doc doesn't exist in Firestore yet, filter from existing cached records instead of calling external API
        try {
            val cachedList = fetchMandiRatesFromFirestore(context, forceRefresh = false)
            if (cachedList.isNotEmpty()) {
                val stateRecords = cachedList.filter { 
                    getCleanStateKey(it.state) == cleanStateKey || 
                    it.state.orEmpty().equals(stateName, ignoreCase = true) 
                }
                if (stateRecords.isNotEmpty()) {
                    if (context != null) {
                        MandiCacheHelper.saveMandiStateLocally(context, cleanStateKey, stateRecords)
                    }
                    return@withContext stateRecords
                }
                return@withContext cachedList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback to cached summary
        fetchMandiRatesFromFirestore(context, forceRefresh)
    }

    suspend fun saveDailySnapshotToFirestore(dateStr: String, records: List<MandiRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty() || dateStr.isBlank()) return@withContext
        snapshotCache[dateStr] = records
        try {
            val groupedByState = records.groupBy { 
                it.state.orEmpty().lowercase().trim().replace("\\s+".toRegex(), "_") 
            }

            for ((stateKey, stateRecords) in groupedByState) {
                if (stateKey.isNotBlank()) {
                    val serializedStateRecords = stateRecords.map { record ->
                        hashMapOf(
                            "state" to record.state,
                            "district" to record.district,
                            "market" to record.market,
                            "commodity" to record.commodity,
                            "variety" to record.variety,
                            "minPrice" to record.minPrice,
                            "maxPrice" to record.maxPrice,
                            "modalPrice" to record.modalPrice,
                            "arrivalDate" to record.arrivalDate,
                            "priceDiff" to record.priceDiff
                        )
                    }
                    val stateData = hashMapOf(
                        "dateStr" to dateStr,
                        "records" to serializedStateRecords
                    )
                    firestore.collection("mandi_snapshots_$dateStr").document(stateKey).set(stateData, SetOptions.merge()).await()
                }
            }

            // Legacy fallback snapshot
            val serializedRecords = records.take(2500).map { record ->
                hashMapOf(
                    "state" to record.state,
                    "district" to record.district,
                    "market" to record.market,
                    "commodity" to record.commodity,
                    "variety" to record.variety,
                    "minPrice" to record.minPrice,
                    "maxPrice" to record.maxPrice,
                    "modalPrice" to record.modalPrice,
                    "arrivalDate" to record.arrivalDate,
                    "priceDiff" to record.priceDiff
                )
            }
            val data = hashMapOf(
                "dateStr" to dateStr,
                "records" to serializedRecords
            )
            firestore.collection("mandi_snapshots").document(dateStr).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchDailySnapshotFromFirestore(dateStr: String): List<MandiRecord> = withContext(Dispatchers.IO) {
        if (dateStr.isBlank()) return@withContext emptyList()
        if (snapshotCache.containsKey(dateStr) && snapshotCache[dateStr]?.isNotEmpty() == true) {
            return@withContext snapshotCache[dateStr]!!
        }
        return@withContext try {
            val doc = firestore.collection("mandi_snapshots").document(dateStr).get().await()
            if (doc.exists()) {
                val rawList = doc.get("records") as? List<Map<String, Any>>
                if (!rawList.isNullOrEmpty()) {
                    val list = rawList.mapNotNull { map ->
                        parseMandiRecordFromMap(map)
                    }
                    snapshotCache[dateStr] = list
                    list
                } else emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            snapshotCache[dateStr] ?: emptyList()
        }
    }

    suspend fun getPreviousDayRecords(liveRecords: List<MandiRecord>): List<MandiRecord> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(calendar.time)

        val yesterdaySnapshot = fetchDailySnapshotFromFirestore(yesterdayStr)
        if (yesterdaySnapshot.isNotEmpty()) {
            return@withContext yesterdaySnapshot
        }

        val currentCache = fetchMandiRatesFromFirestore()
        if (currentCache.isNotEmpty()) {
            val liveDate = liveRecords.firstOrNull()?.arrivalDate.orEmpty()
            val cacheDate = currentCache.firstOrNull()?.arrivalDate.orEmpty()

            if (cacheDate.isNotBlank() && liveDate.isNotBlank() && cacheDate != liveDate) {
                saveDailySnapshotToFirestore(yesterdayStr, currentCache)
                return@withContext currentCache
            } else if (cacheDate.isBlank() && liveDate.isBlank()) {
                return@withContext currentCache
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dayBeforeStr = dateFormat.format(calendar.time)
        val dayBeforeSnapshot = fetchDailySnapshotFromFirestore(dayBeforeStr)
        if (dayBeforeSnapshot.isNotEmpty()) {
            return@withContext dayBeforeSnapshot
        }

        return@withContext currentCache
    }
}
