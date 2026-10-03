package shop.sabjiwala.kisankendra

import android.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.ItemCompactNewsBinding
import shop.sabjiwala.kisankendra.model.NewsItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Locale

class CompactNewsAdapter(
    private val newsList: List<NewsItem>,
    private val onItemClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<CompactNewsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCompactNewsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompactNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = newsList[position]
        val context = holder.itemView.context
        val binding = holder.binding

        binding.tvCompactNewsTitle.text = item.title
        binding.tvCompactNewsCategory.text = item.category.ifBlank { "Agriculture" }

        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val formattedDate = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "Today"
        binding.tvCompactNewsDate.text = "📅 $formattedDate"

        if (item.imageUrl.isNotBlank()) {
            binding.ivCompactNewsImage.visibility = View.VISIBLE
            Glide.with(context)
                .load(item.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_menu_gallery)
                .into(binding.ivCompactNewsImage)
        } else {
            binding.ivCompactNewsImage.visibility = View.GONE
        }

        val activeLang = LocaleHelper.getPersistedLanguage(context)
        if (activeLang != "en") {
            CoroutineScope(Dispatchers.Main).launch {
                val translatedTitle = TranslationHelper.translateText(context, item.title, activeLang)
                binding.tvCompactNewsTitle.text = translatedTitle
            }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = newsList.size
}
