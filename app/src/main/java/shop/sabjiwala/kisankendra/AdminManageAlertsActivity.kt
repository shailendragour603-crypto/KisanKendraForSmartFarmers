package shop.sabjiwala.kisankendra

import android.R
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import shop.sabjiwala.kisankendra.databinding.ActivityAdminManageAlertsBinding
import shop.sabjiwala.kisankendra.model.ScreenAlert
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class AdminManageAlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminManageAlertsBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val screenList = listOf(
        "mandi_screen" to "Mandi Prices (मंडी भाव)",
        "weather_screen" to "Weather Forecast (मौसम जानकारी)",
        "news_screen" to "Agri News & Advisory (कृषि समाचार)",
        "forum_screen" to "Farmers Forum (किसान चर्चा)",
        "khata_screen" to "Kisan Diary & Khata (किसान खाता)",
        "home_screen" to "Home Screen (मुख्य पेज)"
    )

    private var selectedScreenId: String = "mandi_screen"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateResources(newBase, "en"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminManageAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setSupportActionBar(binding.toolbarAdminManageAlerts)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarAdminManageAlerts.setNavigationOnClickListener { finish() }

        setupScreenDropdown()
        setupFocusListeners()

        binding.btnSaveAlert.setOnClickListener {
            saveAlertToFirestore(isActive = binding.switchIsActive.isChecked)
        }

        binding.btnDeactivateAlert.setOnClickListener {
            binding.switchIsActive.isChecked = false
            saveAlertToFirestore(isActive = false)
        }
    }

    private fun setupFocusListeners() {
        val fields = listOf(
            binding.etTitleHi,
            binding.etMessageHi,
            binding.etTitleEn,
            binding.etMessageEn
        )
        for (field in fields) {
            field.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollViewAdminAlerts.postDelayed({
                        val rect = Rect(0, 0, view.width, view.height + 120)
                        view.requestRectangleOnScreen(rect, true)
                    }, 200)
                }
            }
        }
    }

    private fun setupScreenDropdown() {
        val adapterLabels = screenList.map { it.second }
        val adapter = ArrayAdapter(this, R.layout.simple_spinner_dropdown_item, adapterLabels)
        binding.autoCompleteScreenSelect.setAdapter(adapter)

        // Select first item by default
        binding.autoCompleteScreenSelect.setText(adapterLabels[0], false)
        selectedScreenId = screenList[0].first
        loadAlertForScreen(selectedScreenId)

        binding.autoCompleteScreenSelect.setOnItemClickListener { _, _, position, _ ->
            selectedScreenId = screenList[position].first
            loadAlertForScreen(selectedScreenId)
        }
    }

    private fun loadAlertForScreen(screenId: String) {
        firestore.collection("app_screen_alerts")
            .document(screenId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val rawActive = snapshot.getBoolean("active") ?: snapshot.getBoolean("isActive") ?: false
                    val rawBlocking = snapshot.getBoolean("blocking") ?: snapshot.getBoolean("isBlocking") ?: false
                    val alert = snapshot.toObject(ScreenAlert::class.java)?.apply {
                        this.isActive = rawActive
                        this.isBlocking = rawBlocking
                    }
                    if (alert != null) {
                        populateForm(alert)
                        return@addOnSuccessListener
                    }
                }
                // Clear if no alert document exists yet
                clearForm()
            }
            .addOnFailureListener {
                clearForm()
            }
    }

    private fun populateForm(alert: ScreenAlert) {
        binding.switchIsActive.isChecked = alert.isActive
        binding.etTitleHi.setText(alert.titleHi)
        binding.etMessageHi.setText(alert.messageHi)
        binding.etTitleEn.setText(alert.titleEn)
        binding.etMessageEn.setText(alert.messageEn)

        when (alert.alertType.uppercase()) {
            "WARNING" -> binding.rbTypeWarning.isChecked = true
            "MAINTENANCE" -> binding.rbTypeMaintenance.isChecked = true
            else -> binding.rbTypeInfo.isChecked = true
        }
    }

    private fun clearForm() {
        binding.switchIsActive.isChecked = false
        binding.rbTypeInfo.isChecked = true
        binding.etTitleHi.setText("")
        binding.etMessageHi.setText("")
        binding.etTitleEn.setText("")
        binding.etMessageEn.setText("")
    }

    private fun saveAlertToFirestore(isActive: Boolean) {
        val alertType = when {
            binding.rbTypeWarning.isChecked -> "WARNING"
            binding.rbTypeMaintenance.isChecked -> "MAINTENANCE"
            else -> "INFO"
        }

        val alert = ScreenAlert(
            screenId = selectedScreenId,
            isActive = isActive,
            alertType = alertType,
            titleHi = binding.etTitleHi.text?.toString()?.trim().orEmpty(),
            messageHi = binding.etMessageHi.text?.toString()?.trim().orEmpty(),
            titleEn = binding.etTitleEn.text?.toString()?.trim().orEmpty(),
            messageEn = binding.etMessageEn.text?.toString()?.trim().orEmpty(),
            updatedAt = System.currentTimeMillis()
        )

        firestore.collection("app_screen_alerts")
            .document(selectedScreenId)
            .set(alert)
            .addOnSuccessListener {
                val statusText = if (isActive) "Activated" else "Deactivated"
                Toast.makeText(this, "Alert $statusText for $selectedScreenId!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save alert: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}
