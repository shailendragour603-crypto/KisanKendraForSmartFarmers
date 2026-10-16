package shop.sabjiwala.kisankendra

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import shop.sabjiwala.kisankendra.databinding.ItemAdminNotificationBinding
import shop.sabjiwala.kisankendra.model.PushNotification

class AdminNotificationAdapter(
    private var items: List<PushNotification>,
    private val onReuseClick: (PushNotification) -> Unit,
    private val onDeleteClick: (PushNotification) -> Unit
) : RecyclerView.Adapter<AdminNotificationAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAdminNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvHistoryTitle.text = item.title
        holder.binding.tvHistoryBody.text = item.body
        holder.binding.tvHistoryTopic.text = "Topic: ${item.topic}"
        holder.binding.tvHistoryTargetScreen.text = "Screen: ${item.targetScreen}"

        val timeAgo = DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        holder.binding.tvHistoryTimestamp.text = timeAgo

        holder.binding.btnReuseNotification.setOnClickListener {
            onReuseClick(item)
        }

        holder.binding.btnDeleteNotification.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<PushNotification>) {
        items = newItems
        notifyDataSetChanged()
    }
}
