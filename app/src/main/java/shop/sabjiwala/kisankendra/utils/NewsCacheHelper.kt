package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import shop.sabjiwala.kisankendra.model.NewsItem

object NewsCacheHelper {

    private const val PREFS_NAME = "news_local_cache_prefs"
    private const val KEY_NEWS_LIST = "cached_news_list_json"
    private const val KEY_LAST_SYNC_TIME = "cached_news_last_sync_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveNewsListLocally(context: Context, newsList: List<NewsItem>) {
        if (newsList.isEmpty()) return
        val prefs = getPrefs(context)
        val gson = Gson()
        val json = gson.toJson(newsList)

        prefs.edit()
            .putString(KEY_NEWS_LIST, json)
            .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLocalNewsList(context: Context): List<NewsItem> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_NEWS_LIST, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<NewsItem>>() {}.type
            Gson().fromJson<List<NewsItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getLatestNewsTimestampMillis(context: Context): Long {
        val newsList = getLocalNewsList(context)
        if (newsList.isEmpty()) return 0L
        return newsList.maxOfOrNull { it.timestamp?.toDate()?.time ?: 0L } ?: 0L
    }

    fun getLastSyncTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun clearCache(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
