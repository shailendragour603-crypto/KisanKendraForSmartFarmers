package shop.sabjiwala.kisankendra

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.ItemInfeedAdBinding
import shop.sabjiwala.kisankendra.databinding.ItemNewsCardBinding
import shop.sabjiwala.kisankendra.model.NewsItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.RichTextHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Locale

class NewsAdapter(
    private var newsList: List<NewsItem>,
    private val onLikeClick: (NewsItem) -> Unit,
    private val onCommentClick: (NewsItem) -> Unit,
    private val onShareClick: (NewsItem) -> Unit,
    private val onItemClick: (NewsItem) -> Unit,
    var isUserAdmin: Boolean = false,
    private val onAdminEditClick: ((NewsItem) -> Unit)? = null,
    private val onAdminDeleteClick: ((NewsItem) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_NEWS = 0
        private const val TYPE_AD = 1
    }

    inner class NewsViewHolder(val binding: ItemNewsCardBinding) : RecyclerView.ViewHolder(binding.root)
    inner class AdViewHolder(val binding: ItemInfeedAdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if ((position + 1) % 6 == 0) TYPE_AD else TYPE_NEWS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_AD) {
            val binding = ItemInfeedAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdViewHolder(binding)
        } else {
            val binding = ItemNewsCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            NewsViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentPos = if (holder.adapterPosition != RecyclerView.NO_POSITION) holder.adapterPosition else position

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
        if (realIndex !in newsList.indices) return

        val item = newsList[realIndex]
        val newsHolder = holder as NewsViewHolder
        val binding = newsHolder.binding
        val context = holder.itemView.context
        val activeLang = LocaleHelper.getPersistedLanguage(context)

        val descriptionExcerpt = RichTextHelper.getExcerptWithoutFirstSubtitle(item.description)

        binding.tvNewsTitle.text = item.title
        binding.tvNewsDescription.text = RichTextHelper.formatRichText(descriptionExcerpt)
        binding.tvCategoryBadge.text = item.category.ifBlank { "Agriculture" }

        // Dynamic AI translation for non-Hindi active languages
        if (activeLang != "hi") {
            CoroutineScope(Dispatchers.Main).launch {
                val translatedTitle = TranslationHelper.translateText(context, item.title, activeLang)
                binding.tvNewsTitle.text = translatedTitle

                val translatedDesc = TranslationHelper.translateText(context, descriptionExcerpt, activeLang)
                val cleanTranslatedDesc = RichTextHelper.getExcerptWithoutFirstSubtitle(translatedDesc)
                binding.tvNewsDescription.text = RichTextHelper.formatRichText(cleanTranslatedDesc)
            }
        }

        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        binding.tvNewsDate.text = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "📅 Today"

        if (item.imageUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .centerCrop()
                .into(binding.ivNewsImage)
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val hasLiked = currentUid.isNotBlank() && item.likedBy.contains(currentUid)

        val defaultColor = binding.btnCommentNews.currentTextColor
        if (hasLiked) {
            binding.btnLikeNews.text = if (item.likesCount > 0) "${item.likesCount}" else ""
            binding.btnLikeNews.setIconResource(R.drawable.ic_feed_heart_liked)
            binding.btnLikeNews.setTextColor(defaultColor)
            binding.btnLikeNews.strokeColor = ColorStateList.valueOf(defaultColor)
        } else {
            binding.btnLikeNews.text = if (item.likesCount > 0) "${item.likesCount}" else ""
            binding.btnLikeNews.setIconResource(R.drawable.ic_feed_heart)
            binding.btnLikeNews.setTextColor(defaultColor)
            binding.btnLikeNews.strokeColor = ColorStateList.valueOf(defaultColor)
        }

        binding.btnCommentNews.text = if (item.commentsCount > 0) "${item.commentsCount}" else ""
        binding.btnShareNews.text = if (item.sharesCount > 0) "${item.sharesCount}" else ""

        binding.btnLikeNews.setOnClickListener {
            onLikeClick(item)
        }
        binding.btnCommentNews.setOnClickListener { onCommentClick(item) }
        binding.btnShareNews.setOnClickListener { onShareClick(item) }
        binding.root.setOnClickListener { onItemClick(item) }

        if (isUserAdmin && (onAdminEditClick != null || onAdminDeleteClick != null)) {
            binding.root.setOnLongClickListener { view ->
                val ctx = view.context
                val density = ctx.resources.displayMetrics.density

                val popupView = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_compact_popup)
                    val p = (2 * density).toInt()
                    setPadding(p, p, p, p)
                }

                val popupWindow = PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                ).apply {
                    elevation = 10 * density
                    isOutsideTouchable = true
                    isFocusable = true
                }

                fun createOptionRow(iconRes: Int, iconTint: Int, text: String, textColor: Int, onClick: () -> Unit): View {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = ContextCompat.getDrawable(ctx, android.R.drawable.list_selector_background)
                        val padH = (8 * density).toInt()
                        val padV = (6 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        isClickable = true
                        isFocusable = true
                    }
                    val iv = ImageView(ctx).apply {
                        val size = (16 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = (6 * density).toInt()
                        }
                        setImageResource(iconRes)
                        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(iconTint))
                    }
                    val tv = TextView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        this.text = text
                        setTextColor(textColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    row.addView(iv)
                    row.addView(tv)
                    row.setOnClickListener {
                        popupWindow.dismiss()
                        onClick()
                    }
                    return row
                }

                if (onAdminEditClick != null) {
                    val editRow = createOptionRow(R.drawable.ic_edit, Color.parseColor("#81C784"), "Edit Article", Color.WHITE) {
                        onAdminEditClick.invoke(item)
                    }
                    popupView.addView(editRow)
                }

                if (onAdminDeleteClick != null) {
                    if (popupView.childCount > 0) {
                        val divider = View(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                                setMargins((4 * density).toInt(), (1 * density).toInt(), (4 * density).toInt(), (1 * density).toInt())
                            }
                            setBackgroundColor(Color.parseColor("#323236"))
                        }
                        popupView.addView(divider)
                    }
                    val deleteRow = createOptionRow(R.drawable.ic_delete, Color.parseColor("#FF453A"), "Delete Article", Color.parseColor("#FF453A")) {
                        onAdminDeleteClick.invoke(item)
                    }
                    popupView.addView(deleteRow)
                }

                popupWindow.showAsDropDown(view, (20 * density).toInt(), (-40 * density).toInt())
                true
            }
        } else {
            binding.root.setOnLongClickListener(null)
        }
    }

    override fun getItemCount(): Int {
        if (newsList.isEmpty()) return 0
        val totalAds = newsList.size / 5
        return newsList.size + totalAds
    }

    fun setAdminState(admin: Boolean) {
        if (isUserAdmin != admin) {
            isUserAdmin = admin
            notifyDataSetChanged()
        }
    }

    fun updateData(newList: List<NewsItem>) {
        newsList = newList
        notifyDataSetChanged()
    }

    fun notifyItemLiked(itemId: String) {
        val index = newsList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val totalAdsBefore = index / 5
            val adapterPosition = index + totalAdsBefore
            notifyItemChanged(adapterPosition)
        }
    }
}
