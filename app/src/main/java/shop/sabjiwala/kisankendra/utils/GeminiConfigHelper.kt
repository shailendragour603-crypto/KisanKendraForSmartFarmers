package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object GeminiConfigHelper {

    private const val PREFS_NAME = "gemini_config_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    /**
     * Retrieves the cached Gemini API key from local SharedPreferences.
     */
    fun getStoredApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "").orEmpty()
    }

    /**
     * Saves the Gemini API key into local SharedPreferences.
     */
    fun saveApiKeyLocally(context: Context, key: String) {
        val cleanKey = key.trim().replace("\"", "").replace("'", "")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, cleanKey).apply()
    }

    /**
     * Silently fetches the centralized Gemini API key from Firestore (`app_config/gemini`).
     * Automatically updates local SharedPreferences so all AI features remain active & synced.
     */
    fun fetchRemoteApiKey(context: Context, onComplete: ((String) -> Unit)? = null) {
        try {
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("gemini")
                .get()
                .addOnSuccessListener { doc ->
                    val remoteKey = doc.getString("apiKey").orEmpty().trim()
                    if (remoteKey.isNotBlank()) {
                        saveApiKeyLocally(context, remoteKey)
                    }
                    onComplete?.invoke(getStoredApiKey(context))
                }
                .addOnFailureListener {
                    onComplete?.invoke(getStoredApiKey(context))
                }
        } catch (_: Exception) {
            onComplete?.invoke(getStoredApiKey(context))
        }
    }

    /**
     * Saves or updates the central Gemini API key in Firestore (Admin only).
     * This instantly updates the API key for all app users without an app release.
     */
    fun saveRemoteApiKey(
        context: Context,
        newKey: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val cleanKey = newKey.trim().replace("\"", "").replace("'", "")
        saveApiKeyLocally(context, cleanKey)

        val data = mapOf(
            "apiKey" to cleanKey,
            "updatedAt" to Timestamp.now()
        )

        try {
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("gemini")
                .set(data, SetOptions.merge())
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onFailure(e) }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    /**
     * Opens a secure Admin dialog to manage and update the centralized Gemini API key.
     */
    fun showManageApiKeyDialog(context: Context, onUpdated: (() -> Unit)? = null) {
        val currentKey = getStoredApiKey(context)
        val input = EditText(context).apply {
            hint = "Paste Gemini API Key (AQ... or AIza...)"
            setText(currentKey)
            setPadding(40, 30, 40, 30)
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("🔑 Central Gemini AI Config")
            .setMessage("Manage the central Gemini API Key for the entire app (News AI & Kisan AI Chatbot).\n\nChanging this key will instantly update AI services for all farmers without an app update.\n\n👉 Get a key from:\nhttps://aistudio.google.com/app/apikey")
            .setView(input)
            .setPositiveButton("Save Remote Key") { _, _ ->
                val newKey = input.text.toString().trim()
                if (newKey.isNotBlank()) {
                    Toast.makeText(context, "Saving key to Cloud...", Toast.LENGTH_SHORT).show()
                    saveRemoteApiKey(
                        context = context,
                        newKey = newKey,
                        onSuccess = {
                            Toast.makeText(context, "✅ Central AI Key updated successfully!", Toast.LENGTH_SHORT).show()
                            onUpdated?.invoke()
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "⚠️ Saved locally, but Cloud sync failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            onUpdated?.invoke()
                        }
                    )
                } else {
                    Toast.makeText(context, "API Key cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
