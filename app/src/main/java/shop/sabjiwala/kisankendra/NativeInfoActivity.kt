package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import shop.sabjiwala.kisankendra.databinding.ActivityNativeInfoBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class NativeInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNativeInfoBinding

    companion object {
        const val EXTRA_INFO_TYPE = "info_type"
        const val TYPE_ABOUT = "about"
        const val TYPE_CONTACT = "contact"
        const val TYPE_PRIVACY = "privacy"

        fun start(context: Context, type: String) {
            val intent = Intent(context, NativeInfoActivity::class.java).apply {
                putExtra(EXTRA_INFO_TYPE, type)
            }
            context.startActivity(intent)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNativeInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        loadPageContent()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarNativeInfo)
        binding.toolbarNativeInfo.setNavigationOnClickListener { finish() }
    }

    private fun loadPageContent() {
        val type = intent.getStringExtra(EXTRA_INFO_TYPE) ?: TYPE_ABOUT

        when (type) {
            TYPE_CONTACT -> setupContactUsPage()
            TYPE_PRIVACY -> setupPrivacyPolicyPage()
            else -> setupAboutUsPage()
        }
    }

    private fun setupAboutUsPage() {
        binding.toolbarNativeInfo.title = "About Us"
        binding.tvPageTitle.text = "🌾 About Kisan Kendra"

        val content = """
            Welcome to Kisan Kendra — Empowering Indian Farmers with Smart Agriculture Technology.

            Our Mission:
            Kisan Kendra is a comprehensive digital farming ecosystem designed to bridge the information gap for farmers across India. We deliver real-time agricultural market prices, 30-day rate trend graphs, hyper-local weather forecasts, interactive farm management tools, government scheme guides, and a thriving farmer community directly to your smartphone in 12 Indian regional languages.

            Key Features & Modules:

            1. 🛠️ Kisan Toolkit & Kisan Diary:
            • Day-by-Day Crop Expense & Income Ledger: Track input costs (seeds, fertilizer, labor, diesel) vs. Mandi sales income per crop season.
            • Crop Profitability Analytics Dashboard: Compare net profit/loss and profit per acre across crops (Wheat, Soybean, Cotton, Tomato, etc.).
            • Fertilizer & Seed Quantity Calculator: Instant N-P-K (Urea, DAP, NPK, Zinc) and seed quantity recommendations for 10 major crops across Acres, Bigha, and Hectares.
            • Crop Disease & Pest Diagnostic Guide: Visual pest/disease selector with safe chemical spray remedies and tank dosage.

            2. 🌾 Mandi Rates & 30-Day Trend Charts:
            • APMC Mandi Prices: Daily crop market rates from APMC government markets across all Indian states and districts.
            • 30-Day Highest Price Graph: Interactive trend line charts tracking the daily highest rates (Max Price) with touch inspection tooltips.
            • Multi-lingual Voice Search: Speak crop or mandi names in Hindi or English to search instantly across India.

            3. 👨‍🌾 Farmer Community Forum:
            • Share Crop Photos, Camera Shots & Videos: Post questions, crop disease photos, and reels to get expert advice from experienced farmers.
            • Likes, Comments & Moderation: Engage with fellow farmers across India with full content moderation.

            4. ⛅ Weather & Local Farm Advisories:
            • Hyper-local village forecasts, rain probability, wind speed, spray advisories, and irrigation guidance.

            5. 📜 Government Schemes & Direct Subsidies:
            • Central & State-filtered knowledge base for PM-Kisan, PMFBY, KCC, SMAM Tractor Subsidy, Drip Irrigation, and state schemes with linked official guides and government portal links.

            6. 🌐 Regional Multi-Language Support:
            • Live dynamic translation and support across 12 regional languages: Hindi, Marathi, Punjabi, Tamil, Malayalam, Bengali, Kannada, Odia, Assamese, Gujarati, Telugu, and English.

            Version: 1.2 (Build 10)
            Connecting Farmers • Empowering Agriculture • Strengthening India
        """.trimIndent()

        binding.tvPageContent.text = content
        binding.btnPageAction.visibility = View.GONE
    }

    private fun setupContactUsPage() {
        binding.toolbarNativeInfo.title = "Contact Us"
        binding.tvPageTitle.text = "📞 Contact & Farmer Helpdesk"

        val content = """
            Have questions, feedback, or need help with Kisan Kendra? We are here to support you!

            Official Support Email:
            shailwork24@gmail.com

            Working Hours:
            Monday – Saturday (9:00 AM – 6:00 PM IST)

            How we can help:
            • Reporting incorrect Mandi prices or missing markets.
            • Inquiries regarding government scheme updates.
            • App feedback and feature suggestions.
            • Account support and data requests.

            Tap the button below to send an email directly to our support team.
        """.trimIndent()

        binding.tvPageContent.text = content
        binding.btnPageAction.visibility = View.VISIBLE
        binding.btnPageAction.text = "✉️ Send Email to shailwork24@gmail.com"

        binding.btnPageAction.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:shailwork24@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Kisan Kendra Farmer Query & Support")
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Email: shailwork24@gmail.com", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupPrivacyPolicyPage() {
        binding.toolbarNativeInfo.title = "Privacy Policy"
        binding.tvPageTitle.text = "🔒 Privacy Policy for Kisan Kendra"

        val content = """
            Effective Date: September 2026

            At Kisan Kendra ("we", "our", "us"), protecting the privacy of our farmers and app users is our top priority. This Privacy Policy explains how we collect, use, protect, and manage your information when you use our mobile application.

            1. Information We Collect
            • Account & Profile Information: When you register or sign in (via Google, Phone OTP, Email, or Guest mode), we collect basic details such as your name, email address, phone number, and optional profile picture.
            • Photos, Videos & Media: When you post crop questions in the Farmer Community or set a profile picture, we request access to your device camera and gallery to capture or upload photos and videos.
            • Voice & Audio: We request microphone access to enable multi-lingual voice search for Mandi rates and audio recording for community posts.
            • Kisan Diary & Farm Data: Expense and income records, crop details, and land measurements you enter are stored privately in your account to calculate your farm profit and loss.
            • Location Data: With your permission, we access your device location strictly to display nearby Mandi market prices, local weather forecasts, and agricultural advisories. We do not sell or track your location.

            2. How We Use Your Information
            • To provide location-specific Mandi market prices, price trends, and weather forecasts.
            • To calculate crop expenses, fertilizer requirements, and profit/loss analytics.
            • To enable community sharing of crop disease photos, videos, and farming advice.
            • To manage your user account, secure your data, and provide customer support.

            3. Third-Party Services & Advertising
            We work with trusted third-party service providers to support app operations:
            • Advertising Partners: To show advertisements (such as banner, video, and rewarded ads) within the app.
            • Cloud & Analytics Services: To securely store your data and analyze anonymous usage performance to improve the app.
            • Weather & Translation Services: To deliver accurate local weather forecasts and translate content into 12 Indian regional languages.
            • Government Data Disclaimer: Market prices and scheme information are sourced from public open data portals (data.gov.in, pmkisan.gov.in). Kisan Kendra is an independent app and is not affiliated with, endorsed by, or representing any government entity.

            4. Content Guidelines & Moderation
            We enforce a zero-tolerance policy for objectionable or abusive content in our Farmer Community:
            • Reporting Content: You can report inappropriate posts or comments using the "Report" option. Reports are reviewed by our moderation team within 24 hours.
            • Blocking Users: You can block abusive users at any time. Blocked users' content will be immediately hidden from your view.
            • Content Removal: Violating content is promptly removed to maintain a safe and helpful environment for all farmers.

            5. Data Security & Protection
            Your data is transmitted securely using standard encryption technology and stored on secure servers with strict access controls.

            6. Account Deletion & Your Rights
            You have full control over your personal data. You can delete your account and associated records at any time:
            • In-App Deletion: Go to Settings ➔ Account Section ➔ Delete Account.
            • Web Account Deletion Request: You may also request permanent account and data deletion by visiting "Account Deletion Request" at https://sites.google.com/view/kisankendra/delete-account
            • Direct Email Request: Email us at shailwork24@gmail.com with the subject "Account Deletion Request".

            7. Contact Us
            If you have any questions or privacy concerns, please contact our support team at:
            Email: shailwork24@gmail.com
        """.trimIndent()

        binding.tvPageContent.text = content
        binding.btnPageAction.visibility = View.GONE
    }
}
