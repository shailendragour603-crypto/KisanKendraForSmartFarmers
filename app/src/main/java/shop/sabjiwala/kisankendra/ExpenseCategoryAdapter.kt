package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.NumberFormat
import java.util.Locale

data class ExpenseCategoryItem(
    val categoryName: String,
    val amount: Double,
    val percent: Int,
    val iconRes: Int
)

class ExpenseCategoryAdapter(
    private var items: List<ExpenseCategoryItem> = emptyList()
) : RecyclerView.Adapter<ExpenseCategoryAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivExpenseCategoryIcon)
        val tvName: TextView = view.findViewById(R.id.tvExpenseCategoryName)
        val tvAmount: TextView = view.findViewById(R.id.tvExpenseCategoryAmount)
        val tvPercent: TextView = view.findViewById(R.id.tvExpenseCategoryPercent)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progressExpenseCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense_category_row, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)
        val displayCategoryName = TranslationHelper.getTranslatedCategoryName(item.categoryName, activeLang, context)

        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvName.text = displayCategoryName

        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val currencyFormatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }
        holder.tvAmount.text = currencyFormatter.format(item.amount)
        holder.tvPercent.text = String.format(Locale.ENGLISH, "%d%%", item.percent)
        holder.progress.progress = item.percent.coerceIn(0, 100)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ExpenseCategoryItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
