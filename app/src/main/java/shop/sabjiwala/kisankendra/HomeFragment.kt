package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.FragmentHomeBinding
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.WeatherFragment
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import shop.sabjiwala.kisankendra.model.NewsItem
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.DiseaseGuideUtils
import shop.sabjiwala.kisankendra.utils.FarmingCalculatorUtils
import shop.sabjiwala.kisankendra.utils.FarmerScheme
import shop.sabjiwala.kisankendra.utils.FertilizerUtils
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.SchemeUtils
import shop.sabjiwala.kisankendra.utils.ScreenAlertHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val khataRepo by lazy { KhataRepository() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var khataAdapter: KhataAdapter? = null
    private var cropAnalyticsAdapter: CropAnalyticsAdapter? = null

    private var bannerRefreshJob: Job? = null
    private var internalToolSwitchCount = 0
    private var activeChipId: Int = R.id.chipToolKhata

    private var khataItems = mutableListOf<KhataItem>()
    private var cropCycles = mutableListOf<CropCycle>()
    private var selectedCropCycleIds = mutableSetOf<String>()
    private var publishedArticles = mutableListOf<NewsItem>()

    private var cropCyclesListener: ListenerRegistration? = null
    private var khataItemsListener: ListenerRegistration? = null
    private var alertListenerRegistration: ListenerRegistration? = null

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertListenerRegistration = ScreenAlertHelper.setupScreenAlert(requireContext(), "home_screen", binding.alertBanner)

        setupSwipeToRefresh()
        setupWelcomeBanner()

        setupToolTabs()
        setupKhataSection()
        setupCalculatorSection()
        setupWeatherToolSection()
        setupSchemesSection()
        fetchPublishedNewsArticles()
        loadToolAds()
    }

    private fun setupSwipeToRefresh() {
        if (_binding == null) return
        ShimmerHelper.setupSwipeRefresh(binding.swipeRefreshHome)
        binding.swipeRefreshHome.setOnRefreshListener {
            performHomePullRefresh()
        }
    }

    private fun performHomePullRefresh() {
        if (_binding == null) return
        val shimmerViews = listOfNotNull(
            binding.cardWelcomeBanner,
            binding.btnOpenVisualDashboard,
            binding.btnAddKhataEntry,
            binding.containerSchemeItems
        )
        ShimmerHelper.applyShimmerToGroup(shimmerViews)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                setupKhataSection()
                setupSchemesSection()
                fetchPublishedNewsArticles()
                delay(800)
            } finally {
                if (_binding != null) {
                    ShimmerHelper.stopShimmerFromGroup(shimmerViews)
                    binding.swipeRefreshHome.isRefreshing = false
                }
            }
        }
    }

    private fun loadToolAds() {
        loadAdForActiveChip(activeChipId)
    }

    private fun loadAdForActiveChip(checkedId: Int) {
        bannerRefreshJob?.cancel()

        val targetContainer = when (checkedId) {
            R.id.chipToolKhata -> binding.adContainerKhata
            R.id.chipToolCalculator -> binding.adContainerCalculator
            R.id.chipToolSchemes -> binding.adContainerSchemes
            R.id.chipToolWeather -> binding.adContainerSchemes
            else -> binding.adContainerKhata
        }

        loadToolAd(targetContainer)

        // Gentle 2-minute (120s) auto-refresh for banner ads to protect UX & user retention
        bannerRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(120_000L)
                if (_binding != null && isAdded) {
                    loadToolAd(targetContainer)
                }
            }
        }
    }

    private fun loadToolAd(container: FrameLayout) {
        val ctx = context ?: return
        container.post {
            try {
                val adView = AdView(ctx).apply {
                    adUnitId = "ca-app-pub-3054731531860427/9592937417"
                    val displayMetrics = ctx.resources.displayMetrics
                    val density = displayMetrics.density
                    val containerWidthPx = container.width.takeIf { it > 0 }
                        ?: (displayMetrics.widthPixels - (32 * density).toInt())
                    val adWidth = (containerWidthPx / density).toInt().coerceAtLeast(300)
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth))
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            container.visibility = View.VISIBLE
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            container.visibility = View.GONE
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

    companion object {
        private const val PREFS_HOME = "home_prefs"
        private const val KEY_WELCOME_DISMISSED = "welcome_banner_dismissed"
        private var isWelcomeDismissedSession = false
    }

    private fun setupWelcomeBanner() {
        if (_binding == null) return
        val ctx = context ?: return

        val prefs = ctx.getSharedPreferences(PREFS_HOME, Context.MODE_PRIVATE)
        val isDismissed = isWelcomeDismissedSession || prefs.getBoolean(KEY_WELCOME_DISMISSED, false)

        if (isDismissed) {
            binding.cardWelcomeBanner.visibility = View.GONE
            return
        }

        binding.cardWelcomeBanner.visibility = View.VISIBLE
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.ifBlank { null } ?: user?.phoneNumber ?: "Farmer Friend"
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        binding.tvHomeWelcome.text = "👋 Welcome, $name!"

        if (activeLang != "en") {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeCtx = context ?: return@launch
                if (!isAdded || _binding == null) return@launch
                val transWelcome = TranslationHelper.translateText(safeCtx, "Welcome, $name!", activeLang)
                if (_binding != null && isAdded) {
                    binding.tvHomeWelcome.text = "👋 $transWelcome"
                }
            }
        }

        binding.btnDismissWelcome.setOnClickListener {
            isWelcomeDismissedSession = true
            prefs.edit().putBoolean(KEY_WELCOME_DISMISSED, true).apply()
            binding.cardWelcomeBanner.visibility = View.GONE
        }
    }

    private fun saveHomeSessionState() {
        val ctx = context ?: return
        ctx.getSharedPreferences("home_session_prefs", Context.MODE_PRIVATE).edit()
            .putInt("active_tool_chip_id", activeChipId)
            .apply()
    }

    private fun restoreHomeSessionState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("home_session_prefs", Context.MODE_PRIVATE)
        val savedChipId = prefs.getInt("active_tool_chip_id", R.id.chipToolKhata)
        if (savedChipId != 0) {
            activeChipId = savedChipId
        }
    }

    private fun setupToolTabs() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                TranslationHelper.syncMasterDictionaryFromFirestore(ctx, activeLang)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val safeCtx = context ?: return@launch
            if (!isAdded || _binding == null) return@launch
            val transKhata = TranslationHelper.getDirectTranslation("Kisan Diary", activeLang, safeCtx)
                ?: TranslationHelper.translateText(safeCtx, "Kisan Diary", activeLang)
            val transCalc = TranslationHelper.getDirectTranslation("Calculator", activeLang, safeCtx)
                ?: TranslationHelper.translateText(safeCtx, "Calculator", activeLang)
            val transSchemes = TranslationHelper.getDirectTranslation("Govt Schemes", activeLang, safeCtx)
                ?: TranslationHelper.translateText(safeCtx, "Govt Schemes", activeLang)
            val transWeather = TranslationHelper.getDirectTranslation("Weather", activeLang, safeCtx)
                ?: TranslationHelper.translateText(safeCtx, "Weather", activeLang)

            if (_binding != null && isAdded) {
                binding.chipToolKhata.text = transKhata
                binding.chipToolCalculator.text = transCalc
                binding.chipToolSchemes.text = transSchemes
                binding.chipToolWeather.text = transWeather
            }

            val transComparisonHeader = TranslationHelper.getDirectTranslation("Crop Performance Comparison:", activeLang, safeCtx)
                ?: TranslationHelper.translateText(safeCtx, "Crop Performance Comparison:", activeLang)

            if (_binding != null && isAdded) {
                binding.tvHeaderCropComparison.text = transComparisonHeader
                binding.tvHeaderCropComparison.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_color_graphs, 0, 0, 0)
                binding.tvHeaderCropComparison.compoundDrawablePadding = (6 * requireContext().resources.displayMetrics.density).toInt()
            }
        }

        restoreHomeSessionState()
        binding.chipGroupTools.check(activeChipId)

        binding.containerKhata.visibility = if (activeChipId == R.id.chipToolKhata) View.VISIBLE else View.GONE
        binding.containerCalculator.visibility = if (activeChipId == R.id.chipToolCalculator) View.VISIBLE else View.GONE
        binding.containerSchemes.visibility = if (activeChipId == R.id.chipToolSchemes) View.VISIBLE else View.GONE
        binding.containerWeather.visibility = if (activeChipId == R.id.chipToolWeather) View.VISIBLE else View.GONE

        binding.chipGroupTools.setOnCheckedStateChangeListener { _, checkedIds ->
            if (_binding == null) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipToolKhata
            if (checkedId == activeChipId) return@setOnCheckedStateChangeListener

            activeChipId = checkedId
            saveHomeSessionState()
            internalToolSwitchCount++

            binding.containerKhata.visibility = if (checkedId == R.id.chipToolKhata) View.VISIBLE else View.GONE
            binding.containerCalculator.visibility = if (checkedId == R.id.chipToolCalculator) View.VISIBLE else View.GONE
            binding.containerSchemes.visibility = if (checkedId == R.id.chipToolSchemes) View.VISIBLE else View.GONE
            binding.containerWeather.visibility = if (checkedId == R.id.chipToolWeather) View.VISIBLE else View.GONE

            // User retention friendly: require 5 tool switches before checking for an interstitial ad
            if (internalToolSwitchCount >= 5) {
                internalToolSwitchCount = 0
                (activity as? MainActivity)?.tryShowInterstitialAd {
                    loadAdForActiveChip(checkedId)
                }
            } else {
                loadAdForActiveChip(checkedId)
            }
        }
    }

    private fun setupKhataSection() {
        val ctx = context ?: return
        if (_binding == null) return

        binding.rvKhata.layoutManager = LinearLayoutManager(ctx)
        khataAdapter = KhataAdapter(
            emptyList(),
            onEditClick = { item -> showAddKhataDialog(item) },
            onDeleteClick = { item -> deleteKhataEntry(item) }
        )
        binding.rvKhata.adapter = khataAdapter

        binding.rvCropAnalytics.layoutManager = LinearLayoutManager(ctx)
        cropAnalyticsAdapter = CropAnalyticsAdapter(
            cycles = emptyList(),
            khataItems = emptyList(),
            onToggleStatusClick = { cycle -> toggleCropCycleStatus(cycle) },
            onDeleteCropClick = { cycle -> confirmAndDeleteCropCycle(cycle) }
        )
        binding.rvCropAnalytics.adapter = cropAnalyticsAdapter

        binding.btnOpenVisualDashboard.setOnClickListener {
            (activity as? MainActivity)?.tryShowInterstitialAd {
                startActivity(Intent(ctx, KisanDiaryDashboardActivity::class.java))
            }
        }
        val isNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dashboardBgColor = Color.parseColor(if (isNight) "#A5B4FC" else "#4F46E5")
        val dashboardTextColor = Color.parseColor(if (isNight) "#1E1B4B" else "#FFFFFF")
        val rippleColor = Color.parseColor(if (isNight) "#C7D2FE" else "#3730A3")

        binding.tvBtnFullDashboard.setTextColor(dashboardTextColor)
        MediaUtils.applyShimmerAnimation(binding.btnOpenVisualDashboard, 12f, dashboardBgColor)

        val rippleDrawable = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            null,
            ColorDrawable(Color.WHITE)
        )
        binding.btnOpenVisualDashboard.foreground = rippleDrawable

        val addEntryBgColor = Color.parseColor(if (isNight) "#FBBF24" else "#D97706")
        val addEntryTextColor = Color.parseColor(if (isNight) "#451A03" else "#FFFFFF")

        binding.btnAddKhataEntry.setTextColor(addEntryTextColor)
        MediaUtils.applyShimmerAnimation(binding.btnAddKhataEntry, 16f, addEntryBgColor)
        MediaUtils.startPulseAnimation(binding.btnAddKhataEntry)

        updateKhataActionButtons()

        binding.actvCropCycleSelector.setOnClickListener {
            showMultiSelectSeasonDialog()
        }
        binding.actvCropCycleSelector.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showMultiSelectSeasonDialog()
            }
        }

        setupKisanDiarySync()

        binding.btnStartFirstSeason.setOnClickListener { showAddCropCycleDialog() }
        binding.btnAddKhataEntry.setOnClickListener { showAddKhataDialog() }
        binding.btnViewAllHistory.setOnClickListener {
            (activity as? MainActivity)?.tryShowInterstitialAd {
                startActivity(Intent(ctx, KisanDiaryHistoryActivity::class.java))
            }
        }
    }

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun onStart() {
        super.onStart()
        if (authStateListener == null) {
            authStateListener = FirebaseAuth.AuthStateListener {
                if (isAdded && _binding != null) {
                    setupKisanDiarySync()
                }
            }
            FirebaseAuth.getInstance().addAuthStateListener(authStateListener!!)
        }
        setupKisanDiarySync()
    }

    override fun onStop() {
        super.onStop()
        authStateListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        authStateListener = null
        cropCyclesListener?.remove()
        cropCyclesListener = null
        khataItemsListener?.remove()
        khataItemsListener = null
    }

    override fun onPause() {
        super.onPause()
        bannerRefreshJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && isAdded) {
            loadAdForActiveChip(activeChipId)
        }
    }

    private fun setupKisanDiarySync() {
        val safeCtx = context ?: return
        if (!isAdded || _binding == null) return

        KhataHelper.migrateGuestDataToCurrentUser(safeCtx, khataRepo)
        khataItems = KhataHelper.getLocalKhataItems(safeCtx).toMutableList()
        cropCycles = KhataHelper.getLocalCropCycles(safeCtx).toMutableList()

        KhataHelper.reconcileOrphanedKhataItems(safeCtx, khataRepo, cropCycles, khataItems)

        updateCropSelectorText()
        updateKhataUi()

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) return

        // ⚡ Smart Local-First Cloud Restore:
        // ONLY query Firestore if local device cache is empty (e.g., fresh install or app data cleared).
        // If local entries exist, rely 100% on local storage to maintain 0 Firebase Reads during daily app usage.
        if (cropCycles.isEmpty()) {
            khataRepo.fetchCropCycles { remoteCycles ->
                val ctx = context ?: return@fetchCropCycles
                if (!isAdded || _binding == null) return@fetchCropCycles
                if (remoteCycles.isNotEmpty()) {
                    cropCycles = remoteCycles.sortedByDescending { it.timestamp }.toMutableList()
                    KhataHelper.saveLocalCropCycles(ctx, cropCycles)
                    KhataHelper.reconcileOrphanedKhataItems(ctx, khataRepo, cropCycles, khataItems)
                    updateCropSelectorText()
                    updateKhataUi()
                }
            }
        }

        if (khataItems.isEmpty()) {
            khataRepo.fetchKhataItems { remoteItems ->
                val ctx = context ?: return@fetchKhataItems
                if (!isAdded || _binding == null) return@fetchKhataItems
                if (remoteItems.isNotEmpty()) {
                    khataItems = remoteItems.sortedByDescending { it.timestamp }.toMutableList()
                    KhataHelper.saveLocalKhataItems(ctx, khataItems)
                    KhataHelper.reconcileOrphanedKhataItems(ctx, khataRepo, cropCycles, khataItems)
                    updateCropSelectorText()
                    updateKhataUi()
                }
            }
        }
    }

    private fun updateCropSelectorText() {
        val ctx = context ?: return
        if (!isAdded || _binding == null) return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        if (selectedCropCycleIds.isEmpty() && cropCycles.isNotEmpty()) {
            selectedCropCycleIds.add(cropCycles.first().id)
        }

        if (selectedCropCycleIds.isEmpty()) {
            val placeholder = if (activeLang == "hi") "सेलेक्ट सीजन" else "Select Season"
            binding.actvCropCycleSelector.setText(placeholder, false)
        } else if (selectedCropCycleIds.size == 1) {
            val cycle = cropCycles.firstOrNull { it.id == selectedCropCycleIds.first() }
            if (cycle != null) {
                val cropTrans = TranslationHelper.getTranslatedCropName(cycle.cropName, activeLang, ctx)
                val seasonTrans = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, ctx)
                binding.actvCropCycleSelector.setText("$cropTrans ($seasonTrans)", false)
            } else {
                val placeholder = if (activeLang == "hi") "1 सीजन चुना गया" else "1 Season Selected"
                binding.actvCropCycleSelector.setText(placeholder, false)
            }
        } else {
            val count = selectedCropCycleIds.size
            val text = if (activeLang == "hi") "$count सीजन चुने गए" else "$count Seasons Selected"
            binding.actvCropCycleSelector.setText(text, false)
        }
    }

    private fun showMultiSelectSeasonDialog() {
        val ctx = context ?: return
        if (!isAdded || _binding == null) return

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        if (cropCycles.isEmpty()) {
            showAddCropCycleDialog()
            return
        }

        val checkedItems = BooleanArray(cropCycles.size) { index ->
            selectedCropCycleIds.contains(cropCycles[index].id)
        }

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_season_filter_multiselect, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvSeasonFilterTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSeasonFilterSubtitle)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseSeasonDialog)
        val containerChoices = dialogView.findViewById<LinearLayout>(R.id.containerSeasonChoices)
        val btnClearAll = dialogView.findViewById<MaterialButton>(R.id.btnClearAllSeasons)
        val btnAddSeason = dialogView.findViewById<MaterialButton>(R.id.btnAddSeasonInDialog)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplySeasonFilter)

        val transTitle = TranslationHelper.getDirectTranslation("Select Seasons", activeLang) ?: if (activeLang == "hi") "सीजन चुनें" else "Select Seasons"
        val transSubtitle = TranslationHelper.getDirectTranslation("Select seasons to filter your Kisan Diary", activeLang) ?: if (activeLang == "hi") "किसान डायरी फ़िल्टर करने के लिए सीजन चुनें" else "Select seasons to filter your Kisan Diary"
        val transBtnApply = TranslationHelper.getDirectTranslation("Apply", activeLang) ?: if (activeLang == "hi") "लागू करें" else "Apply"
        val transBtnAdd = TranslationHelper.getDirectTranslation("Add New Season", activeLang) ?: if (activeLang == "hi") "नया सीजन जोड़े" else "Add New Season"
        val transBtnClear = TranslationHelper.getDirectTranslation("Clear All", activeLang) ?: if (activeLang == "hi") "सभी साफ़ करें" else "Clear All"
        val maxLimitMsg = if (activeLang == "hi") "आप अधिकतम 10 सीजन चुन सकते हैं" else "You can select up to 10 seasons"

        tvTitle.text = transTitle
        tvSubtitle.text = transSubtitle
        btnApply.text = transBtnApply
        btnAddSeason.text = "+ $transBtnAdd"
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
        for (index in cropCycles.indices) {
            val cycle = cropCycles[index]
            val rowView = LayoutInflater.from(ctx).inflate(R.layout.item_season_filter_choice, containerChoices, false)
            val card = rowView.findViewById<MaterialCardView>(R.id.cardSeasonChoice)
            val tvLabel = rowView.findViewById<TextView>(R.id.tvSeasonLabel)
            val cbCheck = rowView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbSeasonCheck)

            val cropTrans = TranslationHelper.getTranslatedCropName(cycle.cropName, activeLang, ctx)
            val seasonTrans = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, ctx)
            tvLabel.text = "$cropTrans ($seasonTrans)"

            updateRowVisualState(card, cbCheck, checkedItems[index])

            card.setOnClickListener {
                val isCurrentlyChecked = checkedItems[index]
                if (!isCurrentlyChecked) {
                    val count = checkedItems.count { it }
                    if (count >= 10) {
                        Toast.makeText(ctx, maxLimitMsg, Toast.LENGTH_SHORT).show()
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

        val dialog = MaterialAlertDialogBuilder(ctx)
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

        btnAddSeason.setOnClickListener {
            dialog.dismiss()
            showAddCropCycleDialog()
        }

        btnApply.setOnClickListener {
            selectedCropCycleIds.clear()
            for (i in cropCycles.indices) {
                if (checkedItems[i]) {
                    selectedCropCycleIds.add(cropCycles[i].id)
                }
            }
            updateCropSelectorText()
            updateKhataUi()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateKhataActionButtons() {
        val ctx = context ?: return
        if (!isAdded || _binding == null) return

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val transAddEntry = TranslationHelper.getDirectTranslation("Add Entry", activeLang, ctx)
            ?: if (activeLang == "hi") "एन्ट्री जोड़ें" else "Add Entry"

        val transViewHistory = TranslationHelper.getDirectTranslation("Diary History", activeLang, ctx)
            ?: if (activeLang == "hi") "पुरानी एन्ट्री देखें" else "Diary History"

        val transReport = TranslationHelper.getDirectTranslation("Report", activeLang, ctx)
            ?: if (activeLang == "hi") "रिपोर्ट ➔" else "Report ➔"

        binding.btnAddKhataEntry.text = transAddEntry
        binding.btnViewAllHistory.text = transViewHistory
        binding.tvBtnFullDashboard.text = transReport

        if (activeLang != "en" && activeLang != "hi") {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeCtx = context ?: return@launch
                if (!isAdded || _binding == null) return@launch
                val asyncAdd = TranslationHelper.translateText(safeCtx, "Add Entry", activeLang)
                val asyncHistory = TranslationHelper.translateText(safeCtx, "Diary History", activeLang)
                val asyncReport = TranslationHelper.translateText(safeCtx, "Report", activeLang)
                if (_binding != null && isAdded) {
                    if (asyncAdd.isNotBlank()) binding.btnAddKhataEntry.text = asyncAdd
                    if (asyncHistory.isNotBlank()) binding.btnViewAllHistory.text = asyncHistory
                    if (asyncReport.isNotBlank()) binding.tvBtnFullDashboard.text = "$asyncReport ➔"
                }
            }
        }
    }

    private fun updateKhataUi() {
        val ctx = context ?: return
        if (!isAdded || _binding == null) return

        updateKhataActionButtons()
        updateAnalyticsDashboard()

        val filteredItems = if (selectedCropCycleIds.isEmpty()) {
            khataItems
        } else {
            khataItems.filter { item ->
                selectedCropCycleIds.contains(item.cropCycleId) ||
                        (item.cropCycleId.isBlank() && cropCycles.any { selectedCropCycleIds.contains(it.id) && it.cropName.equals(item.cropName, ignoreCase = true) })
            }
        }
        khataAdapter?.updateData(filteredItems.take(3))
    }

    private fun updateAnalyticsDashboard() {
        val ctx = context ?: return
        if (!isAdded || _binding == null) return

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        if (cropCycles.isEmpty()) {
            binding.layoutKisanDiaryOnboarding.visibility = View.VISIBLE
            binding.containerDashboardAnalytics.visibility = View.GONE

            val transTitle = TranslationHelper.getDirectTranslation("Welcome to Kisan Diary!", activeLang)
                ?: if (activeLang == "hi") "किसान डायरी में आपका स्वागत है!" else "Welcome to Kisan Diary!"
            val transSubtitle = TranslationHelper.getDirectTranslation("Track crop expenses, fertilizer, and harvest profits easily.", activeLang)
                ?: if (activeLang == "hi") "अपनी फसल का खर्च, खाद और मुनाफे का हिसाब रखें।" else "Track crop expenses, fertilizer, and harvest profits easily."
            val transBtn = TranslationHelper.getDirectTranslation("+ Start First Crop Season", activeLang)
                ?: if (activeLang == "hi") "पहला फसल सीजन शुरू करें" else "Start First Crop Season"

            binding.tvOnboardingTitle.text = transTitle
            binding.tvOnboardingSubtitle.text = transSubtitle
            binding.btnStartFirstSeason.text = transBtn
            return
        }

        binding.layoutKisanDiaryOnboarding.visibility = View.GONE

        val activeCycles = if (selectedCropCycleIds.isEmpty()) {
            cropCycles.take(4)
        } else {
            cropCycles.filter { selectedCropCycleIds.contains(it.id) }.take(4)
        }

        if (activeCycles.isEmpty()) {
            binding.containerDashboardAnalytics.visibility = View.GONE
            return
        }

        binding.containerDashboardAnalytics.visibility = View.VISIBLE
        cropAnalyticsAdapter?.updateData(activeCycles, khataItems)
    }

    private fun showAddCropCycleDialog() {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_crop_cycle, null)
        val actvCrop = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCycleCrop)
        val actvSeason = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCycleSeason)
        val etArea = dialogView.findViewById<EditText>(R.id.etCycleArea)
        val actvUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCycleUnit)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnCycleSave)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCycleCancel)

        val currYear = Calendar.getInstance().get(Calendar.YEAR)
        val seasons = listOf(
            "Kharif $currYear",
            "Rabi $currYear",
            "Zaid $currYear",
            "Kharif ${currYear + 1}",
            "Rabi ${currYear + 1}",
            "Zaid ${currYear + 1}"
        )
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
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

        val displaySeasons = seasons.map { getTranslatedSeason(it) }

        val masterCropList = TranslationHelper.DEFAULT_HINDI_CROPS_LIST
        val displayCrops = masterCropList.map { crop ->
            if (activeLang == "hi") {
                crop
            } else {
                TranslationHelper.getTranslatedCropName(crop, activeLang, ctx)
            }
        }

        val cropAdapter = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, displayCrops)
        actvCrop.setAdapter(cropAdapter)

        actvCrop.setOnItemClickListener { _, _, position, _ ->
            val itemText = actvCrop.adapter.getItem(position).toString()
            if (itemText.contains("Type Custom") || itemText.contains("अन्य फसल") || itemText.contains("Custom")) {
                actvCrop.setText("")
                actvCrop.requestFocus()
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(actvCrop, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        val seasonAdapter = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, displaySeasons)
        actvSeason.setAdapter(seasonAdapter)

        actvUnit.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, FertilizerUtils.units))
        actvUnit.setText(FertilizerUtils.units[0], false)

        lifecycleScope.launch(Dispatchers.Main) {
            val tvTitle = dialogView.findViewById<TextView>(R.id.tvCycleDialogTitle)
            val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvCycleDialogSubtitle)
            val tilCrop = dialogView.findViewById<TextInputLayout>(R.id.tilCycleCrop)
            val tilSeason = dialogView.findViewById<TextInputLayout>(R.id.tilCycleSeason)
            val tilArea = dialogView.findViewById<TextInputLayout>(R.id.tilCycleArea)
            val tilUnit = dialogView.findViewById<TextInputLayout>(R.id.tilCycleUnit)

            val transTitle = TranslationHelper.translateText(ctx, "Start New Crop Season", activeLang)
            tvTitle?.text = transTitle

            val transSubtitle = TranslationHelper.translateText(ctx, "Add a new crop cycle for Kisan Diary", activeLang)
            tvSubtitle?.text = transSubtitle

            val transCropHint = TranslationHelper.translateText(ctx, "Select or type crop", activeLang)
            val transSeasonHint = TranslationHelper.translateText(ctx, "Select or type season", activeLang)
            val transAreaHint = TranslationHelper.translateText(ctx, "Land Area", activeLang)
            val transUnitHint = TranslationHelper.translateText(ctx, "Unit", activeLang)

            tilCrop?.hint = transCropHint
            tilSeason?.hint = transSeasonHint
            tilArea?.hint = transAreaHint
            tilUnit?.hint = transUnitHint

            btnCancel.text = TranslationHelper.translateText(ctx, "Cancel", activeLang)
            btnSave.text = TranslationHelper.translateText(ctx, "Start Season", activeLang)

            val transUnits = FertilizerUtils.units.map { TranslationHelper.translateText(ctx, it, activeLang) }
            val translatedUnitAdapter = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transUnits)
            actvUnit.setAdapter(translatedUnitAdapter)
            if (transUnits.isNotEmpty()) {
                actvUnit.setText(transUnits[0], false)
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val safeCtx = context ?: return@setOnClickListener
            val rawCropText = actvCrop.text.toString().trim()
            val crop = if (rawCropText.contains("Type Custom") || rawCropText.contains("अन्य फसल") || rawCropText.startsWith("---")) "गेहूँ" else rawCropText.ifBlank { "गेहूँ" }
            val season = actvSeason.text.toString().trim().ifBlank { "Kharif $currYear" }
            val areaVal = etArea.text.toString().toDoubleOrNull() ?: 1.0

            val rawUnitText = actvUnit.text.toString().trim()
            val unitIdx = FertilizerUtils.units.indexOfFirst {
                it.equals(rawUnitText, ignoreCase = true) || rawUnitText.contains(it, ignoreCase = true)
            }.coerceAtLeast(0)

            val areaAcres = when (unitIdx) {
                1 -> areaVal / 2.5
                2 -> areaVal * 2.47
                else -> areaVal
            }

            val dateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
            val todayStr = dateFormat.format(Date())

            val newCycle = CropCycle(
                id = "cycle_${System.currentTimeMillis()}",
                cropName = crop,
                seasonName = season,
                areaAcres = Math.round(areaAcres * 10.0) / 10.0,
                startDateStr = todayStr,
                isCompleted = false,
                timestamp = System.currentTimeMillis()
            )

            cropCycles.add(0, newCycle)
            KhataHelper.saveLocalCropCycles(safeCtx, cropCycles)
            selectedCropCycleIds.clear()
            selectedCropCycleIds.add(newCycle.id)

            khataRepo.addCropCycle(newCycle) { success ->
                val cbCtx = context ?: return@addCropCycle
                if (success && isAdded) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val transMsg = TranslationHelper.translateText(cbCtx, "Crop Season Started!", activeLang)
                        Toast.makeText(cbCtx, transMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            updateCropSelectorText()
            updateKhataUi()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleCropCycleStatus(cycle: CropCycle) {
        val ctx = context ?: return
        cycle.isCompleted = !cycle.isCompleted
        KhataHelper.saveLocalCropCycles(ctx, cropCycles)
        updateCropSelectorText()
        updateKhataUi()

        khataRepo.updateCropCycle(cycle) { _ -> }
        val msg = if (cycle.isCompleted) "Crop Season Harvested & Completed!" else "Crop Season Re-activated!"
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun confirmAndDeleteCropCycle(cycle: CropCycle) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Crop Season")
            .setMessage("Are you sure you want to delete '${cycle.cropName} (${cycle.seasonName})'?\n\nWarning: This will permanently remove this crop season and all associated Khata expense/income entries.")
            .setPositiveButton("Delete Season") { _, _ ->
                val safeCtx = context ?: return@setPositiveButton
                cropCycles.remove(cycle)
                KhataHelper.saveLocalCropCycles(safeCtx, cropCycles)
                khataRepo.deleteCropCycle(cycle.id) { _ -> }

                val itemsToRemove = khataItems.filter { it.cropCycleId == cycle.id || (it.cropCycleId.isBlank() && it.cropName.equals(cycle.cropName, ignoreCase = true)) }
                for (item in itemsToRemove) {
                    khataItems.remove(item)
                    khataRepo.deleteKhataItem(item.id) { _ -> }
                }
                KhataHelper.saveLocalKhataItems(safeCtx, khataItems)

                selectedCropCycleIds.remove(cycle.id)

                updateCropSelectorText()
                updateKhataUi()
                if (isAdded) {
                    Toast.makeText(safeCtx, "Crop season and entries deleted!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddKhataDialog(existingItem: KhataItem? = null) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_khata_entry, null)
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

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        val getTranslatedSeason: (String) -> String = { s ->
            val seasonHindi = s.replace("Kharif", "खरीफ", ignoreCase = true)
                .replace("Rabi", "रबी", ignoreCase = true)
                .replace("Zaid", "जायद", ignoreCase = true)
            if (activeLang == "hi") {
                seasonHindi
            } else if (activeLang == "en") {
                s
            } else {
                TranslationHelper.getDirectTranslation(seasonHindi, activeLang)
                    ?: TranslationHelper.getDirectTranslation(s, activeLang)
                    ?: seasonHindi
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
                TranslationHelper.getTranslatedCategoryName(cat, activeLang, ctx)
            }
        }

        var isIncome = isEdit && existingItem.isIncome
        if (isIncome) {
            toggleType.check(R.id.btnTypeIncome)
        } else {
            toggleType.check(R.id.btnTypeExpense)
        }

        val updateCategoryDropdown = { income: Boolean ->
            val transCats = getTranslatedCategories(income)
            actvCategory.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transCats))
            if (transCats.isNotEmpty()) {
                val currentText = actvCategory.text.toString()
                if (!transCats.contains(currentText)) {
                    actvCategory.setText(transCats[0], false)
                }
            }
        }

        val currentTransStages = stages.map { stage ->
            TranslationHelper.getTranslatedStageName(stage, activeLang, ctx)
        }

        val baseCycles = cropCycles.map { "${it.cropName} (${it.seasonName})" }
        val rawMasterCrops = TranslationHelper.DEFAULT_HINDI_CROPS_LIST.filterNot { it.contains("अन्य फसल") }
        val allAvailableCrops = (baseCycles + rawMasterCrops).distinct()
        val activeCropNames = allAvailableCrops.map { cropItem ->
            if (cropItem.startsWith("---")) {
                if (activeLang == "hi") cropItem else TranslationHelper.getTranslatedCropName(cropItem, activeLang, ctx)
            } else {
                TranslationHelper.getTranslatedCropName(cropItem, activeLang, ctx)
            }
        }
        actvCrop.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, activeCropNames))

        val currentSelectedCycle = cropCycles.firstOrNull { selectedCropCycleIds.contains(it.id) } ?: cropCycles.firstOrNull()
        val defaultCropText = if (isEdit && !existingItem.cropName.isNullOrBlank()) {
            val matchingCycleObj = cropCycles.firstOrNull { it.cropName.equals(existingItem.cropName, ignoreCase = true) }
            val baseText = if (matchingCycleObj != null) "${matchingCycleObj.cropName} (${matchingCycleObj.seasonName})" else existingItem.cropName
            TranslationHelper.getTranslatedCropName(baseText, activeLang, ctx)
        } else if (currentSelectedCycle != null) {
            TranslationHelper.getTranslatedCropName("${currentSelectedCycle.cropName} (${currentSelectedCycle.seasonName})", activeLang, ctx)
        } else if (activeCropNames.isNotEmpty()) {
            activeCropNames.firstOrNull { !it.startsWith("---") } ?: activeCropNames[0]
        } else {
            TranslationHelper.getTranslatedCropName("गेहूँ (Kharif)", activeLang, ctx)
        }
        actvCrop.setText(defaultCropText, false)

        val initialCats = if (isIncome) incomeCategories else expenseCategories
        val transInitialCats = getTranslatedCategories(isIncome)
        val defaultCat = if (isEdit && existingItem != null) existingItem.category else initialCats[0]
        val catInitIndex = initialCats.indexOf(defaultCat).let { if (it != -1) it else 0 }

        val defaultStage = if (isEdit) existingItem.stage else stages[0]
        val stageInitIndex = stages.indexOf(defaultStage).let { if (it != -1) it else 0 }

        actvCategory.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transInitialCats))
        if (catInitIndex in transInitialCats.indices) {
            actvCategory.setText(transInitialCats[catInitIndex], false)
        } else if (transInitialCats.isNotEmpty()) {
            actvCategory.setText(transInitialCats[0], false)
        }

        actvStage.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, currentTransStages))
        actvStage.setText(currentTransStages[stageInitIndex], false)

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
                val transTitleAsync = TranslationHelper.translateText(ctx, if (isEdit) "Edit Entry" else "Add New Entry", activeLang)
                tvTitle?.text = "📖 $transTitleAsync"

                val transExpenseAsync = TranslationHelper.translateText(ctx, "Expense", activeLang)
                val transIncomeAsync = TranslationHelper.translateText(ctx, "Income", activeLang)
                btnExpense?.text = "💸 $transExpenseAsync (-)"
                btnIncome?.text = "💰 $transIncomeAsync (+)"

                tilAmount?.hint = TranslationHelper.translateText(ctx, "Amount", activeLang)
                tilCrop?.hint = TranslationHelper.translateText(ctx, "Select Crop Season", activeLang)
                tilCategory?.hint = TranslationHelper.translateText(ctx, "Category", activeLang)
                tilStage?.hint = TranslationHelper.translateText(ctx, "Crop Stage", activeLang)
                tilNote?.hint = TranslationHelper.translateText(ctx, "Note / Details", activeLang)

                btnCancel.text = TranslationHelper.translateText(ctx, "Cancel", activeLang)
                btnSave.text = TranslationHelper.translateText(ctx, if (isEdit) "Update Entry" else "Save Entry", activeLang)
            }
        }

        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isIncome = (checkedId == R.id.btnTypeIncome)
                updateCategoryDropdown(isIncome)
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val safeCtx = context ?: return@setOnClickListener
            val amountStr = etAmount.text.toString().trim()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (amount <= 0.0) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val transErr = TranslationHelper.translateText(safeCtx, "Please enter valid amount", activeLang)
                    Toast.makeText(safeCtx, transErr, Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
            val todayStr = dateFormat.format(Date())

            val rawCropSelection = actvCrop.text.toString()
            val canonicalCropSelection = getCanonicalSeason(rawCropSelection)
            val matchingCycle = KhataHelper.getOrCreateCropCycle(safeCtx, khataRepo, cropCycles, canonicalCropSelection)

            val rawCategoryText = actvCategory.text.toString()
            val activeCategories = if (isIncome) incomeCategories else expenseCategories
            val activeTransCategories = getTranslatedCategories(isIncome)
            val catIndex = activeTransCategories.indexOfFirst { it.equals(rawCategoryText, ignoreCase = true) || rawCategoryText.contains(it, ignoreCase = true) }
            val savedCategory = if (catIndex in activeCategories.indices) activeCategories[catIndex] else (activeCategories.firstOrNull { it.equals(rawCategoryText, ignoreCase = true) } ?: activeCategories[0])

            val rawStageText = actvStage.text.toString()
            val stageIndex = currentTransStages.indexOfFirst { it.equals(rawStageText, ignoreCase = true) || rawStageText.contains(it, ignoreCase = true) }
            val savedStage = if (stageIndex in stages.indices) stages[stageIndex] else (stages.firstOrNull { it.equals(rawStageText, ignoreCase = true) } ?: stages[0])

            selectedCropCycleIds.clear()
            selectedCropCycleIds.add(matchingCycle.id)

            if (isEdit && existingItem != null) {
                existingItem.cropCycleId = matchingCycle.id
                existingItem.cropName = matchingCycle.cropName
                existingItem.category = savedCategory
                existingItem.stage = savedStage
                existingItem.amount = amount
                existingItem.isIncome = isIncome
                existingItem.note = etNote.text.toString().trim()

                val index = khataItems.indexOfFirst { it.id == existingItem.id }
                if (index != -1) {
                    khataItems[index] = existingItem
                }
                KhataHelper.saveLocalKhataItems(safeCtx, khataItems)
                updateCropSelectorText()
                updateKhataUi()

                khataRepo.addKhataItem(existingItem) { success ->
                    val cbCtx = context ?: return@addKhataItem
                    if (success && isAdded) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            val transMsg = TranslationHelper.translateText(cbCtx, "Updated Kisan Diary Entry!", activeLang)
                            Toast.makeText(cbCtx, transMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                val newItem = KhataItem(
                    id = "item_${System.currentTimeMillis()}",
                    userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    cropCycleId = matchingCycle.id,
                    cropName = matchingCycle.cropName,
                    category = savedCategory,
                    stage = savedStage,
                    amount = amount,
                    isIncome = isIncome,
                    dateStr = todayStr,
                    timestamp = System.currentTimeMillis(),
                    note = etNote.text.toString().trim()
                )

                khataItems.add(0, newItem)
                KhataHelper.saveLocalKhataItems(safeCtx, khataItems)
                updateCropSelectorText()
                updateKhataUi()

                khataRepo.addKhataItem(newItem) { success ->
                    val cbCtx = context ?: return@addKhataItem
                    if (success && isAdded) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            val transMsg = TranslationHelper.translateText(cbCtx, "Saved to Kisan Diary Cloud!", activeLang)
                            Toast.makeText(cbCtx, transMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            dialog.dismiss()
            (activity as? MainActivity)?.tryShowInterstitialAd { }
        }

        dialog.show()
    }

    private fun deleteKhataEntry(item: KhataItem) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this Kisan Diary entry?")
            .setPositiveButton("Delete") { _, _ ->
                val safeCtx = context ?: return@setPositiveButton
                khataItems.remove(item)
                KhataHelper.saveLocalKhataItems(safeCtx, khataItems)
                updateKhataUi()
                khataRepo.deleteKhataItem(item.id) { _ -> }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var lastCalculatedItem: KhataItem? = null

    private fun registerViewForKeyboardScroll(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                registerViewForKeyboardScroll(view.getChildAt(i))
            }
        } else if (view is EditText) {
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.post {
                        val scrollView = binding.scrollViewHome
                        val rect = Rect()
                        v.getDrawingRect(rect)
                        scrollView.offsetDescendantRectToMyCoords(v, rect)
                        scrollView.smoothScrollTo(0, maxOf(0, rect.top - 200))
                    }
                }
            }
            view.setOnClickListener { v ->
                v.post {
                    val scrollView = binding.scrollViewHome
                    val rect = Rect()
                    v.getDrawingRect(rect)
                    scrollView.offsetDescendantRectToMyCoords(v, rect)
                    scrollView.smoothScrollTo(0, maxOf(0, rect.top - 200))
                }
            }
        }
    }

    private fun setupCalculatorSection() {
        val ctx = context ?: return
        if (_binding == null) return

        // Auto-scroll when any calculator input gets focus or is clicked
        registerViewForKeyboardScroll(binding.containerCalculator)

        // Populate Common Dropdowns in Casual Hindi
        val adapterCrop = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, FarmingCalculatorUtils.crops)
        binding.actvCalcCrop.setAdapter(adapterCrop)
        binding.actvCalcCrop.setText(FarmingCalculatorUtils.crops[0], false)

        val adapterUnit = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, FarmingCalculatorUtils.units)
        binding.actvCalcUnit.setAdapter(adapterUnit)
        binding.actvCalcUnit.setText(FarmingCalculatorUtils.units[0], false)

        // Sub-dropdown options in Casual Hindi
        val ops = listOf("जुताई / हकाई", "कल्टीवेटर", "रोटावेटर", "पाटा / समतलीकरण", "हेरो", "अन्य काम")
        binding.actvFieldPrepOperation.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, ops))
        binding.actvFieldPrepOperation.setText(ops[0], false)

        val machines = listOf("ट्रैक्टर", "रोटावेटर", "कल्टीवेटर", "लेवलर", "हार्वेस्टर")
        binding.actvFieldPrepMachine.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, machines))
        binding.actvFieldPrepMachine.setText(machines[0], false)

        val pricingTypes = listOf("₹/एकड़", "₹/घंटा")
        binding.actvFieldPrepPricingType.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, pricingTypes))
        binding.actvFieldPrepPricingType.setText(pricingTypes[0], false)

        val methods = listOf("सीधी बुआई / कतार बुआई", "छिटकवा बुआई", "चौपाई / डिबलिंग", "रोपाई (ट्रांसप्लांटिंग)")
        binding.actvSowingMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, methods))
        binding.actvSowingMethod.setText(methods[0], false)

        val irrMethods = listOf("फ्लड / नाली सिंचाई", "ड्रिप सिंचाई (टपकन)", "स्प्रिंकलर (फव्वारा)")
        binding.actvIrrigationMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, irrMethods))
        binding.actvIrrigationMethod.setText(irrMethods[0], false)

        val pumps = listOf("बिजली का मोटर", "डीजल पंप", "नहर का पानी")
        binding.actvIrrigationPump.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, pumps))
        binding.actvIrrigationPump.setText(pumps[0], false)

        val sprayProblems = listOf("कीट / इल्ली का प्रकोप", "फफूंद / बीमारी", "खरपतवार / घास")
        binding.actvSprayProblem.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, sprayProblems))
        binding.actvSprayProblem.setText(sprayProblems[0], false)

        val workTypes = listOf("खेत की तैयारी", "बुआई / रोपाई", "निंदाई / गुड़ाई", "खाद देना", "दवाई छिड़काव", "सिंचाई", "फसल कटाई", "अन्य काम")
        binding.actvLabourWorkType.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, workTypes))
        binding.actvLabourWorkType.setText(workTypes[0], false)

        val harvestMethods = listOf("कंबाइन हार्वेस्टर / मशीन", "मजदूरों द्वारा कटाई")
        binding.actvHarvestMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, harvestMethods))
        binding.actvHarvestMethod.setText(harvestMethods[0], false)

        // Setup Stage & Calculator Selector lists in Casual Hindi and English
        val productionCalcs = listOf(
            "1. खेत तैयारी कैलकुलेटर",
            "2. बीज व बुआई कैलकुलेटर",
            "3. खाद व उर्वरक कैलकुलेटर",
            "4. सिंचाई कैलकुलेटर",
            "5. स्प्रे व कीटनाशक कैलकुलेटर",
            "6. मजदूरी कैलकुलेटर",
            "7. फसल कटाई कैलकुलेटर"
        )
        val postHarvestCalcs = listOf(
            "8. भाड़ा व भंडारण कैलकुलेटर",
            "9. मंडी बिक्री कैलकुलेटर"
        )
        val financialCalcs = listOf(
            "10. कुल मुनाफा कैलकुलेटर"
        )

        val productionCalcsEn = listOf(
            "1. Field Prep Calculator",
            "2. Seed & Sowing Calculator",
            "3. Fertilizer Calculator",
            "4. Irrigation Calculator",
            "5. Spray & Pesticide Calculator",
            "6. Labor & Wages Calculator",
            "7. Harvesting Calculator"
        )
        val postHarvestCalcsEn = listOf(
            "8. Transport & Storage Calculator",
            "9. Mandi Sale Calculator"
        )
        val financialCalcsEn = listOf(
            "10. Crop Profit & ROI Calculator"
        )

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        fun updateCalculatorSelector(stage: Int, pCalcs: List<String>, phCalcs: List<String>, fCalcs: List<String>) {
            val calcList = when (stage) {
                1 -> phCalcs
                2 -> fCalcs
                else -> pCalcs
            }
            val adapterCalc = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, calcList)
            binding.actvCalculatorSelector.setAdapter(adapterCalc)
            binding.actvCalculatorSelector.setText(calcList[0], false)
            showFormForCalculator(calcList[0])
        }

        if (activeLang != "hi") {
            lifecycleScope.launch(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@launch
                val transHeader = TranslationHelper.translateText(ctx, "किसान केंद्र — कृषि कैलकुलेटर", activeLang)
                val transStageHeader = TranslationHelper.translateText(ctx, "फसल का चरण चुनें:", activeLang)
                val transProd = TranslationHelper.translateText(ctx, "1. फसल बुआई व देखरेख", activeLang)
                val transPost = TranslationHelper.translateText(ctx, "2. कटाई और भंडारण", activeLang)
                val transFin = TranslationHelper.translateText(ctx, "3. कुल कमाई और मुनाफा", activeLang)
                val transSelectCalc = TranslationHelper.translateText(ctx, "कैलकुलेटर चुनें", activeLang)
                val transCalcBtn = TranslationHelper.translateText(ctx, "हिसाब लगाएं", activeLang)
                val transResultTitle = TranslationHelper.translateText(ctx, "हिसाब का परिणाम", activeLang)
                val transSaveBtn = TranslationHelper.translateText(ctx, "किसान डायरी में सेव करें", activeLang)

                val transCrops = FarmingCalculatorUtils.crops.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transUnits = FarmingCalculatorUtils.units.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transOps = ops.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transMachines = machines.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transPricingTypes = pricingTypes.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transMethods = methods.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transIrrMethods = irrMethods.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transPumps = pumps.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transSprayProblems = sprayProblems.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transWorkTypes = workTypes.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transHarvestMethods = harvestMethods.map { TranslationHelper.translateText(ctx, it, activeLang) }

                val transProdCalcs = if (activeLang == "en") productionCalcsEn else productionCalcs.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transPostCalcs = if (activeLang == "en") postHarvestCalcsEn else postHarvestCalcs.map { TranslationHelper.translateText(ctx, it, activeLang) }
                val transFinCalcs = if (activeLang == "en") financialCalcsEn else financialCalcs.map { TranslationHelper.translateText(ctx, it, activeLang) }

                if (_binding != null && isAdded) {
                    binding.tvHeaderCalc.text = transHeader
                    binding.tvCalcStageHeader.text = transStageHeader
                    binding.chipStageProduction.text = transProd
                    binding.chipStagePostHarvest.text = transPost
                    binding.chipStageFinancial.text = transFin
                    binding.tilCalculatorSelector.hint = transSelectCalc
                    binding.btnCalculateFertilizer.text = transCalcBtn
                    binding.tvCalcResultTitle.text = transResultTitle
                    binding.btnSaveCalcToKisanDiary.text = transSaveBtn

                    // Translate Calculator Entry Field Titles and Hints
                    binding.tilCalcCrop.hint = TranslationHelper.translateText(ctx, "फसल चुनें", activeLang)
                    binding.tilCalcArea.hint = TranslationHelper.translateText(ctx, "ज़मीन का क्षेत्रफल", activeLang)
                    binding.tilCalcUnit.hint = TranslationHelper.translateText(ctx, "इकाई", activeLang)

                    binding.tilFieldPrepOperation.hint = TranslationHelper.translateText(ctx, "कार्य / जुताई प्रकार", activeLang)
                    binding.tilFieldPrepMachine.hint = TranslationHelper.translateText(ctx, "मशीन / साधन", activeLang)
                    binding.tilFieldPrepRate.hint = TranslationHelper.translateText(ctx, "किराया / रेट (₹)", activeLang)
                    binding.tilFieldPrepPricingType.hint = if (activeLang == "en") "Hour / Acre" else TranslationHelper.translateText(ctx, "घंटा / एकड़", activeLang)

                    binding.tilSowingVariety.hint = TranslationHelper.translateText(ctx, "बीज की किस्म / वैरायटी (ऐच्छिक)", activeLang)
                    binding.tilSowingMethod.hint = TranslationHelper.translateText(ctx, "बुआई का तरीका", activeLang)
                    binding.tilSowingPrice.hint = TranslationHelper.translateText(ctx, "बीज दर (₹/किलो)", activeLang)

                    binding.tvFertilizerHeader.text = TranslationHelper.translateText(ctx, "खाद के बाजार भाव (₹/बोरी या ₹/किलो):", activeLang)
                    binding.tilFertUreaPrice.hint = TranslationHelper.translateText(ctx, "यूरिया (₹/बोरी)", activeLang)
                    binding.tilFertDapPrice.hint = TranslationHelper.translateText(ctx, "डीएपी (₹/बोरी)", activeLang)
                    binding.tilFertMopPrice.hint = TranslationHelper.translateText(ctx, "एमओपी (₹/बोरी)", activeLang)
                    binding.tilFertZincPrice.hint = TranslationHelper.translateText(ctx, "जिंक (₹/किलो)", activeLang)

                    binding.tilIrrigationMethod.hint = TranslationHelper.translateText(ctx, "सिंचाई का तरीका", activeLang)
                    binding.tilIrrigationCount.hint = TranslationHelper.translateText(ctx, "कुल सिंचाई की संख्या", activeLang)
                    binding.tilIrrigationPump.hint = TranslationHelper.translateText(ctx, "पानी का साधन / पंप", activeLang)
                    binding.tilIrrigationHours.hint = TranslationHelper.translateText(ctx, "पंप HP / चलने के घंटे", activeLang)

                    binding.tilSprayProblem.hint = TranslationHelper.translateText(ctx, "समस्या का प्रकार", activeLang)
                    binding.tilSprayProduct.hint = TranslationHelper.translateText(ctx, "दवाई का नाम", activeLang)
                    binding.tilSprayTankCap.hint = TranslationHelper.translateText(ctx, "टंकी की क्षमता (लीटर)", activeLang)
                    binding.tilSprayDosePerL.hint = TranslationHelper.translateText(ctx, "दवाई की मात्रा (मिली/लीटर)", activeLang)

                    binding.tilLabourWorkType.hint = TranslationHelper.translateText(ctx, "कार्य का प्रकार", activeLang)
                    binding.tilLabourWorkers.hint = TranslationHelper.translateText(ctx, "मजदूरों की संख्या", activeLang)
                    binding.tilLabourDuration.hint = TranslationHelper.translateText(ctx, "दिन / घंटे", activeLang)
                    binding.tilLabourWage.hint = TranslationHelper.translateText(ctx, "मजदूरी दर (₹/दिन)", activeLang)

                    binding.tilHarvestMethod.hint = TranslationHelper.translateText(ctx, "कटाई का तरीका", activeLang)
                    binding.tilHarvestRate.hint = TranslationHelper.translateText(ctx, "मशीन या मजदूरी रेट (₹)", activeLang)
                    binding.tilHarvestCount.hint = TranslationHelper.translateText(ctx, "दिन / मजदूर संख्या", activeLang)

                    binding.tilTransportQty.hint = TranslationHelper.translateText(ctx, "कुल उपज (क्विंटल)", activeLang)
                    binding.tilTransportRate.hint = TranslationHelper.translateText(ctx, "गाड़ी भाड़ा (₹/क्विंटल)", activeLang)
                    binding.tilTransportLoading.hint = TranslationHelper.translateText(ctx, "हमाली व लोड-अनलोड (₹)", activeLang)
                    binding.tilTransportStorage.hint = TranslationHelper.translateText(ctx, "गोदाम / भंडारण खर्च (₹)", activeLang)

                    binding.tilSaleQty.hint = TranslationHelper.translateText(ctx, "बेची गई उपज (क्विंटल)", activeLang)
                    binding.tilSalePrice.hint = TranslationHelper.translateText(ctx, "मंडी भाव (₹/क्विंटल)", activeLang)
                    binding.tilSaleTransport.hint = TranslationHelper.translateText(ctx, "गाड़ी भाड़ा (₹)", activeLang)
                    binding.tilSaleDeductions.hint = TranslationHelper.translateText(ctx, "कमीशन व मंडी टैक्स (₹)", activeLang)

                    binding.tilProfitYieldPerAcre.hint = TranslationHelper.translateText(ctx, "अनुमानित पैदावार (क्विंटल/एकड़)", activeLang)
                    binding.tilProfitPricePerQ.hint = TranslationHelper.translateText(ctx, "अनुमानित मंडी भाव (₹/क्विंटल)", activeLang)
                    binding.tvProfitCostHeader.text = TranslationHelper.translateText(ctx, "लागत और खर्च का विवरण (₹):", activeLang)
                    binding.tilProfitSeedCost.hint = TranslationHelper.translateText(ctx, "बीज का खर्च", activeLang)
                    binding.tilProfitMachineryCost.hint = TranslationHelper.translateText(ctx, "जुताई व मशीन खर्च", activeLang)
                    binding.tilProfitFertilizerCost.hint = TranslationHelper.translateText(ctx, "खाद व उर्वरक खर्च", activeLang)
                    binding.tilProfitIrrigationCost.hint = TranslationHelper.translateText(ctx, "सिंचाई का खर्च", activeLang)
                    binding.tilProfitLabourCost.hint = TranslationHelper.translateText(ctx, "मजदूरी का खर्च", activeLang)
                    binding.tilProfitHarvestCost.hint = TranslationHelper.translateText(ctx, "कटाई व ढोआई खर्च (₹)", activeLang)

                    binding.actvCalcCrop.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transCrops))
                    binding.actvCalcCrop.setText(transCrops[0], false)

                    binding.actvCalcUnit.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transUnits))
                    binding.actvCalcUnit.setText(transUnits[0], false)

                    binding.actvFieldPrepOperation.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transOps))
                    binding.actvFieldPrepOperation.setText(transOps[0], false)

                    binding.actvFieldPrepMachine.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transMachines))
                    binding.actvFieldPrepMachine.setText(transMachines[0], false)

                    binding.actvFieldPrepPricingType.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transPricingTypes))
                    binding.actvFieldPrepPricingType.setText(transPricingTypes[0], false)

                    binding.actvSowingMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transMethods))
                    binding.actvSowingMethod.setText(transMethods[0], false)

                    binding.actvIrrigationMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transIrrMethods))
                    binding.actvIrrigationMethod.setText(transIrrMethods[0], false)

                    binding.actvIrrigationPump.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transPumps))
                    binding.actvIrrigationPump.setText(transPumps[0], false)

                    binding.actvSprayProblem.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transSprayProblems))
                    binding.actvSprayProblem.setText(transSprayProblems[0], false)

                    binding.actvLabourWorkType.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transWorkTypes))
                    binding.actvLabourWorkType.setText(transWorkTypes[0], false)

                    binding.actvHarvestMethod.setAdapter(ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, transHarvestMethods))
                    binding.actvHarvestMethod.setText(transHarvestMethods[0], false)

                    val activeCheckedId = binding.chipGroupCalcStages.checkedChipId
                    val currentList = when (activeCheckedId) {
                        R.id.chipStagePostHarvest -> transPostCalcs
                        R.id.chipStageFinancial -> transFinCalcs
                        else -> transProdCalcs
                    }
                    val adapterCalc = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, currentList)
                    binding.actvCalculatorSelector.setAdapter(adapterCalc)
                    binding.actvCalculatorSelector.setText(currentList[0], false)
                    showFormForCalculator(currentList[0])

                    binding.chipGroupCalcStages.setOnCheckedStateChangeListener { _, checkedIds ->
                        val checkedId = checkedIds.firstOrNull() ?: R.id.chipStageProduction
                        val list = when (checkedId) {
                            R.id.chipStagePostHarvest -> transPostCalcs
                            R.id.chipStageFinancial -> transFinCalcs
                            else -> transProdCalcs
                        }
                        val adapter = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, list)
                        binding.actvCalculatorSelector.setAdapter(adapter)
                        binding.actvCalculatorSelector.setText(list[0], false)
                        showFormForCalculator(list[0])
                    }
                }
            }
        } else {
            binding.chipGroupCalcStages.setOnCheckedStateChangeListener { _, checkedIds ->
                val checkedId = checkedIds.firstOrNull() ?: R.id.chipStageProduction
                when (checkedId) {
                    R.id.chipStagePostHarvest -> updateCalculatorSelector(1, productionCalcs, postHarvestCalcs, financialCalcs)
                    R.id.chipStageFinancial -> updateCalculatorSelector(2, productionCalcs, postHarvestCalcs, financialCalcs)
                    else -> updateCalculatorSelector(0, productionCalcs, postHarvestCalcs, financialCalcs)
                }
            }
            updateCalculatorSelector(0, productionCalcs, postHarvestCalcs, financialCalcs)
        }

        binding.actvCalculatorSelector.setOnItemClickListener { parent, _, position, _ ->
            val selectedCalc = parent.getItemAtPosition(position).toString()
            showFormForCalculator(selectedCalc)
        }

        // Calculate Button Action
        binding.btnCalculateFertilizer.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            (activity as? MainActivity)?.tryShowInterstitialAd {
                performSelectedCalculation()
            }
        }

        // Save to Kisan Diary Action
        binding.btnSaveCalcToKisanDiary.setOnClickListener {
            val itemToSave = lastCalculatedItem
            if (itemToSave != null) {
                val safeCtx = context ?: return@setOnClickListener

                val matchingCycle = KhataHelper.getOrCreateCropCycle(safeCtx, khataRepo, cropCycles, itemToSave.cropName)

                itemToSave.cropCycleId = matchingCycle.id
                itemToSave.cropName = matchingCycle.cropName

                val existing = KhataHelper.getLocalKhataItems(safeCtx).toMutableList()
                existing.add(0, itemToSave)
                KhataHelper.saveLocalKhataItems(safeCtx, existing)
                khataItems = existing

                // Refresh Crop Season dropdown and update UI so user immediately sees entry in Kisan Diary
                selectedCropCycleIds.clear()
                selectedCropCycleIds.add(matchingCycle.id)
                updateCropSelectorText()
                updateKhataUi()

                khataRepo.addKhataItem(itemToSave) { _ -> }

                (activity as? MainActivity)?.tryShowInterstitialAd {
                    val safeCtxCb = context ?: return@tryShowInterstitialAd
                    if (_binding != null && isAdded) {
                        Toast.makeText(safeCtxCb, "✅ Saved estimate to Kisan Diary (${matchingCycle.cropName})!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(ctx, "Please perform a calculation first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFormForCalculator(calcTitle: String) {
        if (_binding == null) return

        // Hide all specific forms first
        binding.layoutCalcFieldPrep.visibility = View.GONE
        binding.layoutCalcSowing.visibility = View.GONE
        binding.layoutCalcFertilizer.visibility = View.GONE
        binding.layoutCalcIrrigation.visibility = View.GONE
        binding.layoutCalcSpray.visibility = View.GONE
        binding.layoutCalcLabour.visibility = View.GONE
        binding.layoutCalcHarvesting.visibility = View.GONE
        binding.layoutCalcTransport.visibility = View.GONE
        binding.layoutCalcSale.visibility = View.GONE
        binding.layoutCalcProfit.visibility = View.GONE

        // Common layout visible for almost all
        binding.layoutCalcCommon.visibility = if (calcTitle.contains("8. 📦") || calcTitle.contains("9. 🏪") || calcTitle.contains("8.") || calcTitle.contains("9.")) View.GONE else View.VISIBLE

        when {
            calcTitle.contains("1. 🚜") || calcTitle.contains("1.") -> binding.layoutCalcFieldPrep.visibility = View.VISIBLE
            calcTitle.contains("2. 🌱") || calcTitle.contains("2.") -> binding.layoutCalcSowing.visibility = View.VISIBLE
            calcTitle.contains("3. 🧪") || calcTitle.contains("3.") -> binding.layoutCalcFertilizer.visibility = View.VISIBLE
            calcTitle.contains("4. 💧") || calcTitle.contains("4.") -> binding.layoutCalcIrrigation.visibility = View.VISIBLE
            calcTitle.contains("5. 💊") || calcTitle.contains("5.") -> binding.layoutCalcSpray.visibility = View.VISIBLE
            calcTitle.contains("6. 👨‍🌾") || calcTitle.contains("6.") -> binding.layoutCalcLabour.visibility = View.VISIBLE
            calcTitle.contains("7. 🌾") || calcTitle.contains("7.") -> binding.layoutCalcHarvesting.visibility = View.VISIBLE
            calcTitle.contains("8. 📦") || calcTitle.contains("8.") -> binding.layoutCalcTransport.visibility = View.VISIBLE
            calcTitle.contains("9. 🏪") || calcTitle.contains("9.") -> binding.layoutCalcSale.visibility = View.VISIBLE
            calcTitle.contains("10. 📊") || calcTitle.contains("10.") -> binding.layoutCalcProfit.visibility = View.VISIBLE
            else -> binding.layoutCalcFertilizer.visibility = View.VISIBLE
        }

        binding.cardCalcResult.visibility = View.GONE
    }

    private fun displayCalculatorResults(
        resSeed: String,
        resUrea: String,
        resDap: String,
        resZinc: String,
        resNote: String
    ) {
        val ctx = context ?: return
        if (_binding == null) return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        if (activeLang != "hi") {
            lifecycleScope.launch(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@launch
                val tSeed = TranslationHelper.translateText(ctx, resSeed, activeLang)
                val tUrea = TranslationHelper.translateText(ctx, resUrea, activeLang)
                val tDap = TranslationHelper.translateText(ctx, resDap, activeLang)
                val tZinc = TranslationHelper.translateText(ctx, resZinc, activeLang)
                val tNote = TranslationHelper.translateText(ctx, resNote, activeLang)

                if (_binding != null && isAdded) {
                    binding.tvCalcResultSeed.text = tSeed
                    binding.tvCalcResultUrea.text = tUrea
                    binding.tvCalcResultDap.text = tDap
                    binding.tvCalcResultZinc.text = tZinc
                    binding.tvCalcResultNote.text = tNote
                    binding.cardCalcResult.visibility = View.VISIBLE
                }
            }
        } else {
            binding.tvCalcResultSeed.text = resSeed
            binding.tvCalcResultUrea.text = resUrea
            binding.tvCalcResultDap.text = resDap
            binding.tvCalcResultZinc.text = resZinc
            binding.tvCalcResultNote.text = resNote
            binding.cardCalcResult.visibility = View.VISIBLE
        }
    }

    private fun performSelectedCalculation() {
        val ctx = context ?: return
        if (_binding == null) return

        val calcTitle = binding.actvCalculatorSelector.text.toString()
        val area = binding.etCalcArea.text.toString().toDoubleOrNull() ?: 5.0
        val crop = binding.actvCalcCrop.text.toString().ifBlank { "गेहूँ" }
        val cropIndex = FarmingCalculatorUtils.crops.indexOfFirst {
            it.equals(crop, ignoreCase = true) || crop.contains(it, ignoreCase = true)
        }.coerceAtLeast(0)

        val rawUnitText = binding.actvCalcUnit.text.toString().trim()
        val unitIdx = FarmingCalculatorUtils.units.indexOfFirst {
            it.equals(rawUnitText, ignoreCase = true) || rawUnitText.contains(it, ignoreCase = true)
        }.coerceAtLeast(0)

        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        when {
            // 1. Farm Machinery & Field Prep
            calcTitle.contains("1. 🚜") || calcTitle.contains("1.") -> {
                val operation = binding.actvFieldPrepOperation.text.toString().ifBlank { "जुताई / हकाई" }
                val machine = binding.actvFieldPrepMachine.text.toString().ifBlank { "ट्रैक्टर" }
                val rate = binding.etFieldPrepRate.text.toString().toDoubleOrNull() ?: 1200.0
                val pricingType = binding.actvFieldPrepPricingType.text.toString()
                val isHourly = pricingType.contains("घंटा", ignoreCase = true) || pricingType.contains("Hour", ignoreCase = true)

                val res = FarmingCalculatorUtils.calculateFieldPrep(
                    cropName = crop,
                    rawArea = area,
                    unitIndex = unitIdx,
                    operation = operation,
                    machine = machine,
                    rate = rate,
                    isHourly = isHourly,
                    hoursPerAcre = 1.5,
                    fuelSeparate = false,
                    fuelConsLPerAcre = 0.0,
                    fuelPrice = 0.0
                )

                displayCalculatorResults(
                    "खेत तैयारी कार्य: ${res.operationName} (${res.machineName})",
                    "कुल ज़मीन: ${String.format(Locale.ENGLISH, "%.1f", res.areaInAcres)} एकड़",
                    "दर / किराया: ${res.rateText}",
                    "कुल मशीन खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)} (₹${String.format(Locale.ENGLISH, "%,.0f", res.costPerAcre)}/एकड़)",
                    "$crop की फसल के लिए खेत तैयारी का अनुमानित खर्च।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Machinery & Fuel",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Field Prep: ${res.operationName} (${res.machineName}) for $crop",
                    stage = "Land Preparation"
                )
            }

            // 2. Sowing & Seed Calculator
            calcTitle.contains("2. 🌱") || calcTitle.contains("2.") -> {
                val variety = binding.etSowingVariety.text.toString().trim().ifBlank { "उन्नत किस्म" }
                val method = binding.actvSowingMethod.text.toString().ifBlank { "सीधी बुआई / कतार बुआई" }
                val price = binding.etSowingPrice.text.toString().toDoubleOrNull() ?: 35.0

                val res = FarmingCalculatorUtils.calculateSowing(
                    cropIndex = cropIndex,
                    variety = variety,
                    rawArea = area,
                    unitIndex = unitIdx,
                    sowingMethod = method,
                    customSeedRateKg = null,
                    seedPricePerKg = price
                )

                displayCalculatorResults(
                    "बुआई योजना: ${res.cropName} (${res.variety}) — ${res.sowingMethod}",
                    "अनुशंसित बीज दर: ${String.format(Locale.ENGLISH, "%.1f", res.seedRatePerAcre)} किग्रा/एकड़",
                    "कुल आवश्यक बीज: ${String.format(Locale.ENGLISH, "%.1f", res.totalSeedKg)} किग्रा",
                    "अनुमानित बीज खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalSeedCost)} (@ ₹${String.format(Locale.ENGLISH, "%.0f", res.seedPricePerKg)}/किग्रा)",
                    "अच्छी पैदावार और पौधों की सही संख्या के लिए बीज की मात्रा।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Seeds",
                    amount = res.totalSeedCost,
                    dateStr = todayStr,
                    note = "Seed Purchase: ${res.totalSeedKg}kg ${res.cropName} (${res.variety})",
                    stage = "Sowing"
                )
            }

            // 3. Fertilizer & Nutrient Calculator
            calcTitle.contains("3. 🧪") || calcTitle.contains("3.") -> {
                val ureaP = binding.etFertUreaPrice.text.toString().toDoubleOrNull() ?: 266.0
                val dapP = binding.etFertDapPrice.text.toString().toDoubleOrNull() ?: 1350.0
                val mopP = binding.etFertMopPrice.text.toString().toDoubleOrNull() ?: 1700.0
                val zincP = binding.etFertZincPrice.text.toString().toDoubleOrNull() ?: 80.0

                val res = FarmingCalculatorUtils.calculateFertilizer(
                    cropIndex = cropIndex,
                    rawArea = area,
                    unitIndex = unitIdx,
                    ureaPriceBag = ureaP,
                    dapPriceBag = dapP,
                    mopPriceBag = mopP,
                    zincPriceKg = zincP
                )

                displayCalculatorResults(
                    "यूरिया: ${String.format(Locale.ENGLISH, "%.1f", res.ureaKg)} किग्रा (${String.format(Locale.ENGLISH, "%.1f", res.ureaBags)} बोरी)",
                    "डीएपी: ${String.format(Locale.ENGLISH, "%.1f", res.dapKg)} किग्रा (${String.format(Locale.ENGLISH, "%.1f", res.dapBags)} बोरी)",
                    "एमओपी: ${String.format(Locale.ENGLISH, "%.1f", res.mopKg)} किग्रा (${String.format(Locale.ENGLISH, "%.1f", res.mopBags)} बोरी) | जिंक: ${String.format(Locale.ENGLISH, "%.1f", res.zincKg)} किग्रा",
                    "अनुमानित खाद खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)}",
                    "खाद देने का सही समय व शेड्यूल:\n${res.applicationSchedule}"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Fertiliser",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Fertilizer Requirement for $crop (${String.format(Locale.ENGLISH, "%.1f", res.areaInAcres)} Acres)",
                    stage = "Sowing"
                )
            }

            // 4. Irrigation & Water Calculator
            calcTitle.contains("4. 💧") || calcTitle.contains("4.") -> {
                val method = binding.actvIrrigationMethod.text.toString().ifBlank { "फ्लड / नाली सिंचाई" }
                val count = binding.etIrrigationCount.text.toString().toIntOrNull() ?: 5
                val pump = binding.actvIrrigationPump.text.toString().ifBlank { "बिजली का मोटर" }
                val hours = binding.etIrrigationHours.text.toString().toDoubleOrNull() ?: 4.0

                val res = FarmingCalculatorUtils.calculateIrrigation(
                    cropIndex = cropIndex,
                    rawArea = area,
                    unitIndex = unitIdx,
                    stageName = "Seasonal",
                    method = method,
                    numIrrigations = count,
                    pumpType = pump,
                    hp = 7.5,
                    hoursPerIrrigation = hours,
                    electricityRate = 7.0,
                    fuelConsLHr = 2.5,
                    fuelPrice = 90.0,
                    directCanalCost = 1500.0
                )

                displayCalculatorResults(
                    "सिंचाई का तरीका: ${res.method} (${res.numIrrigations} बार सिंचाई)",
                    "प्रति सिंचाई पानी की मात्रा: ${String.format(Locale.ENGLISH, "%,.0f", res.waterPerIrrigationLiters)} लीटर",
                    "कुल सीजन में पानी की मात्रा: ${String.format(Locale.ENGLISH, "%,.0f", res.totalSeasonalWaterLiters)} लीटर",
                    "अनुमानित सिंचाई खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)} (₹${String.format(Locale.ENGLISH, "%,.0f", res.costPerAcre)}/एकड़)",
                    "फसल में पानी की आवश्यकता और पंप चलने के घंटे के अनुसार अनुमान।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Irrigation",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Irrigation Cost: ${res.method} (${res.numIrrigations} times) for $crop",
                    stage = "Irrigation"
                )
            }

            // 5. Crop Protection & Spray Calculator
            calcTitle.contains("5. 💊") || calcTitle.contains("5.") -> {
                val problem = binding.actvSprayProblem.text.toString().ifBlank { "कीट / इल्ली का प्रकोप" }
                val product = binding.etSprayProduct.text.toString().trim().ifBlank { "अनुशंसित कीटनाशक" }
                val tankCap = binding.etSprayTankCap.text.toString().toDoubleOrNull() ?: 15.0
                val dose = binding.etSprayDosePerL.text.toString().toDoubleOrNull() ?: 1.5

                val res = FarmingCalculatorUtils.calculateSpray(
                    cropIndex = cropIndex,
                    problemCategory = problem,
                    productName = product,
                    dosePerLiterMl = dose,
                    rawArea = area,
                    unitIndex = unitIdx,
                    tankCapacityLiters = tankCap,
                    waterPerAcreLiters = 150.0,
                    productPricePerUnit = 600.0,
                    packageSizeUnits = 500.0
                )

                displayCalculatorResults(
                    "दवाई / उत्पाद: ${res.productName} (${res.problemCategory})",
                    "कुल आवश्यक पानी: ${String.format(Locale.ENGLISH, "%.0f", res.waterRequirementLiters)} लीटर | कुल टंकी: ${res.tankLoads} टंकी (${String.format(Locale.ENGLISH, "%.0f", res.tankCapacityLiters)}L टंकी)",
                    "आवश्यक कुल दवा: ${String.format(Locale.ENGLISH, "%.0f", res.totalProductRequired)} मिली/ग्राम (${String.format(Locale.ENGLISH, "%.1f", res.productPerTank)} मिली/ग्राम प्रति टंकी)",
                    "अनुमानित दवाई व स्प्रे खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)}",
                    "${res.labelGuidance}"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Pesticides",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Chemical Spray: ${res.productName} for $crop",
                    stage = "Spray"
                )
            }

            // 6. Labour & Worker Cost Calculator
            calcTitle.contains("6. 👨‍🌾") || calcTitle.contains("6.") -> {
                val work = binding.actvLabourWorkType.text.toString().ifBlank { "निंदाई / गुड़ाई" }
                val workers = binding.etLabourWorkers.text.toString().toIntOrNull() ?: 8
                val duration = binding.etLabourDuration.text.toString().toDoubleOrNull() ?: 2.0
                val wage = binding.etLabourWage.text.toString().toDoubleOrNull() ?: 400.0

                val res = FarmingCalculatorUtils.calculateLabour(
                    workType = work,
                    rawArea = area,
                    unitIndex = unitIdx,
                    numWorkers = workers,
                    duration = duration,
                    isHourly = false,
                    wageRate = wage
                )

                displayCalculatorResults(
                    "कार्य का प्रकार: ${res.workType}",
                    "कुल मजदूर: ${res.numWorkers} मजदूर (${String.format(Locale.ENGLISH, "%.1f", res.duration)} दिन/घंटे)",
                    "मजदूरी दर: ₹${String.format(Locale.ENGLISH, "%.0f", res.wageRate)} / मजदूर / दिन",
                    "कुल मजदूरी खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalLabourCost)} (₹${String.format(Locale.ENGLISH, "%,.0f", res.costPerAcre)}/एकड़)",
                    "फसल चक्र के दौरान मजदूरी का अनुमानित खर्च।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Labour",
                    amount = res.totalLabourCost,
                    dateStr = todayStr,
                    note = "Labour Cost: ${res.workType} (${res.numWorkers} workers)",
                    stage = "Land Preparation"
                )
            }

            // 7. Harvesting Calculator
            calcTitle.contains("7. 🌾") || calcTitle.contains("7.") -> {
                val method = binding.actvHarvestMethod.text.toString().ifBlank { "कंबाइन हार्वेस्टर / मशीन" }
                val rate = binding.etHarvestRate.text.toString().toDoubleOrNull() ?: 1800.0
                val count = binding.etHarvestCount.text.toString().toDoubleOrNull() ?: 1.0

                val res = FarmingCalculatorUtils.calculateHarvesting(
                    cropIndex = cropIndex,
                    rawArea = area,
                    unitIndex = unitIdx,
                    method = method,
                    numWorkers = 10,
                    durationDays = count,
                    dailyWage = 400.0,
                    machineRate = rate,
                    isHourlyMachine = false,
                    hoursPerAcre = 1.0
                )

                displayCalculatorResults(
                    "कटाई का तरीका: ${res.method}",
                    "विवरण: ${res.detailsText}",
                    "कुल ज़मीन: ${String.format(Locale.ENGLISH, "%.1f", res.areaInAcres)} एकड़",
                    "कुल कटाई खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)} (₹${String.format(Locale.ENGLISH, "%,.0f", res.costPerAcre)}/एकड़)",
                    "फसल कटाई और गहाई (थ्रेशिंग) का अनुमानित खर्च।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Harvesting",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Harvesting Cost: ${res.cropName} (${res.method})",
                    stage = "Harvesting"
                )
            }

            // 8. Transport & Storage Calculator
            calcTitle.contains("8. 📦") || calcTitle.contains("8.") -> {
                val qty = binding.etTransportQty.text.toString().toDoubleOrNull() ?: 35.0
                val rate = binding.etTransportRate.text.toString().toDoubleOrNull() ?: 85.0
                val loading = binding.etTransportLoading.text.toString().toDoubleOrNull() ?: 1300.0
                val storage = binding.etTransportStorage.text.toString().toDoubleOrNull() ?: 1200.0

                val res = FarmingCalculatorUtils.calculatePostHarvest(
                    cropIndex = cropIndex,
                    quantityQuintals = qty,
                    distanceKm = 20.0,
                    transportRatePerQ = rate,
                    loadingCost = loading,
                    unloadingCost = 0.0,
                    storageMonths = 1.0,
                    storageRatePerQPerMonth = if (qty > 0) storage / qty else 0.0
                )

                displayCalculatorResults(
                    "कुल उपज: ${String.format(Locale.ENGLISH, "%.1f", res.quantityQuintals)} क्विंटल",
                    "गाड़ी भाड़ा: ₹${String.format(Locale.ENGLISH, "%,.0f", res.transportCost)}",
                    "हमाली व लोडिंग: ₹${String.format(Locale.ENGLISH, "%,.0f", res.loadingCost)} | गोदाम/भंडारण: ₹${String.format(Locale.ENGLISH, "%,.0f", res.storageCost)}",
                    "कुल कटाई-बाद व भाड़ा खर्च: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)}",
                    "उपज ले जाने, लादने-उतारने और गोदाम में रखने का खर्च।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = false,
                    category = "Machinery & Fuel",
                    amount = res.totalCost,
                    dateStr = todayStr,
                    note = "Post-Harvest & Transport: ${res.quantityQuintals} Q $crop",
                    stage = "Harvesting"
                )
            }

            // 9. Crop Sale & Mandi Calculator
            calcTitle.contains("9. 🏪") || calcTitle.contains("9.") -> {
                val qty = binding.etSaleQty.text.toString().toDoubleOrNull() ?: 35.0
                val price = binding.etSalePrice.text.toString().toDoubleOrNull() ?: 5000.0
                val transport = binding.etSaleTransport.text.toString().toDoubleOrNull() ?: 3000.0
                val deductions = binding.etSaleDeductions.text.toString().toDoubleOrNull() ?: 1500.0

                val res = FarmingCalculatorUtils.calculateCropSale(
                    cropIndex = cropIndex,
                    quantityQuintals = qty,
                    mandiPricePerQ = price,
                    commissionPercent = 0.0,
                    marketChargesPercent = 0.0,
                    transportDeduction = transport,
                    otherDeduction = deductions
                )

                displayCalculatorResults(
                    "फसल बिक्री: ${res.cropName} (${String.format(Locale.ENGLISH, "%.1f", res.quantityQuintals)} क्विंटल @ ₹${String.format(Locale.ENGLISH, "%,.0f", res.mandiPricePerQ)}/क्विंटल)",
                    "कुल बिक्री राशि (Gross Income): ₹${String.format(Locale.ENGLISH, "%,.0f", res.grossSale)}",
                    "कुल कटौती (भाड़ा + मंडी टैक्स): - ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalDeductions)}",
                    "अनुमानित शुद्ध मंडी आय (Net Income): ₹${String.format(Locale.ENGLISH, "%,.0f", res.netSale)}",
                    "इस मंडी बिक्री आय को अपनी किसान डायरी में सेव करने के लिए नीचे बटन दबाएं!"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = true,
                    category = "Mandi Sale",
                    amount = res.netSale,
                    dateStr = todayStr,
                    note = "Mandi Sale Income: ${res.quantityQuintals} Q $crop @ ₹${res.mandiPricePerQ}/Q",
                    stage = "Harvesting"
                )
            }

            // 10. Complete Crop Profit & ROI Calculator
            else -> {
                val yieldPerAcre = binding.etProfitYieldPerAcre.text.toString().toDoubleOrNull() ?: 7.0
                val pricePerQ = binding.etProfitPricePerQ.text.toString().toDoubleOrNull() ?: 5000.0

                val acres = FarmingCalculatorUtils.toAcres(area, unitIdx)
                val seedCost = 8000.0 * (acres / 5.0)
                val machineryCost = 13500.0 * (acres / 5.0)
                val fertCost = 12000.0 * (acres / 5.0)
                val irrigCost = 5000.0 * (acres / 5.0)
                val sprayCost = 7000.0 * (acres / 5.0)
                val labourCost = 15100.0 * (acres / 5.0)
                val harvestCost = 9000.0 * (acres / 5.0)
                val postHarvestCost = 3000.0 * (acres / 5.0)

                val res = FarmingCalculatorUtils.calculateCropProfit(
                    cropIndex = cropIndex,
                    rawArea = area,
                    unitIndex = unitIdx,
                    expectedYieldPerAcre = yieldPerAcre,
                    expectedPricePerQuintal = pricePerQ,
                    seedCost = seedCost,
                    machineryCost = machineryCost,
                    fertilizerCost = fertCost,
                    irrigationCost = irrigCost,
                    sprayCost = sprayCost,
                    labourCost = labourCost,
                    harvestingCost = harvestCost,
                    postHarvestCost = postHarvestCost
                )

                binding.tvCalcResultDap.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
                binding.tvCalcResultDap.compoundDrawablePadding = (6 * requireContext().resources.displayMetrics.density).toInt()

                displayCalculatorResults(
                    "${res.cropName} (${String.format(Locale.ENGLISH, "%.1f", res.areaInAcres)} एकड़) | कुल उत्पादन: ${String.format(Locale.ENGLISH, "%.1f", res.totalYieldQuintals)} क्विंटल",
                    "अनुमानित कुल बिक्री (Gross Revenue): ₹${String.format(Locale.ENGLISH, "%,.0f", res.expectedRevenue)}",
                    "कुल उत्पादन लागत: ₹${String.format(Locale.ENGLISH, "%,.0f", res.totalCost)} (लागत मूल्य: ₹${String.format(Locale.ENGLISH, "%.0f", res.breakEvenPricePerQuintal)}/क्विंटल)",
                    "अनुमानित शुद्ध मुनाफा (NET PROFIT): ₹${String.format(Locale.ENGLISH, "%,.0f", res.netProfit)} (मुनाफा/एकड़: ₹${String.format(Locale.ENGLISH, "%,.0f", res.profitPerAcre)} | ROI: ${String.format(Locale.ENGLISH, "%.0f", res.roiPercent)}%)",
                    "पूरी फसल का लागत विवरण: बीज, जुताई, खाद, सिंचाई, स्प्रे, मजदूरी, कटाई और भाड़ा शामिल।"
                )

                lastCalculatedItem = KhataItem(
                    id = "calc_${System.currentTimeMillis()}",
                    cropName = crop,
                    isIncome = true,
                    category = "Mandi Sale",
                    amount = res.netProfit,
                    dateStr = todayStr,
                    note = "Estimated Net Crop Profit for $crop (${String.format(Locale.ENGLISH, "%.1f", res.areaInAcres)} Acres)",
                    stage = "Harvesting"
                )
            }
        }

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        if (activeLang != "hi") {
            val seedText = binding.tvCalcResultSeed.text.toString()
            val ureaText = binding.tvCalcResultUrea.text.toString()
            val dapText = binding.tvCalcResultDap.text.toString()
            val zincText = binding.tvCalcResultZinc.text.toString()
            val noteText = binding.tvCalcResultNote.text.toString()

            lifecycleScope.launch(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@launch
                val tSeed = TranslationHelper.translateText(ctx, seedText, activeLang)
                val tUrea = TranslationHelper.translateText(ctx, ureaText, activeLang)
                val tDap = TranslationHelper.translateText(ctx, dapText, activeLang)
                val tZinc = TranslationHelper.translateText(ctx, zincText, activeLang)
                val tNote = TranslationHelper.translateText(ctx, noteText, activeLang)

                if (_binding != null && isAdded) {
                    binding.tvCalcResultSeed.text = tSeed
                    binding.tvCalcResultUrea.text = tUrea
                    binding.tvCalcResultDap.text = tDap
                    binding.tvCalcResultZinc.text = tZinc
                    binding.tvCalcResultNote.text = tNote
                }
            }
        }

        binding.cardCalcResult.visibility = View.VISIBLE
    }

    private fun setupWeatherToolSection() {
        if (_binding == null) return
        if (childFragmentManager.findFragmentByTag("weather_tool_fragment") == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.containerWeather, WeatherFragment(), "weather_tool_fragment")
                .commit()
        }
    }

    private fun setupSchemesSection() {
        val ctx = context ?: return
        if (_binding == null) return

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val displayStates = if (activeLang == "en") {
            SchemeUtils.statesList
        } else {
            SchemeUtils.statesList.map { state ->
                TranslationHelper.getDirectTranslation(state, activeLang) ?: state
            }
        }

        val statesAdapter = ArrayAdapter(ctx, R.layout.item_compact_dropdown_single, displayStates)
        binding.actvSchemeStateFilter.setAdapter(statesAdapter)
        binding.actvSchemeStateFilter.setText(displayStates[0], false)

        if (activeLang != "en") {
            lifecycleScope.launch(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@launch
                val transHeader = TranslationHelper.translateText(ctx, "Government Schemes & Direct Subsidies", activeLang)
                val transFilterHint = TranslationHelper.translateText(ctx, "Filter Schemes by State", activeLang)
                if (_binding != null && isAdded) {
                    binding.tvHeaderSchemes.text = "📜 $transHeader"
                    binding.tilSchemeStateFilter.hint = transFilterHint
                }
            }
        }

        renderSchemeCards("All States")

        binding.actvSchemeStateFilter.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedState = SchemeUtils.statesList.getOrNull(position) ?: "All States"
            renderSchemeCards(selectedState)
        }
    }

    private fun renderSchemeCards(selectedState: String) {
        val ctx = context ?: return
        if (_binding == null) return

        binding.containerSchemeItems.removeAllViews()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val filteredSchemes = if (selectedState == "All States") {
            SchemeUtils.schemesList
        } else {
            SchemeUtils.schemesList.filter { it.state.equals(selectedState, ignoreCase = true) || it.state == "Central (All India)" }
        }

        for (scheme in filteredSchemes) {
            val cardView = LayoutInflater.from(ctx).inflate(R.layout.item_scheme_card, binding.containerSchemeItems, false)
            val tvTitle = cardView.findViewById<TextView>(R.id.tvSchemeTitle)
            val tvStateBadge = cardView.findViewById<TextView>(R.id.tvSchemeStateBadge)
            val tvBenefit = cardView.findViewById<TextView>(R.id.tvSchemeBenefit)
            val tvOverview = cardView.findViewById<TextView>(R.id.tvSchemeOverview)
            val tvEligibility = cardView.findViewById<TextView>(R.id.tvSchemeEligibility)
            val btnReadArticle = cardView.findViewById<MaterialButton>(R.id.btnReadSchemeArticle)
            val btnOpenPortal = cardView.findViewById<MaterialButton>(R.id.btnOpenSchemePortal)

            tvTitle.text = scheme.title
            tvStateBadge.text = scheme.state
            tvBenefit.text = "💰 Benefit: ${scheme.benefitAmount}"
            tvOverview.text = scheme.overview
            tvEligibility.text = "📋 Eligibility: ${scheme.eligibility}"

            if (activeLang != "en") {
                lifecycleScope.launch(Dispatchers.Main) {
                    val safeCtx = context ?: return@launch
                    if (!isAdded || _binding == null) return@launch

                    val transTitle = TranslationHelper.translateText(safeCtx, scheme.title, activeLang)
                    val transStateBadge = TranslationHelper.translateText(safeCtx, scheme.state, activeLang)
                    val transBenefitLabel = TranslationHelper.translateText(safeCtx, "Benefit", activeLang)
                    val transBenefitText = TranslationHelper.translateText(safeCtx, scheme.benefitAmount, activeLang)
                    val transOverview = TranslationHelper.translateText(safeCtx, scheme.overview, activeLang)
                    val transEligibilityLabel = TranslationHelper.translateText(safeCtx, "Eligibility", activeLang)
                    val transEligibility = TranslationHelper.translateText(safeCtx, scheme.eligibility, activeLang)

                    val transReadGuide = TranslationHelper.translateText(safeCtx, "Read Complete Guide & How to Apply", activeLang)
                    val transPortal = TranslationHelper.translateText(safeCtx, "Official Government Portal", activeLang)

                    tvTitle.text = transTitle
                    tvStateBadge.text = transStateBadge
                    tvBenefit.text = "💰 $transBenefitLabel: $transBenefitText"
                    tvOverview.text = transOverview
                    tvEligibility.text = "📋 $transEligibilityLabel: $transEligibility"
                    btnReadArticle.text = "📖 $transReadGuide"
                    btnOpenPortal.text = "🌐 $transPortal"
                }
            }

            btnReadArticle.setOnClickListener {
                openMatchingArticleForScheme(scheme)
            }

            btnOpenPortal.setOnClickListener {
                val url = scheme.officialPortalUrl
                if (url.isNotBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (_: Exception) {
                        val safeCtx = context
                        if (safeCtx != null && isAdded) {
                            Toast.makeText(safeCtx, "Unable to open government portal link", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            binding.containerSchemeItems.addView(cardView)
        }
    }

    private fun fetchPublishedNewsArticles() {
        firestore.collection("news")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                if (!snapshot.isEmpty) {
                    publishedArticles = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(NewsItem::class.java)?.copy(id = doc.id)
                    }.toMutableList()
                }
            }
    }

    private fun openMatchingArticleForScheme(scheme: FarmerScheme) {
        val ctx = context ?: return
        val keyword = scheme.relatedArticleKeyword.lowercase()
        val titleKeyword = scheme.title.lowercase()

        // Match published articles from Firestore
        val matchedArticle = publishedArticles.firstOrNull { article ->
            val artTitle = article.title.lowercase()
            val artDesc = article.description.lowercase()
            artTitle.contains(keyword) || artTitle.contains(titleKeyword) || artDesc.contains(keyword)
        }

        if (matchedArticle != null) {
            val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
            val formattedDate = matchedArticle.timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""

            NewsDetailActivity.start(
                context = ctx,
                title = matchedArticle.title,
                desc = matchedArticle.description,
                category = matchedArticle.category.ifBlank { "Govt Scheme" },
                imageUrl = matchedArticle.imageUrl,
                date = formattedDate,
                id = matchedArticle.id
            )
        } else {
            // Open synthesized rich-text article guide for this scheme
            val guideDesc = """
                ### Overview & Scheme Details
                ${scheme.overview}

                ### Key Financial Benefits
                • **Benefit Amount:** ${scheme.benefitAmount}
                • **Coverage Area / State:** ${scheme.state}

                ### Eligibility Criteria
                ${scheme.eligibility}

                ### Required Documents
                ${scheme.requiredDocuments}

                ### How to Apply
                1. Visit the official government portal: [${scheme.title} Portal](${scheme.officialPortalUrl})
                2. Register using your Aadhaar Card and Land Records (Khatauni).
                3. Enter your active bank account details for Direct Benefit Transfer (DBT).
                4. Submit the online application or contact your local Jan Seva Kendra / Block Agriculture Office.
            """.trimIndent()

            NewsDetailActivity.start(
                context = ctx,
                title = "${scheme.title} - Official Application Guide",
                desc = guideDesc,
                category = "Govt Scheme",
                imageUrl = "",
                date = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date())
            )
        }
    }

    override fun onDestroyView() {
        alertListenerRegistration?.remove()
        alertListenerRegistration = null
        super.onDestroyView()
        cropCyclesListener?.remove()
        cropCyclesListener = null
        khataItemsListener?.remove()
        khataItemsListener = null
        _binding = null
    }
}
