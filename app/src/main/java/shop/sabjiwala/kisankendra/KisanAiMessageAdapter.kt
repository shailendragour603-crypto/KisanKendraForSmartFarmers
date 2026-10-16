package shop.sabjiwala.kisankendra

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import shop.sabjiwala.kisankendra.databinding.ItemKisanAiMessageBinding

data class KisanAiMessage(
    val text: String,
    val isUser: Boolean,
    val imageBitmap: Bitmap? = null
)

class KisanAiMessageAdapter(private val messages: List<KisanAiMessage>) :
    RecyclerView.Adapter<KisanAiMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(val binding: ItemKisanAiMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemKisanAiMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        with(holder.binding) {
            // Bind image if provided
            if (message.imageBitmap != null) {
                ivMessageImage.setImageBitmap(message.imageBitmap)
                ivMessageImage.visibility = View.VISIBLE
            } else {
                ivMessageImage.visibility = View.GONE
            }

            // Clean double asterisks (markdown bold) and trim text
            val cleanText = message.text
                .replace("**", "")
                .replace(Regex("(?m)^#+\\s*"), "")
                .trim()

            if (cleanText.isNotBlank()) {
                tvMessageText.text = cleanText
                tvMessageText.visibility = View.VISIBLE
            } else {
                tvMessageText.visibility = View.GONE
            }

            val layoutParams = layoutMessageContainer.layoutParams as LinearLayout.LayoutParams
            if (message.isUser) {
                layoutParams.gravity = Gravity.END
                layoutMessageContainer.setBackgroundResource(R.drawable.bg_neon_pill)
                tvMessageText.setTextColor(Color.WHITE)
            } else {
                layoutParams.gravity = Gravity.START
                layoutMessageContainer.setBackgroundResource(R.drawable.bg_neon_card)
                tvMessageText.setTextColor(Color.parseColor("#E2E8F0"))
            }
            layoutMessageContainer.layoutParams = layoutParams
        }
    }

    override fun getItemCount(): Int = messages.size
}
