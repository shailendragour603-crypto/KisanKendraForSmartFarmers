package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import shop.sabjiwala.kisankendra.databinding.ActivityOnboardingBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        fun isOnboardingCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }

        fun setOnboardingCompleted(context: Context, completed: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
        }
    }

    data class SlideItem(
        val graphicLayoutRes: Int,
        val title: String,
        val desc: String,
        val featureBadges: List<String>
    )

    private val slides = listOf(
        SlideItem(
            R.layout.graphic_onboarding_mandi,
            "Real-Time Mandi Rates & Price Trends",
            "Get live APMC Mandi commodity prices from 1000+ mandis across India with 7-day price forecasts to maximize your crop income.",
            listOf("📍 1000+ Mandis Live", "📈 7-Day Price Trends", "🔔 Price Surge Alerts")
        ),
        SlideItem(
            R.layout.graphic_onboarding_khata,
            "Kisan Diary & Crop Profitability",
            "Track farm income & expenses (Seeds, Labour, Machinery, Fertilizer) with automated crop accounting and net-profit reports.",
            listOf("📊 Crop Account Book", "🌱 Expense Manager", "💰 Net Profit Report")
        ),
        SlideItem(
            R.layout.graphic_onboarding_tools,
            "Smart Agri Tools & Sarkari Yojana",
            "Calculate exact fertilizer dosage (NPK/Urea/DAP), check PM-Kisan government subsidy eligibility, and diagnose crop diseases.",
            listOf("🧮 Fertilizer Calculator", "🏛️ PM-Kisan Subsidies", "🌾 Disease Guide")
        ),
        SlideItem(
            R.layout.graphic_onboarding_community,
            "Kisan Manch Community & Weather Radar",
            "Connect with 10,000+ farmers across India, share crop field videos, ask questions, and get hyper-local weather radar & rain alerts.",
            listOf("👨‍🌾 10,000+ Farmers", "🎥 Field Photo/Video", "☀️ Weather Radar")
        )
    )

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isOnboardingCompleted(this)) {
            val nextIntent = if (FirebaseAuth.getInstance().currentUser != null || SignInActivity.isGuestUser(this)) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, SignInActivity::class.java)
            }
            startActivity(nextIntent)
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        setupViewPager()
        setupListeners()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(slides)
        binding.viewPagerOnboarding.adapter = adapter
        setupDots(0)

        // Custom smooth depth & scale page transformer
        binding.viewPagerOnboarding.setPageTransformer { page, position ->
            when {
                position < -1 -> {
                    page.alpha = 0f
                }
                position <= 0 -> {
                    page.alpha = 1f
                    page.translationX = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                }
                position <= 1 -> {
                    page.alpha = 1f - position * 0.3f
                    page.translationX = page.width * -position * 0.2f
                    val scale = 0.88f + (1f - position) * 0.12f
                    page.scaleX = scale
                    page.scaleY = scale
                }
                else -> {
                    page.alpha = 0f
                }
            }
        }

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setupDots(position)
                if (position == slides.size - 1) {
                    binding.btnOnboardingNext.text = "Get Started"
                } else {
                    binding.btnOnboardingNext.text = "Next"
                }
            }
        })
    }

    private fun setupDots(currentIndex: Int) {
        binding.layoutDots.removeAllViews()
        val density = resources.displayMetrics.density
        for (i in slides.indices) {
            val dot = View(this).apply {
                val isCurrent = (i == currentIndex)
                val dotWidth = if (isCurrent) (28 * density).toInt() else (8 * density).toInt()
                val dotHeight = (8 * density).toInt()

                val params = LinearLayout.LayoutParams(dotWidth, dotHeight).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }
                layoutParams = params

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * density
                    if (isCurrent) {
                        setColor(ContextCompat.getColor(context, R.color.farmer_green_primary))
                    } else {
                        setColor(ContextCompat.getColor(context, R.color.green_light))
                    }
                }
            }
            binding.layoutDots.addView(dot)
        }
    }

    private fun setupListeners() {
        binding.btnOnboardingSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.btnOnboardingNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem < slides.size - 1) {
                binding.viewPagerOnboarding.currentItem = currentItem + 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun finishOnboarding() {
        setOnboardingCompleted(this, true)
        val intent = Intent(this, SignInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    class OnboardingAdapter(private val slides: List<SlideItem>) :
        RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

        class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val flGraphic: FrameLayout = view.findViewById(R.id.flOnboardingGraphic)
            val tvTitle: TextView = view.findViewById(R.id.tvOnboardingTitle)
            val tvDesc: TextView = view.findViewById(R.id.tvOnboardingDesc)
            val layoutBadges: LinearLayout = view.findViewById(R.id.layoutFeatureBadges)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            return SlideViewHolder(view)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val slide = slides[position]

            // Inflate rich graphic preview layout
            holder.flGraphic.removeAllViews()
            LayoutInflater.from(holder.itemView.context).inflate(slide.graphicLayoutRes, holder.flGraphic, true)

            holder.tvTitle.text = slide.title
            holder.tvDesc.text = slide.desc

            // Populate feature highlight badges
            holder.layoutBadges.removeAllViews()
            val density = holder.itemView.resources.displayMetrics.density
            for (badgeText in slide.featureBadges) {
                val tvBadge = TextView(holder.itemView.context).apply {
                    text = badgeText
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.farmer_green_dark))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_badge_green)
                    setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    }
                }
                holder.layoutBadges.addView(tvBadge)
            }
        }

        override fun getItemCount(): Int = slides.size
    }
}
