package shop.sabjiwala.kisankendra

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
import shop.sabjiwala.kisankendra.utils.MandiPriceHistoryHelper

class MandiRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private var memoryCache: List<MandiRecord>? = null
        private val snapshotCache = mutableMapOf<String, List<MandiRecord>>()

        fun clearCache() {
            memoryCache = null
            snapshotCache.clear()
        }
    }

    private val api: MandiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.data.gov.in/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MandiApiService::class.java)
    }

    suspend fun fetchMandiRatesFromApi(apiKey: String, limit: Int = 10000): List<MandiRecord> {
        return try {
            val response = api.getMandiPrices(apiKey = apiKey, limit = limit)
            response.records ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchMandiRatesFromFirestore(forceRefresh: Boolean = false): List<MandiRecord> = withContext(Dispatchers.IO) {
        if (!forceRefresh && !memoryCache.isNullOrEmpty()) {
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
                "updatedAt" to Timestamp.now(),
                "records" to serializedRecords
            )

            firestore.collection("mandi_cache").document("latest").set(data, SetOptions.merge()).await()
            MandiPriceHistoryHelper.saveDailyPricesToFirestore(records)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveDailySnapshotToFirestore(dateStr: String, records: List<MandiRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty() || dateStr.isBlank()) return@withContext
        snapshotCache[dateStr] = records
        try {
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
}
