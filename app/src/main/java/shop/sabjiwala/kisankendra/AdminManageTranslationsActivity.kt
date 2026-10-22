package shop.sabjiwala.kisankendra

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.ActivityAdminManageTranslationsBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class AdminManageTranslationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminManageTranslationsBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var currentLangCode = "hi"
    private var allEntries = mutableListOf<TranslationEntry>()
    private var filteredEntries = mutableListOf<TranslationEntry>()
    private lateinit var adapter: AdminTranslationAdapter

    private val supportedLangs = listOf(
        "hi" to "🇮🇳 Hindi (हिन्दी)",
        "en" to "🇬🇧 English"
    )

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminManageTranslationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbarAdminTranslations)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarAdminTranslations.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupLanguageSpinner()
        setupSearchFilter()

        binding.fabAddTranslation.setOnClickListener {
            showAddTranslationDialog()
        }

        loadMasterDictionaryFromFirestore()
    }

    private fun setupRecyclerView() {
        adapter = AdminTranslationAdapter(
            items = emptyList(),
            onEditClick = { entry -> showEditTranslationDialog(entry) },
            onDeleteClick = { entry -> confirmDeleteTranslation(entry) }
        )
        binding.rvAdminTranslations.layoutManager = LinearLayoutManager(this)
        binding.rvAdminTranslations.adapter = adapter
    }

    private fun setupLanguageSpinner() {
        val langNames = supportedLangs.map { it.second }
        val adapterLangs = ArrayAdapter(this, R.layout.item_compact_dropdown_single, langNames)
        binding.spinnerLang.setAdapter(adapterLangs)
        binding.spinnerLang.setText(supportedLangs.first().second, false)

        binding.spinnerLang.setOnItemClickListener { _, _, position, _ ->
            currentLangCode = supportedLangs.getOrNull(position)?.first ?: "hi"
            loadMasterDictionaryFromFirestore()
        }
    }

    private fun setupSearchFilter() {
        binding.etSearchTranslation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterEntries()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterEntries() {
        val query = binding.etSearchTranslation.text?.toString()?.trim()?.lowercase().orEmpty()
        filteredEntries = if (query.isBlank()) {
            allEntries.toMutableList()
        } else {
            allEntries.filter { entry ->
                entry.englishKey.lowercase().contains(query) || entry.translation.lowercase().contains(query)
            }.toMutableList()
        }

        adapter.updateData(filteredEntries)
        binding.tvTranslationCount.text = "Count: ${filteredEntries.size}"
        binding.tvEmptyTranslations.visibility = if (filteredEntries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadMasterDictionaryFromFirestore() {
        binding.pbAdminTranslations.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = firestore.collection("app_translations").document(currentLangCode).get().await()
                allEntries.clear()

                if (doc.exists()) {
                    val dict = mutableMapOf<String, String>()

                    val rawDict = doc.get("dictionary") as? Map<*, *>
                    if (rawDict != null) {
                        shop.sabjiwala.kisankendra.utils.TranslationHelper.extractMapEntries(rawDict, dict)
                    }

                    // Check for legacy root-level "dictionary.xyz" fields from previous buggy saves
                    val docData = doc.data
                    var legacyFieldsFound = false
                    if (docData != null) {
                        for ((fKey, fVal) in docData) {
                            if (fKey.startsWith("dictionary.") && fVal != null) {
                                val cleanKey = fKey.removePrefix("dictionary.")
                                if (cleanKey.isNotBlank()) {
                                    dict[cleanKey] = fVal.toString()
                                    legacyFieldsFound = true
                                }
                            }
                        }
                    }

                    // Migrate legacy fields into nested dictionary map in Firestore if found
                    if (legacyFieldsFound) {
                        try {
                            val docRef = firestore.collection("app_translations").document(currentLangCode)
                            docRef.set(hashMapOf("dictionary" to dict, "updatedAt" to Timestamp.now()), SetOptions.merge()).await()
                            if (docData != null) {
                                val deleteMap = hashMapOf<String, Any>()
                                for ((fKey, _) in docData) {
                                    if (fKey.startsWith("dictionary.")) {
                                        deleteMap[fKey] = com.google.firebase.firestore.FieldValue.delete()
                                    }
                                }
                                if (deleteMap.isNotEmpty()) {
                                    docRef.update(deleteMap).await()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    for ((key, value) in dict) {
                        allEntries.add(TranslationEntry(englishKey = key, translation = value))
                    }
                    allEntries.sortBy { it.englishKey }

                    // Synchronize active language entries to TranslationHelper cache
                    for ((key, value) in dict) {
                        shop.sabjiwala.kisankendra.utils.TranslationHelper.updateMasterCache(key, value, currentLangCode)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    filterEntries()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    Toast.makeText(this@AdminManageTranslationsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditTranslationDialog(entry: TranslationEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_translation, null, false)
        val etKey = dialogView.findViewById<EditText>(R.id.etTransKey)
        val etValue = dialogView.findViewById<EditText>(R.id.etTransValue)

        etKey.setText(entry.englishKey)
        etKey.isEnabled = false // Key is read-only when editing
        etValue.setText(entry.translation)

        MaterialAlertDialogBuilder(this)
            .setTitle("✏️ Edit Translation (${currentLangCode.uppercase()})")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newTrans = etValue.text?.toString()?.trim().orEmpty()
                if (newTrans.isNotBlank()) {
                    saveSingleTranslationToFirestore(entry.englishKey, newTrans)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTranslationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_translation, null, false)
        val etKey = dialogView.findViewById<EditText>(R.id.etTransKey)
        val etValue = dialogView.findViewById<EditText>(R.id.etTransValue)

        MaterialAlertDialogBuilder(this)
            .setTitle("➕ Add New Translation (${currentLangCode.uppercase()})")
            .setView(dialogView)
            .setPositiveButton("Add & Save") { dialog, _ ->
                val key = etKey.text?.toString()?.trim()?.lowercase().orEmpty()
                val value = etValue.text?.toString()?.trim().orEmpty()

                if (key.isNotBlank() && value.isNotBlank()) {
                    saveSingleTranslationToFirestore(key, value)
                } else {
                    Toast.makeText(this, "Key and Translation cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteTranslation(entry: TranslationEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ Delete Translation")
            .setMessage("Are you sure you want to delete translation for '${entry.englishKey}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteSingleTranslationFromFirestore(entry.englishKey)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSingleTranslationToFirestore(key: String, value: String) {
        binding.pbAdminTranslations.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val docRef = firestore.collection("app_translations").document(currentLangCode)

                // Save using FieldPath so keys with dots (e.g. "a.p.m.c.") are not split into nested maps
                val dictPath = com.google.firebase.firestore.FieldPath.of("dictionary", key)
                val updatedPath = com.google.firebase.firestore.FieldPath.of("updatedAt")

                try {
                    docRef.update(dictPath, value, updatedPath, Timestamp.now()).await()
                } catch (_: Exception) {
                    val initialMap = hashMapOf<String, Any>(
                        "updatedAt" to Timestamp.now(),
                        "dictionary" to hashMapOf(key to value)
                    )
                    docRef.set(initialMap, SetOptions.merge()).await()
                }

                // Update memory cache and SharedPreferences for current selected language
                shop.sabjiwala.kisankendra.utils.TranslationHelper.updateMasterCacheAndPrefs(
                    this@AdminManageTranslationsActivity,
                    key,
                    value,
                    currentLangCode
                )

                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    Toast.makeText(this@AdminManageTranslationsActivity, "✅ Translation saved successfully!", Toast.LENGTH_SHORT).show()
                    loadMasterDictionaryFromFirestore()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    Toast.makeText(this@AdminManageTranslationsActivity, "Error saving: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteSingleTranslationFromFirestore(key: String) {
        binding.pbAdminTranslations.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val docRef = firestore.collection("app_translations").document(currentLangCode)

                // Delete key from dictionary map using FieldPath
                val dictPath = com.google.firebase.firestore.FieldPath.of("dictionary", key)
                val updatedPath = com.google.firebase.firestore.FieldPath.of("updatedAt")

                docRef.update(dictPath, com.google.firebase.firestore.FieldValue.delete(), updatedPath, Timestamp.now()).await()

                // Also attempt deleting legacy root-level field if it existed
                try {
                    docRef.update("dictionary.$key", com.google.firebase.firestore.FieldValue.delete()).await()
                } catch (_: Exception) {}

                // Remove from local memory cache and SharedPreferences
                shop.sabjiwala.kisankendra.utils.TranslationHelper.removeMasterCacheAndPrefs(
                    this@AdminManageTranslationsActivity,
                    key,
                    currentLangCode
                )

                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    Toast.makeText(this@AdminManageTranslationsActivity, "🗑️ Translation deleted!", Toast.LENGTH_SHORT).show()
                    loadMasterDictionaryFromFirestore()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbAdminTranslations.visibility = View.GONE
                    Toast.makeText(this@AdminManageTranslationsActivity, "Error deleting: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
