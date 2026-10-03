package shop.sabjiwala.kisankendra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import shop.sabjiwala.kisankendra.databinding.ItemAdminFeedbackBinding
import shop.sabjiwala.kisankendra.model.FeedbackItem
import java.text.SimpleDateFormat
import java.util.Locale

class AdminFeedbackAdapter(
    private var items: List<FeedbackItem>,
    private val onItemClick: (FeedbackItem) -> Unit
) : RecyclerView.Adapter<AdminFeedbackAdapter.ViewHolder>() {

    fun updateData(newItems: List<FeedbackItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminFeedbackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemAdminFeedbackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeedbackItem) {
            binding.tvCategory.text = when (item.category) {
                "Feature Request" -> "💡 Feature Request"
                "Report Bug" -> "🐛 Report Bug"
                else -> "💬 General Feedback"
            }

            binding.tvUserInfo.text = "${item.userName} (${item.userContact}) • ${item.userRole}"
            binding.tvFeedbackMessage.text = item.message
            binding.tvDeviceInfo.text = "${item.deviceModel} • Android ${item.androidVersion} • v${item.appVersionName}"

            // Status Badge Formatting & Colors
            val statusUpper = item.status.uppercase(Locale.getDefault())
            binding.tvStatusBadge.text = statusUpper
            val badgeBgColor = when (item.status.lowercase(Locale.getDefault())) {
                "pending" -> Color.parseColor("#E65100") // Deep Orange
                "in_progress" -> Color.parseColor("#0277BD") // Blue
                "resolved" -> Color.parseColor("#2E7D32") // Green
                "closed" -> Color.parseColor("#616161") // Grey
                else -> Color.parseColor("#E65100")
            }
            binding.tvStatusBadge.setBackgroundColor(badgeBgColor)

            // Timestamp formatting
            if (item.timestamp != null) {
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                binding.tvTimestamp.text = sdf.format(item.timestamp!!.toDate())
            } else {
                binding.tvTimestamp.text = "Just now"
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
