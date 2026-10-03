package shop.sabjiwala.kisankendra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.ImageViewCompat
import com.google.android.gms.ads.AdListener
import shop.sabjiwala.kisankendra.databinding.ActivityMainBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var navClickCount = 0
    private var lastInterstitialTime = 0L

    private var splashPulseAnimator: ObjectAnimator? = null
    private val splashHandler = Handler(Looper.getMainLooper())
    private var splashDotsRunnable: Runnable? = null

    companion object {
        private const val INTERSTITIAL_AD_ID = "ca-app-pub-3054731531860427/5297828555"
        private const val REWARDED_AD_ID = "ca-app-pub-3054731531860427/8586977507"
        private const val MIN_INTERSTITIAL_GAP_MS = 120_000L // 2 minutes (120s) minimum gap for user retention
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        // 🔹 1. We NO LONGER hold the native splash screen on screen. Let it dismiss instantly!
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 🔹 First-Time Launch Auth Check
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null && !SignInActivity.isGuestUser(this)) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 2. Start Animated Extended Splash Screen
        val skipSplash = intent.getBooleanExtra("skip_splash", false)
        if (skipSplash || savedInstanceState != null) {
            binding.extendedSplashScreen.visibility = View.GONE
        } else {
            setupSplashAnimation()
        }


        // System Insets & Keyboard Visibility Listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )

            // Hide floating bottom navigation bar when soft keyboard is open so it does not block calculator inputs
            if (isImeVisible) {
                binding.cardBottomNav.visibility = View.GONE
                binding.adContainer.visibility = View.GONE
                binding.adDivider.visibility = View.GONE
            } else {
                binding.cardBottomNav.visibility = View.VISIBLE
                if (binding.adContainer.childCount > 0) {
                    binding.adContainer.visibility = View.VISIBLE
                    binding.adDivider.visibility = View.VISIBLE
                }
            }

            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.cardBottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = v.layoutParams as ViewGroup.MarginLayoutParams
            val baseMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams.bottomMargin = baseMargin + systemBars.bottom
            v.layoutParams = layoutParams
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val density = resources.displayMetrics.density
            v.updatePadding(bottom = (6 * density).toInt())
            insets
        }

        try {
            val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            MobileAds.initialize(this) { status ->
                Log.d("AdMob", "MobileAds Initialized Status: $status")
                runOnUiThread {
                    loadBannerAd()
                    loadInterstitialAd()
                    loadRewardedAd()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadBannerAd()
            loadInterstitialAd()
            loadRewardedAd()
        }

        setupOnBackPressedDispatcher()

        window.decorView.postDelayed({
            checkAndRequestPermissions()
        }, 1000)

        // Set up native bottom navigation menu, top toolbar & language dropdown
        setupBottomNavigation()
        setupLanguageDropdown()

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Load requested tab or HomeFragment as default landing screen
        if (savedInstanceState == null) {
            val selectedTabId = intent.getIntExtra("select_tab_id", R.id.nav_home)
            binding.bottomNavigation.selectedItemId = selectedTabId
        }
    }

    data class LanguageOption(val code: String, val nativeName: String)

    private val languages = listOf(
        LanguageOption("en", "English"),
        LanguageOption("hi", "हिंदी"),
        LanguageOption("mr", "मराठी"),
        LanguageOption("pa", "ਪੰਜਾਬੀ"),
        LanguageOption("ta", "தமிழ்"),
        LanguageOption("ml", "മലയാളം"),
        LanguageOption("bn", "বাংলা"),
        LanguageOption("kn", "कन्नड"),
        LanguageOption("or", "ଓଡ଼ିଆ"),
        LanguageOption("as", "असमिया"),
        LanguageOption("gu", "ગુજરાતી"),
        LanguageOption("te", "తెలుగు")
    )

    private fun setupLanguageDropdown() {
        updateLanguageDropdownText()

        binding.btnLanguageDropdown.setOnClickListener { anchorView ->
            val context = this
            val density = resources.displayMetrics.density
            val popupWidth = (145 * density).toInt()

            val scrollView = ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }

            val popupView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_compact_popup)
                val pVert = (4 * density).toInt()
                val pHoriz = (2 * density).toInt()
                setPadding(pHoriz, pVert, pHoriz, pVert)
            }
            scrollView.addView(popupView)

            val popupWindow = PopupWindow(
                scrollView,
                popupWidth,
                (280 * density).toInt(),
                true
            ).apply {
                elevation = 12 * density
                isOutsideTouchable = true
                isFocusable = true
            }

            val currentLangCode = LocaleHelper.getPersistedLanguage(this)

            for ((index, lang) in languages.withIndex()) {
                if (index > 0) {
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (1 * density).toInt()
                        ).apply {
                            val mH = (6 * density).toInt()
                            setMargins(mH, 0, mH, 0)
                        }
                        setBackgroundColor(Color.parseColor("#323236"))
                    }
                    popupView.addView(divider)
                }

                val isSelected = lang.code == currentLangCode

                val row = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    val padH = (12 * density).toInt()
                    val padV = (10 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                    isClickable = true
                    isFocusable = true
                }

                val tv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = lang.nativeName
                    setTextColor(if (isSelected) Color.parseColor("#81C784") else Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }

                row.addView(tv)

                if (isSelected) {
                    val checkIv = ImageView(context).apply {
                        val size = (16 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        setImageResource(R.drawable.ic_check)
                        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(Color.parseColor("#81C784")))
                    }
                    row.addView(checkIv)
                }

                row.setOnClickListener {
                    popupWindow.dismiss()
                    if (lang.code != currentLangCode) {
                        LocaleHelper.setLocale(this, lang.code)
                        updateLanguageDropdownText()

                        val currentTabId = binding.bottomNavigation.selectedItemId
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("select_tab_id", currentTabId)
                            putExtra("skip_splash", true)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }

                popupView.addView(row)
            }

            val anchorWidth = if (anchorView.width > 0) anchorView.width else (80 * density).toInt()
            val xOffset = -(popupWidth - anchorWidth)
            popupWindow.showAsDropDown(anchorView, xOffset, (4 * density).toInt())
        }
    }

    private fun updateLanguageDropdownText() {
        val currentCode = LocaleHelper.getPersistedLanguage(this)
        val match = languages.firstOrNull { it.code == currentCode } ?: languages[0]
        binding.btnLanguageDropdown.text = "${match.nativeName} ▾"
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navClickCount++

            val targetFragment: Fragment? = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_news -> NewsFragment()
                R.id.nav_mandi -> MandiFragment()
                R.id.nav_weather -> WeatherFragment()
                R.id.nav_forum -> ForumFragment()
                else -> null
            }

            targetFragment?.let { fragment ->
                val currentTime = System.currentTimeMillis()
                if (navClickCount >= 3 && interstitialAd != null && currentTime - lastInterstitialTime >= MIN_INTERSTITIAL_GAP_MS) {
                    navClickCount = 0
                    showInterstitialAdWithNavigation(fragment)
                } else {
                    if (navClickCount >= 3) navClickCount = 0
                    replaceFragment(fragment)
                }
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    fun tryShowInterstitialAd(onComplete: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (interstitialAd != null && currentTime - lastInterstitialTime >= MIN_INTERSTITIAL_GAP_MS) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialTime = System.currentTimeMillis()
                    loadInterstitialAd()
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    lastInterstitialTime = System.currentTimeMillis()
                    loadInterstitialAd()
                    onComplete()
                }
            }
            interstitialAd?.show(this)
        } else {
            onComplete()
            if (interstitialAd == null) {
                loadInterstitialAd()
            }
        }
    }

    fun showInterstitialAdWithNavigation(destinationFragment: Fragment) {
        tryShowInterstitialAd {
            replaceFragment(destinationFragment)
        }
    }

    private fun loadBannerAd() {
        try {
            binding.adContainer.visibility = View.GONE
            val adView = AdView(this).apply {
                adUnitId = "ca-app-pub-3054731531860427/9592937417"
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, getAdWidth())
                setAdSize(adSize)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("AdMob", "Banner Ad Loaded Successfully!")
                        binding.adContainer.visibility = View.VISIBLE
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdMob", "Banner Ad Failed to Load: ${error.message} (Code: ${error.code})")
                        binding.adContainer.visibility = View.GONE
                    }
                }
            }
            binding.adContainer.removeAllViews()
            binding.adContainer.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAdWidth(): Int {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        return (displayMetrics.widthPixels / density).toInt()
    }

    private fun loadInterstitialAd() {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                this,
                INTERSTITIAL_AD_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d("AdMob", "Interstitial Ad Loaded Successfully!")
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdMob", "Interstitial Ad Failed: ${loadAdError.message}")
                        interstitialAd = null
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            interstitialAd = null
        }
    }

    private fun loadRewardedAd() {
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                this,
                REWARDED_AD_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d("AdMob", "Rewarded Ad Loaded Successfully!")
                        rewardedAd = ad
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdMob", "Rewarded Ad Failed to Load: ${loadAdError.message}")
                        rewardedAd = null
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            rewardedAd = null
        }
    }

    private fun setupOnBackPressedDispatcher() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                if (currentFragment !is HomeFragment) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.CAMERA)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun setupSplashAnimation() {
        binding.extendedSplashScreen.visibility = View.VISIBLE

        // Reset views for entrance animation
        binding.tvSplashFor.alpha = 0f
        binding.tvSplashFor.translationY = -25f

        binding.tvSplashSmart.alpha = 0f
        binding.tvSplashSmart.scaleX = 0.5f
        binding.tvSplashSmart.scaleY = 0.5f

        binding.tvSplashFarmers.alpha = 0f
        binding.tvSplashFarmers.scaleX = 0.5f
        binding.tvSplashFarmers.scaleY = 0.5f

        // Staggered Entrance Animations:
        // 1. "for" glides down
        binding.tvSplashFor.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 2. "SMART" pops in with overshoot
        binding.tvSplashSmart.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(120L)
            .setDuration(550L)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        // 3. "FARMERS" pops in with overshoot
        binding.tvSplashFarmers.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(260L)
            .setDuration(550L)
            .setInterpolator(OvershootInterpolator(1.3f))
            .withEndAction {
                startTextPulseAnimation()
            }
            .start()

        // Animated dots for "Loading..."
        var dotCount = 0
        val dotsRunnable = object : Runnable {
            override fun run() {
                if (binding.extendedSplashScreen.visibility == View.VISIBLE) {
                    dotCount = (dotCount % 3) + 1
                    val dots = ".".repeat(dotCount)
                    binding.tvSplashLoadingText.text = "Loading$dots"
                    splashHandler.postDelayed(this, 400L)
                }
            }
        }
        splashDotsRunnable = dotsRunnable
        splashHandler.post(dotsRunnable)

        // Wait 2.5 seconds, then Slide Up our Extended Splash Screen
        binding.root.postDelayed({
            stopSplashAnimations()
            binding.extendedSplashScreen.animate()
                .translationY(-binding.extendedSplashScreen.height.toFloat())
                .alpha(0f)
                .setDuration(600L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    binding.extendedSplashScreen.visibility = View.GONE
                }
                .start()
        }, 2500L)
    }

    private fun startTextPulseAnimation() {
        if (binding.extendedSplashScreen.visibility != View.VISIBLE) return
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f, 1f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f, 1f)
        val alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.88f, 1f)

        splashPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.layoutSmartFarmers,
            scaleXHolder,
            scaleYHolder,
            alphaHolder
        ).apply {
            duration = 1100L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopSplashAnimations() {
        splashPulseAnimator?.cancel()
        splashPulseAnimator = null
        splashDotsRunnable?.let { splashHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        stopSplashAnimations()
        super.onDestroy()
    }
}