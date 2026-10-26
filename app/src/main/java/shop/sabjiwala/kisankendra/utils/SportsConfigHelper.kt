package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object SportsConfigHelper {

    private const val TAG = "SportsConfigHelper"
    private const val PREFS_NAME = "sports_config_prefs"
    private const val KEY_SHOW_SPORTS = "show_sports_button"
    private const val KEY_SPORTS_WEB_URL = "sports_web_url"

    const val DEFAULT_WEB_URL = "https://cwidget.crictimes.org/?v=1.1&a=2788d3&c=fafafa&bo=ffffff&b=f2f3f2&sb=181930&lb=732d21&lc=2ca03f&db=1c972a&dc=fafafa&tc=0a0a0a&ti=f2f3f2"

    /**
     * Checks if the Sports top bar button should be visible based on local SharedPreferences cache.
     * Default is true.
     */
    fun isSportsButtonVisible(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_SPORTS, true)
    }

    /**
     * Updates the local cache for sports button visibility.
     */
    fun saveSportsButtonVisibilityLocally(context: Context, visible: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_SPORTS, visible).apply()
    }

    /**
     * Gets the configured sports web URL or embed link.
     */
    fun getSportsWebUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_SPORTS_WEB_URL, "")?.trim().orEmpty()
        return if (url.isNotBlank()) url else DEFAULT_WEB_URL
    }

    /**
     * Updates the local cache for sports web URL.
     */
    fun saveSportsWebUrlLocally(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SPORTS_WEB_URL, url.trim()).apply()
    }

    /**
     * Listens for real-time sports configuration changes from Firestore (`app_config/sports_config`).
     * Automatically updates local SharedPreferences cache and invokes callback.
     */
    fun listenForSportsConfig(
        context: Context,
        onConfigUpdated: (showSports: Boolean, webUrl: String) -> Unit
    ): ListenerRegistration? {
        // First invoke callback immediately with local cached values
        val initialShow = isSportsButtonVisible(context)
        val initialUrl = getSportsWebUrl(context)
        onConfigUpdated(initialShow, initialUrl)

        return try {
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("sports_config")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Snapshot listener error: ${error.message}. Falling back to get().")
                        fetchSportsConfigOnce(context, onConfigUpdated)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val showSports = snapshot.getBoolean("show_sports_button") ?: true
                        val webUrl = snapshot.getString("sports_web_url")?.trim() ?: ""

                        saveSportsButtonVisibilityLocally(context, showSports)
                        if (webUrl.isNotBlank()) {
                            saveSportsWebUrlLocally(context, webUrl)
                        }

                        val activeUrl = if (webUrl.isNotBlank()) webUrl else DEFAULT_WEB_URL
                        onConfigUpdated(showSports, activeUrl)
                    } else {
                        onConfigUpdated(isSportsButtonVisible(context), getSportsWebUrl(context))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach snapshot listener", e)
            fetchSportsConfigOnce(context, onConfigUpdated)
            null
        }
    }

    /**
     * Single query fallback from Firestore (`app_config/sports_config`).
     */
    fun fetchSportsConfigOnce(
        context: Context,
        onConfigUpdated: ((showSports: Boolean, webUrl: String) -> Unit)? = null
    ) {
        try {
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("sports_config")
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val showSports = doc.getBoolean("show_sports_button") ?: true
                        val webUrl = doc.getString("sports_web_url")?.trim() ?: ""

                        saveSportsButtonVisibilityLocally(context, showSports)
                        if (webUrl.isNotBlank()) {
                            saveSportsWebUrlLocally(context, webUrl)
                        }

                        val activeUrl = if (webUrl.isNotBlank()) webUrl else DEFAULT_WEB_URL
                        onConfigUpdated?.invoke(showSports, activeUrl)
                    } else {
                        onConfigUpdated?.invoke(isSportsButtonVisible(context), getSportsWebUrl(context))
                    }
                }
                .addOnFailureListener {
                    // On failure, preserve existing cached values
                    onConfigUpdated?.invoke(isSportsButtonVisible(context), getSportsWebUrl(context))
                }
        } catch (_: Exception) {
            onConfigUpdated?.invoke(isSportsButtonVisible(context), getSportsWebUrl(context))
        }
    }

    /**
     * Saves or updates sports config in Firestore (Admin).
     */
    fun saveSportsConfig(
        context: Context,
        showSports: Boolean,
        webUrl: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        saveSportsButtonVisibilityLocally(context, showSports)
        if (webUrl.isNotBlank()) {
            saveSportsWebUrlLocally(context, webUrl)
        }

        val data = hashMapOf(
            "show_sports_button" to showSports,
            "sports_web_url" to webUrl.trim(),
            "updated_at" to Timestamp.now()
        )

        try {
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("sports_config")
                .set(data, SetOptions.merge())
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onFailure(e) }
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}
