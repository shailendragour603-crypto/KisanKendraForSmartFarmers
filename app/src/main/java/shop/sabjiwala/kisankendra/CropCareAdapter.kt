package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.ItemCropCareSolutionBinding
import shop.sabjiwala.kisankendra.databinding.ItemInfeedAdBinding
import shop.sabjiwala.kisankendra.model.CropCareItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

class CropCareAdapter(
    private var items: List<CropCareItem>,
    private val isAdmin: Boolean,
    private val onEditClick: (CropCareItem) -> Unit,
    private val onDeleteClick: (CropCareItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SOLUTION = 0
        private const val TYPE_AD = 1
    }

    private val expandedPositions = mutableSetOf<Int>()

    fun updateList(newList: List<CropCareItem>) {
        items = newList
        expandedPositions.clear()
        notifyDataSetChanged()
    }

    inner class CropCareViewHolder(val binding: ItemCropCareSolutionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CropCareItem, adapterPos: Int, realIndex: Int) {
            val context = binding.root.context
            val activeLang = LocaleHelper.getPersistedLanguage(context)
            val isExpanded = expandedPositions.contains(realIndex)
            val rawCategory = item.category.ifBlank { "सामान्य" }
            val transCategory = TranslationHelper.getDirectTranslation(rawCategory, activeLang, context)
                ?: TranslationHelper.getDirectTranslation(rawCategory.trim(), activeLang, context)
                ?: rawCategory

            binding.tvCategoryBadge.text = transCategory
            binding.tvProblemTitle.text = item.title
            binding.tvProblemDesc.text = item.problem
            binding.tvSolutionText.text = item.solution
            binding.tvSolutionHeader.text = TranslationHelper.getDirectTranslation("समाधान", activeLang, context) ?: "समाधान"
            binding.tvExpandToggleText.text = if (isExpanded) 
                (TranslationHelper.getDirectTranslation("समाधान छुपाये", activeLang, context) ?: "समाधान छुपाये") 
            else 
                (TranslationHelper.getDirectTranslation("समाधान देखे ", activeLang, context) ?: "समाधान देखे ")

            if (activeLang != "hi") {
                CoroutineScope(Dispatchers.Main).launch {
                    val catDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, rawCategory, activeLang) }
                    val titleDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, item.title, activeLang) }
                    val probDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, item.problem, activeLang) }
                    val solDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, item.solution, activeLang) }
                    val headerDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, "समाधान", activeLang) }
                    val toggleStr = if (isExpanded) "समाधान छुपाये" else "समाधान देखे "
                    val toggleDeferred = async(Dispatchers.IO) { TranslationHelper.translateText(context, toggleStr, activeLang) }

                    val transCat = catDeferred.await()
                    val transTitle = titleDeferred.await()
                    val transProb = probDeferred.await()
                    val transSol = solDeferred.await()
                    val transHeader = headerDeferred.await()
                    val transToggle = toggleDeferred.await()

                    val currentPos = bindingAdapterPosition
                    if (currentPos == adapterPos || currentPos != RecyclerView.NO_POSITION) {
                        binding.tvCategoryBadge.text = transCat
                        binding.tvProblemTitle.text = transTitle
                        binding.tvProblemDesc.text = transProb
                        binding.tvSolutionText.text = transSol
                        binding.tvSolutionHeader.text = transHeader
                        binding.tvExpandToggleText.text = transToggle
                    }
                }
            }

            if (isExpanded) {
                binding.layoutSolutionContainer.visibility = View.VISIBLE
                binding.ivExpandArrow.rotation = 180f
            } else {
                binding.layoutSolutionContainer.visibility = View.GONE
                binding.ivExpandArrow.rotation = 0f
            }

            val toggleExpand = {
                if (expandedPositions.contains(realIndex)) {
                    expandedPositions.remove(realIndex)
                } else {
                    expandedPositions.add(realIndex)
                }
                notifyItemChanged(adapterPos)
            }

            binding.layoutExpandFooter.setOnClickListener { toggleExpand() }
            binding.root.setOnClickListener { toggleExpand() }

            if (isAdmin) {
                binding.root.setOnLongClickListener {
                    val options = arrayOf("संपादित करें (Edit)", "हटाएं (Delete)")
                    AlertDialog.Builder(binding.root.context)
                        .setTitle("Admin Actions")
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> onEditClick(item)
                                1 -> {
                                    AlertDialog.Builder(binding.root.context)
                                        .setTitle("Delete Solution")
                                        .setMessage("Are you sure you want to delete '${item.title}'?")
                                        .setPositiveButton("Delete") { _, _ -> onDeleteClick(item) }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            }
                        }
                        .show()
                    true
                }
            }
        }
    }

    class AdViewHolder(val binding: ItemInfeedAdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (items.isNotEmpty() && (position + 1) % 5 == 0) TYPE_AD else TYPE_SOLUTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_AD) {
            val binding = ItemInfeedAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdViewHolder(binding)
        } else {
            val binding = ItemCropCareSolutionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            CropCareViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentPos = if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) holder.bindingAdapterPosition else position

        if (getItemViewType(currentPos) == TYPE_AD) {
            val adHolder = holder as AdViewHolder
            val context = holder.itemView.context
            try {
                val adLoader = AdLoader.Builder(context, "ca-app-pub-3054731531860427/8792970999")
                    .forNativeAd { nativeAd ->
                        adHolder.binding.myTemplate.setNativeAd(nativeAd)
                        adHolder.itemView.visibility = View.VISIBLE
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            adHolder.itemView.visibility = View.GONE
                        }
                    })
                    .build()
                adLoader.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                adHolder.itemView.visibility = View.GONE
            }
            return
        }

        val realIndex = currentPos - (currentPos / 5)
        if (realIndex !in items.indices) return

        val item = items[realIndex]
        val solutionHolder = holder as CropCareViewHolder
        solutionHolder.bind(item, currentPos, realIndex)
    }

    override fun getItemCount(): Int {
        return if (items.isEmpty()) 0 else items.size + (items.size / 4)
    }
}
