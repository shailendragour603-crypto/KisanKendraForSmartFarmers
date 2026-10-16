package shop.sabjiwala.kisankendra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.NumberFormat
import java.util.Locale

class CropAnalyticsAdapter(
    private var cycles: List<CropCycle>,
    private var khataItems: List<KhataItem>,
    private val onToggleStatusClick: (CropCycle) -> Unit,
    private val onDeleteCropClick: (CropCycle) -> Unit
) : RecyclerView.Adapter<CropAnalyticsAdapter.ViewHolder>() {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvAnalyticsCropTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvAnalyticsStatusBadge)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteCropCycle)
        val tvExpense: TextView = view.findViewById(R.id.tvAnalyticsExpense)
        val tvIncome: TextView = view.findViewById(R.id.tvAnalyticsIncome)
        val tvNetProfit: TextView = view.findViewById(R.id.tvAnalyticsNetProfit)
        val tvNetProfitLabel: TextView = view.findViewById(R.id.tvAnalyticsNetProfitLabel)
        val tvProfitPerAcre: TextView = view.findViewById(R.id.tvAnalyticsProfitPerAcre)
        val btnToggleStatus: Button = view.findViewById(R.id.btnToggleCropStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crop_analytics_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cycle = cycles[position]
        val context = holder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)

        val cleanCrop = cycle.cropName
        val displayCrop = TranslationHelper.getTranslatedCropName(cleanCrop, activeLang, context)
        val displaySeason = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, context)
        holder.tvTitle.text = "$displayCrop ($displaySeason) • ${cycle.areaAcres} Acre"

        if (cycle.isCompleted) {
            holder.tvStatus.text = "🔵 Harvested"
            holder.tvStatus.setTextColor(Color.parseColor("#1565C0"))
            holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
            holder.btnToggleStatus.text = "Mark Active"
        } else {
            holder.tvStatus.text = "🟢 Active"
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
            holder.btnToggleStatus.text = "Mark Harvested"
        }

        val cycleItems = khataItems.filter { it.cropCycleId == cycle.id || (it.cropCycleId.isBlank() && it.cropName.equals(cycle.cropName, ignoreCase = true)) }
        val totalExpense = cycleItems.filter { !it.isIncome }.sumOf { it.amount }
        val totalIncome = cycleItems.filter { it.isIncome }.sumOf { it.amount }
        val netProfit = totalIncome - totalExpense

        holder.tvExpense.text = numberFormat.format(totalExpense)
        holder.tvIncome.text = numberFormat.format(totalIncome)

        if (netProfit >= 0) {
            holder.tvNetProfit.text = "+ ${numberFormat.format(netProfit)}"
            holder.tvNetProfit.setTextColor(ContextCompat.getColor(context, R.color.price_max_text))
            holder.tvNetProfitLabel.text = "Net Profit"
            holder.tvNetProfitLabel.setTextColor(ContextCompat.getColor(context, R.color.price_max_text))
            holder.tvNetProfitLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_color_graphs, 0, 0, 0)
            holder.tvNetProfitLabel.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()
        } else {
            holder.tvNetProfit.text = "- ${numberFormat.format(-netProfit)}"
            holder.tvNetProfit.setTextColor(ContextCompat.getColor(context, R.color.price_min_text))
            holder.tvNetProfitLabel.text = "Net Loss"
            holder.tvNetProfitLabel.setTextColor(ContextCompat.getColor(context, R.color.price_min_text))
            holder.tvNetProfitLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
            holder.tvNetProfitLabel.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()
        }

        val profitPerAcre = if (cycle.areaAcres > 0) netProfit / cycle.areaAcres else netProfit
        val ppaFormatted = numberFormat.format(profitPerAcre)
        holder.tvProfitPerAcre.text = "Profit/Acre: $ppaFormatted / Acre"
        holder.tvProfitPerAcre.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
        holder.tvProfitPerAcre.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()

        if (activeLang != "en") {
            CoroutineScope(Dispatchers.Main).launch {
                val transCrop = TranslationHelper.getTranslatedCropName(cycle.cropName, activeLang, context)
                val transSeason = TranslationHelper.getTranslatedSeasonName(cycle.seasonName, activeLang, context)
                holder.tvTitle.text = "$transCrop ($transSeason) • ${cycle.areaAcres} Acre"

                if (cycle.isCompleted) {
                    val transHarv = TranslationHelper.translateText(context, "Harvested", activeLang)
                    val transBtnActive = TranslationHelper.translateText(context, "Mark Active", activeLang)
                    holder.tvStatus.text = "🔵 $transHarv"
                    holder.btnToggleStatus.text = transBtnActive
                } else {
                    val transAct = TranslationHelper.translateText(context, "Active", activeLang)
                    val transBtnHarv = TranslationHelper.translateText(context, "Mark Harvested", activeLang)
                    holder.tvStatus.text = "🟢 $transAct"
                    holder.btnToggleStatus.text = transBtnHarv
                }

                val transPpaLabel = TranslationHelper.translateText(context, "Profit/Acre", activeLang)
                holder.tvProfitPerAcre.text = "$transPpaLabel: $ppaFormatted / Acre"
                holder.tvProfitPerAcre.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, 0, 0)
                holder.tvProfitPerAcre.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()
            }
        }

        holder.btnToggleStatus.setOnClickListener {
            onToggleStatusClick(cycle)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteCropClick(cycle)
        }
    }

    override fun getItemCount(): Int = cycles.size

    fun updateData(newCycles: List<CropCycle>, newItems: List<KhataItem>) {
        this.cycles = newCycles
        this.khataItems = newItems
        notifyDataSetChanged()
    }
}
