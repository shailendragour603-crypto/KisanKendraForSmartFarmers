package shop.sabjiwala.kisankendra

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.DialogGuestSigninPromptBinding
import shop.sabjiwala.kisankendra.databinding.DialogKisanAiChatBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KisanAiChatBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogKisanAiChatBinding? = null
    private val binding get() = _binding!!

    private val messagesList = mutableListOf<KisanAiMessage>()
    private lateinit var chatAdapter: KisanAiMessageAdapter

    private var selectedBitmap: Bitmap? = null

    private val PREFS_NAME = "kisan_ai_prefs"
    private val MAX_QUESTIONS_PER_DAY = 5

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                binding.etUserQuery.setText(spokenText)
                sendQueryToKisanAi(spokenText)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedImageUri(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { processSelectedBitmap(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogKisanAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = context ?: return

        // 🔒 Auth Check
        val user = FirebaseAuth.getInstance().currentUser
        val isGuest = user == null || user.isAnonymous || SignInActivity.isGuestUser(ctx)
        if (isGuest) {
            dismiss()
            showSignInPrompt()
            return
        }

        setupRecyclerView()
        updateRemainingQuestionsCount()

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.fetchRemoteApiKey(ctx)

        // Setup localized prompt chips, query hint, and initial welcome message
        setupQuickPromptChips(activeLang)
        binding.etUserQuery.hint = getQueryHintText(activeLang)
        binding.tvImagePreviewLabel.text = getImagePreviewLabelText(activeLang)

        val welcomeMsg = getWelcomeMessage(activeLang, getRemainingQuestions())
        messagesList.add(KisanAiMessage(welcomeMsg, false))
        chatAdapter.notifyItemInserted(0)

        loadChatBannerAd()

        binding.btnCloseChat.setOnClickListener {
            (activity as? MainActivity)?.tryShowInterstitialAd {
                dismiss()
            }
        }

        binding.btnAttachImage.setOnClickListener { showImagePickerDialog() }

        binding.btnRemoveSelectedImage.setOnClickListener { clearSelectedImage() }

        binding.btnSend.setOnClickListener {
            val query = binding.etUserQuery.text.toString().trim()
            if (query.isNotBlank() || selectedBitmap != null) {
                binding.etUserQuery.setText("")
                sendQueryToKisanAi(query)
            } else {
                Toast.makeText(ctx, getEmptyInputWarningMessage(activeLang), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMic.setOnClickListener {
            startVoiceRecognition()
        }

        // Quick prompt chips click handlers
        binding.chipPrompt1.setOnClickListener { sendQueryToKisanAi(binding.chipPrompt1.text.toString()) }
        binding.chipPrompt2.setOnClickListener { sendQueryToKisanAi(binding.chipPrompt2.text.toString()) }
        binding.chipPrompt3.setOnClickListener { sendQueryToKisanAi(binding.chipPrompt3.text.toString()) }
        binding.chipPrompt4.setOnClickListener { sendQueryToKisanAi(binding.chipPrompt4.text.toString()) }
    }

    private fun setupRecyclerView() {
        val ctx = context ?: return
        chatAdapter = KisanAiMessageAdapter(messagesList)
        binding.recyclerViewChat.layoutManager = LinearLayoutManager(ctx).apply {
            stackFromEnd = true
        }
        binding.recyclerViewChat.adapter = chatAdapter
    }

    private fun showImagePickerDialog() {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_image_picker_modern, null)
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPickerTitle)
        val cardCamera = dialogView.findViewById<View>(R.id.cardPickCamera)
        val cardGallery = dialogView.findViewById<View>(R.id.cardPickGallery)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelPicker)

        val tvCameraTitle = dialogView.findViewById<TextView>(R.id.tvCameraTitle)
        val tvGalleryTitle = dialogView.findViewById<TextView>(R.id.tvGalleryTitle)

        tvTitle.text = getImagePickerTitle(activeLang)
        val options = getImagePickerOptions(activeLang)
        if (options.size >= 2) {
            tvCameraTitle.text = options[0]
            tvGalleryTitle.text = options[1]
        }

        cardCamera.setOnClickListener {
            dialog.dismiss()
            try {
                cameraLauncher.launch(null)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }

        cardGallery.setOnClickListener {
            dialog.dismiss()
            try {
                galleryLauncher.launch("image/*")
            } catch (_: Exception) {
                Toast.makeText(ctx, "Gallery not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun processSelectedImageUri(uri: Uri) {
        val ctx = context ?: return
        try {
            val inputStream = ctx.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                processSelectedBitmap(bitmap)
            } else {
                Toast.makeText(ctx, "Unable to load selected image", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(ctx, "Failed to load selected image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSelectedBitmap(bitmap: Bitmap) {
        val scaledBitmap = scaleDownBitmap(bitmap, 1024)
        selectedBitmap = scaledBitmap
        binding.ivSelectedImagePreview.setImageBitmap(scaledBitmap)
        binding.layoutImagePreview.visibility = View.VISIBLE
    }

    private fun clearSelectedImage() {
        selectedBitmap = null
        binding.layoutImagePreview.visibility = View.GONE
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to maxOf(1, (maxDimension / ratio).toInt())
        } else {
            maxOf(1, (maxDimension * ratio).toInt()) to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }

    private fun getQuestionsCountToday(): Int {
        val user = FirebaseAuth.getInstance().currentUser ?: return 0
        val ctx = context ?: return 0
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
        return prefs.getInt("count_${user.uid}_${getTodayDateString()}", 0)
    }

    private fun incrementQuestionsCountToday() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
        val key = "count_${user.uid}_${getTodayDateString()}"
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    private fun getEarnedExtraQuestionsToday(): Int {
        val user = FirebaseAuth.getInstance().currentUser ?: return 0
        val ctx = context ?: return 0
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
        return prefs.getInt("extra_${user.uid}_${getTodayDateString()}", 0)
    }

    private fun addEarnedExtraQuestionsToday(extraCount: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
        val key = "extra_${user.uid}_${getTodayDateString()}"
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + extraCount).apply()
    }

    private fun getEffectiveMaxQuestions(): Int {
        return MAX_QUESTIONS_PER_DAY + getEarnedExtraQuestionsToday()
    }

    private fun getRemainingQuestions(): Int {
        return maxOf(0, getEffectiveMaxQuestions() - getQuestionsCountToday())
    }

    private fun updateRemainingQuestionsCount() {
        val remaining = getRemainingQuestions()
        val total = getEffectiveMaxQuestions()
        binding.tvRemainingQuestions.text = "Questions left today: $remaining/$total"
    }

    private fun loadChatBannerAd() {
        val ctx = context ?: return
        binding.adContainerChatBanner.post {
            try {
                val adView = AdView(ctx).apply {
                    adUnitId = "ca-app-pub-3054731531860427/9592937417"
                    val displayMetrics = ctx.resources.displayMetrics
                    val density = displayMetrics.density
                    val containerWidthPx = binding.adContainerChatBanner.width.takeIf { it > 0 }
                        ?: (displayMetrics.widthPixels - (32 * density).toInt())
                    val adWidth = (containerWidthPx / density).toInt().coerceAtLeast(300)
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth))
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            if (_binding != null) binding.adContainerChatBanner.visibility = View.VISIBLE
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (_binding != null) binding.adContainerChatBanner.visibility = View.GONE
                        }
                    }
                }
                binding.adContainerChatBanner.removeAllViews()
                binding.adContainerChatBanner.addView(adView)
                adView.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showSignInPrompt() {
        val ctx = context ?: return
        val dialogBinding = DialogGuestSigninPromptBinding.inflate(LayoutInflater.from(ctx))
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogBinding.root)
            .create()

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        dialogBinding.tvGuestTitle.text = "Kisan AI"
        dialogBinding.tvGuestMessage.text = "Kisan AI is ready to help you, Please sign in."
        dialogBinding.btnCancelGuest.text = "Cancel"
        dialogBinding.btnSignInGuest.text = "Sign In"

        if (activeLang != "en") {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                val tTitle = TranslationHelper.translateText(ctx, "Kisan AI", activeLang)
                val tMsg = TranslationHelper.translateText(ctx, "Kisan AI is ready to help you, Please sign in.", activeLang)
                val tCancel = TranslationHelper.translateText(ctx, "Cancel", activeLang)
                val tSign = TranslationHelper.translateText(ctx, "Sign In", activeLang)

                if (_binding != null && isAdded) {
                    dialogBinding.tvGuestTitle.text = tTitle
                    dialogBinding.tvGuestMessage.text = tMsg
                    dialogBinding.btnCancelGuest.text = tCancel
                    dialogBinding.btnSignInGuest.text = tSign
                }
            }
        }

        dialogBinding.btnSignInGuest.setOnClickListener {
            dialog.dismiss()
            activity?.let {
                it.startActivity(Intent(it, SignInActivity::class.java))
            } ?: ctx.startActivity(Intent(ctx, SignInActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        dialogBinding.btnCancelGuest.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getStoredApiKey(): String {
        val ctx = context ?: return ""
        return shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.getStoredApiKey(ctx)
    }

    private fun cleanAiResponse(rawText: String): String {
        return rawText
            .replace("**", "")
            .replace(Regex("(?m)^#+\\s*"), "")
            .replace(Regex("(?m)^\\*\\s+"), "• ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun showLimitReachedDialog(pendingQuery: String) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_kisan_ai_reward, null)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseRewardDialog)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvRewardTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvRewardSubtitle)
        val btnWatch = dialogView.findViewById<MaterialButton>(R.id.btnWatchVideo)

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        val transTitle = TranslationHelper.getDirectTranslation("Unlock 3 Free Questions", activeLang)
            ?: if (activeLang == "hi") "3 मुफ़्त प्रश्न अनलॉक करें" else "Unlock 3 Free Questions"
        val transSubtitle = TranslationHelper.getDirectTranslation("Watch a short video to continue asking", activeLang)
            ?: if (activeLang == "hi") "पूछना जारी रखने के लिए एक छोटा वीडियो देखें" else "Watch a short video to continue asking"
        val transBtnWatch = TranslationHelper.getDirectTranslation("Watch Video", activeLang)
            ?: if (activeLang == "hi") "वीडियो देखें" else "Watch Video"

        tvTitle.text = transTitle
        tvSubtitle.text = transSubtitle
        btnWatch.text = transBtnWatch

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnWatch.setOnClickListener {
            dialog.dismiss()
            showRewardedAdForExtraQuestions(pendingQuery)
        }

        dialog.show()
    }

    private fun showRewardedAdForExtraQuestions(pendingQuery: String) {
        val ctx = context ?: return
        val act = activity ?: return

        Toast.makeText(ctx, "⏳ Loading video ad...", Toast.LENGTH_SHORT).show()
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            ctx,
            "ca-app-pub-3054731531860427/8586977507",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Toast.makeText(ctx, "Video ad failed to play. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    rewardedAd.show(act) { _ ->
                        addEarnedExtraQuestionsToday(3)
                        updateRemainingQuestionsCount()
                        Toast.makeText(ctx, "🎉 +3 Extra Questions Granted!", Toast.LENGTH_LONG).show()
                        if (pendingQuery.isNotBlank() || selectedBitmap != null) {
                            sendQueryToKisanAi(pendingQuery)
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Toast.makeText(ctx, "Video ad not ready. Please try again in a few seconds.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendQueryToKisanAi(query: String) {
        val ctx = context ?: return
        // Check daily limit
        if (getRemainingQuestions() <= 0) {
            showLimitReachedDialog(query)
            return
        }

        val imageInput = selectedBitmap
        clearSelectedImage()

        // Add user message with image if attached
        messagesList.add(KisanAiMessage(text = query, isUser = true, imageBitmap = imageInput))
        chatAdapter.notifyItemInserted(messagesList.size - 1)
        binding.recyclerViewChat.scrollToPosition(messagesList.size - 1)

        incrementQuestionsCountToday()
        updateRemainingQuestionsCount()

        binding.progressBarChat.visibility = View.VISIBLE

        val apiKey = getStoredApiKey()
        if (apiKey.isBlank()) {
            binding.progressBarChat.visibility = View.GONE
            showApiKeyDialog()
            return
        }

        val activeLang = LocaleHelper.getPersistedLanguage(ctx)
        val languageName = getLanguageName(activeLang)

        lifecycleScope.launch(Dispatchers.IO) {
            var aiResponse = ""
            val modelNames = selectModelsForQuery(hasImage = imageInput != null, query = query)

            val systemInstruction = """
                You are 'Kisan AI', an expert, polite, and practical agricultural assistant for Indian farmers.

                LANGUAGE INSTRUCTIONS:
                1. AUTOMATICALLY DETECT the language of the farmer's question or attached prompt.
                2. RESPOND IN THE EXACT SAME LANGUAGE that the farmer used (whether Hindi or English).
                3. If no text question is provided or language is ambiguous, respond in $languageName.

                IMAGE DIAGNOSIS & CAUSE ANALYSIS (If an image is attached):
                1. Identify the crop, plant disease, pest attack, nutrient deficiency, leaf curling, or health problem in the photo.
                2. Explain the ROOT CAUSE of the issue in clear, practical terms for farmers.
                3. Provide ACTIONABLE SOLUTIONS:
                   • Chemical Remedies: Exact chemical names, spray dosages per acre / per litre of water.
                   • Organic / Natural Remedies: Neem oil, sour buttermilk, yellow sticky traps, biological controls.
                   • Preventive & Cultural Practices: Soil management, irrigation, crop rotation advice.

                GENERAL ADVICE (If text question is asked):
                1. Provide accurate, practical, and direct advice on crops, pest control, plant diseases, fertilizers, weather, and market rates.
                2. Keep the answer clean, direct to the point, and easy to understand.

                STRICT FORMATTING & TOKEN EFFICIENCY RULES:
                1. DO NOT use markdown bold formatting like double asterisks (** or *).
                2. DO NOT use markdown headings (# or ##).
                3. Provide output strictly in clean plain text. Use bullet dots (•) for list items.
                4. POINT-TO-POINT ANSWERS: Be strictly direct and concise. Avoid introductory greetings, repetitive polite filler, or token-wasting summaries. Give actionable answers immediately.
            """.trimIndent()

            val promptText = if (query.isNotBlank()) {
                "$systemInstruction\n\nFarmer's Question: $query"
            } else {
                "$systemInstruction\n\nFarmer uploaded a crop/plant photo. Please analyze the attached image thoroughly, diagnose any disease/pest/deficiency, explain its root cause, and provide actionable chemical, organic, and preventive remedies."
            }

            for (modelName in modelNames) {
                try {
                    val generativeModel = GenerativeModel(modelName = modelName, apiKey = apiKey)
                    val response = if (imageInput != null) {
                        val inputContent = content {
                            image(imageInput)
                            text(promptText)
                        }
                        generativeModel.generateContent(inputContent)
                    } else {
                        generativeModel.generateContent(promptText)
                    }

                    val text = response.text.orEmpty().trim()
                    if (text.isNotBlank()) {
                        aiResponse = cleanAiResponse(text)
                        break
                    }
                } catch (_: Exception) {
                    // try next model
                }
            }

            if (aiResponse.isBlank()) {
                aiResponse = getFallbackErrorMessage(activeLang)
            }

            withContext(Dispatchers.Main) {
                if (_binding != null && isAdded) {
                    binding.progressBarChat.visibility = View.GONE
                    messagesList.add(KisanAiMessage(aiResponse, false))
                    chatAdapter.notifyItemInserted(messagesList.size - 1)
                    binding.recyclerViewChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }
    }

    private fun selectModelsForQuery(hasImage: Boolean, query: String): List<String> {
        return when {
            // Tier 1: Leaf disease photo diagnosis & deep research queries
            hasImage -> listOf(
                "gemini-3.8-flash",
                "gemini-3.7-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite"
            )

            // Tier 2: Complex dosage, chemical mixing, technical calculations & treatment schedules
            isComplexQuery(query) -> listOf(
                "gemini-3.8-flash",
                "gemini-3.7-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite"
            )

            // Tier 3: General farmer queries, weather, mandi rates, sowing dates, article rewrites (High RPM/RPD)
            else -> listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-3.8-flash"
            )
        }
    }

    private fun isComplexQuery(query: String): Boolean {
        val complexKeywords = listOf(
            "formula", "dosage", "calculation", "treatment", "schedule", "technical", "mix", "chemical", "pesticide",
            "मात्रा", "खुराक", "दवाई", "स्प्रे", "हिसाब", "इलाज", "रोकथाम", "केमिकल", "dawai", "matra", "ilaj"
        )
        val lowerQuery = query.lowercase()
        return complexKeywords.any { lowerQuery.contains(it) }
    }

    private fun showApiKeyDialog() {
        val ctx = getContext() ?: return
        shop.sabjiwala.kisankendra.utils.GeminiConfigHelper.showManageApiKeyDialog(ctx)
    }

    private fun startVoiceRecognition() {
        val ctx = context ?: return
        val lang = LocaleHelper.getPersistedLanguage(ctx)
        val langTag = getSpeechLanguageTag(lang)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getVoicePromptText(lang))
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(ctx, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLanguageName(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "Hindi"
            else -> "English"
        }
    }

    private fun getSpeechLanguageTag(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "hi-IN"
            else -> "en-IN"
        }
    }

    private fun getImagePickerTitle(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "फसल/पौधे की फोटो चुनें"
            else -> "Attach Crop / Plant Image"
        }
    }

    private fun getImagePickerOptions(langCode: String): Array<String> {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> arrayOf("कैमरे से फोटो लें", "गैलरी से चुनें")
            else -> arrayOf("Take Photo with Camera", "Choose from Gallery")
        }
    }

    private fun getImagePreviewLabelText(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "फसल की फोटो संलग्न"
            else -> "Crop Photo Attached"
        }
    }

    private fun getEmptyInputWarningMessage(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "कृपया कोई प्रश्न लिखें या फोटो संलग्न करें"
            else -> "Please enter a question or attach an image"
        }
    }

    private fun getVoicePromptText(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "अपनी खेती से जुड़ी समस्या बोलें..."
            else -> "Speak your farming question..."
        }
    }

    private fun getQueryHintText(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "Kisan AI से कुछ भी पूछें..."
            else -> "Ask Kisan AI anything..."
        }
    }

    private fun getWelcomeMessage(langCode: String, remainingQuestions: Int): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "नमस्ते! मैं 'Kisan AI' हूँ। अपनी फसल या पौधे की फोटो भेजें या रोग, कीट, खाद व मंडी भाव के बारे में पूछें। (आज के शेष प्रश्न: $remainingQuestions)"
            else -> "Hello! I am 'Kisan AI', your expert agricultural assistant. Attach a photo of your crop or ask me anything about crop diseases, pest control, fertilizers, or market advice! (Questions left today: $remainingQuestions)"
        }
    }

    private fun getFallbackErrorMessage(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> "माफ़ कीजिए, अभी AI से संपर्क नहीं हो पाया। कृपया पुनः प्रयास करें।"
            else -> "Sorry, unable to connect to AI right now. Please try again later."
        }
    }

    private fun setupQuickPromptChips(langCode: String) {
        val prompts = when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> listOf("फसल रोग और उपचार", "सिंचाई का सही तरीका", "खाद और उर्वरक सुझाव", "मंडी भाव और सलाह")
            else -> listOf("Crop Disease & Remedy", "Proper Irrigation Tips", "Fertilizer Advice", "Market Rates & Advice")
        }

        binding.chipPrompt1.text = prompts[0]
        binding.chipPrompt2.text = prompts[1]
        binding.chipPrompt3.text = prompts[2]
        binding.chipPrompt4.text = prompts[3]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
