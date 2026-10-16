package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.NumberFormat
import java.util.Locale

class KhataAdapter(
    private var items: List<KhataItem>,
    private val onEditClick: (KhataItem) -> Unit,
    private val onDeleteClick: (KhataItem) -> Unit,
    private var totalCount: Int = items.size,
    private val onLoadMore: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_FOOTER = 1
    }

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvKhataIcon: TextView = view.findViewById(R.id.tvKhataIcon)
        val tvCropAndCategory: TextView = view.findViewById(R.id.tvKhataCropAndCategory)
        val tvStageAndNote: TextView = view.findViewById(R.id.tvKhataStageAndNote)
        val tvAmount: TextView = view.findViewById(R.id.tvKhataAmount)
        val tvDate: TextView = view.findViewById(R.id.tvKhataDate)
        val btnEdit: ImageButton = view.findViewById(R.id.btnKhataEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnKhataDelete)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnLoadMore: MaterialButton = view.findViewById(R.id.btnLoadMore)
        val tvPaginationCount: TextView = view.findViewById(R.id.tvPaginationCount)
    }

    override fun getItemViewType(position: Int): Int {
        return if (onLoadMore != null && position == items.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_FOOTER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_load_more_footer, parent, false)
            FooterViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_khata_entry, parent, false)
            ViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterViewHolder) {
            val context = holder.itemView.context
            val activeLang = LocaleHelper.getPersistedLanguage(context)

            val transLoadMore = TranslationHelper.getDirectTranslation("Load More Entries", activeLang, context)
                ?: if (activeLang == "hi") "+ और पुरानी एन्ट्री देखें" else "+ Load More Entries"
            holder.btnLoadMore.text = transLoadMore

            if (items.size < totalCount) {
                holder.btnLoadMore.visibility = View.VISIBLE
                if (activeLang == "hi") {
                    holder.tvPaginationCount.text = "$totalCount में से ${items.size} एन्ट्री दिख रही हैं"
                } else {
                    holder.tvPaginationCount.text = "Showing ${items.size} of $totalCount entries"
                }
            } else {
                holder.btnLoadMore.visibility = View.GONE
                if (activeLang == "hi") {
                    holder.tvPaginationCount.text = "सभी $totalCount एन्ट्री दिखाई जा रही हैं"
                } else {
                    holder.tvPaginationCount.text = "Showing all $totalCount entries"
                }
            }

            if (activeLang != "en" && activeLang != "hi") {
                CoroutineScope(Dispatchers.Main).launch {
                    val asyncBtn = TranslationHelper.translateText(context, "Load More Entries", activeLang)
                    if (asyncBtn.isNotBlank()) {
                        holder.btnLoadMore.text = "+ $asyncBtn"
                    }
                }
            }

            holder.btnLoadMore.setOnClickListener {
                onLoadMore?.invoke()
            }
            return
        }

        val itemHolder = holder as ViewHolder
        val item = items[position]
        val context = itemHolder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)

        val transCrop = TranslationHelper.getTranslatedCropName(item.cropName, activeLang, context)
        val transCat = TranslationHelper.getTranslatedCategoryName(item.category, activeLang, context)
        val transStage = TranslationHelper.getTranslatedStageName(item.stage, activeLang, context)
        val transDate = TranslationHelper.getTranslatedDate(item.dateStr.ifBlank { "Today" }, activeLang)

        itemHolder.tvKhataIcon.text = if (item.isIncome) "💰" else "💸"
        itemHolder.tvCropAndCategory.text = "$transCrop • $transCat"

        val noteText = if (item.note.isNotBlank()) " (${item.note})" else ""
        itemHolder.tvStageAndNote.text = "📍 $transStage$noteText"
        itemHolder.tvDate.text = transDate

        if (activeLang != "en" && activeLang != "hi") {
            CoroutineScope(Dispatchers.Main).launch {
                val transCropAsync = TranslationHelper.translateText(context, item.cropName, activeLang)
                val transCatAsync = TranslationHelper.translateText(context, item.category, activeLang)
                val transStageAsync = TranslationHelper.translateText(context, item.stage, activeLang)
                val transNote = if (item.note.isNotBlank()) TranslationHelper.translateText(context, item.note, activeLang) else ""

                itemHolder.tvCropAndCategory.text = "${transCropAsync.ifBlank { transCrop }} • ${transCatAsync.ifBlank { transCat }}"
                val fmtNote = if (transNote.isNotBlank()) " ($transNote)" else ""
                itemHolder.tvStageAndNote.text = "📍 $transStageAsync$fmtNote"
            }
        }

        val formattedAmount = numberFormat.format(item.amount)
        if (item.isIncome) {
            itemHolder.tvAmount.text = "+ $formattedAmount"
            itemHolder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.price_modal_text))
        } else {
            itemHolder.tvAmount.text = "- $formattedAmount"
            itemHolder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.price_min_text))
        }

        itemHolder.btnEdit.setOnClickListener { onEditClick(item) }
        itemHolder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int {
        return if (onLoadMore != null && totalCount > 0) {
            items.size + 1
        } else {
            items.size
        }
    }

    fun updateData(newItems: List<KhataItem>, totalCount: Int = newItems.size) {
        this.items = newItems
        this.totalCount = totalCount
        notifyDataSetChanged()
    }
}
