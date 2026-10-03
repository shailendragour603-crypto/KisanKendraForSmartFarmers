package shop.sabjiwala.kisankendra

import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.ItemInfeedAdBinding
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MandiCacheHelper
import shop.sabjiwala.kisankendra.utils.MandiUtils
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import kotlin.math.abs

class MandiAdapter(
    private var records: List<MandiRecord>,
    private var totalFilteredSize: Int = 0,
    private val onShareClick: (MandiRecord) -> Unit,
    private val onItemClick: ((MandiRecord) -> Unit)? = null,
    private val onFavoriteToggle: ((MandiRecord, Boolean) -> Unit)? = null,
    private val onLoadMore: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MANDI = 0
        private const val TYPE_AD = 1
        private const val TYPE_FOOTER = 2
    }

    class MandiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvCommodity: TextView = view.findViewById(R.id.tvCommodity)
        val layoutPriceDiff: View = view.findViewById(R.id.layoutPriceDiff)
        val tvPriceDiffValue: TextView = view.findViewById(R.id.tvPriceDiffValue)
        val tvPriceDiffSub: TextView = view.findViewById(R.id.tvPriceDiffSub)
        val tvMarketDetails: TextView = view.findViewById(R.id.tvMarketDetails)
        val tvMinPrice: TextView = view.findViewById(R.id.tvMinPrice)
        val tvModalPrice: TextView = view.findViewById(R.id.tvModalPrice)
        val tvMaxPrice: TextView = view.findViewById(R.id.tvMaxPrice)
        val layoutChart: View = view.findViewById(R.id.layoutChart)
        val ivFavorite: ImageView = view.findViewById(R.id.ivFavorite)
    }

    class AdViewHolder(val binding: ItemInfeedAdBinding) : RecyclerView.ViewHolder(binding.root)

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnLoadMore: MaterialButton = view.findViewById(R.id.btnLoadMore)
        val tvPaginationCount: TextView = view.findViewById(R.id.tvPaginationCount)
    }

    private val hasMore: Boolean
        get() = records.size < totalFilteredSize

    override fun getItemViewType(position: Int): Int {
        val baseCount = records.size + (records.size / 5)
        if (hasMore && position == baseCount) {
            return TYPE_FOOTER
        }
        return if (records.isNotEmpty() && (position + 1) % 6 == 0) TYPE_AD else TYPE_MANDI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_AD -> {
                val binding = ItemInfeedAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AdViewHolder(binding)
            }
            TYPE_FOOTER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_load_more_footer, parent, false)
                FooterViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_mandi_rate, parent, false)
                MandiViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentPos = if (holder.adapterPosition != RecyclerView.NO_POSITION) holder.adapterPosition else position

        if (getItemViewType(currentPos) == TYPE_FOOTER) {
            val footerHolder = holder as FooterViewHolder
            footerHolder.tvPaginationCount.text = "Showing ${records.size} of $totalFilteredSize rates"
            footerHolder.btnLoadMore.setOnClickListener {
                onLoadMore?.invoke()
            }
            return
        }

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

        val realIndex = currentPos - (currentPos / 6)
        if (realIndex !in records.indices) return

        val item = records[realIndex]
        val mandiHolder = holder as MandiViewHolder
        val context = holder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)

        val emoji = MandiUtils.getEmoji(item.commodity)
        mandiHolder.tvEmoji.text = emoji

        val rawMarket = item.market.orEmpty().trim()
        val rawDistrict = item.district.orEmpty().trim()
        val displayLoc = if (rawMarket.equals(rawDistrict, ignoreCase = true) || rawDistrict.isBlank()) {
            rawMarket.ifBlank { rawDistrict }
        } else {
            "$rawMarket, $rawDistrict"
        }

        mandiHolder.tvCommodity.text = item.commodity ?: "N/A"
        mandiHolder.tvMarketDetails.text = displayLoc

        if (activeLang != "en") {
            val rawCommodity = item.commodity.orEmpty()

            CoroutineScope(Dispatchers.Main).launch {
                val transCommodity = TranslationHelper.translateText(context, rawCommodity, activeLang)
                val transMarket = TranslationHelper.translateText(context, rawMarket, activeLang)
                val transDistrict = TranslationHelper.translateText(context, rawDistrict, activeLang)

                val transLoc = if (transMarket.equals(transDistrict, ignoreCase = true) || transDistrict.isBlank()) {
                    transMarket.ifBlank { transDistrict }
                } else {
                    "$transMarket, $transDistrict"
                }

                mandiHolder.tvCommodity.text = transCommodity.ifBlank { rawCommodity }
                mandiHolder.tvMarketDetails.text = transLoc
            }
        }

        val min = item.minPrice?.toDoubleOrNull() ?: 0.0
        val max = item.maxPrice?.toDoubleOrNull() ?: 0.0
        val modal = item.modalPrice?.toDoubleOrNull() ?: 0.0

        mandiHolder.tvMinPrice.text = "₹${min.toInt()}"
        mandiHolder.tvMaxPrice.text = "₹${max.toInt()}"
        mandiHolder.tvModalPrice.text = "₹${modal.toInt()}"

        // Price comparison diff logic
        val diff = item.priceDiff
        mandiHolder.layoutPriceDiff.visibility = View.VISIBLE
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val posColor = if (isNight) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")
        val negColor = if (isNight) Color.parseColor("#FF8A80") else Color.parseColor("#C62828")
        val subColor = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")

        mandiHolder.tvPriceDiffSub.setTextColor(subColor)

        if (diff != null && diff != 0.0) {
            if (diff > 0) {
                mandiHolder.tvPriceDiffValue.text = "▲ +₹${diff.toInt()}"
                mandiHolder.tvPriceDiffValue.setTextColor(posColor)
            } else {
                mandiHolder.tvPriceDiffValue.text = "▼ -₹${abs(diff.toInt())}"
                mandiHolder.tvPriceDiffValue.setTextColor(negColor)
            }
        } else {
            mandiHolder.tvPriceDiffValue.text = "Stable"
            mandiHolder.tvPriceDiffValue.setTextColor(posColor)
        }

        // Favorite Heart logic
        val docId = MandiCacheHelper.getDocId(item)
        var isFav = MandiCacheHelper.isFavorite(context, docId)
        mandiHolder.ivFavorite.setImageResource(if (isFav) R.drawable.ic_feed_heart_liked else R.drawable.ic_feed_heart)

        mandiHolder.ivFavorite.setOnClickListener {
            val isFavNow = MandiCacheHelper.toggleFavorite(context, docId)
            isFav = isFavNow
            mandiHolder.ivFavorite.setImageResource(if (isFavNow) R.drawable.ic_feed_heart_liked else R.drawable.ic_feed_heart)

            mandiHolder.ivFavorite.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(120)
                .withEndAction {
                    mandiHolder.ivFavorite.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
                .start()

            onFavoriteToggle?.invoke(item, isFavNow)
        }

        mandiHolder.layoutChart.setOnClickListener { onItemClick?.invoke(item) }
        mandiHolder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    override fun getItemCount(): Int {
        if (records.isEmpty()) return 0
        val baseCount = records.size + (records.size / 5)
        return if (hasMore) baseCount + 1 else baseCount
    }

    fun updateData(newRecords: List<MandiRecord>, newTotalSize: Int) {
        this.records = newRecords
        this.totalFilteredSize = newTotalSize
        notifyDataSetChanged()
    }
}
