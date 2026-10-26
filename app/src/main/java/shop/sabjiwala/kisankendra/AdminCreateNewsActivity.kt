package shop.sabjiwala.kisankendra

import android.R
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.firestore.SetOptions
import shop.sabjiwala.kisankendra.databinding.ActivityAdminCreateNewsBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.NewsCacheHelper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AdminCreateNewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminCreateNewsBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storageRef by lazy { FirebaseStorage.getInstance().reference }
    private var editNewsId: String? = null

    private val categories = listOf(
        "News",
        "Govt Scheme",
        "Agri Market",
        "Agri Gyan",
        "Crop Protection",
        "Technology",
        "Success Stories"
    )

    // Default image presets for quick selection
    private val imageYojana = "https://images.unsplash.com/photo-1532629345422-7515f3d16bb6?w=800"
    private val imageMandi = "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800"
    private val imageGyan = "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800"

    companion object {
        private const val PREFS_NAME = "admin_prefs"

        const val EXTRA_EDIT_NEWS_ID = "extra_edit_news_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_IS_PINNED = "extra_is_pinned"
    }

    private fun saveUriToInternalStorage(uri: Uri): Uri? {
        return try {
            if (uri.scheme == "file") return uri
            val inputStream = contentResolver.openInputStream(uri) ?: return MediaUtils.copyUriToAppCache(this, uri)
            val imagesDir = File(filesDir, "news_images").apply { if (!exists()) mkdirs() }
            val destFile = File(imagesDir, "news_${System.currentTimeMillis()}.jpg")
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            MediaUtils.copyUriToAppCache(this, uri)
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val savedUri = saveUriToInternalStorage(uri) ?: MediaUtils.copyUriToAppCache(this, uri) ?: uri
                binding.etImageUrl.setText(savedUri.toString())
                binding.ivImagePreview.visibility = View.VISIBLE
                Glide.with(this)
                    .load(savedUri)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_gallery)
                    .error(R.drawable.stat_notify_error)
                    .into(binding.ivImagePreview)
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCreateNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setupToolbar()
        setupCategoryDropdown()
        setupImagePreview()
        setupPresetChips()
        setupListeners()
        setupFocusListeners()
        checkEditModeIntent()
        shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.fetchRemoteApiKey(this)
    }

    private fun setupFocusListeners() {
        val fields = listOf(
            binding.etRawInput,
            binding.etNewsTitle,
            binding.actvCategory,
            binding.etNewsDescription,
            binding.etImageUrl
        )
        for (field in fields) {
            field.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollViewAdminCreateNews.postDelayed({
                        val rect = Rect(0, 0, view.width, view.height + 120)
                        view.requestRectangleOnScreen(rect, true)
                    }, 200)
                }
            }
        }
    }

    private fun checkEditModeIntent() {
        if (intent.hasExtra(EXTRA_EDIT_NEWS_ID)) {
            editNewsId = intent.getStringExtra(EXTRA_EDIT_NEWS_ID)
            val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
            val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
            val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
            val isPinned = intent.getBooleanExtra(EXTRA_IS_PINNED, false)

            binding.toolbarAdmin.title = "Admin - Edit News"
            if (title.isNotBlank()) binding.etNewsTitle.setText(title)
            if (category.isNotBlank()) binding.actvCategory.setText(category, false)
            if (description.isNotBlank()) binding.etNewsDescription.setText(description)
            if (imageUrl.isNotBlank()) {
                binding.etImageUrl.setText(imageUrl)
                binding.ivImagePreview.visibility = View.VISIBLE
                Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .into(binding.ivImagePreview)
            }
            binding.switchPinned.isChecked = isPinned
            binding.btnPublishNews.text = "💾 Update News Article"
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdmin)
        binding.toolbarAdmin.setNavigationOnClickListener { finish() }
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, shop.sabjiwala.kisankendra.R.layout.item_compact_dropdown_single, categories)
        binding.actvCategory.setAdapter(adapter)
    }

    private fun setupPresetChips() {
        binding.chipImgYojana.setOnClickListener {
            binding.etImageUrl.setText(imageYojana)
        }
        binding.chipImgMandi.setOnClickListener {
            binding.etImageUrl.setText(imageMandi)
        }
        binding.chipImgGyan.setOnClickListener {
            binding.etImageUrl.setText(imageGyan)
        }
    }

    private fun setupImagePreview() {
        binding.etImageUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) {
                    binding.ivImagePreview.visibility = View.VISIBLE
                    Glide.with(this@AdminCreateNewsActivity)
                        .load(url)
                        .centerCrop()
                        .placeholder(R.drawable.ic_menu_gallery)
                        .error(R.drawable.stat_notify_error)
                        .into(binding.ivImagePreview)
                } else {
                    binding.ivImagePreview.visibility = View.GONE
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnApiKey.setOnClickListener { showApiKeyDialog() }
        binding.btnPickGalleryImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnRewriteGemini.setOnClickListener { rewriteWithGemini() }
        binding.btnPublishNews.setOnClickListener { publishNewsToFirebase() }
    }

    private fun getStoredApiKey(): String {
        return shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.getStoredApiKey(this)
    }

    private fun showApiKeyDialog() {
        shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.showManageApiKeyDialog(this)
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1).trim()
        }
        return text
            .replace("(?i)^```json".toRegex(), "")
            .replace("^```".toRegex(), "")
            .replace("```$".toRegex(), "")
            .trim()
    }

    private fun rewriteWithGemini() {
        val rawInput = binding.etRawInput.text.toString().trim()
        if (rawInput.isBlank()) {
            binding.tilRawInput.error = "Please paste article link or text first"
            return
        }
        binding.tilRawInput.error = null

        val apiKey = getStoredApiKey()
        if (apiKey.isBlank()) {
            showApiKeyDialog()
            return
        }

        binding.pbGemini.visibility = View.VISIBLE
        binding.btnRewriteGemini.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // If rawInput is an article URL, extract its web page content first
                val articleContent = if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdminCreateNewsActivity, "🌐 Fetching article content from link...", Toast.LENGTH_SHORT).show()
                    }
                    val extracted = fetchArticleTextFromUrl(rawInput)
                    if (extracted.length > 50) extracted else rawInput
                } else {
                    rawInput
                }

                val prompt = """
                    You are an expert Indian agricultural journalist writing for farmers on the Kisan Kendra app.
                    Read, rewrite, and expand the following news content into a complete, engaging article STRICTLY IN CASUAL HINDI (सरल और बोलचाल की हिंदी ताकि किसान भाई आसानी से समझ सकें).

                    INPUT CONTENT TO REWRITE:
                    $articleContent

                    REQUIRED ARTICLE STRUCTURE & FORMATTING RULES:
                    1. ALWAYS WRITE AND OUTPUT ALL FIELDS (title, category, description) STRICTLY IN CASUAL HINDI ONLY.
                    2. Structure the article with a clear title, subtitle headings starting with ### (e.g. ### 🌾 मुख्य बातें, ### 📋 जरूरी बातें या लाभ), and well-written informative paragraphs.
                    3. INCLUDE OFFICIAL GOVERNMENT PORTALS & WEBSITE LINKS WHEN RELEVANT: If the article or scheme involves an official government portal, official website, or online application link (e.g. pmkisan.gov.in, pmfby.gov.in, state portal, or any official URL in the input content), include the relevant official link(s) in markdown format [पोर्टल या वेबसाइट का नाम](https://...) or as clickable URLs so farmers can easily visit the official portal.
                    4. TOKEN EFFICIENCY: Be strictly point-to-point, highly informative, and direct without filler text or unnecessary words.
                    5. CRITICAL: DO NOT OUTPUT ANY PREAMBLE, COMMENTARY, THINKING PROCESS, OR TEXT BEFORE OR AFTER THE JSON. OUTPUT ONLY THE RAW JSON OBJECT STARTING WITH '{' AND ENDING WITH '}'.

                    Format your response PRECISELY as a JSON object with the following fields:
                    {
                      "title": "किसान भाइयों के लिए एक आकर्षक और स्पष्ट शीर्षक हिंदी में",
                      "category": "News OR Govt Scheme OR Agri Market OR Agri Gyan OR Crop Protection OR Technology OR Success Stories",
                      "description": "एक विस्तृत, आकर्षक और समृद्ध लेख (rich-text article) जो सरल और बोलचाल की हिंदी में हो, जिसमें ### उपशीर्षक (subtitles), पैराग्राफ और आवश्यकतानुसार आधिकारिक सरकारी पोर्टल/वेबसाइट के लिंक [Portal Name](https://...) शामिल हों।",
                      "suggestedImageKeyword": "yojana OR mandi OR gyan"
                    }
                """.trimIndent()

                var responseText = ""
                val modelNames = listOf(
                    "gemini-3.5-flash-lite",
                    "gemini-3.1-flash-lite",
                    "gemini-3.8-flash"
                )

                for (modelName in modelNames) {
                    try {
                        val model = GenerativeModel(modelName = modelName, apiKey = apiKey)
                        val text = model.generateContent(prompt).text.orEmpty().trim()
                        if (text.isNotBlank()) {
                            responseText = text
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("AdminCreateNewsActivity", "GenerativeModel $modelName exception: ${e.localizedMessage}")
                    }
                }

                if (responseText.isBlank()) {
                    responseText = callGeminiRestApi(apiKey, prompt).trim()
                }

                // Extract substring starting at '{' and ending at '}' to remove any leading/trailing thinking text
                val jsonString = extractJsonObject(responseText)

                var title = ""
                var category = "Sarkari Yojana"
                var description = ""
                var keyword = "yojana"

                try {
                    val json = JSONObject(jsonString)
                    title = json.optString("title", "")
                    category = json.optString("category", "Sarkari Yojana")
                    description = json.optString("description", "")
                    keyword = json.optString("suggestedImageKeyword", "yojana")
                } catch (_: Exception) {
                    // Fallback text parsing if JSON is malformed
                    val lines = responseText.lines().filter { it.isNotBlank() && !it.trim().startsWith("*") && !it.trim().startsWith("-") }
                    if (lines.isNotEmpty()) {
                        title = lines[0].replace("^[^a-zA-Z0-9\\u0900-\\u097F]+".toRegex(), "").trim()
                        description = lines.drop(1).joinToString("\n").trim()
                    }
                    if (description.isBlank()) description = articleContent
                }

                withContext(Dispatchers.Main) {
                    binding.pbGemini.visibility = View.GONE
                    binding.btnRewriteGemini.isEnabled = true

                    if (title.isNotBlank()) binding.etNewsTitle.setText(title)
                    if (description.isNotBlank()) binding.etNewsDescription.setText(description)

                    val matchedCategory = categories.firstOrNull { it.equals(category, ignoreCase = true) } ?: "News"
                    binding.actvCategory.setText(matchedCategory, false)

                    // Auto select image preset based on keyword
                    val presetImage = when {
                        keyword.contains("mandi", ignoreCase = true) -> imageMandi
                        keyword.contains("gyan", ignoreCase = true) -> imageGyan
                        else -> imageYojana
                    }
                    if (binding.etImageUrl.text.isNullOrBlank()) {
                        binding.etImageUrl.setText(presetImage)
                    }

                    Toast.makeText(this@AdminCreateNewsActivity, "✨ Article rewritten by Gemini!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbGemini.visibility = View.GONE
                    binding.btnRewriteGemini.isEnabled = true

                    val errDetail = e.message ?: e.toString()
                    val userMsg = when {
                        errDetail.contains("QUOTA_EXCEEDED", ignoreCase = true) || errDetail.contains("429", ignoreCase = true) ->
                            "⏳ Gemini Free Quota Reached (429).\n\nYou have used up the 20 free requests/day limit on this Gemini model. Please wait ~1 minute and tap Rewrite again, or use a new free API key from https://aistudio.google.com/app/apikey"
                        errDetail.contains("UNAVAILABLE", ignoreCase = true) || errDetail.contains("503", ignoreCase = true) ->
                            "⚡ Gemini Servers Temporarily Busy (503).\n\nHigh demand on AI servers. Please wait a few seconds and tap Rewrite again."
                        errDetail.contains("API_KEY_INVALID", ignoreCase = true) || errDetail.contains("API key", ignoreCase = true) ->
                            "Invalid Gemini API Key.\n\nPlease tap '🔑 Enter API Key' below to enter a valid key from https://aistudio.google.com/app/apikey"
                        errDetail.contains("NOT_FOUND", ignoreCase = true) || errDetail.contains("404", ignoreCase = true) ->
                            "Gemini Model Update Required (404).\n\nPlease verify that your Google Cloud API key has 'Generative Language API' enabled in Google Cloud Console:\nhttps://console.cloud.google.com/apis/library/generativelanguage.googleapis.com\n\nOr generate a free key from Google AI Studio:\nhttps://aistudio.google.com/app/apikey"
                        else ->
                            "Gemini AI Notice: $errDetail"
                    }

                    MaterialAlertDialogBuilder(this@AdminCreateNewsActivity)
                        .setTitle("✨ AI Generation Notice")
                        .setMessage(userMsg)
                        .setPositiveButton("🔑 Enter API Key") { _, _ -> showApiKeyDialog() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun fetchArticleTextFromUrl(urlStr: String): String {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val cleanText = html
                    .replace("(?s)<script.*?>.*?</script>".toRegex(RegexOption.IGNORE_CASE), " ")
                    .replace("(?s)<style.*?>.*?</style>".toRegex(RegexOption.IGNORE_CASE), " ")
                    .replace("<[^>]+>".toRegex(), " ")
                    .replace("&nbsp;".toRegex(RegexOption.IGNORE_CASE), " ")
                    .replace("&amp;".toRegex(RegexOption.IGNORE_CASE), "&")
                    .replace("&lt;".toRegex(RegexOption.IGNORE_CASE), "<")
                    .replace("&gt;".toRegex(RegexOption.IGNORE_CASE), ">")
                    .replace("&quot;".toRegex(RegexOption.IGNORE_CASE), "\"")
                    .replace("\\s+".toRegex(), " ")
                    .trim()

                if (cleanText.length > 12000) cleanText.substring(0, 12000) else cleanText
            } else {
                urlStr
            }
        } catch (_: Exception) {
            urlStr
        }
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String): String {
        val cleanKey = apiKey.trim().replace("\"", "").replace("'", "")
        val models = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.8-flash"
        )

        val jsonRequest = JSONObject().apply {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val contents = JSONArray().put(JSONObject().put("parts", parts))
            put("contents", contents)
        }

        var lastException: Exception? = null

        for (model in models) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"

            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", cleanKey)
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                conn.outputStream.use { os ->
                    os.write(jsonRequest.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResp = JSONObject(responseStr)
                    val candidates = jsonResp.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.optJSONObject("content")
                        if (contentObj != null) {
                            val partsArr = contentObj.optJSONArray("parts")
                            if (partsArr != null && partsArr.length() > 0) {
                                val resultText = partsArr.getJSONObject(0).optString("text", "")
                                if (resultText.isNotBlank()) return resultText
                            }
                        }
                    }
                } else {
                    val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e("GeminiRestApi", "Model $model HTTP $responseCode: $errStr")
                    lastException = Exception("HTTP $responseCode: $errStr")
                }
            } catch (e: Exception) {
                Log.e("GeminiRestApi", "Model $model exception: ${e.localizedMessage}")
                lastException = e
            }
        }

        throw lastException ?: Exception("Failed to connect to Gemini API")
    }

    private fun publishNewsToFirebase() {
        val title = binding.etNewsTitle.text.toString().trim()
        val category = binding.actvCategory.text.toString().trim()
        val description = binding.etNewsDescription.text.toString().trim()
        var imageUrl = binding.etImageUrl.text.toString().trim()
        val isPinned = binding.switchPinned.isChecked

        if (title.isBlank()) {
            binding.tilNewsTitle.error = "Title is required"
            return
        }
        binding.tilNewsTitle.error = null

        if (category.isBlank()) {
            binding.tilCategory.error = "Category is required"
            return
        }
        binding.tilCategory.error = null

        if (description.isBlank()) {
            binding.tilNewsDescription.error = "Description is required"
            return
        }
        binding.tilNewsDescription.error = null

        if (imageUrl.isBlank()) {
            imageUrl = when (category) {
                "Market Updates" -> imageMandi
                "Krishi Gyan" -> imageGyan
                else -> imageYojana
            }
        }

        binding.pbPublishing.visibility = View.VISIBLE
        binding.btnPublishNews.isEnabled = false

        if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
            val rawUri = Uri.parse(imageUrl)
            val fileUri = MediaUtils.copyUriToAppCache(this, rawUri) ?: rawUri
            val ref = storageRef.child("news_images/news_${System.currentTimeMillis()}.jpg")
            ref.putFile(fileUri)
                .addOnSuccessListener { _ ->
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        saveNewsDocumentToFirestore(title, category, description, downloadUri.toString(), isPinned)
                    }.addOnFailureListener { e ->
                        uploadFailed(e.localizedMessage ?: "Failed to get download URL")
                    }
                }
                .addOnFailureListener { e ->
                    uploadFailed(e.localizedMessage ?: "Failed to upload image")
                }
        } else {
            saveNewsDocumentToFirestore(title, category, description, imageUrl, isPinned)
        }
    }

    private fun saveNewsDocumentToFirestore(title: String, category: String, description: String, imageUrl: String, isPinned: Boolean) {
        val currentEditId = editNewsId
        if (!currentEditId.isNullOrBlank()) {
            val updateData = hashMapOf<String, Any>(
                "title" to title,
                "category" to category,
                "description" to description,
                "imageUrl" to imageUrl,
                "isPinned" to isPinned,
                "updatedAt" to Timestamp.now()
            )

            firestore.collection("news").document(currentEditId)
                .set(updateData, SetOptions.merge())
                .addOnSuccessListener {
                    NewsCacheHelper.clearCache(this)
                    binding.pbPublishing.visibility = View.GONE
                    binding.btnPublishNews.isEnabled = true
                    Toast.makeText(this, "🎉 News updated successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.pbPublishing.visibility = View.GONE
                    binding.btnPublishNews.isEnabled = true
                    Toast.makeText(this, "Failed to update: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } else {
            val newsDocument = hashMapOf(
                "title" to title,
                "category" to category,
                "description" to description,
                "imageUrl" to imageUrl,
                "timestamp" to Timestamp.now(),
                "isPinned" to isPinned
            )

            firestore.collection("news")
                .add(newsDocument)
                .addOnSuccessListener {
                    NewsCacheHelper.clearCache(this)
                    binding.pbPublishing.visibility = View.GONE
                    binding.btnPublishNews.isEnabled = true
                    Toast.makeText(this, "🎉 News published successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.pbPublishing.visibility = View.GONE
                    binding.btnPublishNews.isEnabled = true
                    Toast.makeText(this, "Failed to publish: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun uploadFailed(error: String) {
        binding.pbPublishing.visibility = View.GONE
        binding.btnPublishNews.isEnabled = true
        Toast.makeText(this, "Failed to upload image: $error", Toast.LENGTH_LONG).show()
    }
}