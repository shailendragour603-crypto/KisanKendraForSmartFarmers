package shop.sabjiwala.kisankendra

import android.R
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import shop.sabjiwala.kisankendra.databinding.DialogAddCropCareBinding
import shop.sabjiwala.kisankendra.databinding.FragmentCropCareBinding
import shop.sabjiwala.kisankendra.model.CropCareItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

class CropCareFragment : Fragment() {

    private var _binding: FragmentCropCareBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var allSolutionsList = mutableListOf<CropCareItem>()
    private lateinit var adapter: CropCareAdapter
    private var isUserAdmin = false

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            spokenText?.let {
                _binding?.etSearchProblem?.setText(it)
                filterSolutions(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropCareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewCropCare.layoutManager = LinearLayoutManager(requireContext())
        adapter = CropCareAdapter(
            items = emptyList(),
            isAdmin = isUserAdmin,
            onEditClick = { item -> showAddSolutionDialog(item) },
            onDeleteClick = { item -> deleteSolutionItem(item) }
        )
        binding.recyclerViewCropCare.adapter = adapter

        setupDynamicTranslations()
        checkAdminRole()
        fetchSolutionsFromFirestore()

        binding.swipeRefreshCropCare.setOnRefreshListener {
            fetchSolutionsFromFirestore()
        }

        binding.layoutKisanAiFab.setOnClickListener {
            KisanAiChatBottomSheet().show(parentFragmentManager, "KisanAiChatBottomSheet")
        }
        binding.fabKisanAi.setOnClickListener {
            KisanAiChatBottomSheet().show(parentFragmentManager, "KisanAiChatBottomSheet")
        }
        startKisanAiGlowAnimation()

        binding.fabAddCropCare.setOnClickListener {
            showAddSolutionDialog()
        }

        binding.btnVoiceSearchCropCare.setOnClickListener {
            try {
                val ctx = requireContext()
                val activeLang = LocaleHelper.getPersistedLanguage(ctx)
                val langTag = if (activeLang == "hi") "hi-IN" else "en-US"
                val promptText = if (activeLang == "hi") "अपनी समस्या बोलें (उदा. गेहूं में पीला रतुआ)..." else "Speak your problem (e.g. Yellow rust in wheat)..."
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
                }
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Voice search not supported", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearchProblem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSolutions(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupDynamicTranslations() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                TranslationHelper.syncMasterDictionaryFromFirestore(ctx, activeLang)
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        applyCropCareTranslations(ctx, activeLang)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        applyCropCareTranslations(ctx, activeLang)
    }

    private fun applyCropCareTranslations(ctx: Context, activeLang: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (_binding == null || !isAdded) return@launch
            val transHeader = TranslationHelper.getDirectTranslation("फसल सुरक्षा और समाधान", activeLang, ctx)
                ?: TranslationHelper.translateText(ctx, "फसल सुरक्षा और समाधान", activeLang)
            val transHint = TranslationHelper.getDirectTranslation("अपनी समस्या यहाँ खोजें...", activeLang, ctx)
                ?: TranslationHelper.translateText(ctx, "अपनी समस्या यहाँ खोजें...", activeLang)
            val transEmpty = TranslationHelper.getDirectTranslation("कोई समस्या या समाधान नहीं मिला।", activeLang, ctx)
                ?: TranslationHelper.translateText(ctx, "कोई समस्या या समाधान नहीं मिला।", activeLang)
            val transFab = TranslationHelper.getDirectTranslation("समस्या और समाधान जोड़ें", activeLang, ctx)
                ?: TranslationHelper.translateText(ctx, "समस्या और समाधान जोड़ें", activeLang)

            if (_binding != null && isAdded) {
                binding.tvHeaderCropCare.text = transHeader
                binding.etSearchProblem.hint = transHint
                binding.tvEmptyState.text = transEmpty
                binding.fabAddCropCare.text = transFab
            }
        }
    }

    private fun checkAdminRole() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            isUserAdmin = false
            binding.fabAddCropCare.visibility = View.GONE
            return
        }
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: doc.getString("userRole") ?: ""
                isUserAdmin = role.equals("admin", ignoreCase = true) || user.email == "admin@sabjiwala.shop"
                binding.fabAddCropCare.visibility = if (isUserAdmin) View.VISIBLE else View.GONE
                
                adapter = CropCareAdapter(
                    items = allSolutionsList,
                    isAdmin = isUserAdmin,
                    onEditClick = { item -> showAddSolutionDialog(item) },
                    onDeleteClick = { item -> deleteSolutionItem(item) }
                )
                binding.recyclerViewCropCare.adapter = adapter
                filterSolutions(binding.etSearchProblem.text.toString())
            }
            .addOnFailureListener {
                isUserAdmin = false
                binding.fabAddCropCare.visibility = View.GONE
            }
    }

    private fun fetchSolutionsFromFirestore() {
        binding.progressBarCropCare.visibility = View.VISIBLE
        firestore.collection("crop_care_data").document("latest")
            .get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBarCropCare.visibility = View.GONE
                binding.swipeRefreshCropCare.isRefreshing = false

                allSolutionsList.clear()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val rawList = doc.get("solutions") as? List<Map<*, *>>
                    if (!rawList.isNullOrEmpty()) {
                        for (map in rawList) {
                            allSolutionsList.add(CropCareItem.fromMap(map))
                        }
                    }
                }

                if (allSolutionsList.isEmpty() && !doc.exists()) {
                    seedInitialData()
                } else {
                    filterSolutions(binding.etSearchProblem.text.toString())
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBarCropCare.visibility = View.GONE
                binding.swipeRefreshCropCare.isRefreshing = false
                Toast.makeText(requireContext(), "लोड करने में विफल", Toast.LENGTH_SHORT).show()
            }
    }

    private fun seedInitialData() {
        val sampleList = listOf(
            CropCareItem(
                title = "गेहूं में पीला रतुआ (Yellow Rust)",
                problem = "गेहूं की पत्तियों पर पीले रंग की फफूंद वाली धारियां बन जाती हैं, जिससे पौधे का विकास रुक जाता है और दाने कमजोर हो जाते हैं।",
                solution = "रोग के लक्षण दिखते ही प्रोपिकोनाजोल 25% EC को 1 लीटर पानी में 1 मिली मिलाकर छिड़काव करें। हमेशा प्रमाणित रोग-प्रतिरोधी बीजों का चयन करें।",
                category = "फसल रोग"
            ),
            CropCareItem(
                title = "कपास में गुलाबी सुंडी का प्रकोप (Pink Bollworm)",
                problem = "सुंडी कपास के डोडे के अंदर घुसकर रेशे और बीजों को खाकर नष्ट कर देती है, जिससे डोडे समय से पहले खुल जाते हैं।",
                solution = "नर कीट नियंत्रण के लिए प्रति एकड़ 5 फिरोमोन ट्रैप लगाएं। स्थानीय कृषि विशेषज्ञों की सलाह से क्विनालफॉस 25% EC या इमामेक्टिन बेंजोएट का छिड़काव करें।",
                category = "कीट नियंत्रण"
            ),
            CropCareItem(
                title = "टमाटर में पत्ती मोड़क वायरस (Leaf Curl Virus)",
                problem = "टमाटर की पत्तियां ऊपर की तरफ मुड़ जाती हैं, मोटी और खुरदरी हो जाती हैं, तथा पौधे में फूल और फल नहीं बनते।",
                solution = "सफेद मक्खी (वाहक कीट) की रोकथाम के लिए इमिडाक्लोप्रिड 17.8% SL का छिड़काव करें। संक्रमित पौधों को तुरंत उखाड़कर नष्ट कर दें।",
                category = "विषाणु एवं देखभाल"
            )
        )
        allSolutionsList.addAll(sampleList)
        saveAllSolutionsToFirestore()
    }

    private fun filterSolutions(query: String) {
        val q = query.lowercase().trim()
        val filtered = if (q.isBlank()) {
            allSolutionsList
        } else {
            allSolutionsList.filter {
                it.title.lowercase().contains(q) ||
                it.problem.lowercase().contains(q) ||
                it.solution.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }

        adapter.updateList(filtered)
        if (filtered.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    private fun showAddSolutionDialog(existingItem: CropCareItem? = null) {
        val dialogBinding = DialogAddCropCareBinding.inflate(layoutInflater)
        val categories = arrayOf(
            "फसल रोग",
            "कीट नियंत्रण",
            "खरपतवार",
            "पीलापन",
            "पत्ता मरोड़",
            "उकठा रोग",
            "फूल झड़ना",
            "फल सड़न",
            "कम बढ़वार",
            "खाद खुराक",
            "सिंचाई कमी",
            "जलभराव",
            "मिट्टी जांच",
            "बीज अंकुरण",
            "फसल कीट",
            "दवा स्प्रे",
            "पाले से बचाव",
            "फफूंद रोग",
            "दीमक समस्या",
            "अवांछित घास",
            "सामान्य"
        )
        val catAdapter =
            ArrayAdapter(requireContext(), R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.actvDialogCategory.setAdapter(catAdapter)

        val isEdit = existingItem != null
        if (isEdit) {
            dialogBinding.tvDialogTitleHeader.text = "✏️ समस्या और समाधान संशोधित करें"
            dialogBinding.etDialogTitle.setText(existingItem.title)
            dialogBinding.actvDialogCategory.setText(existingItem.category, false)
            dialogBinding.etDialogProblem.setText(existingItem.problem)
            dialogBinding.etDialogSolution.setText(existingItem.solution)
        } else {
            dialogBinding.tvDialogTitleHeader.text = "➕ नई समस्या और समाधान जोड़ें"
            dialogBinding.actvDialogCategory.setText(categories[0], false)
        }

        val dialogTitle = if (isEdit) "समस्या और समाधान संशोधित करें" else "नई समस्या और समाधान जोड़ें"
        val positiveButtonText = if (isEdit) "अपडेट करें" else "सुरक्षित करें और प्रकाशित करें"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogBinding.root)
            .setPositiveButton(positiveButtonText) { _, _ ->
                val title = dialogBinding.etDialogTitle.text.toString().trim()
                val category = dialogBinding.actvDialogCategory.text.toString().trim()
                val problem = dialogBinding.etDialogProblem.text.toString().trim()
                val solution = dialogBinding.etDialogSolution.text.toString().trim()

                if (title.isBlank() || problem.isBlank() || solution.isBlank()) {
                    Toast.makeText(requireContext(), "कृपया शीर्षक, समस्या और समाधान भरें", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (isEdit && existingItem != null) {
                    val updatedItem = existingItem.copy(
                        title = title,
                        category = category.ifBlank { "सामान्य" },
                        problem = problem,
                        solution = solution
                    )
                    val index = allSolutionsList.indexOfFirst { it.id == existingItem.id }
                    if (index != -1) {
                        allSolutionsList[index] = updatedItem
                    } else {
                        allSolutionsList.add(updatedItem)
                    }
                } else {
                    val newItem = CropCareItem(
                        title = title,
                        category = category.ifBlank { "सामान्य" },
                        problem = problem,
                        solution = solution
                    )
                    allSolutionsList.add(0, newItem)
                }

                saveAllSolutionsToFirestore()
            }
            .setNegativeButton("रद्द करें", null)
            .show()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun deleteSolutionItem(item: CropCareItem) {
        allSolutionsList.removeAll { it.id == item.id }
        saveAllSolutionsToFirestore()
    }

    private fun startKisanAiGlowAnimation() {
        val scaleX = ObjectAnimator.ofFloat(binding.layoutKisanAiFab, "scaleX", 1.0f, 1.08f, 1.0f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.layoutKisanAiFab, "scaleY", 1.0f, 1.08f, 1.0f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun saveAllSolutionsToFirestore() {
        val serialized = allSolutionsList.map { it.toMap() }
        val data = hashMapOf(
            "updatedAt" to Timestamp.now(),
            "solutions" to serialized
        )
        firestore.collection("crop_care_data").document("latest")
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (_binding != null && isAdded) {
                    filterSolutions(binding.etSearchProblem.text.toString())
                    Toast.makeText(requireContext(), "सफलतापूर्वक सहेजा गया!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    Toast.makeText(requireContext(), "सहेजने में विफल", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
