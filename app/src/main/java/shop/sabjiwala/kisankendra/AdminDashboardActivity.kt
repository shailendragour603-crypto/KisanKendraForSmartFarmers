package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.ActivityAdminDashboardBinding
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.model.parseMandiRecordFromMap
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.SportsConfigHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val jsonFilePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("📄 Upload Mandi Rates JSON?")
                .setMessage("Do you want to upload and process Mandi rates from this selected JSON file into Firebase?")
                .setPositiveButton("📤 Yes, Upload & Sync") { _, _ ->
                    uploadMandiJsonFromFile(uri)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateResources(newBase, "en"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setSupportActionBar(binding.toolbarAdminDashboard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarAdminDashboard.setNavigationOnClickListener { finish() }

        binding.cardManageFeedback.setOnClickListener {
            startActivity(Intent(this, AdminFeedbackActivity::class.java))
        }

        binding.cardManageReportedPosts.setOnClickListener {
            startActivity(Intent(this, AdminReportedPostsActivity::class.java))
        }

        binding.cardPostNews.setOnClickListener {
            startActivity(Intent(this, AdminCreateNewsActivity::class.java))
        }

        binding.cardEmergencySyncMandi.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚡ Trigger Emergency Mandi Sync?")
                .setMessage("This will fetch live prices directly from data.gov.in and overwrite Mandi rates across all states in Firebase.\n\nDo you want to proceed?")
                .setPositiveButton("⚡ Yes, Sync Now") { _, _ ->
                    triggerEmergencyApiSync()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.cardUploadMandiJson.setOnClickListener {
            jsonFilePickerLauncher.launch("application/json")
        }

        binding.cardManageTranslations.setOnClickListener {
            startActivity(Intent(this, AdminManageTranslationsActivity::class.java))
        }

        binding.cardManageScreenAlerts.setOnClickListener {
            startActivity(Intent(this, AdminManageAlertsActivity::class.java))
        }

        binding.cardManageNotifications.setOnClickListener {
            startActivity(Intent(this, AdminNotificationActivity::class.java))
        }

        binding.cardManageGeminiApiKey.setOnClickListener {
            shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.showManageApiKeyDialog(this)
        }

        binding.cardManageSportsButton.setOnClickListener {
            showManageSportsButtonDialog()
        }

        loadFirebaseMandiSyncTimestamp()
    }

    override fun onResume() {
        super.onResume()
        loadFirebaseMandiSyncTimestamp()
    }

    private fun loadFirebaseMandiSyncTimestamp() {
        binding.tvMandiSyncTimestamp.text = "🔄 Checking Firebase Sync Time..."
        firestore.collection("mandi_cache").document("latest").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val timestamp = doc.getTimestamp("updatedAt")
                    if (timestamp != null) {
                        val df = SimpleDateFormat("dd MMM, hh:mm a 'IST'", Locale.ENGLISH)
                        df.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                        val formattedDate = df.format(timestamp.toDate())
                        binding.tvMandiSyncTimestamp.text = "🕒 Last Synced: $formattedDate (All 28 States Synced)"
                    } else {
                        binding.tvMandiSyncTimestamp.text = "🕒 Last Synced: Today"
                    }
                } else {
                    binding.tvMandiSyncTimestamp.text = "🕒 Last Synced: No cache found"
                }
            }
            .addOnFailureListener {
                binding.tvMandiSyncTimestamp.text = "🕒 Last Synced: Unable to check"
            }
    }

    private fun triggerEmergencyApiSync() {
        Toast.makeText(this, "⚡ Starting Emergency Mandi API Sync & Auto-Dictionary Builder...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = MandiRepository()
                val apiKey = "579b464db66ec23bdd000001f0acdf7b0b4943fe5b9998c1304ba55f"

                // Read live records first, then fetch previous/yesterday records to compare price changes accurately
                val liveRecords = repository.fetchMandiRatesFromApi(apiKey = apiKey, limit = 2500)
                val previousRecords = repository.getPreviousDayRecords(liveRecords)

                if (liveRecords.isNotEmpty()) {
                    val recordsWithDiff = calculatePriceDifferences(liveRecords, previousRecords)
                    repository.saveMandiRatesToFirestore(recordsWithDiff)

                    val uniqueStates = liveRecords.mapNotNull { it.state }.distinct().size

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AdminDashboardActivity,
                            "✅ Emergency Sync Successful! Synced ${liveRecords.size} records across $uniqueStates states.",
                            Toast.LENGTH_LONG
                        ).show()
                        loadFirebaseMandiSyncTimestamp()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AdminDashboardActivity,
                            "⚠️ API returned 0 records. Please check API key or try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminDashboardActivity,
                        "❌ Emergency Sync Failed: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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

    private fun uploadMandiJsonFromFile(uri: Uri) {
        Toast.makeText(this, "Reading JSON backup file...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = reader.readLine()
                }
                inputStream?.close()

                val jsonStr = stringBuilder.toString()
                val gson = Gson()
                // Support both direct list or object with "records" key
                val records: List<MandiRecord>? = try {
                    val listType = object : TypeToken<List<MandiRecord>>() {}.type
                    gson.fromJson<List<MandiRecord>>(jsonStr, listType)
                } catch (_: Exception) {
                    // Try parsing as wrapper object
                    val wrapperType = object : TypeToken<Map<String, Any>>() {}.type
                    val map = gson.fromJson<Map<String, Any>>(jsonStr, wrapperType)
                    val rawList = map["records"] as? List<Map<String, Any>>
                    rawList?.map { m -> parseMandiRecordFromMap(m) }
                }

                if (!records.isNullOrEmpty()) {
                    val repository = MandiRepository()
                    val previousRecords = repository.getPreviousDayRecords(records)
                    val recordsWithDiff = calculatePriceDifferences(records, previousRecords)

                    repository.saveMandiRatesToFirestore(recordsWithDiff)
                    val uniqueStates = recordsWithDiff.mapNotNull { it.state }.distinct().size

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AdminDashboardActivity,
                            "Successfully uploaded and synced ${records.size} mandi rates across $uniqueStates states with price trend comparisons!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdminDashboardActivity, "Error: JSON file contains no valid mandi records.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminDashboardActivity, "Upload Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showManageSportsButtonDialog() {
        val currentVisible = SportsConfigHelper.isSportsButtonVisible(this)
        val currentUrl = SportsConfigHelper.getSportsWebUrl(this)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val messageTv = android.widget.TextView(this).apply {
            text = "1. Toggle Sports icon visibility in top bar header across all user devices.\n2. Change the Sports Web URL / Widget link anytime without releasing an app update."
            textSize = 13f
            setPadding(0, 0, 0, 15)
        }
        layout.addView(messageTv)

        val urlInputLabel = android.widget.TextView(this).apply {
            text = "🌐 Sports Web Page / Score Widget URL:"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f
            setPadding(0, 10, 0, 5)
        }
        layout.addView(urlInputLabel)

        val urlInput = android.widget.EditText(this).apply {
            hint = "e.g. https://cwidget.crictimes.org/... or https://m.cricbuzz.com"
            setText(currentUrl)
            textSize = 13f
        }
        layout.addView(urlInput)

        val statusText = if (currentVisible) "Header Icon Status: VISIBLE ✅" else "Header Icon Status: HIDDEN 🚫"
        val statusTv = android.widget.TextView(this).apply {
            text = statusText
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f
            setPadding(0, 20, 0, 10)
        }
        layout.addView(statusTv)

        val toggleActionText = if (currentVisible) "🚫 Hide Icon & Save" else "✅ Show Icon & Save"

        MaterialAlertDialogBuilder(this)
            .setTitle("🏏 Manage Sports & Cricket Screen")
            .setView(layout)
            .setPositiveButton(toggleActionText) { _, _ ->
                val newUrl = urlInput.text.toString().trim()
                saveSportsConfigAndUpdate(!currentVisible, newUrl)
            }
            .setNeutralButton("💾 Save URL Only") { _, _ ->
                val newUrl = urlInput.text.toString().trim()
                saveSportsConfigAndUpdate(currentVisible, newUrl)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSportsConfigAndUpdate(show: Boolean, webUrl: String) {
        val activeUrl = if (webUrl.isNotBlank()) webUrl else SportsConfigHelper.DEFAULT_WEB_URL
        SportsConfigHelper.saveSportsConfig(
            context = this,
            showSports = show,
            webUrl = activeUrl,
            onSuccess = {
                val statusText = if (show) "VISIBLE ✅" else "HIDDEN 🚫"
                Toast.makeText(this, "Live Sports updated! Header button is now $statusText across all devices in real-time!", Toast.LENGTH_LONG).show()
            },
            onFailure = { e ->
                Toast.makeText(this, "Saved locally, but cloud sync failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
