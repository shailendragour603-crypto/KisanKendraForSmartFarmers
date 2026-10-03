package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.repository.KhataRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object KhataHelper {

    private const val PREFS_NAME = "khata_prefs"
    private const val TAG = "KhataHelper"
    // 5 Years retention window in milliseconds (5 * 365 * 24 * 60 * 60 * 1000)
    private const val FIVE_YEARS_MS = 5L * 365 * 24 * 60 * 60 * 1000L

    private fun getCurrentUserKey(suffix: String): String {
        val user = FirebaseAuth.getInstance().currentUser
        return if (user != null && !user.isAnonymous && !user.uid.isNullOrBlank()) {
            "${suffix}_${user.uid}"
        } else {
            "${suffix}_guest"
        }
    }

    fun getLocalKhataItems(context: Context): List<KhataItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(getCurrentUserKey("khata_items"), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<KhataItem>>() {}.type
            val items: List<KhataItem> = Gson().fromJson(json, type) ?: emptyList()
            val cutoff = System.currentTimeMillis() - FIVE_YEARS_MS
            // Retain entries within 5-year retention window
            items.filter { it.timestamp == 0L || it.timestamp >= cutoff }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalKhataItems(context: Context, items: List<KhataItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cutoff = System.currentTimeMillis() - FIVE_YEARS_MS
        val retainedItems = items.filter { it.timestamp == 0L || it.timestamp >= cutoff }
        val json = Gson().toJson(retainedItems)
        prefs.edit().putString(getCurrentUserKey("khata_items"), json).apply()
    }

    fun getLocalCropCycles(context: Context): List<CropCycle> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(getCurrentUserKey("crop_cycles"), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CropCycle>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalCropCycles(context: Context, cycles: List<CropCycle>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(cycles)
        prefs.edit().putString(getCurrentUserKey("crop_cycles"), json).apply()
    }

    fun getGuestKhataItems(context: Context): List<KhataItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("khata_items_guest", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<KhataItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getGuestCropCycles(context: Context): List<CropCycle> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("crop_cycles_guest", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CropCycle>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearGuestCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("khata_items_guest")
            .remove("crop_cycles_guest")
            .apply()
    }

    fun migrateGuestDataToCurrentUser(context: Context, repository: KhataRepository, onComplete: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous || user.uid.isBlank()) {
            onComplete?.invoke()
            return
        }

        val uid = user.uid
        val guestCycles = getGuestCropCycles(context)
        val guestItems = getGuestKhataItems(context)

        if (guestCycles.isEmpty() && guestItems.isEmpty()) {
            onComplete?.invoke()
            return
        }

        Log.d(TAG, "Migrating ${guestCycles.size} cycles and ${guestItems.size} items from Guest to User: $uid")

        val currentCycles = getLocalCropCycles(context).toMutableList()
        val currentItems = getLocalKhataItems(context).toMutableList()

        for (cycle in guestCycles) {
            cycle.userId = uid
            if (currentCycles.none { it.id == cycle.id }) {
                currentCycles.add(0, cycle)
            }
            repository.addCropCycle(cycle) { success ->
                Log.d(TAG, "Migrated CropCycle ${cycle.id} to Firestore: $success")
            }
        }

        for (item in guestItems) {
            item.userId = uid
            if (currentItems.none { it.id == item.id }) {
                currentItems.add(0, item)
            }
            repository.addKhataItem(item) { success ->
                Log.d(TAG, "Migrated KhataItem ${item.id} to Firestore: $success")
            }
        }

        saveLocalCropCycles(context, currentCycles)
        saveLocalKhataItems(context, currentItems)
        clearGuestCache(context)
        onComplete?.invoke()
    }

    fun clearUserCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getOrCreateCropCycle(
        context: Context,
        repository: KhataRepository?,
        cropCycles: MutableList<CropCycle>,
        rawCropSelection: String
    ): CropCycle {
        val rawTrimmed = rawCropSelection.trim()

        val pureCropName = if (rawTrimmed.contains("(")) {
            rawTrimmed.substringBefore("(").trim()
        } else {
            rawTrimmed.ifBlank { "Wheat" }
        }

        val seasonInText = if (rawTrimmed.contains("(")) {
            rawTrimmed.substringAfter("(").substringBefore(")").trim()
        } else {
            ""
        }

        // 1. Try exact match by "CropName (SeasonName)"
        var matching = cropCycles.firstOrNull {
            "${it.cropName} (${it.seasonName})".equals(rawTrimmed, ignoreCase = true)
        }

        // 2. Try match by cropName case-insensitively
        if (matching == null) {
            matching = cropCycles.firstOrNull {
                it.cropName.equals(pureCropName, ignoreCase = true)
            }
        }

        // 3. If no matching cycle exists, auto-create a new CropCycle for this crop
        if (matching == null) {
            val cal = Calendar.getInstance()
            val currYear = cal.get(Calendar.YEAR)
            val currMonth = cal.get(Calendar.MONTH)
            val autoSeasonName = if (seasonInText.isNotBlank()) {
                seasonInText
            } else {
                when (currMonth) {
                    in 5..9 -> "Kharif $currYear"
                    in 3..4 -> "Zaid $currYear"
                    else -> "Rabi $currYear"
                }
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

            val autoCycle = CropCycle(
                id = "cycle_${System.currentTimeMillis()}_${(100..999).random()}",
                userId = uid,
                cropName = pureCropName,
                seasonName = autoSeasonName,
                areaAcres = 1.0,
                startDateStr = todayStr,
                isCompleted = false,
                timestamp = System.currentTimeMillis()
            )

            cropCycles.add(0, autoCycle)
            saveLocalCropCycles(context, cropCycles)
            repository?.addCropCycle(autoCycle) { _ -> }
            matching = autoCycle
        }

        return matching
    }

    fun reconcileOrphanedKhataItems(
        context: Context,
        repository: KhataRepository?,
        cropCycles: MutableList<CropCycle>,
        khataItems: MutableList<KhataItem>
    ): Boolean {
        var modified = false
        val cal = Calendar.getInstance()
        val currYear = cal.get(Calendar.YEAR)
        val currMonth = cal.get(Calendar.MONTH)
        val defaultSeason = when (currMonth) {
            in 5..9 -> "Kharif $currYear"
            in 3..4 -> "Zaid $currYear"
            else -> "Rabi $currYear"
        }

        for (item in khataItems) {
            if (item.cropCycleId.isBlank()) {
                val cropName = item.cropName.trim().ifBlank { "Wheat" }
                var matching = cropCycles.firstOrNull { it.cropName.equals(cropName, ignoreCase = true) }
                if (matching == null) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                    matching = CropCycle(
                        id = "cycle_${System.currentTimeMillis()}_${(100..999).random()}",
                        userId = uid,
                        cropName = cropName,
                        seasonName = defaultSeason,
                        areaAcres = 1.0,
                        startDateStr = todayStr,
                        isCompleted = false,
                        timestamp = System.currentTimeMillis()
                    )
                    cropCycles.add(0, matching)
                    repository?.addCropCycle(matching) { _ -> }
                    saveLocalCropCycles(context, cropCycles)
                }
                item.cropCycleId = matching.id
                item.cropName = matching.cropName
                repository?.addKhataItem(item) { _ -> }
                modified = true
            }
        }

        if (modified) {
            saveLocalKhataItems(context, khataItems)
        }
        return modified
    }
}
