package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import shop.sabjiwala.kisankendra.model.KhataItem
import java.text.NumberFormat
import java.util.Locale

class RecentEntriesAdapter(
    private var items: List<KhataItem> = emptyList()
) : RecyclerView.Adapter<RecentEntriesAdapter.EntryViewHolder>() {

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTypeBadge: TextView = view.findViewById(R.id.tvEntryTypeBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvEntryTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvEntrySubtitle)
        val tvAmount: TextView = view.findViewById(R.id.tvEntryAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_diary_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val currencyFormatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }

        val crop = item.cropName.ifBlank { "Farm" }
        val category = item.category.ifBlank { "General" }
        holder.tvTitle.text = "$crop • $category"

        val stage = item.stage.ifBlank { "General" }
        val date = item.dateStr.ifBlank { "Recently" }
        holder.tvSubtitle.text = "$stage • $date"

        val colorIncome = ContextCompat.getColor(context, R.color.price_modal_text)
        val colorExpense = ContextCompat.getColor(context, R.color.price_min_text)

        if (item.isIncome) {
            holder.tvTypeBadge.text = "INCOME"
            holder.tvTypeBadge.setTextColor(colorIncome)
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_income)

            holder.tvAmount.text = "+ ${currencyFormatter.format(item.amount)}"
            holder.tvAmount.setTextColor(colorIncome)
        } else {
            holder.tvTypeBadge.text = "EXPENSE"
            holder.tvTypeBadge.setTextColor(colorExpense)
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_expense)

            holder.tvAmount.text = "- ${currencyFormatter.format(item.amount)}"
            holder.tvAmount.setTextColor(colorExpense)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<KhataItem>) {
        this.items = newItems.take(6)
        notifyDataSetChanged()
    }
}