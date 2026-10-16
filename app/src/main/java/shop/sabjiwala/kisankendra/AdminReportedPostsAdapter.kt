package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import shop.sabjiwala.kisankendra.databinding.ItemAdminReportedPostBinding
import shop.sabjiwala.kisankendra.model.ReportedPostItem
import java.text.SimpleDateFormat
import java.util.Locale

class AdminReportedPostsAdapter(
    private var items: List<ReportedPostItem>,
    private val onDeletePostClick: (ReportedPostItem) -> Unit,
    private val onDismissReportClick: (ReportedPostItem) -> Unit
) : RecyclerView.Adapter<AdminReportedPostsAdapter.ViewHolder>() {

    fun updateData(newItems: List<ReportedPostItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminReportedPostBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemAdminReportedPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReportedPostItem) {
            val report = item.report
            val post = item.post

            // Report Header Details
            binding.tvReportReason.text = if (report.reason.isNotBlank()) {
                "🚩 ${report.reason}"
            } else {
                "🚩 Reported Content"
            }

            if (report.timestamp != null) {
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                binding.tvReportTimestamp.text = sdf.format(report.timestamp.toDate())
            } else {
                binding.tvReportTimestamp.text = "Just now"
            }

            val reporterShort = if (report.reporterUid.length > 8) report.reporterUid.take(8) + "..." else report.reporterUid
            val authorShort = if (report.authorUid.length > 8) report.authorUid.take(8) + "..." else report.authorUid
            binding.tvReportMeta.text = "Reporter: $reporterShort • Author: $authorShort"

            // Post Content or Missing Notice
            if (post != null) {
                binding.layoutPostContent.visibility = View.VISIBLE
                binding.tvPostMissingNotice.visibility = View.GONE

                val name = post.authorName.ifBlank { "Anonymous Farmer" }
                val location = if (post.authorLocation.isNotBlank()) " • ${post.authorLocation}" else ""
                binding.tvPostAuthor.text = "$name$location"

                binding.tvPostCategory.text = post.category.ifBlank { "General Discussion" }
                binding.tvPostDescription.text = post.description

                // Engagement Stats
                binding.tvPostStats.text = "❤️ ${post.likesCount} Likes • 💬 ${post.repliesCount} Replies • 🔁 ${post.sharesCount} Shares"

                // Media Preview
                when {
                    post.photoUrl.isNotBlank() -> {
                        binding.framePostMedia.visibility = View.VISIBLE
                        binding.ivVideoPlayIndicator.visibility = View.GONE
                        Glide.with(itemView.context)
                            .load(post.photoUrl)
                            .centerCrop()
                            .into(binding.ivPostMedia)
                    }
                    post.videoUrl.isNotBlank() -> {
                        binding.framePostMedia.visibility = View.VISIBLE
                        binding.ivVideoPlayIndicator.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(post.videoUrl)
                            .centerCrop()
                            .into(binding.ivPostMedia)
                    }
                    else -> {
                        binding.framePostMedia.visibility = View.GONE
                    }
                }
            } else {
                binding.layoutPostContent.visibility = View.GONE
                binding.tvPostMissingNotice.visibility = View.VISIBLE

                val snippet = if (report.postSnippet.isNotBlank()) "\nSnippet: \"${report.postSnippet}\"" else ""
                binding.tvPostMissingNotice.text = "⚠️ Original forum post (ID: ${report.postId}) was already deleted or not found.$snippet"
            }

            // Button Click Listeners
            binding.btnDeletePost.isEnabled = (post != null)
            binding.btnDeletePost.alpha = if (post != null) 1.0f else 0.5f

            binding.btnDeletePost.setOnClickListener {
                onDeletePostClick(item)
            }

            binding.btnDismissReport.setOnClickListener {
                onDismissReportClick(item)
            }
        }
    }
}
