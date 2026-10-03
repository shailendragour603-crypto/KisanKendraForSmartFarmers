package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

data class CropPerformanceItem(
    val cropName: String,
    val netProfit: Double,
    val totalIncome: Double,
    val totalExpenses: Double,
    val profitPerAcre: Double = 0.0
)

class CropPerformanceAdapter(
    private var items: List<CropPerformanceItem> = emptyList()
) : RecyclerView.Adapter<CropPerformanceAdapter.CropViewHolder>() {

    class CropViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCropIcon: ImageView = view.findViewById(R.id.ivCropIcon)
        val tvCropName: TextView = view.findViewById(R.id.tvCropName)
        val tvCropProfit: TextView = view.findViewById(R.id.tvCropProfit)
        val tvCropRoi: TextView = view.findViewById(R.id.tvCropRoi)
        val tvCropProfitPerAcre: TextView = view.findViewById(R.id.tvCropProfitPerAcre)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CropViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crop_performance_card, parent, false)
        return CropViewHolder(view)
    }

    override fun onBindViewHolder(holder: CropViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val displayCropName = getCleanCropName(item.cropName)
        holder.tvCropName.text = displayCropName

        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val formatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }
        val formattedProfit = formatter.format(item.netProfit)
        holder.tvCropProfit.text = formattedProfit

        val colorGreen = ContextCompat.getColor(context, R.color.price_max_text)
        val colorRed = ContextCompat.getColor(context, R.color.price_min_text)

        if (item.netProfit < 0) {
            holder.tvCropProfit.setTextColor(colorRed)
        } else {
            holder.tvCropProfit.setTextColor(colorGreen)
        }

        val roiPercent = if (item.totalExpenses > 0) ((item.netProfit / item.totalExpenses) * 100).toInt() else 0
        holder.tvCropRoi.text = String.format(Locale.ENGLISH, "ROI: %+d%%", roiPercent)
        if (roiPercent >= 0) {
            holder.tvCropRoi.setTextColor(colorGreen)
        } else {
            holder.tvCropRoi.setTextColor(colorRed)
        }

        val formattedPpa = formatter.format(item.profitPerAcre)
        holder.tvCropProfitPerAcre.text = "$formattedPpa / Acre"

        val iconRes = getCropIconRes(item.cropName)
        holder.ivCropIcon.setImageResource(iconRes)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<CropPerformanceItem>) {
        this.items = newItems.sortedByDescending { it.netProfit }
        notifyDataSetChanged()
    }

    private fun getCleanCropName(rawName: String): String {
        return rawName.split("/").firstOrNull()?.trim() ?: rawName
    }

    private fun getCropIconRes(cropName: String): Int {
        val lower = cropName.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("soybean") || lower.contains("सोयाबीन") -> R.drawable.ic_crop_soybean
            lower.contains("cotton") || lower.contains("कपास") -> R.drawable.ic_crop_cotton
            lower.contains("wheat") || lower.contains("गेहूँ") -> R.drawable.ic_crop_wheat
            else -> R.drawable.ic_crop_generic
        }
    }
}
