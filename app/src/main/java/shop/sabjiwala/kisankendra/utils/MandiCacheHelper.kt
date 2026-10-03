package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.model.PriceDataPoint

object MandiCacheHelper {

    private const val PREF_NAME = "mandi_cache_prefs"
    private const val KEY_FAVORITES = "favorite_mandi_doc_ids"
    private const val PREFIX_HISTORY = "history_cache_"
    private const val PREFIX_HISTORY_TIME = "history_time_"
    private const val CACHE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000L // 30 Days

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getDocId(record: MandiRecord): String {
        return MandiPriceHistoryHelper.getDocId(record)
    }

    fun isFavorite(context: Context, docId: String): Boolean {
        if (docId.isBlank()) return false
        val favorites = getFavorites(context)
        return favorites.contains(docId)
    }

    fun toggleFavorite(context: Context, docId: String): Boolean {
        if (docId.isBlank()) return false
        val prefs = getPrefs(context)
        val favorites = getFavorites(context).toMutableSet()
        val isFavNow: Boolean

        if (favorites.contains(docId)) {
            favorites.remove(docId)
            isFavNow = false
        } else {
            favorites.add(docId)
            isFavNow = true
        }

        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
        return isFavNow
    }

    fun getFavorites(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun save30DayHistoryLocally(context: Context, docId: String, newPoints: List<PriceDataPoint>) {
        if (docId.isBlank() || newPoints.isEmpty()) return
        val prefs = getPrefs(context)

        // Merge existing local points with new points to keep a seamless rolling 30-day history
        val existingJson = prefs.getString(PREFIX_HISTORY + docId, null)
        val existingPoints: List<PriceDataPoint> = if (!existingJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<PriceDataPoint>>() {}.type
                Gson().fromJson(existingJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        // Combine and deduplicate by dateLabel, sort by timestamp, keep last 30
        val combinedMap = existingPoints.associateBy { it.dateLabel }.toMutableMap()
        for (point in newPoints) {
            combinedMap[point.dateLabel] = point
        }
        val final30Points = combinedMap.values.sortedBy { it.timestamp }.takeLast(30)

        val json = Gson().toJson(final30Points)
        prefs.edit()
            .putString(PREFIX_HISTORY + docId, json)
            .putLong(PREFIX_HISTORY_TIME + docId, System.currentTimeMillis())
            .apply()
    }

    fun getLocal30DayHistory(context: Context, docId: String): List<PriceDataPoint>? {
        if (docId.isBlank()) return null
        val prefs = getPrefs(context)
        val saveTime = prefs.getLong(PREFIX_HISTORY_TIME + docId, 0L)
        val json = prefs.getString(PREFIX_HISTORY + docId, null) ?: return null

        // Check if cache is fresh (less than 30 days old)
        if (System.currentTimeMillis() - saveTime > CACHE_EXPIRY_MS) {
            return null
        }

        return try {
            val type = object : TypeToken<List<PriceDataPoint>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearExpiredCache(context: Context) {
        val prefs = getPrefs(context)
        val allEntries = prefs.all
        val editor = prefs.edit()
        val now = System.currentTimeMillis()

        for ((key, value) in allEntries) {
            if (key.startsWith(PREFIX_HISTORY_TIME)) {
                val saveTime = (value as? Long) ?: 0L
                if (now - saveTime > CACHE_EXPIRY_MS) {
                    val docId = key.removePrefix(PREFIX_HISTORY_TIME)
                    editor.remove(PREFIX_HISTORY_TIME + docId)
                    editor.remove(PREFIX_HISTORY + docId)
                }
            }
        }
        editor.apply()
    }
}
