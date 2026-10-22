package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KisanDiaryDashboardActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

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
    private lateinit var tvSectionCropExpenseTitle: TextView

    private lateinit var tvSectionRecentLogTitle: TextView
    private lateinit var rvRecentEntries: RecyclerView
    private lateinit var btnViewAllEntries: MaterialButton
    private val recentAdapter = RecentEntriesAdapter()

    private var localCropCycles = mutableListOf<CropCycle>()
    private var localKhataItems = mutableListOf<KhataItem>()
    private lateinit var actvDashboardSeasonFilter: AutoCompleteTextView
    private var selectedSeasonFilter = "All-Time"
    private var alertListenerRegistration: ListenerRegistration? = null

    private var dashboardInterstitialAd: InterstitialAd? = null
    private var lastDashboardInterstitialTime = 0L

    companion object {
        private const val MIN_INTERSTITIAL_GAP_MS = 60_000L // 60s minimum gap
    }

    private fun loadDashboardInterstitialAd() {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                this,
                "ca-app-pub-3054731531860427/5297828555",
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        dashboardInterstitialAd = ad
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        dashboardInterstitialAd = null
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryShowDashboardInterstitialAd(onComplete: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (dashboardInterstitialAd != null && currentTime - lastDashboardInterstitialTime >= MIN_INTERSTITIAL_GAP_MS) {
            dashboardInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dashboardInterstitialAd = null
                    lastDashboardInterstitialTime = System.currentTimeMillis()
                    loadDashboardInterstitialAd()
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    dashboardInterstitialAd = null
                    lastDashboardInterstitialTime = System.currentTimeMillis()
                    loadDashboardInterstitialAd()
                    onComplete()
                }
            }
            dashboardInterstitialAd?.show(this)
        } else {
            onComplete()
            if (dashboardInterstitialAd == null) {
                loadDashboardInterstitialAd()
            }
        }
    }

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
        loadDashboardInterstitialAd()
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

    override fun onResume() {
        super.onResume()
        applyDynamicTranslations()
        loadLocalDataAndRender()
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
                    tryShowDashboardInterstitialAd {
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
        tvSectionCropExpenseTitle = findViewById(R.id.tvSectionCropExpenseTitle)

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

        btnViewAllEntries = findViewById(R.id.btnViewAllEntries)
        btnViewAllEntries.setOnClickListener {
            tryShowDashboardInterstitialAd {
                startActivity(Intent(this, KisanDiaryHistoryActivity::class.java))
            }
        }

        val btnExportPdfReport = findViewById<MaterialButton>(R.id.btnExportPdfReport)
        btnExportPdfReport?.setOnClickListener {
            showExportPdfRewardDialog()
        }

        actvDashboardSeasonFilter = findViewById(R.id.actvDashboardSeasonFilter)
    }

    private fun showExportPdfRewardDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_kisan_ai_reward, null)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseRewardDialog)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvRewardBadge)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvRewardTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvRewardSubtitle)
        val btnWatch = dialogView.findViewById<MaterialButton>(R.id.btnWatchVideo)

        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val transTitle = TranslationHelper.getDirectTranslation("Download Full PDF Report", activeLang)
            ?: if (activeLang == "hi") "पूरी PDF रिपोर्ट डाउनलोड करें" else "Download Full PDF Report"
        val transSubtitle = TranslationHelper.getDirectTranslation("Watch a short video to download your complete report", activeLang)
            ?: if (activeLang == "hi") "पूरी पीडीएफ रिपोर्ट पाने के लिए एक छोटा वीडियो देखें" else "Watch a short video to download your complete report"
        val transBtnWatch = TranslationHelper.getDirectTranslation("Watch Video", activeLang)
            ?: if (activeLang == "hi") "वीडियो देखें" else "Watch Video"

        tvBadge.text = "PDF"
        tvBadge.textSize = 20f
        tvTitle.text = transTitle
        tvSubtitle.text = transSubtitle
        btnWatch.text = transBtnWatch

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnWatch.setOnClickListener {
            dialog.dismiss()
            showRewardedAdForPdfReport()
        }

        dialog.show()
    }

    private fun showRewardedAdForPdfReport() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val loadingMsg = if (activeLang == "hi") "⏳ वीडियो विज्ञापन लोड हो रहा है..." else "⏳ Loading video ad..."
        Toast.makeText(this, loadingMsg, Toast.LENGTH_SHORT).show()

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            "ca-app-pub-3054731531860427/8586977507",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            generateAndExportPdfReport()
                        }
                    }
                    rewardedAd.show(this@KisanDiaryDashboardActivity) { _ ->
                        generateAndExportPdfReport()
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val failLoadMsg = if (activeLang == "hi") "विज्ञापन तैयार नहीं है। आपकी रिपोर्ट जनरेट हो रही है..." else "Ad not ready. Generating your report..."
                    Toast.makeText(this@KisanDiaryDashboardActivity, failLoadMsg, Toast.LENGTH_SHORT).show()
                    generateAndExportPdfReport()
                }
            }
        )
    }

    private var selectedDashboardCropCycleIds = mutableSetOf<String>()

    private fun updateSeasonFilterDropdown() {
        if (selectedDashboardCropCycleIds.isEmpty() && localCropCycles.isNotEmpty()) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

            val currentYearCycles = localCropCycles.filter { cycle ->
                val cycleCal = java.util.Calendar.getInstance().apply { timeInMillis = cycle.timestamp }
                val cycleYear = cycleCal.get(java.util.Calendar.YEAR)
                cycleYear == currentYear ||
                cycle.seasonName.contains(currentYear.toString()) ||
                cycle.startDateStr.contains(currentYear.toString())
            }.take(10)

            if (currentYearCycles.isNotEmpty()) {
                selectedDashboardCropCycleIds.addAll(currentYearCycles.map { it.id })
            } else {
                selectedDashboardCropCycleIds.add(localCropCycles.first().id)
            }
        }

        updateDashboardCropSelectorText()

        actvDashboardSeasonFilter.setOnClickListener {
            showMultiSelectDashboardSeasonDialog()
        }
        actvDashboardSeasonFilter.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showMultiSelectDashboardSeasonDialog()
            }
        }
    }

    private fun updateDashboardCropSelectorText() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)

        if (selectedDashboardCropCycleIds.isEmpty()) {
            val placeholder = if (activeLang == "hi") "सभी सीजन" else "All Seasons"
            actvDashboardSeasonFilter.setText(placeholder, false)
        } else if (selectedDashboardCropCycleIds.size == 1) {
            val cycle = localCropCycles.firstOrNull { it.id == selectedDashboardCropCycleIds.first() }
            if (cycle != null) {
                val cropTrans = TranslationHelper.getTranslatedCropName(cycle.cropName, activeLang, this)
                val seasonTrans = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, this)
                actvDashboardSeasonFilter.setText("$cropTrans ($seasonTrans)", false)
            } else {
                val text = if (activeLang == "hi") "1 सीजन चुना गया" else "1 Season Selected"
                actvDashboardSeasonFilter.setText(text, false)
            }
        } else if (selectedDashboardCropCycleIds.size == localCropCycles.size && localCropCycles.isNotEmpty()) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val label = if (activeLang == "hi") "सभी सीजन ($currentYear)" else "All Seasons ($currentYear)"
            actvDashboardSeasonFilter.setText(label, false)
        } else {
            val count = selectedDashboardCropCycleIds.size
            val text = if (activeLang == "hi") "$count सीजन चुने गए" else "$count Seasons Selected"
            actvDashboardSeasonFilter.setText(text, false)
        }
    }

    private fun showMultiSelectDashboardSeasonDialog() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)

        if (localCropCycles.isEmpty()) {
            return
        }

        val checkedItems = BooleanArray(localCropCycles.size) { index ->
            selectedDashboardCropCycleIds.contains(localCropCycles[index].id)
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_season_filter_multiselect, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvSeasonFilterTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSeasonFilterSubtitle)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseSeasonDialog)
        val containerChoices = dialogView.findViewById<LinearLayout>(R.id.containerSeasonChoices)
        val btnClearAll = dialogView.findViewById<MaterialButton>(R.id.btnClearAllSeasons)
        val btnAddSeason = dialogView.findViewById<MaterialButton>(R.id.btnAddSeasonInDialog)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplySeasonFilter)

        btnAddSeason.visibility = View.GONE

        val transTitle = TranslationHelper.getDirectTranslation("Select Seasons", activeLang) ?: if (activeLang == "hi") "सीजन चुनें" else "Select Seasons"
        val transSubtitle = TranslationHelper.getDirectTranslation("Select seasons for your report", activeLang) ?: if (activeLang == "hi") "अपनी रिपोर्ट के लिए सीजन चुनें" else "Select seasons for your report"
        val transBtnApply = TranslationHelper.getDirectTranslation("Apply", activeLang) ?: if (activeLang == "hi") "लागू करें" else "Apply"
        val transBtnClear = TranslationHelper.getDirectTranslation("Clear All", activeLang) ?: if (activeLang == "hi") "सभी साफ़ करें" else "Clear All"
        val maxLimitMsg = if (activeLang == "hi") "आप अधिकतम 10 सीजन चुन सकते हैं" else "You can select up to 10 seasons"

        tvTitle.text = transTitle
        tvSubtitle.text = transSubtitle
        btnApply.text = transBtnApply
        btnClearAll.text = transBtnClear

        fun updateClearAllVisibility() {
            val checkedCount = checkedItems.count { it }
            btnClearAll.visibility = if (checkedCount > 0) View.VISIBLE else View.GONE
        }

        updateClearAllVisibility()

        fun updateRowVisualState(card: MaterialCardView, cbCheck: com.google.android.material.checkbox.MaterialCheckBox, isChecked: Boolean) {
            cbCheck.isChecked = isChecked
            if (isChecked) {
                card.setCardBackgroundColor(Color.parseColor("#153A21"))
                card.strokeColor = Color.parseColor("#2E7D32")
            } else {
                card.setCardBackgroundColor(Color.parseColor("#1E293B"))
                card.strokeColor = Color.parseColor("#334155")
            }
        }

        val rowEntries = mutableListOf<Pair<MaterialCardView, com.google.android.material.checkbox.MaterialCheckBox>>()

        containerChoices.removeAllViews()
        for (index in localCropCycles.indices) {
            val cycle = localCropCycles[index]
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_season_filter_choice, containerChoices, false)
            val card = rowView.findViewById<MaterialCardView>(R.id.cardSeasonChoice)
            val tvLabel = rowView.findViewById<TextView>(R.id.tvSeasonLabel)
            val cbCheck = rowView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbSeasonCheck)

            val cropTrans = TranslationHelper.getTranslatedCropName(cycle.cropName, activeLang, this)
            val seasonTrans = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, this)
            tvLabel.text = "$cropTrans ($seasonTrans)"

            updateRowVisualState(card, cbCheck, checkedItems[index])

            card.setOnClickListener {
                val isCurrentlyChecked = checkedItems[index]
                if (!isCurrentlyChecked) {
                    val count = checkedItems.count { it }
                    if (count >= 10) {
                        Toast.makeText(this, maxLimitMsg, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    checkedItems[index] = true
                } else {
                    checkedItems[index] = false
                }
                updateRowVisualState(card, cbCheck, checkedItems[index])
                updateClearAllVisibility()
            }

            rowEntries.add(Pair(card, cbCheck))
            containerChoices.addView(rowView)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnClearAll.setOnClickListener {
            for (i in checkedItems.indices) {
                checkedItems[i] = false
            }
            rowEntries.forEachIndexed { _, (card, cb) ->
                updateRowVisualState(card, cb, false)
            }
            updateClearAllVisibility()
        }

        btnApply.setOnClickListener {
            selectedDashboardCropCycleIds.clear()
            for (i in localCropCycles.indices) {
                if (checkedItems[i]) {
                    selectedDashboardCropCycleIds.add(localCropCycles[i].id)
                }
            }
            updateDashboardCropSelectorText()
            calculateAndRenderDashboard()
            dialog.dismiss()
        }

        dialog.show()
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

        val layoutEmpty = findViewById<View>(R.id.layoutDashboardEmptyState)
        if (localCropCycles.isEmpty() && localKhataItems.isEmpty()) {
            layoutEmpty?.visibility = View.VISIBLE
            val tvEmptyTitle = findViewById<TextView>(R.id.tvDashboardEmptyTitle)
            val tvEmptySubtitle = findViewById<TextView>(R.id.tvDashboardEmptySubtitle)
            val btnAddFirst = findViewById<MaterialButton>(R.id.btnAddFirstEntryDashboard)

            val activeLang = LocaleHelper.getPersistedLanguage(this)
            val transTitle = TranslationHelper.getDirectTranslation("No Reports Available Yet", activeLang)
                ?: if (activeLang == "hi") "अभी कोई रिपोर्ट उपलब्ध नहीं है" else "No Reports Available Yet"
            val transSubtitle = TranslationHelper.getDirectTranslation("Please check again after adding crop season and expenses.", activeLang)
                ?: if (activeLang == "hi") "फसल सीजन और खर्च दर्ज करने के बाद पुनः चेक करे।" else "Please check again after adding crop season and expenses."
            val transBtn = TranslationHelper.getDirectTranslation("+ Add First Entry", activeLang)
                ?: if (activeLang == "hi") "पहला खर्च / आय दर्ज करें" else "Add First Entry"

            tvEmptyTitle?.text = transTitle
            tvEmptySubtitle?.text = transSubtitle
            btnAddFirst?.text = transBtn

            btnAddFirst?.setOnClickListener {
                val intent = Intent(this, KisanDiaryHistoryActivity::class.java).apply {
                    putExtra("EXTRA_OPEN_ADD_ENTRY", true)
                }
                startActivity(intent)
            }
        } else {
            layoutEmpty?.visibility = View.GONE
        }

        val filteredCycles = if (selectedDashboardCropCycleIds.isEmpty()) {
            localCropCycles.take(10)
        } else {
            localCropCycles.filter { selectedDashboardCropCycleIds.contains(it.id) }.take(10)
        }

        val filteredCycleIds = filteredCycles.map { it.id }.toSet()
        val filteredKhataItems = if (selectedDashboardCropCycleIds.isEmpty()) {
            localKhataItems
        } else {
            localKhataItems.filter { it.cropCycleId in filteredCycleIds || (it.cropCycleId.isBlank() && filteredCycles.any { cycle -> it.cropName.contains(cycle.cropName, ignoreCase = true) }) }
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
        val activeLang = LocaleHelper.getPersistedLanguage(this)
        val highestCostExpense = expenseItems.firstOrNull { it.amount > 0 }
        val inputCostPercent = expenseItems.filter { it.categoryName in listOf("Seeds", "Fertiliser", "Pesticides") }.sumOf { it.percent }

        val rawInsight1Val = if (activeLang == "hi") "मुनाफा: ${profitMarginPercent.toInt()}%" else "${profitMarginPercent.toInt()}% Margin"
        val rawInsight1Desc = if (activeLang == "hi") "आप कमाई का ${profitMarginPercent.toInt()}% हिस्सा मुनाफा बचा रहे हैं।" else "You keep ${profitMarginPercent.toInt()}% of income as profit."

        val rawInsight2Val = if (highestCostExpense != null) {
            TranslationHelper.getTranslatedCategoryName(highestCostExpense.categoryName, activeLang, this)
        } else {
            if (activeLang == "hi") "सबसे बड़ा खर्च" else "Highest Cost"
        }

        val rawInsight2Desc = if (highestCostExpense != null) {
            val catName = TranslationHelper.getTranslatedCategoryName(highestCostExpense.categoryName, activeLang, this)
            if (activeLang == "hi") "$catName पर सबसे ज्यादा खर्च हुआ (${highestCostExpense.percent}%)।" else "$catName is your biggest expense (${highestCostExpense.percent}%)."
        } else {
            if (activeLang == "hi") "अपने दैनिक खर्चों को ट्रैक करें।" else "Track your daily expenses to discover cost drivers."
        }

        val rawInsight3Val = if (activeLang == "hi") "लागत घटाएं" else "Cost Reduction"
        val rawInsight3Desc = if (activeLang == "hi") {
            if (inputCostPercent > 0) "बीज-खाद जैसी इनपुट लागत कुल खर्च का $inputCostPercent% है। इसे घटाने से मुनाफा बढ़ेगा।" else "इनपुट लागत कम करने से सीधा मुनाफा बढ़ता है।"
        } else {
            if (inputCostPercent > 0) "Input costs make up $inputCostPercent% of expenses. Reducing them boosts profit." else "Reducing input costs directly increases your profit."
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val transI1V = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight1Val, activeLang)
            val transI1D = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight1Desc, activeLang)
            val transI2V = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight2Val, activeLang)
            val transI2D = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight2Desc, activeLang)
            val transI3V = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight3Val, activeLang)
            val transI3D = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, rawInsight3Desc, activeLang)

            if (!isFinishing) {
                tvInsight1Value.text = transI1V
                tvInsight1Desc.text = transI1D
                tvInsight2Value.text = transI2V
                tvInsight2Desc.text = transI2D
                tvInsight3Value.text = transI3V
                tvInsight3Desc.text = transI3D
            }
        }

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
        cardInsight2.setCardBackgroundColor(bgRed)
        tvInsight2Value.setTextColor(colorRed)
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

        lifecycleScope.launch(Dispatchers.Main) {
            val titleText = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Kisan Diary Dashboard", activeLang)
            toolbar.title = titleText
            tvFarmingHeaderTitle.text = titleText

            tvLabelTotalArea.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Farming Area", activeLang)
            tvLabelTotalExpenses.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Expenses", activeLang)
            tvLabelTotalIncome.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Income", activeLang)
            tvLabelEstimatedProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Estimated Profit", activeLang)

            tvSectionFinancialSummaryTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Earnings, Expenses & Profit", activeLang)
            tvLabelLegendProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Profit", activeLang)
            tvLabelLegendExpenses.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Expense", activeLang)

            btnToggleTotalProfit.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Profit (₹)", activeLang)
            btnToggleProfitPerAcre.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Profit / Acre (₹/Acre)", activeLang)

            btnToggleCropTotalExpense.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Total Expense (₹)", activeLang)
            btnToggleCropExpensePerAcre.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Expense / Acre (₹/Acre)", activeLang)

            tvSectionCropPerformanceTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Crop Wise Profit", activeLang)
            tvHeaderMajorExpenses.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Highest Expense", activeLang)
            tvSectionStageExpenseTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Crop Stage Wise Expenses", activeLang)
            tvSectionCropExpenseTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Crop Wise Expenses", activeLang)
            tvSectionKeyInsightsTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Suggestions & Conclusions", activeLang)
            tvSectionRecentLogTitle.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "Recent Activity Log", activeLang)

            btnViewAllEntries.text = TranslationHelper.translateText(this@KisanDiaryDashboardActivity, "View All Entries", activeLang)
            val btnExportPdf = findViewById<MaterialButton>(R.id.btnExportPdfReport)
            if (btnExportPdf != null) {
                btnExportPdf.text = TranslationHelper.getDirectTranslation("PDF Report", activeLang)
                    ?: if (activeLang == "hi") "PDF रिपोर्ट" else "PDF Report"
            }
        }
    }

    private fun generateAndExportPdfReport() {
        val activeLang = LocaleHelper.getPersistedLanguage(this)
        Toast.makeText(this, if (activeLang == "hi") "⏳ PDF रिपोर्ट जनरेट हो रही है..." else "Generating PDF Report...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageWidth = 595 // Standard A4 Width in points
                val pageHeight = 842 // Standard A4 Height in points

                var currentPageNum = 1
                var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                val paint = Paint()
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

                val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
                val formatter = NumberFormat.getCurrencyInstance(inrLocale).apply { maximumFractionDigits = 0 }

                // Data Aggregations
                val filteredCycles = if (selectedDashboardCropCycleIds.isEmpty()) localCropCycles.take(10) else localCropCycles.filter { selectedDashboardCropCycleIds.contains(it.id) }.take(10)
                val filteredCycleIds = filteredCycles.map { it.id }.toSet()
                val filteredKhataItems = if (selectedDashboardCropCycleIds.isEmpty()) localKhataItems else localKhataItems.filter { it.cropCycleId in filteredCycleIds || (it.cropCycleId.isBlank() && filteredCycles.any { cycle -> it.cropName.contains(cycle.cropName, ignoreCase = true) }) }

                val totalAreaAcres = filteredCycles.sumOf { it.areaAcres }
                val expenseEntries = filteredKhataItems.filter { !it.isIncome }
                val incomeEntries = filteredKhataItems.filter { it.isIncome }
                val totalExpenses = expenseEntries.sumOf { it.amount }
                val totalIncome = incomeEntries.sumOf { it.amount }
                val netProfit = totalIncome - totalExpenses
                val profitMarginPercent = if (totalIncome > 0) ((netProfit / totalIncome) * 100).coerceAtLeast(0.0) else 0.0

                fun drawHeader(canvas: Canvas) {
                    paint.color = Color.parseColor("#1B5E20")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 70f, paint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = 15f
                    textPaint.isFakeBoldText = true
                    canvas.drawText("KISAN KENDRA - FARMING REPORT", 20f, 32f, textPaint)

                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = false
                    canvas.drawText("किसान केंद्र — सम्पूर्ण कृषि एवं व्यय रिपोर्ट", 20f, 52f, textPaint)

                    // Draw White Badge on Top-Right Header for High Contrast & Logo Visibility
                    paint.color = Color.WHITE
                    paint.style = Paint.Style.FILL
                    val logoBadgeRect = RectF((pageWidth - 68).toFloat(), 11f, (pageWidth - 20).toFloat(), 59f)
                    canvas.drawRoundRect(logoBadgeRect, 10f, 10f, paint)

                    // Draw App Logo inside the White Badge
                    try {
                        val logoDrawable = ContextCompat.getDrawable(this@KisanDiaryDashboardActivity, R.mipmap.ic_launcher)
                        if (logoDrawable != null) {
                            val logoWidth = 38
                            val logoHeight = 38
                            val logoBitmap = Bitmap.createBitmap(logoWidth, logoHeight, Bitmap.Config.ARGB_8888)
                            val logoCanvas = Canvas(logoBitmap)
                            logoDrawable.setBounds(0, 0, logoWidth, logoHeight)
                            logoDrawable.draw(logoCanvas)

                            canvas.drawBitmap(logoBitmap, (pageWidth - 63).toFloat(), 16f, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                fun drawFooter(canvas: Canvas, pageNum: Int) {
                    paint.color = Color.parseColor("#2E7D32")
                    paint.strokeWidth = 1.5f
                    canvas.drawLine(20f, (pageHeight - 35).toFloat(), (pageWidth - 20).toFloat(), (pageHeight - 35).toFloat(), paint)

                    textPaint.color = Color.parseColor("#64748B")
                    textPaint.textSize = 8.5f
                    textPaint.isFakeBoldText = false
                    canvas.drawText("Generated by Kisan Kendra App — Smart Farming Companion", 20f, (pageHeight - 18).toFloat(), textPaint)
                    canvas.drawText("Page $pageNum", (pageWidth - 60).toFloat(), (pageHeight - 18).toFloat(), textPaint)
                }

                var yPos = 95f

                fun checkAndNewPage(requiredHeight: Float): Float {
                    if (yPos + requiredHeight > pageHeight - 50f) {
                        drawFooter(canvas, currentPageNum)
                        pdfDocument.finishPage(page)

                        currentPageNum++
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        drawHeader(canvas)
                        return 90f
                    }
                    return yPos
                }

                // Initial Page Setup
                drawHeader(canvas)

                // Metadata Bar
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                val dateStr = sdf.format(Date())

                textPaint.color = Color.parseColor("#334155")
                textPaint.textSize = 9.5f
                textPaint.isFakeBoldText = false
                canvas.drawText("Generated On: $dateStr", 20f, yPos, textPaint)
                canvas.drawText("Season Filter: $selectedSeasonFilter", 350f, yPos, textPaint)

                yPos += 14f
                paint.color = Color.parseColor("#CBD5E1")
                paint.strokeWidth = 1f
                canvas.drawLine(20f, yPos, (pageWidth - 20).toFloat(), yPos, paint)

                yPos += 16f

                // 1. KPI Summary Box
                paint.color = Color.parseColor("#F1F8E9")
                paint.style = Paint.Style.FILL
                val kpiRect = RectF(20f, yPos, (pageWidth - 20).toFloat(), yPos + 62f)
                canvas.drawRoundRect(kpiRect, 8f, 8f, paint)

                paint.color = Color.parseColor("#2E7D32")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(kpiRect, 8f, 8f, paint)

                textPaint.textSize = 9f
                textPaint.color = Color.parseColor("#1B5E20")
                textPaint.isFakeBoldText = true

                canvas.drawText("TOTAL AREA", 35f, yPos + 22f, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 11f
                canvas.drawText(String.format(Locale.ENGLISH, "%.1f Acres", totalAreaAcres), 35f, yPos + 44f, textPaint)

                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                canvas.drawText("TOTAL INCOME", 160f, yPos + 22f, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 11f
                textPaint.color = Color.parseColor("#2E7D32")
                canvas.drawText(formatter.format(totalIncome), 160f, yPos + 44f, textPaint)

                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                textPaint.color = Color.parseColor("#C62828")
                canvas.drawText("TOTAL EXPENSES", 300f, yPos + 22f, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 11f
                canvas.drawText(formatter.format(totalExpenses), 300f, yPos + 44f, textPaint)

                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                textPaint.color = if (netProfit >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                canvas.drawText("NET PROFIT (${profitMarginPercent.toInt()}%)", 430f, yPos + 22f, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 11f
                canvas.drawText(formatter.format(netProfit), 430f, yPos + 44f, textPaint)

                yPos += 78f

                // 2. Crop Performance & Profitability Table
                yPos = checkAndNewPage(120f)

                textPaint.color = Color.parseColor("#0F172A")
                textPaint.textSize = 11f
                textPaint.isFakeBoldText = true
                canvas.drawText("1. Crop Wise Profitability & Performance Breakdown", 20f, yPos, textPaint)

                yPos += 10f
                paint.color = Color.parseColor("#1B5E20")
                paint.style = Paint.Style.FILL
                canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 20f, paint)

                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                canvas.drawText("Crop & Season", 26f, yPos + 14f, textPaint)
                canvas.drawText("Area", 200f, yPos + 14f, textPaint)
                canvas.drawText("Expenses", 280f, yPos + 14f, textPaint)
                canvas.drawText("Earnings", 370f, yPos + 14f, textPaint)
                canvas.drawText("Net Profit", 460f, yPos + 14f, textPaint)

                yPos += 20f
                textPaint.textSize = 8.5f
                textPaint.isFakeBoldText = false

                var rIdx = 0
                for (cycle in filteredCycles) {
                    yPos = checkAndNewPage(20f)
                    val items = filteredKhataItems.filter { it.cropCycleId == cycle.id || (it.cropCycleId.isBlank() && it.cropName.equals(cycle.cropName, ignoreCase = true)) }
                    val cExp = items.filter { !it.isIncome }.sumOf { it.amount }
                    val cInc = items.filter { it.isIncome }.sumOf { it.amount }
                    val cNet = cInc - cExp

                    paint.color = if (rIdx % 2 == 0) Color.parseColor("#F8FAFC") else Color.WHITE
                    canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 18f, paint)

                    textPaint.color = Color.parseColor("#1E293B")
                    val cropLabel = "${cycle.cropName} (${cycle.seasonName})"
                    val truncatedCrop = if (cropLabel.length > 26) cropLabel.substring(0, 23) + "..." else cropLabel
                    canvas.drawText(truncatedCrop, 26f, yPos + 13f, textPaint)
                    canvas.drawText(String.format(Locale.ENGLISH, "%.1f Ac", cycle.areaAcres), 200f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#C62828")
                    canvas.drawText(formatter.format(cExp), 280f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#2E7D32")
                    canvas.drawText(formatter.format(cInc), 370f, yPos + 13f, textPaint)

                    textPaint.color = if (cNet >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                    canvas.drawText(formatter.format(cNet), 460f, yPos + 13f, textPaint)

                    yPos += 18f
                    rIdx++
                }

                yPos += 16f

                // 3. Major Expense Category Breakdown Table
                yPos = checkAndNewPage(100f)

                textPaint.color = Color.parseColor("#0F172A")
                textPaint.textSize = 11f
                textPaint.isFakeBoldText = true
                canvas.drawText("2. Major Expenses Breakdown by Category", 20f, yPos, textPaint)

                yPos += 10f
                paint.color = Color.parseColor("#1B5E20")
                paint.style = Paint.Style.FILL
                canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 20f, paint)

                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                canvas.drawText("Expense Category", 26f, yPos + 14f, textPaint)
                canvas.drawText("Total Amount (₹)", 280f, yPos + 14f, textPaint)
                canvas.drawText("Share (% of Total)", 440f, yPos + 14f, textPaint)

                yPos += 20f

                val categoryMap = mutableMapOf<String, Double>()
                for (item in expenseEntries) {
                    val cName = getCleanCategoryName(item.category)
                    categoryMap[cName] = (categoryMap[cName] ?: 0.0) + item.amount
                }

                val sortedCategories = categoryMap.entries.sortedByDescending { it.value }
                var catIdx = 0
                for ((catName, catAmt) in sortedCategories) {
                    yPos = checkAndNewPage(18f)
                    val pct = if (totalExpenses > 0) ((catAmt / totalExpenses) * 100).toInt() else 0

                    paint.color = if (catIdx % 2 == 0) Color.parseColor("#F8FAFC") else Color.WHITE
                    canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 18f, paint)

                    textPaint.color = Color.parseColor("#1E293B")
                    canvas.drawText(catName, 26f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#C62828")
                    canvas.drawText(formatter.format(catAmt), 280f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#334155")
                    canvas.drawText("$pct%", 440f, yPos + 13f, textPaint)

                    yPos += 18f
                    catIdx++
                }

                yPos += 16f

                // 4. Stage-Wise Crop Expense Breakdown
                yPos = checkAndNewPage(100f)

                textPaint.color = Color.parseColor("#0F172A")
                textPaint.textSize = 11f
                textPaint.isFakeBoldText = true
                canvas.drawText("3. Stage-Wise Crop Expense Breakdown", 20f, yPos, textPaint)

                yPos += 10f
                paint.color = Color.parseColor("#1B5E20")
                paint.style = Paint.Style.FILL
                canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 20f, paint)

                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                textPaint.isFakeBoldText = true
                canvas.drawText("Farming Stage", 26f, yPos + 14f, textPaint)
                canvas.drawText("Expense Amount (₹)", 280f, yPos + 14f, textPaint)
                canvas.drawText("Stage Share (%)", 440f, yPos + 14f, textPaint)

                yPos += 20f

                val stageMap = mutableMapOf<String, Double>()
                for (item in expenseEntries) {
                    val sKey = normalizeStageName(item.stage)
                    stageMap[sKey] = (stageMap[sKey] ?: 0.0) + item.amount
                }

                val defaultStagesList = listOf("Sowing", "Irrigation", "Spray", "Harvesting", "Other")
                var stageIdx = 0
                for (stg in defaultStagesList) {
                    val stgAmt = stageMap[stg] ?: 0.0
                    yPos = checkAndNewPage(18f)
                    val pct = if (totalExpenses > 0) ((stgAmt / totalExpenses) * 100).toInt() else 0

                    paint.color = if (stageIdx % 2 == 0) Color.parseColor("#F8FAFC") else Color.WHITE
                    canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 18f, paint)

                    textPaint.color = Color.parseColor("#1E293B")
                    canvas.drawText(stg, 26f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#C62828")
                    canvas.drawText(formatter.format(stgAmt), 280f, yPos + 13f, textPaint)

                    textPaint.color = Color.parseColor("#334155")
                    canvas.drawText("$pct%", 440f, yPos + 13f, textPaint)

                    yPos += 18f
                    stageIdx++
                }

                yPos += 16f

                // 5. Key Insights & Recommendations Box
                yPos = checkAndNewPage(110f)

                textPaint.color = Color.parseColor("#0F172A")
                textPaint.textSize = 11f
                textPaint.isFakeBoldText = true
                canvas.drawText("4. Key Farming Insights & Recommendations", 20f, yPos, textPaint)

                yPos += 10f
                paint.color = Color.parseColor("#FFF8E1")
                paint.style = Paint.Style.FILL
                val insightRect = RectF(20f, yPos, (pageWidth - 20).toFloat(), yPos + 60f)
                canvas.drawRoundRect(insightRect, 8f, 8f, paint)

                paint.color = Color.parseColor("#F57F17")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(insightRect, 8f, 8f, paint)

                val highestCostExpense = sortedCategories.firstOrNull()
                val topCatName = highestCostExpense?.key ?: "Input Costs"
                val topCatPct = if (totalExpenses > 0 && highestCostExpense != null) ((highestCostExpense.value / totalExpenses) * 100).toInt() else 0

                textPaint.textSize = 8.5f
                textPaint.color = Color.parseColor("#78350F")
                textPaint.isFakeBoldText = true

                canvas.drawText("• Profit Ratio: ${profitMarginPercent.toInt()}% of income kept as net profit.", 28f, yPos + 18f, textPaint)
                canvas.drawText("• Highest Cost Driver: $topCatName is your largest expense category ($topCatPct% of total expenses).", 28f, yPos + 34f, textPaint)
                canvas.drawText("• Cost Reduction Tip: Optimizing fertilizer & pesticide usage directly increases harvest margins.", 28f, yPos + 50f, textPaint)

                yPos += 75f

                // 6. Recent Diary Activity Log
                if (filteredKhataItems.isNotEmpty()) {
                    yPos = checkAndNewPage(120f)

                    textPaint.color = Color.parseColor("#0F172A")
                    textPaint.textSize = 11f
                    textPaint.isFakeBoldText = true
                    canvas.drawText("5. Recent Kisan Diary Activity Log", 20f, yPos, textPaint)

                    yPos += 10f
                    paint.color = Color.parseColor("#1B5E20")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 20f, paint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = true
                    canvas.drawText("Date", 26f, yPos + 14f, textPaint)
                    canvas.drawText("Category", 120f, yPos + 14f, textPaint)
                    canvas.drawText("Type", 270f, yPos + 14f, textPaint)
                    canvas.drawText("Amount", 370f, yPos + 14f, textPaint)
                    canvas.drawText("Note", 460f, yPos + 14f, textPaint)

                    yPos += 20f
                    textPaint.textSize = 8.5f
                    textPaint.isFakeBoldText = false

                    for ((idx, item) in filteredKhataItems.withIndex()) {
                        yPos = checkAndNewPage(18f)

                        paint.color = if (idx % 2 == 0) Color.parseColor("#F8FAFC") else Color.WHITE
                        canvas.drawRect(20f, yPos, (pageWidth - 20).toFloat(), yPos + 18f, paint)

                        textPaint.color = Color.parseColor("#334155")
                        canvas.drawText(item.dateStr.ifBlank { "Recent" }, 26f, yPos + 13f, textPaint)

                        val catText = if (item.category.length > 20) item.category.substring(0, 18) + ".." else item.category
                        canvas.drawText(catText, 120f, yPos + 13f, textPaint)

                        val typeText = if (item.isIncome) "Income (+)" else "Expense (-)"
                        textPaint.color = if (item.isIncome) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                        canvas.drawText(typeText, 270f, yPos + 13f, textPaint)

                        canvas.drawText(formatter.format(item.amount), 370f, yPos + 13f, textPaint)

                        textPaint.color = Color.parseColor("#64748B")
                        val noteText = if (item.note.length > 20) item.note.substring(0, 18) + ".." else item.note.ifBlank { "-" }
                        canvas.drawText(noteText, 460f, yPos + 13f, textPaint)

                        yPos += 18f
                    }
                }

                // Draw Footer on Final Page
                drawFooter(canvas, currentPageNum)
                pdfDocument.finishPage(page)

                val pdfFile = File(cacheDir, "Kisan_Kendra_Report_${System.currentTimeMillis()}.pdf")
                val fos = FileOutputStream(pdfFile)
                pdfDocument.writeTo(fos)
                fos.close()
                pdfDocument.close()

                val uri = FileProvider.getUriForFile(this@KisanDiaryDashboardActivity, "${packageName}.fileprovider", pdfFile)

                withContext(Dispatchers.Main) {
                    val msg = if (activeLang == "hi") "PDF रिपोर्ट जनरेट हो गई!" else "PDF Report Generated Successfully!"
                    Toast.makeText(this@KisanDiaryDashboardActivity, msg, Toast.LENGTH_SHORT).show()

                    val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(pdfIntent, if (activeLang == "hi") "PDF रिपोर्ट खोलें या शेयर करें" else "View or Share PDF Report")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@KisanDiaryDashboardActivity, "Error generating PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getCleanCategoryName(rawCategory: String): String {
        val trimmed = rawCategory.trim()
        if (trimmed.isBlank()) return "General Expense"

        val lower = trimmed.lowercase(Locale.ENGLISH)
        return when {
            lower == "seed" || lower == "seeds" || lower == "बीज" -> "Seeds"
            lower.contains("fertilizer") || lower.contains("fertiliser") || lower.contains("खाद") || lower.contains("उर्वरक") -> "Fertilizer"
            lower.contains("pesticide") || lower.contains("pesticides") || lower.contains("कीटनाशक") || lower.contains("दवा") -> "Pesticides"
            lower == "irrigation" || lower == "water" || lower == "पानी" || lower == "सिंचाई" -> "Irrigation"
            lower.contains("machinery") || lower.contains("मशीनरी") -> "Machinery"
            lower.contains("fuel") || lower.contains("ईंधन") || lower.contains("diesel") -> "Fuel"
            lower.contains("labor") || lower.contains("labour") || lower.contains("मजदूरी") -> "Labour"
            lower.contains("tillage") || lower.contains("जुताई") -> "Tillage"
            lower.contains("storage") || lower.contains("भंडारण") -> "Storage"
            lower.contains("transport") || lower.contains("परिवहन") -> "Transport"
            lower.contains("insurance") || lower.contains("बीमा") -> "Insurance"
            lower.contains("loan") || lower.contains("interest") || lower.contains("ऋण") || lower.contains("ब्याज") -> "Loan Interest"
            lower.contains("crop sale") || lower.contains("फसल बिक्री") -> "Crop Sale"
            lower.contains("vegetable sale") || lower.contains("सब्जी बिक्री") -> "Vegetable Sale"
            lower.contains("rental") || lower.contains("किराया") -> "Tractor / Equipment Rental"
            lower.contains("fodder") || lower.contains("चारा") -> "Fodder Sale"
            else -> TranslationHelper.getTranslatedCategoryName(trimmed, "en")
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
            lower.contains("fertilizer") || lower.contains("fertiliser") || lower.contains("खाद") || lower.contains("उर्वरक") -> R.drawable.ic_expense_fertilizer
            lower.contains("pesticide") || lower.contains("spray") || lower.contains("कीटनाशक") || lower.contains("दवा") -> R.drawable.ic_expense_pesticide
            lower.contains("irrig") || lower.contains("water") || lower.contains("सिंचाई") -> R.drawable.ic_expense_irrigation
            lower.contains("machin") || lower.contains("मशीनरी") -> R.drawable.ic_expense_machinery
            lower.contains("fuel") || lower.contains("diesel") || lower.contains("ईंधन") -> R.drawable.ic_expense_machinery
            lower.contains("labour") || lower.contains("labor") || lower.contains("मजदूरी") -> R.drawable.ic_expense_labour
            lower.contains("tillage") || lower.contains("जुताई") -> R.drawable.ic_expense_machinery
            lower.contains("storage") || lower.contains("भंडारण") -> R.drawable.ic_expense_electricity
            lower.contains("transport") || lower.contains("परिवहन") -> R.drawable.ic_expense_machinery
            lower.contains("insurance") || lower.contains("बीमा") -> R.drawable.ic_expense_electricity
            lower.contains("loan") || lower.contains("interest") || lower.contains("ऋण") || lower.contains("ब्याज") -> R.drawable.ic_expense_electricity
            else -> R.drawable.ic_expense_electricity
        }
    }
}
