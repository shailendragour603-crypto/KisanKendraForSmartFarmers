package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import shop.sabjiwala.kisankendra.databinding.ItemAdminTranslationBinding

data class TranslationEntry(
    val englishKey: String,
    val translation: String
)

class AdminTranslationAdapter(
    private var items: List<TranslationEntry>,
    private val onEditClick: (TranslationEntry) -> Unit,
    private val onDeleteClick: (TranslationEntry) -> Unit
) : RecyclerView.Adapter<AdminTranslationAdapter.TranslationViewHolder>() {

    class TranslationViewHolder(val binding: ItemAdminTranslationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationViewHolder {
        val binding = ItemAdminTranslationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TranslationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
        val entry = items[position]
        holder.binding.tvEnglishKey.text = entry.englishKey
        holder.binding.tvTranslation.text = entry.translation

        holder.binding.btnEditTranslation.setOnClickListener {
            onEditClick(entry)
        }

        holder.binding.btnDeleteTranslation.setOnClickListener {
            onDeleteClick(entry)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TranslationEntry>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
