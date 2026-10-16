package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KisanDiaryHistoryActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    private val khataRepo by lazy { KhataRepository() }
    private lateinit var khataAdapter: KhataAdapter
    private var allKhataItems = mutableListOf<KhataItem>()
    private var displayedKhataItems = mutableListOf<KhataItem>()
    private var cropCycles = mutableListOf<CropCycle>()
    private var selectedFilter = "All Entries"
    private var currentPage = 1
    private val pageSize = 10

    private var historyInterstitialAd: InterstitialAd? = null
    private var lastHistoryInterstitialTime = 0L

    companion object {
        private const val MIN_INTERSTITIAL_GAP_MS = 60_000L // 60s minimum gap
    }

    private fun loadHistoryInterstitialAd() {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                this,
                "ca-app-pub-3054731531860427/5297828555",
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        historyInterstitialAd = ad
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        historyInterstitialAd = null
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryShowHistoryInterstitialAd(onComplete: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (historyInterstitialAd != null && currentTime - lastHistoryInterstitialTime >= MIN_INTERSTITIAL_GAP_MS) {
            historyInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    historyInterstitialAd = null
                    lastHistoryInterstitialTime = System.currentTimeMillis()
                    loadHistoryInterstitialAd()
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    historyInterstitialAd = null
                    lastHistoryInterstitialTime = System.currentTimeMillis()
                    loadHistoryInterstitialAd()
                    onComplete()
                }
            }
            historyInterstitialAd?.show(this)
        } else {
            onComplete()
            if (historyInterstitialAd == null) {
                loadHistoryInterstitialAd()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kisan_diary_history)

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        loadData()
        loadHistoryInterstitialAd()

        if (intent.getBooleanExtra("EXTRA_OPEN_ADD_ENTRY", false)) {
            showAddKhataDialog(null)
        }
    }

    private fun setupSwipeRefresh() {
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshDiaryHistory) ?: return
        val rv = findViewById<RecyclerView>(R.id.rvAllKhataEntries)
        ShimmerHelper.setupSwipeRefresh(swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            if (rv != null) ShimmerHelper.applyShimmer(rv)
            loadData()
            if (rv != null) ShimmerHelper.stopShimmer(rv)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun setupWindowInsets() {
        val rootLayout = findViewById<View>(R.id.rootHistoryLayout)
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarHistory)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvAllKhataEntries)
        val layoutManager = LinearLayoutManager(this)
        rv.layoutManager = layoutManager
        khataAdapter = KhataAdapter(
            items = emptyList(),
            onEditClick = { item -> showAddKhataDialog(item) },
            onDeleteClick = { item -> deleteEntry(item) },
            onLoadMore = { loadMoreItems() }
        )
        rv.adapter = khataAdapter

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisiblesItems = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                        loadMoreItems()
                    }
                }
            }
        })
    }

    private fun loadMoreItems() {
        val filtered = getFilteredList()
        if (displayedKhataItems.size < filtered.size) {
            currentPage++
            updateDisplayedList()
        }
    }

    private fun getFilteredList(): List<KhataItem> {
        val sorted = allKhataItems.sortedByDescending { it.timestamp }
        return if (selectedFilter == "All Entries") {
            sorted
        } else {
            sorted.filter { item ->
                val matchingCycle = cropCycles.firstOrNull { it.id == item.cropCycleId }
                if (matchingCycle != null) {
                    "${matchingCycle.cropName} (${matchingCycle.seasonName})" == selectedFilter
                } else {
                    item.cropName.contains(selectedFilter.substringBefore("(").trim(), ignoreCase = true)
                }
            }
        }
    }

    private fun updateDisplayedList() {
        val filtered = getFilteredList()
        val limit = (currentPage * pageSize).coerceAtMost(filtered.size)
        displayedKhataItems = filtered.take(limit).toMutableList()
        khataAdapter.updateData(displayedKhataItems, filtered.size)

        val layoutEmpty = findViewById<View>(R.id.layoutHistoryEmptyState)
        if (filtered.isEmpty()) {
            layoutEmpty?.visibility = View.VISIBLE
            val tvEmptyTitle = findViewById<TextView>(R.id.tvHistoryEmptyTitle)
            val tvEmptySubtitle = findViewById<TextView>(R.id.tvHistoryEmptySubtitle)
            val btnAddFirst = findViewById<MaterialButton>(R.id.btnAddFirstEntryHistory)

            val activeLang = LocaleHelper.getPersistedLanguage(this)
            val transTitle = TranslationHelper.getDirectTranslation("Kisan Diary is Empty", activeLang)
                ?: if (activeLang == "hi") "किसान डायरी खाली है" else "Kisan Diary is Empty"
            val transSubtitle = TranslationHelper.getDirectTranslation("Add an entry to track seed, fertilizer, or labor expenses.", activeLang)
                ?: if (activeLang == "hi") "अपने बीज, खाद या मजदूरी के खर्चों का हिसाब रखने के लिए एन्ट्री जोड़ें।" else "Add an entry to track seed, fertilizer, or labor expenses."
            val transBtn = TranslationHelper.getDirectTranslation("+ Add New Entry", activeLang)
                ?: if (activeLang == "hi") "नई एन्ट्री जोड़ें" else "Add New Entry"

            tvEmptyTitle?.text = transTitle
            tvEmptySubtitle?.text = transSubtitle
            btnAddFirst?.text = transBtn

            btnAddFirst?.setOnClickListener {
                showAddKhataDialog(null)
            }
        } else {
            layoutEmpty?.visibility = View.GONE
        }
    }

    private fun setupFab() {
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabHistoryAdd)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val addEntryBgColor = Color.parseColor(if (isNight) "#FBBF24" else "#D97706")
        val addEntryTextColor = Color.parseColor(if (isNight) "#451A03" else "#FFFFFF")
        val addEntryRippleColor = Color.parseColor(if (isNight) "#FDE68A" else "#B45309")

        fab.backgroundTintList = ColorStateList.valueOf(addEntryBgColor)
        fab.setTextColor(addEntryTextColor)
        fab.rippleColor = ColorStateList.valueOf(addEntryRippleColor)
        MediaUtils.startPulseAnimation(fab)

        fab.setOnClickListener {
            showAddKhataDialog(null)
        }
    }

    private fun loadData() {
        allKhataItems = KhataHelper.getLocalKhataItems(this).toMutableList()
        cropCycles = KhataHelper.getLocalCropCycles(this).toMutableList()
        KhataHelper.reconcileOrphanedKhataItems(this, khataRepo, cropCycles, allKhataItems)
        updateFilterDropdown()
        filterAndApplyData()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // ⚡ Smart Local-First Restore:
            // ONLY query Firestore if local device cache is empty (e.g. fresh install or app data cleared).
            if (allKhataItems.isEmpty()) {
                khataRepo.fetchKhataItems { remoteItems ->
                    if (remoteItems.isNotEmpty()) {
                        allKhataItems = remoteItems.sortedByDescending { it.timestamp }.toMutableList()
                        KhataHelper.saveLocalKhataItems(this, allKhataItems)
                        KhataHelper.reconcileOrphanedKhataItems(this, khataRepo, cropCycles, allKhataItems)
                        filterAndApplyData()
                    }
                }
            }
            if (cropCycles.isEmpty()) {
                khataRepo.fetchCropCycles { remoteCycles ->
                    if (remoteCycles.isNotEmpty()) {
                        cropCycles = remoteCycles.sortedByDescending { it.timestamp }.toMutableList()
                        KhataHelper.saveLocalCropCycles(this, cropCycles)
                        KhataHelper.reconcileOrphanedKhataItems(this, khataRepo, cropCycles, allKhataItems)
                        updateFilterDropdown()
                    }
                }
            }
        }
    }

    private fun updateFilterDropdown() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val actvFilter = findViewById<AutoCompleteTextView>(R.id.actvHistoryFilter)
        val defaultOption = TranslationHelper.getDirectTranslation("All Entries", activeLang)
            ?: if (activeLang == "hi") "सभी एंट्री" else "All Entries"

        val filterOptions = mutableListOf(defaultOption)
        for (cycle in cropCycles) {
            val transCrop = TranslationHelper.getTranslatedCropName("${cycle.cropName} (${cycle.seasonName})", activeLang, this)
            filterOptions.add(transCrop)
        }
        val adapter = ArrayAdapter(this, R.layout.item_compact_dropdown_single, filterOptions)
        actvFilter.setAdapter(adapter)

        val displaySelected = if (selectedFilter == "All Entries") defaultOption else TranslationHelper.getTranslatedCropName(selectedFilter, activeLang, this)
        actvFilter.setText(displaySelected, false)

        actvFilter.setOnItemClickListener { _, _, position, _ ->
            selectedFilter = if (position == 0) "All Entries" else {
                val cycle = cropCycles.getOrNull(position - 1)
                if (cycle != null) "${cycle.cropName} (${cycle.seasonName})" else filterOptions.getOrElse(position) { "All Entries" }
            }
            filterAndApplyData()
        }
    }

    private fun filterAndApplyData() {
        currentPage = 1
        updateDisplayedList()
    }

    private fun deleteEntry(item: KhataItem) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this Kisan Diary entry?")
            .setPositiveButton("Delete") { _, _ ->
                allKhataItems.remove(item)
                KhataHelper.saveLocalKhataItems(this, allKhataItems)
                filterAndApplyData()
                khataRepo.deleteKhataItem(item.id) { _ -> }
                Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddKhataDialog(existingItem: KhataItem?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_khata_entry, null)
        val toggleType = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleKhataType)
        val etAmount = dialogView.findViewById<EditText>(R.id.etKhataAmount)
        val actvCrop = dialogView.findViewById<AutoCompleteTextView>(R.id.actvKhataCrop)
        val actvCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.actvKhataCategory)
        val actvStage = dialogView.findViewById<AutoCompleteTextView>(R.id.actvKhataStage)
        val etNote = dialogView.findViewById<EditText>(R.id.etKhataNote)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnKhataSave)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnKhataCancel)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvKhataDialogTitle)

        val isEdit = existingItem != null
        if (isEdit) {
            tvTitle?.text = "Edit Entry"
            btnSave.text = "Update Entry"
            etAmount.setText(existingItem.amount.toInt().toString())
            etNote.setText(existingItem.note)
            if (existingItem.isIncome) {
                toggleType.check(R.id.btnTypeIncome)
            } else {
                toggleType.check(R.id.btnTypeExpense)
            }
        } else {
            tvTitle?.text = "📖 Add New Entry"
        }

        val expenseCategories = listOf(
            "Seeds",
            "Fertilizer",
            "Pesticide",
            "Irrigation",
            "Machinery",
            "Fuel",
            "Labour",
            "Tillage",
            "Storage",
            "Transport",
            "Insurance",
            "Loan Interest"
        )
        val incomeCategories = listOf(
            "Crop Sale",
            "Vegetable Sale",
            "Tractor / Equipment Rental",
            "Fodder Sale"
        )
        val stages = listOf("Sowing", "Irrigation", "Spray", "Harvesting", "Sale")

        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val getTranslatedSeason: (String) -> String = { s ->
            if (activeLang == "hi") {
                s.replace("Kharif", "खरीफ", ignoreCase = true)
                 .replace("Rabi", "रबी", ignoreCase = true)
                 .replace("Zaid", "जायद", ignoreCase = true)
            } else {
                s
            }
        }
        val getCanonicalSeason: (String) -> String = { s ->
            s.replace("खरीफ", "Kharif", ignoreCase = true)
             .replace("रबी", "Rabi", ignoreCase = true)
             .replace("जायद", "Zaid", ignoreCase = true)
        }

        val getTranslatedCategories: (Boolean) -> List<String> = { income ->
            val list = if (income) incomeCategories else expenseCategories
            list.map { cat ->
                TranslationHelper.getTranslatedCategoryName(cat, activeLang, this)
            }
        }

        var isIncome = isEdit && existingItem?.isIncome == true
        if (isIncome) {
            toggleType.check(R.id.btnTypeIncome)
        } else {
            toggleType.check(R.id.btnTypeExpense)
        }

        val updateCategoryDropdown = { income: Boolean ->
            val transCats = getTranslatedCategories(income)
            actvCategory.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, transCats))
            if (transCats.isNotEmpty()) {
                val currentText = actvCategory.text.toString()
                if (!transCats.contains(currentText)) {
                    actvCategory.setText(transCats[0], false)
                }
            }
        }

        val transStages = stages.map { stage ->
            TranslationHelper.getTranslatedStageName(stage, activeLang, this)
        }

        val baseCycles = cropCycles.map { "${it.cropName} (${it.seasonName})" }
        val rawMasterCrops = TranslationHelper.DEFAULT_HINDI_CROPS_LIST.filterNot { it.contains("अन्य फसल") }
        val allAvailableCrops = (baseCycles + rawMasterCrops).distinct()
        val activeCropNames = allAvailableCrops.map { cropItem ->
            if (cropItem.startsWith("---")) {
                if (activeLang == "hi") cropItem else TranslationHelper.getTranslatedCropName(cropItem, activeLang, this)
            } else {
                TranslationHelper.getTranslatedCropName(cropItem, activeLang, this)
            }
        }
        actvCrop.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, activeCropNames))

        val defaultCropText = if (isEdit && existingItem?.cropName != null) {
            val matchingCycleObj = cropCycles.firstOrNull { it.cropName.equals(existingItem.cropName, ignoreCase = true) }
            val baseText = if (matchingCycleObj != null) "${matchingCycleObj.cropName} (${matchingCycleObj.seasonName})" else existingItem.cropName
            TranslationHelper.getTranslatedCropName(baseText, activeLang, this)
        } else if (cropCycles.isNotEmpty()) {
            TranslationHelper.getTranslatedCropName("${cropCycles[0].cropName} (${cropCycles[0].seasonName})", activeLang, this)
        } else if (activeCropNames.isNotEmpty()) {
            activeCropNames.firstOrNull { !it.startsWith("---") } ?: activeCropNames[0]
        } else {
            TranslationHelper.getTranslatedCropName("गेहूँ (Kharif)", activeLang, this)
        }
        actvCrop.setText(defaultCropText, false)

        val initialCats = if (isIncome) incomeCategories else expenseCategories
        val transInitialCats = getTranslatedCategories(isIncome)
        val defaultCat = if (isEdit && existingItem != null) existingItem.category else initialCats[0]
        val catInitIndex = initialCats.indexOf(defaultCat).let { if (it != -1) it else 0 }

        val defaultStage = if (isEdit) existingItem?.stage else stages[0]
        val stageInitIndex = stages.indexOf(defaultStage).let { if (it != -1) it else 0 }

        actvCategory.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, transInitialCats))
        if (catInitIndex in transInitialCats.indices) {
            actvCategory.setText(transInitialCats[catInitIndex], false)
        } else if (transInitialCats.isNotEmpty()) {
            actvCategory.setText(transInitialCats[0], false)
        }

        actvStage.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, transStages))
        actvStage.setText(transStages[stageInitIndex], false)

        val btnExpense = dialogView.findViewById<MaterialButton>(R.id.btnTypeExpense)
        val btnIncome = dialogView.findViewById<MaterialButton>(R.id.btnTypeIncome)
        val tilAmount = dialogView.findViewById<TextInputLayout>(R.id.tilKhataAmount)
        val tilCrop = dialogView.findViewById<TextInputLayout>(R.id.tilKhataCrop)
        val tilCategory = dialogView.findViewById<TextInputLayout>(R.id.tilKhataCategory)
        val tilStage = dialogView.findViewById<TextInputLayout>(R.id.tilKhataStage)
        val tilNote = dialogView.findViewById<TextInputLayout>(R.id.tilKhataNote)

        val transTitle = TranslationHelper.getDirectTranslation(if (isEdit) "Edit Entry" else "Add New Entry", activeLang)
            ?: if (activeLang == "hi") (if (isEdit) "एन्ट्री बदलें" else "नई एन्ट्री जोड़ें") else (if (isEdit) "Edit Entry" else "Add New Entry")
        tvTitle?.text = "📖 $transTitle"

        val transExpense = TranslationHelper.getDirectTranslation("Expense", activeLang) ?: if (activeLang == "hi") "खर्च" else "Expense"
        val transIncome = TranslationHelper.getDirectTranslation("Income", activeLang) ?: if (activeLang == "hi") "आय" else "Income"
        btnExpense?.text = "💸 $transExpense (-)"
        btnIncome?.text = "💰 $transIncome (+)"

        val transAmountHint = TranslationHelper.getDirectTranslation("Amount", activeLang) ?: if (activeLang == "hi") "राशि" else "Amount"
        val transCropHint = TranslationHelper.getDirectTranslation("Select Crop Season", activeLang) ?: if (activeLang == "hi") "फसल का मौसम चुनें" else "Select Crop Season"
        val transCatHint = TranslationHelper.getDirectTranslation("Category", activeLang) ?: if (activeLang == "hi") "श्रेणी" else "Category"
        val transStageHint = TranslationHelper.getDirectTranslation("Crop Stage", activeLang) ?: if (activeLang == "hi") "फसल का चरण" else "Crop Stage"
        val transNoteHint = TranslationHelper.getDirectTranslation("Note / Details", activeLang) ?: if (activeLang == "hi") "नोट / विवरण" else "Note / Details"

        tilAmount?.hint = transAmountHint
        tilCrop?.hint = transCropHint
        tilCategory?.hint = transCatHint
        tilStage?.hint = transStageHint
        tilNote?.hint = transNoteHint

        btnCancel.text = TranslationHelper.getDirectTranslation("Cancel", activeLang) ?: if (activeLang == "hi") "रद्द करें" else "Cancel"
        btnSave.text = if (isEdit) (TranslationHelper.getDirectTranslation("Update Entry", activeLang) ?: if (activeLang == "hi") "एन्ट्री अपडेट करें" else "Update Entry")
                       else (TranslationHelper.getDirectTranslation("Save Entry", activeLang) ?: if (activeLang == "hi") "एन्ट्री सहेजें" else "Save Entry")

        if (activeLang != "en" && activeLang != "hi") {
            lifecycleScope.launch(Dispatchers.Main) {
                val transTitleAsync = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, if (isEdit) "Edit Entry" else "Add New Entry", activeLang)
                tvTitle?.text = "📖 $transTitleAsync"

                val transExpenseAsync = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Expense", activeLang)
                val transIncomeAsync = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Income", activeLang)
                btnExpense?.text = "💸 $transExpenseAsync (-)"
                btnIncome?.text = "💰 $transIncomeAsync (+)"

                tilAmount?.hint = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Amount", activeLang)
                tilCrop?.hint = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Select Crop Season", activeLang)
                tilCategory?.hint = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Category", activeLang)
                tilStage?.hint = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Crop Stage", activeLang)
                tilNote?.hint = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Note / Details", activeLang)

                btnCancel.text = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, "Cancel", activeLang)
                btnSave.text = TranslationHelper.translateText(this@KisanDiaryHistoryActivity, if (isEdit) "Update Entry" else "Save Entry", activeLang)
            }
        }

        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isIncome = (checkedId == R.id.btnTypeIncome)
                updateCategoryDropdown(isIncome)
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            if (amountStr.isEmpty()) {
                etAmount.error = "Enter amount"
                return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val cropText = getCanonicalSeason(actvCrop.text.toString().trim())
            val rawCategoryText = actvCategory.text.toString().trim()
            val rawStageText = actvStage.text.toString().trim()

            val activeCategories = if (isIncome) incomeCategories else expenseCategories
            val activeTransCategories = getTranslatedCategories(isIncome)
            val catIndex = activeTransCategories.indexOfFirst { it.equals(rawCategoryText, ignoreCase = true) || rawCategoryText.contains(it, ignoreCase = true) }
            val category = if (catIndex in activeCategories.indices) activeCategories[catIndex] else (activeCategories.firstOrNull { it.equals(rawCategoryText, ignoreCase = true) } ?: activeCategories[0])

            val stageIndex = transStages.indexOfFirst { it.equals(rawStageText, ignoreCase = true) }
            val stage = if (stageIndex in stages.indices) stages[stageIndex] else rawStageText

            val note = etNote.text.toString().trim()
            val isIncome = toggleType.checkedButtonId == R.id.btnTypeIncome

            val matchingCycle = KhataHelper.getOrCreateCropCycle(this, khataRepo, cropCycles, cropText)

            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid ?: ""
            val itemId = if (isEdit && existingItem != null) existingItem.id else "khata_${System.currentTimeMillis()}"
            val timestamp = if (isEdit && existingItem != null) existingItem.timestamp else System.currentTimeMillis()
            val dateStr = if (isEdit && existingItem != null) existingItem.dateStr else SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())

            val khataItem = KhataItem(
                id = itemId,
                userId = userId,
                cropCycleId = matchingCycle.id,
                cropName = matchingCycle.cropName,
                category = category,
                stage = stage,
                amount = amount,
                isIncome = isIncome,
                note = note,
                timestamp = timestamp,
                dateStr = dateStr
            )

            if (isEdit && existingItem != null) {
                val index = allKhataItems.indexOfFirst { it.id == existingItem.id }
                if (index != -1) {
                    allKhataItems[index] = khataItem
                }
            } else {
                allKhataItems.add(0, khataItem)
            }

            KhataHelper.saveLocalKhataItems(this, allKhataItems)
            selectedFilter = "${matchingCycle.cropName} (${matchingCycle.seasonName})"
            updateFilterDropdown()
            filterAndApplyData()

            if (currentUser != null && !currentUser.isAnonymous) {
                khataRepo.addKhataItem(khataItem) { _ -> }
            }

            tryShowHistoryInterstitialAd {
                dialog.dismiss()
                Toast.makeText(this, if (isEdit) "Entry updated!" else "Entry added!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
