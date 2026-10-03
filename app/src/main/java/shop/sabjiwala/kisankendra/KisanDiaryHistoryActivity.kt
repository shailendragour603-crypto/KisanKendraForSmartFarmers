package shop.sabjiwala.kisankendra

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.FertilizerUtils
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KisanDiaryHistoryActivity : AppCompatActivity() {

    private val khataRepo by lazy { KhataRepository() }
    private lateinit var khataAdapter: KhataAdapter
    private var allKhataItems = mutableListOf<KhataItem>()
    private var displayedKhataItems = mutableListOf<KhataItem>()
    private var cropCycles = mutableListOf<CropCycle>()
    private var selectedFilter = "All Entries"
    private var currentPage = 1
    private val pageSize = 20

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
            onDeleteClick = { item -> deleteEntry(item) }
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
        khataAdapter.updateData(displayedKhataItems)
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
        val actvFilter = findViewById<AutoCompleteTextView>(R.id.actvHistoryFilter)
        val filterOptions = mutableListOf("All Entries")
        for (cycle in cropCycles) {
            filterOptions.add("${cycle.cropName} (${cycle.seasonName})")
        }
        val adapter = ArrayAdapter(this, R.layout.item_compact_dropdown_single, filterOptions)
        actvFilter.setAdapter(adapter)
        actvFilter.setText(selectedFilter, false)

        actvFilter.setOnItemClickListener { _, _, position, _ ->
            selectedFilter = filterOptions.getOrElse(position) { "All Entries" }
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

        val categories = listOf("Seeds", "Fertilizer", "Pesticides", "Labor", "Diesel & Irrigation", "Machinery & Tractor", "Electricity & Water", "Miscellaneous", "Mandi Sale")
        val stages = listOf("Sowing", "Irrigation", "Spray", "Harvesting", "Sale")

        val allAvailableCrops = (cropCycles.map { "${it.cropName} (${it.seasonName})" } + FertilizerUtils.crops).distinct()
        val activeCropNames = if (allAvailableCrops.isNotEmpty()) allAvailableCrops else listOf("Wheat", "Rice", "Soybean", "Cotton", "Mustard")
        actvCrop.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, activeCropNames))

        val defaultCropText = if (isEdit && existingItem?.cropName != null) {
            val matchingCycleObj = cropCycles.firstOrNull { it.cropName.equals(existingItem.cropName, ignoreCase = true) }
            if (matchingCycleObj != null) "${matchingCycleObj.cropName} (${matchingCycleObj.seasonName})" else existingItem.cropName
        } else if (cropCycles.isNotEmpty()) {
            "${cropCycles[0].cropName} (${cropCycles[0].seasonName})"
        } else if (activeCropNames.isNotEmpty()) {
            activeCropNames[0]
        } else {
            "Wheat"
        }
        actvCrop.setText(defaultCropText, false)

        val defaultCat = if (isEdit) existingItem?.category else categories[0]
        val catInitIndex = categories.indexOf(defaultCat).let { if (it != -1) it else 0 }

        val defaultStage = if (isEdit) existingItem?.stage else stages[0]
        val stageInitIndex = stages.indexOf(defaultStage).let { if (it != -1) it else 0 }

        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val transCategories = categories
        val transStages = stages.map { TranslationHelper.getDirectTranslation(it, activeLang) ?: it }

        actvCategory.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, transCategories))
        actvCategory.setText(transCategories[catInitIndex], false)

        actvStage.setAdapter(ArrayAdapter(this, R.layout.item_compact_dropdown_single, transStages))
        actvStage.setText(transStages[stageInitIndex], false)

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
            val cropText = actvCrop.text.toString().trim()
            val rawCategoryText = actvCategory.text.toString().trim()
            val rawStageText = actvStage.text.toString().trim()

            val catIndex = transCategories.indexOfFirst { it.equals(rawCategoryText, ignoreCase = true) }
            val category = if (catIndex in categories.indices) categories[catIndex] else rawCategoryText

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

            dialog.dismiss()
            Toast.makeText(this, if (isEdit) "Entry updated!" else "Entry added!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
