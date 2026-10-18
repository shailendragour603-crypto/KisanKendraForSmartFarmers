package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
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

    private class SavedUrlSpan(val url: String, val start: Int, val end: Int, val flags: Int)

    private fun setFormattedText(textView: TextView, rawText: String) {
        var processedText = rawText

        // 1. Unescape HTML entities if escaped
        if (processedText.contains("&lt;")) {
            processedText = processedText
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
        }

        // 2. Preserve line breaks so paragraphs do not collapse into a single block
        processedText = processedText.replace("\n", "<br>")

        // 3. Convert HTML tags (like <a href="...">, <b>, <i>) to Spanned
        val spanned = HtmlCompat.fromHtml(processedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val spannable = SpannableStringBuilder(spanned)

        // 4. SAVE URLSpans BEFORE Linkify runs (because Linkify wipes existing URLSpans)
        val existingSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
        val savedSpans = existingSpans.map { span ->
            SavedUrlSpan(
                url = span.url,
                start = spannable.getSpanStart(span),
                end = spannable.getSpanEnd(span),
                flags = spannable.getSpanFlags(span)
            )
        }

        // 5. Apply auto-linking for plain URLs / emails
        Linkify.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)

        // 6. RESTORE HTML URLSpans using saved positions
        for (saved in savedSpans) {
            if (saved.start >= 0 && saved.end > saved.start) {
                spannable.setSpan(URLSpan(saved.url), saved.start, saved.end, saved.flags)
            }
        }

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun loadPageContent() {
        val type = intent.getStringExtra(EXTRA_INFO_TYPE) ?: TYPE_ABOUT

        when (type) {
            TYPE_CONTACT -> setupContactUsPage()
            TYPE_PRIVACY -> setupPrivacyPolicyPage()
            else -> setupAboutUsPage()
        }
    }

    private fun fetchRemoteContent(type: String, localTitle: String, localContent: String, localActionText: String? = null) {
        try {
            val docRef = FirebaseFirestore.getInstance().collection("app_pages").document(type)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val remoteContent = snapshot.getString("content")
                        val remoteTitle = snapshot.getString("title")
                        val remoteActionText = snapshot.getString("action_button_text")

                        if (!remoteContent.isNullOrBlank()) {
                            setFormattedText(binding.tvPageContent, remoteContent)
                        }
                        if (!remoteTitle.isNullOrBlank()) {
                            binding.tvPageTitle.text = remoteTitle
                        }
                        if (!remoteActionText.isNullOrBlank()) {
                            binding.btnPageAction.text = remoteActionText
                        }

                        // If remote content is completely blank, seed default content once
                        if (remoteContent.isNullOrBlank()) {
                            val updateData = hashMapOf<String, Any>(
                                "title" to localTitle,
                                "content" to localContent,
                                "updated_at" to System.currentTimeMillis()
                            )
                            if (!localActionText.isNullOrBlank()) {
                                updateData["action_button_text"] = localActionText
                            }
                            docRef.set(updateData)
                        }
                    } else {
                        // Document doesn't exist in Firebase yet. Auto-create it with default content!
                        val initialData = hashMapOf<String, Any>(
                            "title" to localTitle,
                            "content" to localContent,
                            "updated_at" to System.currentTimeMillis()
                        )
                        if (!localActionText.isNullOrBlank()) {
                            initialData["action_button_text"] = localActionText
                        }
                        docRef.set(initialData)
                    }
                }
                .addOnFailureListener {
                    // Silent fallback - local content is already displayed
                }
        } catch (_: Exception) {
            // Silent fallback
        }
    }

    private fun setupAboutUsPage() {
        binding.toolbarNativeInfo.title = "About Us"
        val titleText = "🌾 About Kisan Kendra"
        binding.tvPageTitle.text = titleText

        val content = """
            Welcome to Kisan Kendra — Empowering Indian Farmers with Smart Agriculture Technology.

            Our Mission:
            Kisan Kendra is a comprehensive digital farming ecosystem designed to bridge the information gap for farmers across India. We deliver real-time agricultural market prices, 30-day rate trend graphs, hyper-local weather forecasts, interactive farm management tools, government scheme guides, Kisan AI assistant, and a thriving farmer community directly to your smartphone in Hindi and English.

            Key Features & Modules:

            1. 🤖 Kisan AI — Smart Agricultural Assistant:
            • AI Crop & Plant Disease Advisor: Upload or snap photos of affected crops or leaves to get instant pest/disease diagnosis, root causes, and safe treatment recommendations.
            • Smart Farming Q&A: Ask any question about crop management, soil health, fertilizer dosages, Mandi trends, and seasonal farming advice in simple language.

            2. 🛠️ Kisan Toolkit & Kisan Diary:
            • Day-by-Day Crop Expense & Income Ledger: Track input costs (seeds, fertilizer, labor, diesel) vs. Mandi sales income per crop season.
            • Crop Profitability Analytics Dashboard: Compare net profit/loss and profit per acre across crops (Wheat, Soybean, Cotton, Tomato, etc.).
            • Fertilizer & Seed Quantity Calculator: Instant N-P-K (Urea, DAP, NPK, Zinc) and seed quantity recommendations across Acres, Bigha, and Hectares.
            • Crop Disease & Pest Diagnostic Guide: Visual pest/disease selector with safe chemical spray remedies and tank dosage.

            3. 🌾 Mandi Rates & 30-Day Trend Charts:
            • APMC Mandi Prices: Daily crop market rates from APMC government markets across all Indian states and districts.
            • 30-Day Highest Price Graph: Interactive trend line charts tracking the daily highest rates with easy-to-read details when you touch the screen.
            • Multi-lingual Voice Search: Speak crop or mandi names in Hindi or English to search instantly across India.

            4. 👨‍🌾 Farmer Community Forum:
            • Share Crop Photos & Videos: Post questions, crop disease photos, and videos to get expert advice from experienced farmers.
            • Likes, Comments & Moderation: Engage with fellow farmers across India with full content moderation.

            5. ⛅ Weather & Local Farm Advisories:
            • Hyper-local village forecasts, rain probability, wind speed, spray advisories, and irrigation guidance.

            6. 📜 Government Schemes & Direct Subsidies:
            • Central & State-filtered knowledge base for PM-Kisan, PMFBY, KCC, SMAM Tractor Subsidy, Drip Irrigation, and state schemes with linked official guides and government portal links.

            7. 🌐 Language Support:
            • Available in Hindi and English with simple navigation designed specifically for farmers.

            Connecting Farmers • Empowering Agriculture • Strengthening India
        """.trimIndent()

        setFormattedText(binding.tvPageContent, content)
        binding.btnPageAction.visibility = View.GONE

        fetchRemoteContent(TYPE_ABOUT, titleText, content)
    }

    private fun setupContactUsPage() {
        binding.toolbarNativeInfo.title = "Contact Us"
        val titleText = "📞 Contact & Farmer Helpdesk"
        binding.tvPageTitle.text = titleText

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

        val buttonText = "✉️ Send Email to shailwork24@gmail.com"
        setFormattedText(binding.tvPageContent, content)
        binding.btnPageAction.visibility = View.VISIBLE
        binding.btnPageAction.text = buttonText

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

        fetchRemoteContent(TYPE_CONTACT, titleText, content, buttonText)
    }

    private fun setupPrivacyPolicyPage() {
        binding.toolbarNativeInfo.title = "Privacy Policy"
        val titleText = "🔒 Privacy Policy for Kisan Kendra"
        binding.tvPageTitle.text = titleText

        val content = """
            Effective Date: September 2026

            At Kisan Kendra ("we", "our", "us"), protecting the privacy of our farmers and app users is our top priority. This Privacy Policy explains how we collect, use, protect, and manage your information when you use our mobile application.

            1. Information We Collect
            • Account & Profile Information: When you register or sign in (via your Google account, phone number, email, or guest login), we collect basic details such as your name, email address, phone number, and optional profile picture.
            • Photos, Videos & Media: When you ask questions to Kisan AI, post crop queries in the Farmer Community, or set a profile picture, we request access to your device camera and gallery to capture or upload photos and videos.
            • Voice & Audio: We request microphone access to enable multi-lingual voice search for Mandi rates and voice input for Kisan AI and community posts.
            • Kisan Diary & Farm Data: Expense and income records, crop details, and land measurements you enter are stored privately in your account to calculate your farm profit and loss.
            • Location Data: With your permission, we access your device location strictly to display nearby Mandi market prices, local weather forecasts, and agricultural advisories. We do not sell or track your location.

            2. How We Use Your Information
            • To provide Kisan AI crop disease diagnosis and agricultural advice.
            • To provide location-specific Mandi market prices, price trends, and weather forecasts.
            • To calculate crop expenses, fertilizer requirements, and profit/loss analytics.
            • To enable community sharing of crop disease photos, videos, and farming advice.
            • To manage your user account, secure your data, and provide customer support.

            3. Third-Party Services & Advertising
            We work with trusted third-party service providers to support app operations:
            • Advertising Partners: To show advertisements within the app.
            • Secure Data Storage & App Improvement Tools: To securely store your information and understand how the app is used so we can make it better.
            • Weather & Smart Advisory Services: To deliver accurate local weather forecasts and AI crop guidance in Hindi and English.
            • Government Data Disclaimer: Market prices and scheme information are sourced from public open data portals (data.gov.in, pmkisan.gov.in). Kisan Kendra is an independent app and is not affiliated with, endorsed by, or representing any government entity.

            4. Content Guidelines, Moderation & Copyright Notice
            We enforce a strict policy regarding user-uploaded content and copyright compliance:
            • User Responsibility for Media Uploads: Users are solely responsible for ensuring that crop photos, videos, and media they upload do not infringe on third-party copyrights, trademarks, or proprietary rights.
            • Reporting & DMCA Takedown Notice: If you believe any photo or video posted on Kisan Kendra infringes your copyright, please report it via the in-app "Report" button or email us directly at shailwork24@gmail.com with content details.
            • Takedown & Account Action: Confirmed infringing content will be removed within 24–48 hours, and repeat copyright offenders will have their community accounts suspended.
            • Blocking Abusive Users: Users can block abusive or offending community members at any time to hide their posts immediately.

            5. Data Security & Protection
            Your data is transmitted securely and stored safely with restricted access.

            6. Account Deletion & Your Rights
            You have full control over your personal data. You can delete your account and associated records at any time:
            • In-App Deletion: Go to Settings ➔ Account Section ➔ Delete Account.
            • Web Account Deletion Request: You may also request permanent account and data deletion by visiting <a href="https://sites.google.com/view/kisankendra/delete-account">Data Deletion Request</a>.
            • Direct Email Request: Email us at shailwork24@gmail.com with the subject "Account Deletion Request".

            7. Contact Us
            If you have any questions or privacy/copyright concerns, please contact our support team at:
            Email: shailwork24@gmail.com
        """.trimIndent()

        setFormattedText(binding.tvPageContent, content)
        binding.btnPageAction.visibility = View.GONE

        fetchRemoteContent(TYPE_PRIVACY, titleText, content)
    }
}
