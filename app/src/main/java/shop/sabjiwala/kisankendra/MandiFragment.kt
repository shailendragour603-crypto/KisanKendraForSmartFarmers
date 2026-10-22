package shop.sabjiwala.kisankendra

import androidx.activity.OnBackPressedCallback
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.FragmentMandiBinding
import java.io.File
import java.io.FileOutputStream
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.model.PriceDataPoint
import shop.sabjiwala.kisankendra.utils.CategoryUtils
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.LocationHelper
import shop.sabjiwala.kisankendra.utils.MandiCacheHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.MandiPriceHistoryHelper
import shop.sabjiwala.kisankendra.utils.MandiUtils
import shop.sabjiwala.kisankendra.utils.ScreenAlertHelper
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import com.google.firebase.firestore.ListenerRegistration
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import shop.sabjiwala.kisankendra.views.PriceChartView
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.net.URLEncoder

class MandiFragment : Fragment() {

    private var _binding: FragmentMandiBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MandiViewModel
    private var allRecords: List<MandiRecord> = emptyList()
    private var filteredRecords: List<MandiRecord> = emptyList()
    private var adapter: MandiAdapter? = null
    private var currentLoadedLimit = 20

    private var selectedStateName: String = "⭐ Saved"
    private var selectedDistrictName: String = "All Districts"
    private var selectedMarketName: String = "All Markets"
    private var isUserStateManuallySelected = false

    private var alertListenerRegistration: ListenerRegistration? = null

    private var onBackPressedCallback: OnBackPressedCallback? = null

    private fun setupBackPressHandler() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (selectedStateName == "⭐ Saved") {
                    resetToDefaultMandiHome()
                } else if (!binding.etSearch.text.isNullOrEmpty()) {
                    binding.etSearch.setText("")
                    filterData()
                    updateBackPressState()
                } else if (isNonDefaultStateSelected()) {
                    resetToDefaultMandiHome()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback!!)
    }

    private fun getDefaultStateName(): String {
        val ctx = context ?: return "⭐ Saved"
        return LocationHelper.getSavedState(ctx) ?: "⭐ Saved"
    }

    private fun isNonDefaultStateSelected(): Boolean {
        val defaultState = getDefaultStateName()
        return selectedStateName != defaultState
    }

    private fun updateBackPressState() {
        val isSavedActive = selectedStateName == "⭐ Saved"
        val isSearchActive = !binding.etSearch.text.isNullOrEmpty()
        val isNonDefaultState = isNonDefaultStateSelected()
        onBackPressedCallback?.isEnabled = isSavedActive || isSearchActive || isNonDefaultState
    }

    private fun saveMandiSessionState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("mandi_session_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("session_state", selectedStateName)
            .putString("session_district", selectedDistrictName)
            .putString("session_market", selectedMarketName)
            .putBoolean("session_manual_state", isUserStateManuallySelected)
            .apply()
    }

    private fun restoreMandiSessionState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("mandi_session_prefs", Context.MODE_PRIVATE)
        val savedS = prefs.getString("session_state", null)
        val savedD = prefs.getString("session_district", null)
        val savedM = prefs.getString("session_market", null)
        val manualS = prefs.getBoolean("session_manual_state", false)

        val userLocationState = LocationHelper.getSavedState(ctx)
        val userLocationDistrict = LocationHelper.getSavedDistrict(ctx)

        if (manualS) {
            isUserStateManuallySelected = true
            if (!savedS.isNullOrBlank()) selectedStateName = savedS
            if (!savedD.isNullOrBlank()) selectedDistrictName = savedD
            if (!savedM.isNullOrBlank()) selectedMarketName = savedM
        } else {
            isUserStateManuallySelected = false
            if (!userLocationState.isNullOrBlank()) {
                selectedStateName = userLocationState
            }
            if (!userLocationDistrict.isNullOrBlank()) {
                selectedDistrictName = userLocationDistrict
            }
        }
    }

    private fun resetToDefaultMandiHome() {
        val ctx = context ?: return
        val savedState = LocationHelper.getSavedState(ctx)
        val savedDistrict = LocationHelper.getSavedDistrict(ctx)

        selectedStateName = if (!savedState.isNullOrBlank()) savedState else "⭐ Saved"
        selectedDistrictName = if (!savedDistrict.isNullOrBlank()) savedDistrict else "All Districts"
        selectedMarketName = "All Markets"
        isUserStateManuallySelected = false
        binding.etSearch.setText("")

        ctx.getSharedPreferences("mandi_session_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        setupStateChips()
        filterData()
        updateBackPressState()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            requestLocationPermissionAndFetch()
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            spokenText?.let {
                binding.etSearch.setText(it)
                filterData()
                updateBackPressState()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMandiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackPressHandler()
        alertListenerRegistration = ScreenAlertHelper.setupScreenAlert(requireContext(), "mandi_screen", binding.alertBanner)

        val repository = MandiRepository()
        val factory = MandiViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[MandiViewModel::class.java]

        binding.recyclerViewMandi.layoutManager = LinearLayoutManager(requireContext())
        adapter = MandiAdapter(
            records = emptyList(),
            totalFilteredSize = 0,
            onShareClick = { item -> shareOnWhatsApp(item) },
            onItemClick = { item -> showPriceChartBottomSheet(item) },
            onFavoriteToggle = { item, isFav ->
                if (isFav) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        MandiPriceHistoryHelper.fetchPriceHistory(item, context)
                    }
                    Toast.makeText(requireContext(), "Saved to Favorites ⭐", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Removed from Favorites", Toast.LENGTH_SHORT).show()
                }
                if (selectedStateName == "⭐ Saved") {
                    filterData()
                    updateBackPressState()
                }
            },
            onLoadMore = {
                if (currentLoadedLimit < filteredRecords.size) {
                    currentLoadedLimit = (currentLoadedLimit + 100).coerceAtMost(filteredRecords.size)
                    adapter?.updateData(filteredRecords.take(currentLoadedLimit), filteredRecords.size)
                }
            }
        )
        binding.recyclerViewMandi.adapter = adapter

        ShimmerHelper.setupSwipeRefresh(binding.swipeRefreshMandi)
        binding.swipeRefreshMandi.setOnRefreshListener {
            MandiRepository.clearCache()
            val ctx = context
            val prefState = if (selectedStateName != "All States" && selectedStateName != "⭐ Saved") selectedStateName else (if (ctx != null) LocationHelper.getSavedState(ctx) else null)
            if (!prefState.isNullOrBlank() && prefState != "All States" && prefState != "⭐ Saved") {
                viewModel.loadMandiRatesByState(prefState, ctx, forceRefresh = true)
            } else {
                viewModel.loadMandiRates(preferredStateName = prefState, forceRefresh = true, context = ctx)
            }
        }

        binding.btnForceRefreshMandi.setOnClickListener {
            binding.swipeRefreshMandi.isRefreshing = true
            MandiRepository.clearCache()
            val ctx = context
            val prefState = if (selectedStateName != "All States" && selectedStateName != "⭐ Saved") selectedStateName else (if (ctx != null) LocationHelper.getSavedState(ctx) else null)
            if (!prefState.isNullOrBlank() && prefState != "All States" && prefState != "⭐ Saved") {
                viewModel.loadMandiRatesByState(prefState, ctx, forceRefresh = true)
            } else {
                viewModel.loadMandiRates(preferredStateName = prefState, forceRefresh = true, context = ctx)
            }
            Toast.makeText(requireContext(), "Refreshing Mandi Rates...", Toast.LENGTH_SHORT).show()
        }
        MediaUtils.startPulseAnimation(binding.btnForceRefreshMandi)

        binding.recyclerViewMandi.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible > 2 || recyclerView.computeVerticalScrollOffset() > 300) {
                    if (binding.fabScrollTop.visibility != View.VISIBLE) {
                        binding.fabScrollTop.show()
                    }
                } else {
                    if (binding.fabScrollTop.visibility == View.VISIBLE) {
                        binding.fabScrollTop.hide()
                    }
                }

                val lastVisiblePos = lm.findLastVisibleItemPosition()
                if (lastVisiblePos >= adapter!!.itemCount - 3) {
                    if (currentLoadedLimit < filteredRecords.size) {
                        currentLoadedLimit = (currentLoadedLimit + 100).coerceAtMost(filteredRecords.size)
                        adapter?.updateData(filteredRecords.take(currentLoadedLimit), filteredRecords.size)
                    }
                }
            }
        })

        binding.fabScrollTop.setOnClickListener {
            binding.appBarMandiFilters.setExpanded(true, true)
            binding.recyclerViewMandi.smoothScrollToPosition(0)
        }

        setupCategorySpinner()
        translateMandiUiElements()

        context?.let { ctx ->
            val activeLang = LocaleHelper.getPersistedLanguage(ctx)
            lifecycleScope.launch(Dispatchers.IO) {
                TranslationHelper.syncMasterDictionaryFromFirestore(ctx, activeLang)
            }
        }

        viewModel.mandiData.observe(viewLifecycleOwner) { records ->
            allRecords = records

            restoreMandiSessionState()

            val ctx = context
            if (ctx != null && !isUserStateManuallySelected) {
                val savedState = LocationHelper.getSavedState(ctx)
                val savedDistrict = LocationHelper.getSavedDistrict(ctx)
                if (!savedState.isNullOrBlank()) {
                    selectedStateName = savedState
                }
                if (!savedDistrict.isNullOrBlank()) {
                    selectedDistrictName = savedDistrict
                }
            }

            if (binding.chipGroupStates.childCount == 0) {
                setupStateChips()
            } else {
                setupDistrictSpinner()
            }

            filterData()
            updateBackPressState()

            requestLocationPermissionAndFetch()
        }

        viewModel.mandiDate.observe(viewLifecycleOwner) { activeDate ->
            if (!activeDate.isNullOrBlank()) {
                binding.tvMandiRatesDate.text = "📅 $activeDate"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshMandi.isRefreshing = isLoading
            if (isLoading && allRecords.isEmpty()) {
                ShimmerHelper.applyShimmer(binding.recyclerViewMandi, cornerRadiusDp = 8f)
            } else {
                ShimmerHelper.stopShimmer(binding.recyclerViewMandi)
            }
        }

        binding.btnVoiceSearch.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN"))
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak crop or mandi name (उदा. गेहूं, सोयाबीन, इंदौर, रतलाम)...")
            }
            voiceLauncher.launch(intent)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData()
                updateBackPressState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val ctx = context
        val savedState = if (ctx != null) LocationHelper.getSavedState(ctx) else null
        val savedDistrict = if (ctx != null) LocationHelper.getSavedDistrict(ctx) else null

        if (!isUserStateManuallySelected) {
            if (!savedState.isNullOrBlank() && (selectedStateName == "⭐ Saved" || selectedStateName == "All States" || selectedStateName.isBlank())) {
                selectedStateName = savedState
            }
            if (!savedDistrict.isNullOrBlank() && (selectedDistrictName == "All Districts" || selectedDistrictName.isBlank())) {
                selectedDistrictName = savedDistrict
            }
        }

        val targetStateToLoad = if (selectedStateName != "⭐ Saved" && selectedStateName != "All States") selectedStateName else savedState
        if (!targetStateToLoad.isNullOrBlank()) {
            viewModel.loadMandiRatesByState(targetStateToLoad, ctx)
        } else {
            viewModel.loadMandiRates(preferredStateName = savedState, context = ctx)
        }

        updateBackPressState()
    }



    private fun requestLocationPermissionAndFetch() {
        val safeCtx = context ?: return
        val hasFine = ContextCompat.checkSelfPermission(safeCtx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(safeCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            LocationHelper.getUserLocationDetails(safeCtx) { district, state, _ ->
                if (!isAdded || _binding == null) return@getUserLocationDetails

                if (!isUserStateManuallySelected) {
                    var locationUpdated = false
                    if (!state.isNullOrBlank() && selectedStateName != state) {
                        selectedStateName = state
                        locationUpdated = true
                    }
                    if (!district.isNullOrBlank() && selectedDistrictName != district) {
                        selectedDistrictName = district
                        locationUpdated = true
                    }
                    if (locationUpdated) {
                        LocationHelper.saveUserLocation(safeCtx, selectedStateName, selectedDistrictName)
                        if (binding.chipGroupStates.childCount == 0) {
                            setupStateChips()
                        } else {
                            setupDistrictSpinner()
                        }
                        filterData()
                        updateBackPressState()
                    }
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun setupStateChips() {
        val chipGroup = binding.chipGroupStates
        chipGroup.removeAllViews()

        val ctx = context
        val savedState = if (ctx != null) LocationHelper.getSavedState(ctx) else null
        val defaultState = if (!savedState.isNullOrBlank()) savedState else if (selectedStateName != "⭐ Saved" && selectedStateName != "All States") selectedStateName else null

        val availableStates = mutableListOf(
            "Madhya Pradesh",
            "Rajasthan",
            "Maharashtra",
            "Uttar Pradesh",
            "Gujarat",
            "Punjab",
            "Haryana",
            "Bihar",
            "Chhattisgarh",
            "West Bengal",
            "Karnataka",
            "Tamil Nadu",
            "Telangana",
            "Andhra Pradesh",
            "Odisha",
            "Jharkhand",
            "Kerala",
            "Assam",
            "Himachal Pradesh",
            "Uttarakhand",
            "Goa",
            "Tripura",
            "Meghalaya",
            "Manipur",
            "Nagaland",
            "Mizoram",
            "Arunachal Pradesh",
            "Sikkim",
            "Jammu and Kashmir",
            "Delhi",
            "Puducherry",
            "Chandigarh",
            "Andaman and Nicobar",
            "Dadra and Nagar Haveli",
            "Ladakh"
        )

        val recordStates = allRecords.mapNotNull { it.state?.trim() }.filter { it.isNotBlank() }.distinct()
        for (st in recordStates) {
            if (!availableStates.any { it.equals(st, ignoreCase = true) }) {
                availableStates.add(st)
            }
        }

        val allStates = mutableListOf("⭐ Saved")

        // Prioritize user's saved/location state right after "⭐ Saved"
        if (!defaultState.isNullOrBlank()) {
            val matchedDefault = availableStates.find { it.equals(defaultState.trim(), ignoreCase = true) }
            if (matchedDefault != null) {
                availableStates.remove(matchedDefault)
                allStates.add(matchedDefault)
            }
        }

        allStates.addAll(availableStates)

        if (selectedStateName == "All States" || selectedStateName.isBlank()) {
            selectedStateName = if (!defaultState.isNullOrBlank()) defaultState else (allStates.getOrNull(1) ?: "⭐ Saved")
        }

        val activeLang = if (ctx != null) LocaleHelper.getPersistedLanguage(ctx) else "en"

        for (stateName in allStates) {
            val chip = Chip(requireContext()).apply {
                text = stateName
                isCheckable = true
                isClickable = true
                setChipBackgroundColorResource(R.color.chip_bg_color)
                setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text_color))
                setChipStrokeColorResource(R.color.chip_stroke_color)
                chipStrokeWidth = 1.5f
            }

            if (activeLang != "en" && stateName != "⭐ Saved") {
                lifecycleScope.launch(Dispatchers.Main) {
                    val transState = TranslationHelper.translateText(requireContext(), stateName, activeLang)
                    chip.text = transState
                }
            }

            if (stateName.equals(selectedStateName, ignoreCase = true)) {
                chip.isChecked = true
                selectedStateName = stateName
                binding.scrollStateChips.post {
                    binding.scrollStateChips.smoothScrollTo(chip.left, 0)
                }
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    isUserStateManuallySelected = true
                    selectedStateName = stateName
                    val ctx = context
                    val savedD = if (ctx != null) LocationHelper.getSavedDistrict(ctx) else null
                    val savedS = if (ctx != null) LocationHelper.getSavedState(ctx) else null
                    selectedDistrictName = if (!savedD.isNullOrBlank() && (stateName.equals(savedS, ignoreCase = true) || stateName == "⭐ Saved" || stateName == "All States")) savedD else "All Districts"
                    selectedMarketName = "All Markets"
                    if (stateName != "All States" && stateName != "⭐ Saved") {
                        viewModel.loadMandiRatesByState(stateName)
                    }
                    setupDistrictSpinner()
                    filterData()
                    updateBackPressState()
                }
            }

            chipGroup.addView(chip)
        }

        setupDistrictSpinner()
    }

    private fun setupDistrictSpinner() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val stateRecords = if (selectedStateName == "All States") {
            allRecords
        } else {
            allRecords.filter { it.state.orEmpty().equals(selectedStateName, ignoreCase = true) }
        }

        val defaultDistText = TranslationHelper.getDirectTranslation("All Districts", activeLang)
            ?: if (activeLang == "hi") "सभी ज़िले" else "All Districts"

        val rawDistricts = stateRecords.mapNotNull { it.district?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        val displayDistricts = mutableListOf(defaultDistText)
        displayDistricts.addAll(rawDistricts.map { if (activeLang == "en") it else TranslationHelper.getTranslatedMarketName(it, activeLang) })

        val adapterDistricts = ArrayAdapter(requireContext(), R.layout.item_compact_dropdown_single, displayDistricts)
        binding.spinnerState.setAdapter(adapterDistricts)

        val userSavedDistrict = LocationHelper.getSavedDistrict(ctx)
        val targetDistrict = if (!isUserStateManuallySelected && !userSavedDistrict.isNullOrBlank()) {
            userSavedDistrict
        } else if (selectedDistrictName != "All Districts" && selectedDistrictName.isNotBlank()) {
            selectedDistrictName
        } else if (!userSavedDistrict.isNullOrBlank()) {
            userSavedDistrict
        } else {
            "All Districts"
        }

        val cleanSelDist = LocationHelper.cleanDistrictName(targetDistrict).orEmpty().lowercase()
        val matchedIndex = rawDistricts.indexOfFirst { raw ->
            val cleanRaw = LocationHelper.cleanDistrictName(raw).orEmpty().lowercase()
            cleanRaw.isNotBlank() && cleanSelDist.isNotBlank() &&
            (raw.equals(targetDistrict, ignoreCase = true) || cleanRaw == cleanSelDist || cleanRaw.contains(cleanSelDist) || cleanSelDist.contains(cleanRaw))
        }

        val initialDistrictText: String
        if (matchedIndex != -1) {
            selectedDistrictName = rawDistricts[matchedIndex]
            initialDistrictText = displayDistricts.getOrNull(matchedIndex + 1) ?: selectedDistrictName
        } else {
            if (targetDistrict == "All Districts" || selectedDistrictName == "All Districts") {
                selectedDistrictName = "All Districts"
                initialDistrictText = defaultDistText
            } else {
                selectedDistrictName = targetDistrict
                initialDistrictText = TranslationHelper.getTranslatedMarketName(targetDistrict, activeLang)
            }
        }

        binding.spinnerState.setText(initialDistrictText, false)

        binding.spinnerState.setOnItemClickListener { parent, _, position, _ ->
            val chosenDistrict = parent.getItemAtPosition(position).toString()
            isUserStateManuallySelected = true
            selectedDistrictName = if (position == 0) "All Districts" else rawDistricts.getOrNull(position - 1) ?: chosenDistrict
            selectedMarketName = "All Markets"
            setupMarketChips()
            filterData()
            updateBackPressState()
        }

        if (activeLang != "en") {
            lifecycleScope.launch(Dispatchers.Main) {
                var needsUpdate = false
                val updatedDistricts = mutableListOf(defaultDistText)
                for (raw in rawDistricts) {
                    val currentTrans = TranslationHelper.getTranslatedMarketName(raw, activeLang)
                    if (currentTrans == raw) {
                        val asyncTrans = TranslationHelper.translateText(requireContext(), raw, activeLang)
                        updatedDistricts.add(asyncTrans)
                        needsUpdate = true
                    } else {
                        updatedDistricts.add(currentTrans)
                    }
                }
                if (needsUpdate && _binding != null) {
                    val updatedAdapter = ArrayAdapter(requireContext(), R.layout.item_compact_dropdown_single, updatedDistricts)
                    binding.spinnerState.setAdapter(updatedAdapter)
                }
            }
        }

        setupMarketChips()
    }

    private fun setupMarketChips() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        val chipGroup = binding.chipGroupMarkets
        chipGroup.removeAllViews()

        var filteredForMarkets = allRecords
        if (selectedStateName != "All States" && selectedStateName != "⭐ Saved") {
            filteredForMarkets = filteredForMarkets.filter { it.state.orEmpty().equals(selectedStateName, ignoreCase = true) }
        }
        if (selectedDistrictName != "All Districts") {
            val cleanSelDist = LocationHelper.cleanDistrictName(selectedDistrictName).orEmpty().lowercase()
            filteredForMarkets = filteredForMarkets.filter { item ->
                val cleanItemDist = LocationHelper.cleanDistrictName(item.district).orEmpty().lowercase()
                val cleanItemMkt = TranslationHelper.getCleanMarketName(item.market.orEmpty()).lowercase()
                item.district.orEmpty().contains(selectedDistrictName, ignoreCase = true) ||
                selectedDistrictName.contains(item.district.orEmpty(), ignoreCase = true) ||
                (cleanSelDist.isNotBlank() && cleanItemDist.isNotBlank() && (cleanItemDist.contains(cleanSelDist) || cleanSelDist.contains(cleanItemDist))) ||
                (cleanSelDist.isNotBlank() && cleanItemMkt.isNotBlank() && cleanItemMkt.contains(cleanSelDist))
            }
        }

        val rawMarkets = filteredForMarkets.mapNotNull { it.market?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        val cleanMarkets = rawMarkets.map { TranslationHelper.getCleanMarketName(it) }.distinct()

        val allMarketsLabel = TranslationHelper.getDirectTranslation("All Markets", activeLang)
            ?: if (activeLang == "hi") "सभी मंडियाँ" else "All Markets"

        val markets = mutableListOf(allMarketsLabel)
        markets.addAll(cleanMarkets.map { if (activeLang == "en") it else TranslationHelper.getTranslatedMarketName(it, activeLang) })

        if (markets.size <= 1) {
            binding.scrollMarketTabs.visibility = View.GONE
            return
        }

        binding.scrollMarketTabs.visibility = View.VISIBLE

        for ((index, marketDisplayName) in markets.withIndex()) {
            val rawMarket = if (index == 0) "All Markets" else cleanMarkets.getOrNull(index - 1) ?: marketDisplayName
            val chip = Chip(requireContext()).apply {
                text = marketDisplayName
                isCheckable = true
                isClickable = true
                setChipBackgroundColorResource(R.color.chip_bg_color)
                setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text_color))
                setChipStrokeColorResource(R.color.chip_stroke_color)
                chipStrokeWidth = 1.5f
            }

            if (activeLang != "en" && index > 0 && marketDisplayName == rawMarket) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val asyncMarket = TranslationHelper.getTranslatedMarketName(rawMarket, activeLang, requireContext())
                    if (asyncMarket.isNotBlank() && _binding != null) {
                        chip.text = asyncMarket
                    }
                }
            }

            if (rawMarket == selectedMarketName) {
                chip.isChecked = true
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedMarketName = rawMarket
                    filterData()
                    updateBackPressState()
                }
            }

            chipGroup.addView(chip)
        }
    }

    private fun translateMandiUiElements() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.Main) {
            val selectDist = TranslationHelper.getDirectTranslation("Select District", activeLang)
                ?: TranslationHelper.translateText(ctx, "Select District", activeLang)
            val selectItem = TranslationHelper.getDirectTranslation("Select Category", activeLang)
                ?: TranslationHelper.translateText(ctx, "Select Category", activeLang)
            val searchHint = TranslationHelper.getDirectTranslation("Search Crop or Market...", activeLang)
                ?: TranslationHelper.translateText(ctx, "Search Crop or Market...", activeLang)

            binding.spinnerState.hint = selectDist
            binding.spinnerCategory.hint = selectItem
            binding.etSearch.hint = searchHint
        }
    }

    private fun setupCategorySpinner() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        val dropdownItems = CategoryUtils.buildFullCategoryDropdown(allRecords, activeLang)
        val categoryAdapter = ArrayAdapter(requireContext(), R.layout.item_compact_dropdown_single, dropdownItems)
        binding.spinnerCategory.setAdapter(categoryAdapter)

        if (binding.spinnerCategory.text.isNullOrBlank()) {
            binding.spinnerCategory.setText(CategoryUtils.getMajorCategories(activeLang).first(), false)
        }

        binding.spinnerCategory.setOnItemClickListener { parent, _, position, _ ->
            val chosen = parent.getItemAtPosition(position).toString()
            if (chosen.startsWith("---")) {
                binding.spinnerCategory.setText(CategoryUtils.getMajorCategories(activeLang).first(), false)
                return@setOnItemClickListener
            }
            filterData()
            updateBackPressState()
        }
    }

    private fun getSelectedCategory(): String {
        return binding.spinnerCategory.text?.toString()?.ifBlank { CategoryUtils.MAJOR_CATEGORIES.first() } ?: CategoryUtils.MAJOR_CATEGORIES.first()
    }

    private fun filterData() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val selectedCategory = getSelectedCategory()
        val isSavedFilter = selectedStateName == "⭐ Saved"
        val favorites = if (isSavedFilter) MandiCacheHelper.getFavorites(requireContext()) else emptySet()

        val cleanSelDist = LocationHelper.cleanDistrictName(selectedDistrictName).orEmpty().lowercase()
        val cleanSelMarket = TranslationHelper.getCleanMarketName(selectedMarketName).lowercase()

        filteredRecords = allRecords.filter { item ->
            val docId = MandiCacheHelper.getDocId(item)
            val cleanItemDist = LocationHelper.cleanDistrictName(item.district).orEmpty().lowercase()
            val cleanItemMarket = TranslationHelper.getCleanMarketName(item.market.orEmpty()).lowercase()

            val matchesSaved = !isSavedFilter || favorites.contains(docId)
            val matchesState = isSavedFilter || selectedStateName == "All States" || item.state.orEmpty().equals(selectedStateName, ignoreCase = true)
            val matchesDistrict = isSavedFilter || selectedDistrictName == "All Districts" ||
                    item.district.orEmpty().contains(selectedDistrictName, ignoreCase = true) ||
                    selectedDistrictName.contains(item.district.orEmpty(), ignoreCase = true) ||
                    item.market.orEmpty().contains(selectedDistrictName, ignoreCase = true) ||
                    (cleanSelDist.isNotBlank() && cleanItemDist.isNotBlank() && (cleanItemDist.contains(cleanSelDist) || cleanSelDist.contains(cleanItemDist))) ||
                    (cleanSelDist.isNotBlank() && cleanItemMarket.isNotBlank() && cleanItemMarket.contains(cleanSelDist))

            val matchesMarket = isSavedFilter || selectedMarketName == "All Markets" ||
                    item.market.orEmpty().equals(selectedMarketName, ignoreCase = true) ||
                    item.market.orEmpty().contains(selectedMarketName, ignoreCase = true) ||
                    selectedMarketName.contains(item.market.orEmpty(), ignoreCase = true) ||
                    (cleanSelMarket.isNotBlank() && cleanItemMarket.isNotBlank() && (cleanItemMarket.contains(cleanSelMarket) || cleanSelMarket.contains(cleanItemMarket)))

            val matchesCategory = CategoryUtils.matchesSelection("${item.commodity.orEmpty()} ${item.variety.orEmpty()}", selectedCategory)

            val matchesQuery = query.isBlank() ||
                    item.commodity.orEmpty().contains(query, ignoreCase = true) ||
                    item.variety.orEmpty().contains(query, ignoreCase = true) ||
                    item.market.orEmpty().contains(query, ignoreCase = true) ||
                    item.district.orEmpty().contains(query, ignoreCase = true) ||
                    item.state.orEmpty().contains(query, ignoreCase = true)

            matchesSaved && matchesState && matchesDistrict && matchesMarket && matchesCategory && matchesQuery
        }

        currentLoadedLimit = if (selectedDistrictName != "All Districts" || query.isNotBlank()) 100 else 50
        adapter?.updateData(filteredRecords.take(currentLoadedLimit), filteredRecords.size)
        if (filteredRecords.isEmpty()) {
            binding.tvEmptyMandi.visibility = View.VISIBLE
        } else {
            binding.tvEmptyMandi.visibility = View.GONE
        }
        saveMandiSessionState()
    }

    private fun shareOnWhatsApp(item: MandiRecord) {
        val appUrl = "https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"
        val ctx = requireContext()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.Main) {
            val rawCommodity = item.commodity.orEmpty()
            val rawMarket = item.market.orEmpty()
            val rawDistrict = item.district.orEmpty()
            val rawState = item.state.orEmpty()

            val commodity = if (activeLang != "en") TranslationHelper.translateText(ctx, rawCommodity, activeLang) else rawCommodity
            val market = if (activeLang != "en") TranslationHelper.getTranslatedMarketName(rawMarket, activeLang, ctx) else rawMarket
            val district = if (activeLang != "en") TranslationHelper.getTranslatedMarketName(rawDistrict, activeLang, ctx) else rawDistrict
            val state = if (activeLang != "en") TranslationHelper.translateText(ctx, rawState, activeLang) else rawState

            val message = "🌱 *$commodity*\n📍 Market: $market\n🗺️ Dist: $district, $state\n💰 Min: ₹${item.minPrice}\n🚀 Max: ₹${item.maxPrice}\n\n📲 Download Kisan Kendra App on Google Play Store:\n$appUrl"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(message, "UTF-8"))
            }
            startActivity(intent)
        }
    }

    private fun showPriceChartBottomSheet(item: MandiRecord) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_price_chart, null)
        bottomSheetDialog.setContentView(dialogView)

        // 4 PM IST Cutoff Time Rules
        val currentHour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).get(Calendar.HOUR_OF_DAY)
        val isAfter4Pm = currentHour >= 16

        val tabTodayText = if (isAfter4Pm) "Today" else "Last Day"

        val tvSheetEmoji = dialogView.findViewById<TextView>(R.id.tvSheetEmoji)
        val tvSheetCommodity = dialogView.findViewById<TextView>(R.id.tvSheetCommodity)
        val tvSheetMarket = dialogView.findViewById<TextView>(R.id.tvSheetMarket)
        val tvSheetTimeBadge = dialogView.findViewById<TextView>(R.id.tvSheetTimeBadge)
        val tvSheetDateSub = dialogView.findViewById<TextView>(R.id.tvSheetDateSub)

        val tvSheetPrice = dialogView.findViewById<TextView>(R.id.tvSheetPrice)
        val tvSheetPriceDiff = dialogView.findViewById<TextView>(R.id.tvSheetPriceDiff)

        val btnTabToday = dialogView.findViewById<MaterialButton>(R.id.btnTabToday)
        val btnTab7Days = dialogView.findViewById<MaterialButton>(R.id.btnTab7Days)
        val btnTab30Days = dialogView.findViewById<MaterialButton>(R.id.btnTab30Days)

        val tvChartHeaderTitle = dialogView.findViewById<TextView>(R.id.tvChartHeaderTitle)
        val priceChartView = dialogView.findViewById<PriceChartView>(R.id.priceChartView)

        val tvSummaryHighest = dialogView.findViewById<TextView>(R.id.tvSummaryHighest)
        val tvSummaryAverage = dialogView.findViewById<TextView>(R.id.tvSummaryAverage)
        val tvSummaryLowest = dialogView.findViewById<TextView>(R.id.tvSummaryLowest)

        val activeLang = LocaleHelper.getPersistedLanguage(requireContext())

        val tvSheetUnitSub = dialogView.findViewById<TextView>(R.id.tvSheetUnitSub)
        val tvSheetHighestSub = dialogView.findViewById<TextView>(R.id.tvSheetHighestSub)
        val tvSheetDiffSub = dialogView.findViewById<TextView>(R.id.tvSheetDiffSub)
        val tvHighestRateTitle = dialogView.findViewById<TextView>(R.id.tvHighestRateTitle)
        val tvHighestRateSub = dialogView.findViewById<TextView>(R.id.tvHighestRateSub)
        val tvAvgRateTitle = dialogView.findViewById<TextView>(R.id.tvAvgRateTitle)
        val tvAvgRateSub = dialogView.findViewById<TextView>(R.id.tvAvgRateSub)
        val tvLowestRateTitle = dialogView.findViewById<TextView>(R.id.tvLowestRateTitle)
        val tvLowestRateSub = dialogView.findViewById<TextView>(R.id.tvLowestRateSub)

        // Localized Labels
        tvSheetTimeBadge.text = TranslationHelper.getDirectTranslation("Price Chart", activeLang) ?: if (activeLang == "hi") "मूल्य चार्ट" else "Price Chart"

        val transTodayText = TranslationHelper.getDirectTranslation(tabTodayText, activeLang) ?: if (activeLang == "hi") (if (isAfter4Pm) "आज" else "पिछला दिन") else tabTodayText
        val trans7DaysText = TranslationHelper.getDirectTranslation("7 Days", activeLang) ?: if (activeLang == "hi") "7 दिन" else "7 Days"
        val trans30DaysText = TranslationHelper.getDirectTranslation("30 Days", activeLang) ?: if (activeLang == "hi") "30 दिन" else "30 Days"

        btnTabToday.text = transTodayText
        btnTab7Days.text = trans7DaysText
        btnTab30Days.text = trans30DaysText

        if (tvSheetUnitSub != null) tvSheetUnitSub.text = TranslationHelper.getDirectTranslation("/ quintal", activeLang) ?: if (activeLang == "hi") "/ क्विंटल" else "/ quintal"
        if (tvSheetHighestSub != null) tvSheetHighestSub.text = TranslationHelper.getDirectTranslation("Highest recorded price", activeLang) ?: if (activeLang == "hi") "उच्चतम दर्ज भाव" else "Highest recorded price"
        if (tvSheetDiffSub != null) tvSheetDiffSub.text = TranslationHelper.getDirectTranslation("Compared to yesterday", activeLang) ?: if (activeLang == "hi") "कल की तुलना में" else "Compared to yesterday"

        if (tvHighestRateTitle != null) tvHighestRateTitle.text = TranslationHelper.getDirectTranslation("▲ Highest Rate", activeLang) ?: if (activeLang == "hi") "▲ उच्चतम भाव" else "▲ Highest Rate"
        if (tvHighestRateSub != null) tvHighestRateSub.text = TranslationHelper.getDirectTranslation("per quintal", activeLang) ?: if (activeLang == "hi") "प्रति क्विंटल" else "per quintal"
        if (tvAvgRateTitle != null) tvAvgRateTitle.text = TranslationHelper.getDirectTranslation("Average Rate", activeLang) ?: if (activeLang == "hi") "औसत भाव" else "Average Rate"
        if (tvAvgRateSub != null) tvAvgRateSub.text = TranslationHelper.getDirectTranslation("per quintal", activeLang) ?: if (activeLang == "hi") "प्रति क्विंटल" else "per quintal"
        if (tvLowestRateTitle != null) tvLowestRateTitle.text = TranslationHelper.getDirectTranslation("▼ Lowest Rate", activeLang) ?: if (activeLang == "hi") "▼ न्यूनतम भाव" else "▼ Lowest Rate"
        if (tvLowestRateSub != null) tvLowestRateSub.text = TranslationHelper.getDirectTranslation("per quintal", activeLang) ?: if (activeLang == "hi") "प्रति क्विंटल" else "per quintal"

        // Emoji & Names (Clean APMC and Exclude State Name)
        val emoji = MandiUtils.getEmoji(item.commodity)
        tvSheetEmoji.text = emoji

        val rawCommodity = item.commodity.orEmpty().ifBlank { "Crop" }
        val cleanMkt = TranslationHelper.getCleanMarketName(item.market.orEmpty())
        val cleanDist = TranslationHelper.getCleanMarketName(item.district.orEmpty())

        val transCommodity = if (activeLang == "en") {
            rawCommodity
        } else {
            val t = TranslationHelper.getTranslatedCropName(rawCommodity, "hi")
            if (t == rawCommodity) MandiUtils.translateFast(rawCommodity).ifBlank { TranslationHelper.getHindiCropName(rawCommodity) } else t
        }

        val transMkt = if (activeLang == "en") {
            cleanMkt
        } else {
            val t = TranslationHelper.getTranslatedMarketName(cleanMkt, "hi")
            if (t == cleanMkt) MandiUtils.translateFast(cleanMkt).ifBlank { cleanMkt } else t
        }

        val transDist = if (activeLang == "en") {
            cleanDist
        } else {
            val t = TranslationHelper.getTranslatedMarketName(cleanDist, "hi")
            if (t == cleanDist) MandiUtils.translateFast(cleanDist).ifBlank { cleanDist } else t
        }
        val marketStr = if (transMkt.equals(transDist, ignoreCase = true) || transDist.isBlank()) transMkt else "$transMkt • $transDist"

        tvSheetCommodity.text = transCommodity
        tvSheetMarket.text = marketStr

        // Prices & Differences (Highest price of that day coming from Mandi card)
        val highestPrice = item.maxPrice?.toDoubleOrNull() ?: item.modalPrice?.toDoubleOrNull() ?: 0.0
        tvSheetPrice.text = "₹${String.format(Locale.ENGLISH, "%,.0f", highestPrice)}"

        val diff = item.priceDiff
        val isNight = (requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val posColor = if (isNight) Color.parseColor("#81C784") else ContextCompat.getColor(requireContext(), R.color.farmer_green_dark)
        val negColor = if (isNight) Color.parseColor("#FF8A80") else ContextCompat.getColor(requireContext(), R.color.price_min_text)

        if (diff != null && diff != 0.0) {
            val pct = if (highestPrice > 0) Math.abs(diff / highestPrice * 100.0) else 0.0
            if (diff > 0) {
                tvSheetPriceDiff.text = "▲ + ₹${diff.toInt()} (▲ ${String.format(Locale.ENGLISH, "%.1f", pct)}%)"
                tvSheetPriceDiff.setTextColor(posColor)
            } else {
                tvSheetPriceDiff.text = "▼ - ₹${Math.abs(diff).toInt()} (▼ ${String.format(Locale.ENGLISH, "%.1f", pct)}%)"
                tvSheetPriceDiff.setTextColor(negColor)
            }
        } else {
            val stableText = TranslationHelper.getDirectTranslation("Stable", activeLang) ?: if (activeLang == "hi") "स्थिर (₹0)" else "Stable (₹0)"
            tvSheetPriceDiff.text = stableText
            tvSheetPriceDiff.setTextColor(posColor)
        }

        val activeDate = viewModel.mandiDate.value ?: item.formattedDate ?: "Today"
        tvSheetDateSub.text = TranslationHelper.getTranslatedDate(activeDate, activeLang)

        val getChartHeaderTitle: (String) -> String = { key ->
            TranslationHelper.getDirectTranslation(key, activeLang) ?: if (activeLang == "hi") {
                when (key) {
                    "Price Trend (Today)" -> "भाव ट्रेंड (आज)"
                    "Price Trend (Last Day)" -> "भाव ट्रेंड (पिछला दिन)"
                    "Price Trend (Last 7 Days)" -> "भाव ट्रेंड (पिछले 7 दिन)"
                    "Price Trend (Last 30 Days)" -> "भाव ट्रेंड (पिछले 30 दिन)"
                    else -> key
                }
            } else key
        }

        tvChartHeaderTitle.text = getChartHeaderTitle("Price Trend (Last 7 Days)")

        // History Chart Data & Dynamic Range Summary Calculation
        var fullHistory = emptyList<PriceDataPoint>()

        fun updateTabStyles(activeBtn: MaterialButton) {
            val activeBgColor = if (isNight) Color.WHITE else Color.BLACK
            val activeTextColor = if (isNight) Color.BLACK else Color.WHITE
            val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            val strokeColor = ContextCompat.getColor(requireContext(), R.color.card_stroke_color)

            listOf(btnTabToday, btnTab7Days, btnTab30Days).forEach { btn ->
                if (btn == activeBtn) {
                    btn.backgroundTintList = ColorStateList.valueOf(activeBgColor)
                    btn.setTextColor(activeTextColor)
                    btn.strokeWidth = 0
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    btn.setTextColor(inactiveTextColor)
                    btn.strokeColor = ColorStateList.valueOf(strokeColor)
                    btn.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                }
            }
        }

        fun renderTab(days: Int, activeBtn: MaterialButton) {
            updateTabStyles(activeBtn)

            val displayPoints = if (days <= 1) {
                fullHistory.takeLast(2)
            } else {
                fullHistory.takeLast(days)
            }.ifEmpty {
                listOf(PriceDataPoint(activeDate, highestPrice, System.currentTimeMillis()))
            }

            // Calculate Highest, Average, Lowest for selected period
            val periodMax = displayPoints.maxOf { it.price }
            val periodMin = displayPoints.minOf { it.price }
            val periodAvg = displayPoints.map { it.price }.average()

            tvSummaryHighest.text = "₹${String.format(Locale.ENGLISH, "%,.0f", periodMax)}"
            tvSummaryAverage.text = "₹${String.format(Locale.ENGLISH, "%,.0f", periodAvg)}"
            tvSummaryLowest.text = "₹${String.format(Locale.ENGLISH, "%,.0f", periodMin)}"

            priceChartView.setDataPoints(displayPoints)
        }

        lifecycleScope.launch {
            fullHistory = MandiPriceHistoryHelper.fetchPriceHistory(item, requireContext())
            renderTab(7, btnTab7Days) // Default 7 Days
        }

        // Tab Selection Handlers
        btnTabToday.setOnClickListener {
            val titleKey = if (isAfter4Pm) "Price Trend (Today)" else "Price Trend (Last Day)"
            tvChartHeaderTitle.text = getChartHeaderTitle(titleKey)
            tvChartHeaderTitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
            tvChartHeaderTitle.compoundDrawablePadding = (6 * requireContext().resources.displayMetrics.density).toInt()
            renderTab(1, btnTabToday)
        }

        btnTab7Days.setOnClickListener {
            tvChartHeaderTitle.text = getChartHeaderTitle("Price Trend (Last 7 Days)")
            tvChartHeaderTitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
            tvChartHeaderTitle.compoundDrawablePadding = (6 * requireContext().resources.displayMetrics.density).toInt()
            renderTab(7, btnTab7Days)
        }

        btnTab30Days.setOnClickListener {
            tvChartHeaderTitle.text = getChartHeaderTitle("Price Trend (Last 30 Days)")
            tvChartHeaderTitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
            tvChartHeaderTitle.compoundDrawablePadding = (6 * requireContext().resources.displayMetrics.density).toInt()
            renderTab(30, btnTab30Days)
        }

        val btnSharePriceChart = dialogView.findViewById<View>(R.id.btnSharePriceChart)
        val viewPulseRingPriceChart = dialogView.findViewById<View>(R.id.viewPulseRingPriceChart)
        MediaUtils.startCompactGreenPulseAnimation(viewPulseRingPriceChart)
        btnSharePriceChart.setOnClickListener {
            val containerChartContent = dialogView.findViewById<ViewGroup>(R.id.containerChartContent)
            val file = MediaUtils.takeFullScrollScreenshot(containerChartContent, requireContext())
            val uri = file?.let { FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", it) }
            val shareText = "Mandi Price Chart for ${item.commodity} at ${item.market}\n\nAccess farming calculators, track expenses, check mandi rates, and read daily agri news!\n\n📥 Download Kisan Kendra App: https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"

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
            val chooser = Intent.createChooser(intent, "Share Price Chart via")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        }



        bottomSheetDialog.show()
    }

    override fun onDestroyView() {
        alertListenerRegistration?.remove()
        alertListenerRegistration = null
        super.onDestroyView()
        _binding = null
    }
}
