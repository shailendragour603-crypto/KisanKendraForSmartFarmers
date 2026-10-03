package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
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
    private val onDeleteClick: (KhataItem) -> Unit
) : RecyclerView.Adapter<KhataAdapter.ViewHolder>() {

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_khata_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)

        holder.tvKhataIcon.text = if (item.isIncome) "💰" else "💸"
        holder.tvCropAndCategory.text = "${item.cropName} • ${item.category}"
        
        val noteText = if (item.note.isNotBlank()) " (${item.note})" else ""
        holder.tvStageAndNote.text = "📍 ${item.stage}$noteText"
        holder.tvDate.text = item.dateStr.ifBlank { "Today" }

        if (activeLang != "en") {
            CoroutineScope(Dispatchers.Main).launch {
                val transCrop = TranslationHelper.translateText(context, item.cropName, activeLang)
                val transStage = TranslationHelper.translateText(context, item.stage, activeLang)
                val transNote = if (item.note.isNotBlank()) TranslationHelper.translateText(context, item.note, activeLang) else ""

                holder.tvCropAndCategory.text = "$transCrop • ${item.category}"
                val fmtNote = if (transNote.isNotBlank()) " ($transNote)" else ""
                holder.tvStageAndNote.text = "📍 $transStage$fmtNote"
            }
        }

        val formattedAmount = numberFormat.format(item.amount)
        if (item.isIncome) {
            holder.tvAmount.text = "+ $formattedAmount"
            holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.price_modal_text))
        } else {
            holder.tvAmount.text = "- $formattedAmount"
            holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.price_min_text))
        }

        holder.btnEdit.setOnClickListener { onEditClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<KhataItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}