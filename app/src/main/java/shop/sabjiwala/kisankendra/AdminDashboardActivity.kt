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
import java.io.BufferedReader
import java.io.InputStreamReader

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val jsonFilePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadMandiJsonFromFile(uri)
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

        binding.cardPostNews.setOnClickListener {
            startActivity(Intent(this, AdminCreateNewsActivity::class.java))
        }

        binding.cardUploadMandiJson.setOnClickListener {
            jsonFilePickerLauncher.launch("application/json")
        }

        binding.cardManageScreenAlerts.setOnClickListener {
            startActivity(Intent(this, AdminManageAlertsActivity::class.java))
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
                    val serializedRecords = records.map { record ->
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdminDashboardActivity, "Successfully uploaded ${records.size} mandi rates to Firebase!", Toast.LENGTH_LONG).show()
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
}
