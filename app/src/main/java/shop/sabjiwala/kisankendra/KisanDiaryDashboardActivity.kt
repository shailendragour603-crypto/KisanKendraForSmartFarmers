package shop.sabjiwala.kisankendra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.ScreenAlertHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import shop.sabjiwala.kisankendra.databinding.LayoutScreenAlertBannerBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.delay
import shop.sabjiwala.kisankendra.views.ChartMode
import shop.sabjiwala.kisankendra.views.CropExpenseChartView
import shop.sabjiwala.kisankendra.views.CropExpenseDataPoint
import shop.sabjiwala.kisankendra.views.CropProfitDataPoint
import shop.sabjiwala.kisankendra.views.CropProfitabilityChartView
import shop.sabjiwala.kisankendra.views.CropStageExpenseChartView
import shop.sabjiwala.kisankendra.views.ExpenseChartMode
import shop.sabjiwala.kisankendra.views.FinancialDonutChartView
import shop.sabjiwala.kisankendra.views.StageExpenseDataPoint
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KisanDiaryDashboardActivity : AppCompatActivity() {

    private val khataRepo by lazy { KhataRepository() }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollViewDashboard: ScrollView

    private lateinit var tvFarmingHeaderTitle: TextView
    private lateinit var tvBadgeProfitMargin: TextView

    private lateinit var tvLabelTotalArea: TextView
    private lateinit var tvKpiTotalArea: TextView
    private lateinit var tvLabelTotalExpenses: TextView
    private lateinit var tvKpiTotalExpenses: TextView
    private lateinit var tvLabelTotalIncome: TextView
    private lateinit var tvKpiTotalIncome: TextView
    private lateinit var tvLabelEstimatedProfit: TextView
    private lateinit var tvKpiEstimatedProfit: TextView

    private lateinit var tvSectionFinancialSummaryTitle: TextView
    private lateinit var donutChartView: FinancialDonutChartView
    private lateinit var tvLabelLegendProfit: TextView
    private lateinit var tvLegendProfit: TextView
    private lateinit var tvLabelLegendExpenses: TextView
    private lateinit var tvLegendExpenses: TextView

    private lateinit var tvHeaderMajorExpenses: TextView
    private lateinit var tvMajorExpensesTotalAmount: TextView
    private lateinit var rvMajorExpenses: RecyclerView
    private val expenseCategoryAdapter = ExpenseCategoryAdapter()

    private lateinit var tvSectionStageExpenseTitle: TextView
    private lateinit var stageExpenseChartView: CropStageExpenseChartView

    private lateinit var tvSectionKeyInsightsTitle: TextView
    private lateinit var tvInsight1Value: TextView
    private lateinit var tvInsight1Desc: TextView
    private lateinit var tvInsight2Value: TextView
    private lateinit var tvInsight2Desc: TextView
    private lateinit var tvInsight3Value: TextView
    private lateinit var tvInsight3Desc: TextView

    private lateinit var tvSectionCropPerformanceTitle: TextView
    private lateinit var toggleGroupCropMetric: MaterialButtonToggleGroup
    private lateinit var btnToggleTotalProfit: MaterialButton
    private lateinit var btnToggleProfitPerAcre: MaterialButton
    private lateinit var cropProfitabilityChartView: CropProfitabilityChartView
    private lateinit var rvCropPerformance: RecyclerView
    private val cropAdapter = CropPerformanceAdapter()

    private lateinit var toggleGroupCropExpenseMetric: MaterialButtonToggleGroup
    private lateinit var btnToggleCropTotalExpense: MaterialButton
    private lateinit var btnToggleCropExpensePerAcre: MaterialButton
    private lateinit var cropExpenseChartView: CropExpenseChartView

    private lateinit var tvSectionRecentLogTitle: TextView
    private lateinit var rvRecentEntries: RecyclerView
    private val recentAdapter = RecentEntriesAdapter()

    private var localCropCycles = mutableListOf<CropCycle>()
    private var localKhataItems = mutableListOf<KhataItem>()
    private lateinit var actvDashboardSeasonFilter: AutoCompleteTextView
    private var selectedSeasonFilter = "All-Time"
    private var alertListenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kisan_diary_dashboard)

        setupWindowInsets()
        setupToolbar()
        bindViews()
        setupScreenAlert()
        setupSwipeRefresh()
        setupShareFab()
        loadLocalDataAndRender()
        listenToFirestoreData()
        applyDynamicTranslations()
        loadBannerAds()
    }

    private fun setupScreenAlert() {
        val bannerView = findViewById<View>(R.id.alertBanner) ?: return
        val bannerBinding = LayoutScreenAlertBannerBinding.bind(bannerView)
        alertListenerRegistration = ScreenAlertHelper.setupScreenAlert(this, "khata_screen", bannerBinding)
    }

    override fun onDestroy() {
        alertListenerRegistration?.remove()
        alertListenerRegistration = null
        super.onDestroy()
    }

    private fun setupSwipeRefresh() {
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshDiaryDashboard) ?: return
        ShimmerHelper.setupSwipeRefresh(swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            val summaryHeader = findViewById<View>(R.id.tvFarmingHeaderTitle)
            if (summaryHeader != null) ShimmerHelper.applyShimmer(summaryHeader)
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    loadLocalDataAndRender()
                    delay(600)
                } finally {
                    if (summaryHeader != null) ShimmerHelper.stopShimmer(summaryHeader)
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun setupWindowInsets() {
        val rootLayout: View? = findViewById(R.id.rootDashboardLayout)
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )
                insets
            }
        }
    }

    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbarDashboard)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupShareFab() {
        val btnShareDashboard = findViewById<View>(R.id.btnShareDashboard)
        val viewPulseRingDashboard = findViewById<View>(R.id.viewPulseRingDashboard)
        val scrollViewDashboard = findViewById<ScrollView>(R.id.scrollViewDashboard)
        MediaUtils.startCompactGreenPulseAnimation(viewPulseRingDashboard)
        btnShareDashboard.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = MediaUtils.takeFullScrollScreenshot(scrollViewDashboard, this@KisanDiaryDashboardActivity)
                val uri = file?.let { FileProvider.getUriForFile(this@KisanDiaryDashboardActivity, "${packageName}.fileprovider", it) }
                withContext(Dispatchers.Main) {
                    val shareText = "Build your personal Farm Dashboard on Kisan Kendra!\n" +
                            "Access farming calculators, track expenses, check mandi rates, and read daily agri news!\n\n📥 Download Kisan Kendra App: https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        if (uri != null) {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            type = "text/plain"
                        }
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    val chooser = Intent.createChooser(intent, "Share Dashboard via")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                }
            }
        }
    }

    private fun bindViews() {
        scrollViewDashboard = findViewById(R.id.scrollViewDashboard)

        tvFarmingHeaderTitle = findViewById(R.id.tvFarmingHeaderTitle)
        tvBadgeProfitMargin = findViewById(R.id.tvBadgeProfitMargin)

        tvLabelTotalArea = findViewById(R.id.tvLabelTotalArea)
        tvKpiTotalArea = findViewById(R.id.tvKpiTotalArea)
        tvLabelTotalExpenses = findViewById(R.id.tvLabelTotalExpenses)
        tvKpiTotalExpenses = findViewById(R.id.tvKpiTotalExpenses)
        tvLabelTotalIncome = findViewById(R.id.tvLabelTotalIncome)
        tvKpiTotalIncome = findViewById(R.id.tvKpiTotalIncome)
        tvLabelEstimatedProfit = findViewById(R.id.tvLabelEstimatedProfit)
        tvKpiEstimatedProfit = findViewById(R.id.tvKpiEstimatedProfit)

        tvSectionFinancialSummaryTitle = findViewById(R.id.tvSectionFinancialSummaryTitle)
        donutChartView = findViewById(R.id.donutChartView)
        tvLabelLegendProfit = findViewById(R.id.tvLabelLegendProfit)
        tvLegendProfit = findViewById(R.id.tvLegendProfit)
        tvLabelLegendExpenses = findViewById(R.id.tvLabelLegendExpenses)
        tvLegendExpenses = findViewById(R.id.tvLegendExpenses)

        tvHeaderMajorExpenses = findViewById(R.id.tvHeaderMajorExpenses)
        tvMajorExpensesTotalAmount = findViewById(R.id.tvMajorExpensesTotalAmount)
        rvMajorExpenses = findViewById(R.id.rvMajorExpenses)
        rvMajorExpenses.layoutManager = LinearLayoutManager(this)
        rvMajorExpenses.adapter = expenseCategoryAdapter

        tvSectionStageExpenseTitle = findViewById(R.id.tvSectionStageExpenseTitle)
        stageExpenseChartView = findViewById(R.id.stageExpenseChartView)

        tvSectionKeyInsightsTitle = findViewById(R.id.tvSectionKeyInsightsTitle)
        tvInsight1Value = findViewById(R.id.tvInsight1Value)
        tvInsight1Desc = findViewById(R.id.tvInsight1Desc)
        tvInsight2Value = findViewById(R.id.tvInsight2Value)
        tvInsight2Desc = findViewById(R.id.tvInsight2Desc)
        tvInsight3Value = findViewById(R.id.tvInsight3Value)
        tvInsight3Desc = findViewById(R.id.tvInsight3Desc)

        tvSectionCropPerformanceTitle = findViewById(R.id.tvSectionCropPerformanceTitle)
        toggleGroupCropMetric = findViewById(R.id.toggleGroupCropMetric)
        btnToggleTotalProfit = findViewById(R.id.btnToggleTotalProfit)
        btnToggleProfitPerAcre = findViewById(R.id.btnToggleProfitPerAcre)
        cropProfitabilityChartView = findViewById(R.id.cropProfitabilityChartView)

        toggleGroupCropMetric.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnToggleProfitPerAcre) {
                    cropProfitabilityChartView.setChartMode(ChartMode.PROFIT_PER_ACRE)
                } else {
                    cropProfitabilityChartView.setChartMode(ChartMode.TOTAL_PROFIT)
                }
            }
        }

        rvCropPerformance = findViewById(R.id.rvCropPerformance)
        rvCropPerformance.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvCropPerformance.adapter = cropAdapter

        toggleGroupCropExpenseMetric = findViewById(R.id.toggleGroupCropExpenseMetric)
        btnToggleCropTotalExpense = findViewById(R.id.btnToggleCropTotalExpense)
        btnToggleCropExpensePerAcre = findViewById(R.id.btnToggleCropExpensePerAcre)
        cropExpenseChartView = findViewById(R.id.cropExpenseChartView)

        toggleGroupCropExpenseMetric.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnToggleCropExpensePerAcre) {
                    cropExpenseChartView.setChartMode(ExpenseChartMode.EXPENSE_PER_ACRE)
                } else {
                    cropExpenseChartView.setChartMode(ExpenseChartMode.TOTAL_EXPENSE)
                }
            }
        }

        tvSectionRecentLogTitle = findViewById(R.id.tvSectionRecentLogTitle)
        rvRecentEntries = findViewById(R.id.rvRecentEntries)
        rvRecentEntries.layoutManager = LinearLayoutManager(this)
        rvRecentEntries.adapter = recentAdapter

        actvDashboardSeasonFilter = findViewById(R.id.actvDashboardSeasonFilter)
    }

    private fun updateSeasonFilterDropdown() {
        val filterOptions = mutableListOf("All-Time")
        val currentYearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())

        for (cycle in localCropCycles) {
            val option = cycle.seasonName.ifBlank { "Season" }
            if (!filterOptions.contains(option)) {
                filterOptions.add(option)
            }
        }

        if (selectedSeasonFilter == "All-Time" && filterOptions.size > 1) {
            val currentYearSeason = filterOptions.firstOrNull { it.contains(currentYearStr, ignoreCase = true) }
            selectedSeasonFilter = currentYearSeason ?: filterOptions[1]
        }

        val adapter = ArrayAdapter(this, R.layout.item_compact_dropdown_single, filterOptions)
        actvDashboardSeasonFilter.setAdapter(adapter)
        actvDashboardSeasonFilter.setText(selectedSeasonFilter, false)

        actvDashboardSeasonFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSeasonFilter = filterOptions.getOrElse(position) { "All-Time" }
            calculateAndRenderDashboard()
        }
    }

    private fun loadBannerAds() {
        val containerMid: FrameLayout? = findViewById(R.id.containerAdMidDashboard)
        val cardMid: View? = findViewById(R.id.cardAdMidDashboard)
        if (containerMid != null && cardMid != null) {
            loadBannerIntoContainer(containerMid, cardMid)
        }

        val containerBottom: FrameLayout? = findViewById(R.id.containerAdBottomDashboard)
        val cardBottom: View? = findViewById(R.id.cardAdBottomDashboard)
        if (containerBottom != null && cardBottom != null) {
            loadBannerIntoContainer(containerBottom, cardBottom)
        }
    }

    private fun loadBannerIntoContainer(container: FrameLayout, cardView: View) {
        container.post {
            try {
                cardView.visibility = View.GONE
                val adView = AdView(this).apply {
                    adUnitId = "ca-app-pub-3054731531860427/9592937417"
                    val displayMetrics = resources.displayMetrics
                    val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt().coerceAtLeast(300)
                    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@KisanDiaryDashboardActivity, adWidth)
                    setAdSize(adSize)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            cardView.visibility = View.VISIBLE
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            cardView.visibility = View.GONE
                        }
                    }
                }
                container.removeAllViews()
                container.addView(adView)
                adView.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    private fun loadLocalDataAndRender() {
        KhataHelper.migrateGuestDataToCurrentUser(this, khataRepo)
        localCropCycles = KhataHelper.getLocalCropCycles(this).toMutableList()
        localKhataItems = KhataHelper.getLocalKhataItems(this).toMutableList()
        KhataHelper.reconcileOrphanedKhataItems(this, khataRepo, localCropCycles, localKhataItems)
        calculateAndRenderDashboard()
    }

    private fun listenToFirestoreData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous) return

        // ⚡ Smart Local-First Restore:
        // ONLY query Firestore if local device cache is empty (e.g. fresh install or app data cleared).
        if (localCropCycles.isEmpty()) {
            khataRepo.fetchCropCycles { remoteCycles ->
                if (remoteCycles.isNotEmpty()) {
                    localCropCycles = remoteCycles.sortedByDescending { it.timestamp }.toMutableList()
                    KhataHelper.saveLocalCropCycles(this, localCropCycles)
                    calculateAndRenderDashboard()
                }
            }
        }

        if (localKhataItems.isEmpty()) {
            khataRepo.fetchKhataItems { remoteItems ->
                if (remoteItems.isNotEmpty()) {
                    localKhataItems = remoteItems.sortedByDescending { it.timestamp }.toMutableList()
                    KhataHelper.saveLocalKhataItems(this, localKhataItems)
                    calculateAndRenderDashboard()
                }
            }
        }
    }

    private fun calculateAndRenderDashboard() {
        updateSeasonFilterDropdown()

        val filteredCycles = if (selectedSeasonFilter == "All-Time") {
            localCropCycles
        } else {
            localCropCycles.filter { it.seasonName.equals(selectedSeasonFilter, ignoreCase = true) }
        }

        val filteredCycleIds = filteredCycles.map { it.id }.toSet()
        val filteredKhataItems = if (selectedSeasonFilter == "All-Time") {
            localKhataItems
        } else {
            localKhataItems.filter { it.cropCycleId in filteredCycleIds || it.cropName.contains(selectedSeasonFilter.substringBefore(" "), ignoreCase = true) }
        }

        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val currencyFormatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }

        // 1. Total Land Area
        val totalAreaAcres = filteredCycles.sumOf { it.areaAcres }
        tvKpiTotalArea.text = String.format(Locale.ENGLISH, "%.1f Acres", totalAreaAcres)

        // 2. Financial Aggregations
        val expenseEntries = filteredKhataItems.filter { !it.isIncome }
        val incomeEntries = filteredKhataItems.filter { it.isIncome }

        val totalIncome = incomeEntries.sumOf { it.amount }
        val totalExpenses = expenseEntries.sumOf { it.amount }
        val netProfit = totalIncome - totalExpenses

        tvKpiTotalIncome.text = currencyFormatter.format(totalIncome)
        tvKpiTotalExpenses.text = currencyFormatter.format(totalExpenses)
        tvKpiEstimatedProfit.text = currencyFormatter.format(netProfit)

        val colorGreen = ContextCompat.getColor(this, R.color.price_max_text)
        val colorRed = ContextCompat.getColor(this, R.color.price_min_text)
        val bgGreen = ContextCompat.getColor(this, R.color.price_max_bg)
        val bgRed = ContextCompat.getColor(this, R.color.price_min_bg)

        if (netProfit >= 0) {
            tvKpiEstimatedProfit.setTextColor(colorGreen)
        } else {
            tvKpiEstimatedProfit.setTextColor(colorRed)
        }

        val profitMarginPercent = if (totalIncome > 0) ((netProfit / totalIncome) * 100).coerceAtLeast(0.0) else 0.0
        val expenseRatioPercent = if (totalIncome > 0) ((totalExpenses / totalIncome) * 100).coerceAtLeast(0.0) else 0.0

        tvBadgeProfitMargin.text = String.format(Locale.ENGLISH, "Profit Margin %.0f%%", profitMarginPercent)
        tvBadgeProfitMargin.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
        tvBadgeProfitMargin.compoundDrawablePadding = (4 * resources.displayMetrics.density).toInt()
        val cardProfitMarginBadge = findViewById<MaterialCardView>(R.id.cardProfitMarginBadge)
        if (profitMarginPercent >= 0) {
            tvBadgeProfitMargin.setTextColor(colorGreen)
            cardProfitMarginBadge.setCardBackgroundColor(bgGreen)
        } else {
            tvBadgeProfitMargin.setTextColor(colorRed)
            cardProfitMarginBadge.setCardBackgroundColor(bgRed)
        }

        // 3. Donut Chart & Legend
        donutChartView.setData(totalIncome, totalExpenses, netProfit)

        val expensePctInt = expenseRatioPercent.toInt()
        val profitPctInt = profitMarginPercent.toInt()

        tvLegendExpenses.text = String.format(Locale.ENGLISH, "%s (%d%%)", currencyFormatter.format(totalExpenses), expensePctInt)
        tvLegendProfit.text = String.format(Locale.ENGLISH, "%s (%d%%)", currencyFormatter.format(netProfit), profitPctInt)

        // 4. Major Expenses Breakdown by Category (All recorded categories + padding up to 5)
        tvMajorExpensesTotalAmount.text = currencyFormatter.format(totalExpenses)

        val categoryExpenseMap = mutableMapOf<String, Double>()

        // Aggregate expense entries by their clean, distinct category name
        for (item in expenseEntries) {
            val catName = getCleanCategoryName(item.category)
            categoryExpenseMap[catName] = (categoryExpenseMap[catName] ?: 0.0) + item.amount
        }

        // If fewer than 5 categories recorded, pad with standard farm expense categories
        val defaultCategories = listOf("Seeds", "Fertiliser", "Pesticides", "Labour", "Irrigation", "Machinery & Fuel")
        for (cat in defaultCategories) {
            if (!categoryExpenseMap.containsKey(cat) && categoryExpenseMap.size < 5) {
                categoryExpenseMap[cat] = 0.0
            }
        }

        val allCategoryItems = categoryExpenseMap.map { (catName, amt) ->
            val pct = if (totalExpenses > 0) ((amt / totalExpenses) * 100).toInt() else 0
            ExpenseCategoryItem(
                categoryName = catName,
                amount = amt,
                percent = pct,
                iconRes = getExpenseCategoryIcon(catName)
            )
        }.sortedByDescending { it.amount }

        val nonZeroCount = allCategoryItems.count { it.amount > 0 }
        val displayCount = maxOf(5, nonZeroCount)
        val expenseItems = allCategoryItems.take(displayCount)

        expenseCategoryAdapter.updateItems(expenseItems)

        // 5. Stage-Wise Expense Breakdown Line Chart
        val stageMap = mutableMapOf<String, Double>()
        for (item in expenseEntries) {
            val stageKey = normalizeStageName(item.stage)
            stageMap[stageKey] = (stageMap[stageKey] ?: 0.0) + item.amount
        }

        val defaultStages = listOf("Sowing", "Irrigation", "Spray", "Harvesting", "Other")
        val stagePoints = defaultStages.map { stageName ->
            StageExpenseDataPoint(
                stageName = stageName,
                totalExpense = stageMap[stageName] ?: 0.0
            )
        }
        stageExpenseChartView.setData(stagePoints)

        // 6. Key Insights
        tvInsight1Value.text = String.format(Locale.ENGLISH, "%.0f%% Margin", profitMarginPercent)
        tvInsight1Desc.text = String.format(Locale.ENGLISH, "You keep %.0f%% of income as profit.", profitMarginPercent)

        val cardInsight1 = findViewById<MaterialCardView>(R.id.cardInsight1)
        val cardInsight2 = findViewById<MaterialCardView>(R.id.cardInsight2)
        val cardInsight3 = findViewById<MaterialCardView>(R.id.cardInsight3)

        if (profitMarginPercent >= 0) {
            cardInsight1.setCardBackgroundColor(bgGreen)
            tvInsight1Value.setTextColor(colorGreen)
        } else {
            cardInsight1.setCardBackgroundColor(bgRed)
            tvInsight1Value.setTextColor(colorRed)
        }

        val highestCostExpense = expenseItems.firstOrNull { it.amount > 0 }
        if (highestCostExpense != null) {
            tvInsight2Value.text = highestCostExpense.categoryName
            tvInsight2Desc.text = "${highestCostExpense.categoryName} is your biggest expense (${highestCostExpense.percent}%)."
        } else {
            tvInsight2Value.text = "Highest Cost"
            tvInsight2Desc.text = "Track your daily expenses to discover cost drivers."
        }
        cardInsight2.setCardBackgroundColor(bgRed)
        tvInsight2Value.setTextColor(colorRed)

        val inputCostPercent = expenseItems.filter { it.categoryName in listOf("Seeds", "Fertiliser", "Pesticides") }.sumOf { it.percent }
        tvInsight3Value.text = "Cost Reduction"
        tvInsight3Desc.text = if (inputCostPercent > 0) {
            "Input costs make up $inputCostPercent% of expenses. Reducing them boosts profit."
        } else {
            "Reducing input costs directly increases your profit."
        }
        cardInsight3.setCardBackgroundColor(ContextCompat.getColor(this, R.color.harvest_gold_light))
        tvInsight3Value.setTextColor(ContextCompat.getColor(this, R.color.harvest_gold))

        // 7. Crop-Wise Profitability & ROI Performance Graph
        val cropPerformanceMap = mutableMapOf<String, Triple<Double, Double, Double>>()
        val cycleNameMap = filteredCycles.associate { it.id to it.cropName }

        for (item in filteredKhataItems) {
            val name = item.cropName.ifBlank { cycleNameMap[item.cropCycleId] ?: "Other Crop" }
            val current = cropPerformanceMap.getOrDefault(name, Triple(0.0, 0.0, 0.0))
            if (item.isIncome) {
                cropPerformanceMap[name] = Triple(current.first + item.amount, current.second, current.third + item.amount)
            } else {
                cropPerformanceMap[name] = Triple(current.first, current.second + item.amount, current.third - item.amount)
            }
        }

        val performanceList = cropPerformanceMap.map { (cropName, tuple) ->
            val cropCycle = filteredCycles.firstOrNull { it.cropName.equals(cropName, ignoreCase = true) }
            val ppa = if (cropCycle != null && cropCycle.areaAcres > 0) tuple.third / cropCycle.areaAcres else tuple.third
            CropPerformanceItem(
                cropName = cropName,
                netProfit = tuple.third,
                totalIncome = tuple.first,
                totalExpenses = tuple.second,
                profitPerAcre = ppa
            )
        }
        cropAdapter.updateItems(performanceList)

        val cropProfitDataList = cropPerformanceMap.map { (cropName, tuple) ->
            val income = tuple.first
            val expenses = tuple.second
            val profit = tuple.third
            val roi = if (expenses > 0) ((profit / expenses) * 100).toInt() else 0
            val cropCycle = filteredCycles.firstOrNull { it.cropName.equals(cropName, ignoreCase = true) }
            val ppa = if (cropCycle != null && cropCycle.areaAcres > 0) profit / cropCycle.areaAcres else profit
            CropProfitDataPoint(
                cropName = cropName,
                netProfit = profit,
                totalIncome = income,
                totalExpenses = expenses,
                roiPercent = roi,
                profitPerAcre = ppa
            )
        }.sortedByDescending { it.netProfit }

        cropProfitabilityChartView.setData(cropProfitDataList)

        val cropExpenseDataList = cropPerformanceMap.map { (cropName, tuple) ->
            val expenses = tuple.second
            val cropCycle = filteredCycles.firstOrNull { it.cropName.equals(cropName, ignoreCase = true) }
            val area = cropCycle?.areaAcres ?: 0.0
            val epa = if (area > 0) expenses / area else expenses
            CropExpenseDataPoint(
                cropName = cropName,
                totalExpense = expenses,
                expensePerAcre = epa,
                areaAcres = area
            )
        }.sortedByDescending { it.totalExpense }

        cropExpenseChartView.setData(cropExpenseDataList)

        // 8. Recent Diary Activity Log
        recentAdapter.updateItems(filteredKhataItems.sortedByDescending { it.timestamp })
    }

    private fun applyDynamicTranslations() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)
        if (activeLang == "en") return

        lifecycleScope.launch(Dispatchers.Main) {
            toolbar.title = "My Farming Dashboard"

            tvLabelTotalArea.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Area", activeLang)
            tvLabelTotalExpenses.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Expenses", activeLang)
            tvLabelTotalIncome.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Income", activeLang)
            tvLabelEstimatedProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Estimated Profit", activeLang)

            tvLabelLegendProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Profit", activeLang)
            tvLabelLegendExpenses.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Expenses", activeLang)

            btnToggleTotalProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Profit (₹)", activeLang)
            btnToggleProfitPerAcre.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Profit / Acre (₹/Acre)", activeLang)

            btnToggleCropTotalExpense.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Expense (₹)", activeLang)
            btnToggleCropExpensePerAcre.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Expense / Acre (₹/Acre)", activeLang)
        }
    }

    private fun getCleanCategoryName(rawCategory: String): String {
        val trimmed = rawCategory.trim()
        if (trimmed.isBlank()) return "General Expense"

        val lower = trimmed.lowercase(Locale.ENGLISH)
        return when {
            lower == "seed" || lower == "seeds" || lower == "बीज" -> "Seeds"
            lower.contains("fertilizer") || lower.contains("fertiliser") || lower.contains("खाद") -> "Fertiliser"
            lower.contains("pesticide") || lower.contains("pesticides") || lower.contains("दवा") -> "Pesticides"
            lower == "irrigation" || lower == "water" || lower == "पानी" || lower == "सिंचाई" -> "Irrigation"
            lower.contains("machinery") || lower == "tractor" || lower == "fuel" || lower == "diesel" -> "Machinery & Fuel"
            lower.contains("labor") || lower.contains("labour") || lower.contains("मजदूरी") -> "Labour"
            lower.contains("harvest") || lower.contains("cutting") || lower.contains("कटाई") -> "Harvesting"
            else -> trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
    }

    private fun normalizeStageName(rawStage: String): String {
        val lower = rawStage.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("sow") || lower.contains("sowing") || lower.contains("बुआई") || lower.contains("पेरणी") || lower.contains("ਬੀਜਾਈ") || lower.contains("વાવણી") || lower.contains("বপন") || lower.contains("விதைப்பு") || lower.contains("విత్తనం") || lower.contains("ಬಿತ್ತನೆ") || lower.contains("വിത") -> "Sowing"
            lower.contains("irrig") || lower.contains("water") || lower.contains("सिंचाई") || lower.contains("सिंचन") || lower.contains("ਸਿੰਚਾਈ") || lower.contains("પિયત") || lower.contains("সেচ") || lower.contains("பாசனம்") || lower.contains("పారుదల") || lower.contains("ನೀರಾವರಿ") || lower.contains("നനയ്ക്കൽ") -> "Irrigation"
            lower.contains("spray") || lower.contains("pesticide") || lower.contains("छिड़काव") || lower.contains("फवारणी") || lower.contains("ਛਿੜਕਾਅ") || lower.contains("ਛੰਟਕાવ") || lower.contains("સ્પ્રે") || lower.contains("தெளிப்பு") || lower.contains("పిచికారీ") || lower.contains("ಸಿಂಪಡಣೆ") || lower.contains("തളിക്കൽ") -> "Spray"
            lower.contains("harvest") || lower.contains("cutting") || lower.contains("कटाई") || lower.contains("कापणी") || lower.contains("ਵਾਢੀ") || lower.contains("લણણી") || lower.contains("কাটা") || lower.contains("அறுவடை") || lower.contains("కోత") || lower.contains("ಕೊಯ್ಲು") || lower.contains("വിളവെടുപ്പ്") -> "Harvesting"
            else -> "Other"
        }
    }

    private fun getExpenseCategoryIcon(categoryName: String): Int {
        val lower = categoryName.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("seed") || lower.contains("बीज") -> R.drawable.ic_expense_seeds
            lower.contains("fertilizer") || lower.contains("fertiliser") || lower.contains("खाद") -> R.drawable.ic_expense_fertilizer
            lower.contains("pesticide") || lower.contains("spray") || lower.contains("दवा") -> R.drawable.ic_expense_pesticide
            lower.contains("irrig") || lower.contains("water") || lower.contains("diesel") || lower.contains("पानी") -> R.drawable.ic_expense_irrigation
            lower.contains("machin") || lower.contains("tractor") || lower.contains("fuel") -> R.drawable.ic_expense_machinery
            lower.contains("labour") || lower.contains("labor") || lower.contains("मजदूरी") -> R.drawable.ic_expense_labour
            else -> R.drawable.ic_expense_electricity
        }
    }
}
