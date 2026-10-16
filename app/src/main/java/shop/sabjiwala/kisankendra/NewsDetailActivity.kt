package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.ads.nativetemplates.TemplateView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.AdminCreateNewsActivity
import shop.sabjiwala.kisankendra.CompactNewsAdapter
import shop.sabjiwala.kisankendra.R
import shop.sabjiwala.kisankendra.databinding.ActivityNewsDetailBinding
import shop.sabjiwala.kisankendra.model.NewsItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.NewsCacheHelper
import shop.sabjiwala.kisankendra.utils.RichTextHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class NewsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsDetailBinding
    private var isUserAdmin = false
    private var articleId: String = ""
    private var articleTitle: String = ""
    private var articleCategory: String = ""
    private var articleDesc: String = ""
    private var articleImageUrl: String = ""
    private var articleIsPinned: Boolean = false

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_IS_PINNED = "extra_is_pinned"

        fun start(
            context: Context,
            title: String,
            desc: String,
            category: String,
            imageUrl: String,
            date: String,
            id: String = "",
            isPinned: Boolean = false
        ) {
            val intent = Intent(context, NewsDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DESC, desc)
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_IMAGE_URL, imageUrl)
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_IS_PINNED, isPinned)
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
        binding = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        bindArticleData()
        loadAllArticleAds()
        setupShareFab()
        checkAdminRole()
    }

    override fun onResume() {
        super.onResume()
        if (articleId.isNotBlank()) {
            fetchArticleFromFirestore()
        }
    }

    private fun fetchArticleFromFirestore() {
        FirebaseFirestore.getInstance().collection("news").document(articleId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    articleTitle = doc.getString("title").orEmpty()
                    articleDesc = doc.getString("description").orEmpty()
                    articleCategory = doc.getString("category").orEmpty()
                    articleImageUrl = doc.getString("imageUrl").orEmpty()
                    articleIsPinned = doc.getBoolean("isPinned") ?: false

                    binding.tvDetailTitle.text = articleTitle
                    displayDescription(articleDesc)
                    binding.tvDetailCategory.text = articleCategory.ifBlank { "Agriculture" }

                    if (articleImageUrl.isNotBlank()) {
                        binding.ivDetailImage.visibility = View.VISIBLE
                        Glide.with(this)
                            .load(articleImageUrl)
                            .centerCrop()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.stat_notify_error)
                            .into(binding.ivDetailImage)
                    } else {
                        binding.ivDetailImage.visibility = View.GONE
                    }
                }
            }
    }

    private fun checkAdminRole() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val role = userDoc.getString("role")
                val isAdmin = userDoc.getBoolean("isAdmin") ?: false
                if (role.equals("admin", ignoreCase = true) || isAdmin) {
                    isUserAdmin = true
                    invalidateOptionsMenu()
                } else {
                    checkAdminCollectionFallback(uid)
                }
            }
            .addOnFailureListener {
                checkAdminCollectionFallback(uid)
            }
    }

    private fun checkAdminCollectionFallback(uid: String) {
        FirebaseFirestore.getInstance().collection("admin").document(uid).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc.exists()) {
                    isUserAdmin = true
                    invalidateOptionsMenu()
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isUserAdmin && articleId.isNotBlank()) {
            menuInflater.inflate(R.menu.menu_news_detail, menu)
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_news -> {
                editNewsArticle()
                true
            }
            R.id.action_delete_news -> {
                confirmDeleteArticle()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editNewsArticle() {
        val intent = Intent(this, AdminCreateNewsActivity::class.java).apply {
            putExtra(AdminCreateNewsActivity.EXTRA_EDIT_NEWS_ID, articleId)
            putExtra(AdminCreateNewsActivity.EXTRA_TITLE, articleTitle)
            putExtra(AdminCreateNewsActivity.EXTRA_CATEGORY, articleCategory)
            putExtra(AdminCreateNewsActivity.EXTRA_DESCRIPTION, articleDesc)
            putExtra(AdminCreateNewsActivity.EXTRA_IMAGE_URL, articleImageUrl)
            putExtra(AdminCreateNewsActivity.EXTRA_IS_PINNED, articleIsPinned)
        }
        startActivity(intent)
    }

    private fun confirmDeleteArticle() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete News Article?")
            .setMessage("Are you sure you want to delete this article? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (articleId.isNotBlank()) {
                    FirebaseFirestore.getInstance().collection("news").document(articleId).delete()
                        .addOnSuccessListener {
                            NewsCacheHelper.clearCache(this)
                            Toast.makeText(this, "Article deleted successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetail)
        binding.toolbarDetail.setNavigationOnClickListener { finish() }
    }

    private fun setupShareFab() {
        val btnShareNewsDetail = findViewById<View>(R.id.btnShareNewsDetail)
        val viewPulseRingNewsDetail = findViewById<View>(R.id.viewPulseRingNewsDetail)
        MediaUtils.startCompactGreenPulseAnimation(viewPulseRingNewsDetail)
        btnShareNewsDetail.setOnClickListener {
            val title = binding.tvDetailTitle.text.toString()
            val rawDesc = intent.getStringExtra(EXTRA_DESC).orEmpty()
            val paragraphs = rawDesc.split("\n\n").filter { it.isNotBlank() }
            val summaryParagraphs = paragraphs.take(3).joinToString("\n\n")
            val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
            val appLink = "https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"
            val shareText = "📰 *$title*\n\n$summaryParagraphs\n\n📥 Download Kisan Kendra App for more:\n$appLink"

            lifecycleScope.launch(Dispatchers.IO) {
                var imageUri: Uri? = null
                if (imageUrl.isNotBlank()) {
                    try {
                        val cachedFile = Glide.with(this@NewsDetailActivity)
                            .asFile()
                            .load(imageUrl)
                            .submit()
                            .get()
                        val jpegFile = File(cacheDir, "shared_news_detail_${System.currentTimeMillis()}.jpg")
                        cachedFile.copyTo(jpegFile, overwrite = true)
                        imageUri = FileProvider.getUriForFile(this@NewsDetailActivity, "${packageName}.fileprovider", jpegFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            if (imageUri != null) {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, imageUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } else {
                                type = "text/plain"
                            }
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share News Article via")
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(chooser)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@NewsDetailActivity, "Could not share article", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadAllArticleAds() {
        binding.btnCloseTopAd.setOnClickListener {
            binding.cardTopArticleAd.visibility = View.GONE
        }
        loadNativeAdIntoContainer(binding.adTopArticleTemplate, binding.cardTopArticleAd)
        loadNativeAdIntoContainer(binding.adInArticleTemplate, binding.adInArticleTemplate.parent?.parent as? View)
        loadNativeAdIntoContainer(binding.adMidArticleTemplate, binding.cardMidArticleAd)
        loadNativeAdIntoContainer(binding.adBottomArticleTemplate, binding.adBottomArticleTemplate.parent?.parent as? View)
    }

    private fun loadNativeAdIntoContainer(templateView: TemplateView, cardView: View?) {
        try {
            cardView?.visibility = View.GONE
            val adLoader = AdLoader.Builder(this, "ca-app-pub-3054731531860427/8792970999")
                .forNativeAd { nativeAd ->
                    templateView.setNativeAd(nativeAd)
                    cardView?.visibility = View.VISIBLE
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        cardView?.visibility = View.GONE
                    }
                })
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
            cardView?.visibility = View.GONE
        }
    }

    private fun displayDescription(desc: String) {
        val paragraphs = desc.split("\n\n").filter { it.isNotBlank() }
        if (paragraphs.size >= 3) {
            val mid = paragraphs.size / 2
            val part1 = paragraphs.subList(0, mid).joinToString("\n\n")
            val part2 = paragraphs.subList(mid, paragraphs.size).joinToString("\n\n")
            binding.tvDetailDescriptionPart1.text = RichTextHelper.formatRichText(part1)
            binding.tvDetailDescriptionPart1.movementMethod = LinkMovementMethod.getInstance()
            binding.tvDetailDescriptionPart2.text = RichTextHelper.formatRichText(part2)
            binding.tvDetailDescriptionPart2.movementMethod = LinkMovementMethod.getInstance()
            binding.tvDetailDescriptionPart2.visibility = View.VISIBLE
            binding.cardMidArticleAd.visibility = View.VISIBLE
        } else {
            binding.tvDetailDescriptionPart1.text = RichTextHelper.formatRichText(desc)
            binding.tvDetailDescriptionPart1.movementMethod = LinkMovementMethod.getInstance()
            binding.tvDetailDescriptionPart2.visibility = View.GONE
            binding.cardMidArticleAd.visibility = View.GONE
        }
    }

    private fun bindArticleData() {
        articleId = intent.getStringExtra(EXTRA_ID).orEmpty()
        articleTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        articleDesc = intent.getStringExtra(EXTRA_DESC).orEmpty()
        articleCategory = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        articleImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        articleIsPinned = intent.getBooleanExtra(EXTRA_IS_PINNED, false)
        val date = intent.getStringExtra(EXTRA_DATE).orEmpty()

        binding.tvDetailTitle.text = articleTitle
        displayDescription(articleDesc)
        binding.tvDetailCategory.text = articleCategory.ifBlank { "Agriculture" }
        binding.tvDetailDate.text = if (date.isNotBlank()) "📅 $date" else "📅 Today"

        val activeLang = LocaleHelper.getPersistedLanguage(this)
        if (activeLang != "hi") {
            lifecycleScope.launch(Dispatchers.Main) {
                if (articleTitle.isNotBlank()) {
                    val translatedTitle = TranslationHelper.translateText(this@NewsDetailActivity, articleTitle, activeLang)
                    binding.tvDetailTitle.text = translatedTitle
                }

                if (articleDesc.isNotBlank()) {
                    val translatedDesc = TranslationHelper.translateText(this@NewsDetailActivity, articleDesc, activeLang)
                    displayDescription(translatedDesc)
                }

                if (articleCategory.isNotBlank()) {
                    binding.tvDetailCategory.text = articleCategory
                }

                val translatedRel = TranslationHelper.translateText(this@NewsDetailActivity, "📌 संबंधित खबरें", activeLang)
                binding.tvRelatedNewsHeader.text = translatedRel

                val translatedLat = TranslationHelper.translateText(this@NewsDetailActivity, "🔥 ताज़ा ख़बरें", activeLang)
                binding.tvLatestNewsHeader.text = translatedLat
            }
        }

        if (articleImageUrl.isNotBlank()) {
            binding.ivDetailImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(articleImageUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.stat_notify_error)
                .into(binding.ivDetailImage)
        } else {
            binding.ivDetailImage.visibility = View.GONE
        }

        fetchRelatedAndLatestNews(articleId, articleCategory, articleTitle)
    }

    private fun fetchRelatedAndLatestNews(currentArticleId: String, currentCategory: String, currentTitle: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("news")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val allNews = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(NewsItem::class.java)?.let { rawItem ->
                        if (rawItem.id.isBlank()) rawItem.copy(id = doc.id) else rawItem
                    }
                }

                val otherNews = allNews.filter { item ->
                    (currentArticleId.isNotBlank() && item.id != currentArticleId) ||
                    (currentArticleId.isBlank() && !item.title.equals(currentTitle, ignoreCase = true))
                }

                val targetCategory = currentCategory.trim()
                val relatedNews = otherNews.filter { item ->
                    val cat = item.category.trim()
                    cat.equals(targetCategory, ignoreCase = true) ||
                    (targetCategory.contains("Yojana", ignoreCase = true) && (cat.contains("Scheme", ignoreCase = true) || cat.contains("Yojana", ignoreCase = true))) ||
                    (targetCategory.contains("Scheme", ignoreCase = true) && (cat.contains("Scheme", ignoreCase = true) || cat.contains("Yojana", ignoreCase = true)))
                }.take(5)

                if (relatedNews.isNotEmpty()) {
                    binding.layoutRelatedNewsSection.visibility = View.VISIBLE
                    binding.rvRelatedNews.layoutManager = LinearLayoutManager(this)
                    binding.rvRelatedNews.adapter = CompactNewsAdapter(relatedNews) { selectedItem ->
                        openNewsDetailItem(selectedItem)
                    }
                } else {
                    binding.layoutRelatedNewsSection.visibility = View.GONE
                }

                val relatedIds = relatedNews.map { it.id }.toSet()
                val latestNews = otherNews.filter { it.id !in relatedIds }.take(5)

                if (latestNews.isNotEmpty()) {
                    binding.layoutLatestNewsSection.visibility = View.VISIBLE
                    binding.rvLatestNews.layoutManager = LinearLayoutManager(this)
                    binding.rvLatestNews.adapter = CompactNewsAdapter(latestNews) { selectedItem ->
                        openNewsDetailItem(selectedItem)
                    }
                } else {
                    binding.layoutLatestNewsSection.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                binding.layoutRelatedNewsSection.visibility = View.GONE
                binding.layoutLatestNewsSection.visibility = View.GONE
            }
    }

    private fun openNewsDetailItem(item: NewsItem) {
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val formattedDate = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""

        start(
            context = this,
            title = item.title,
            desc = item.description,
            category = item.category.ifBlank { "Agriculture" },
            imageUrl = item.imageUrl,
            date = formattedDate,
            id = item.id
        )
    }

    override fun onDestroy() {
        try {
            binding.adTopArticleTemplate.destroyNativeAd()
            binding.adInArticleTemplate.destroyNativeAd()
            binding.adMidArticleTemplate.destroyNativeAd()
            binding.adBottomArticleTemplate.destroyNativeAd()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
