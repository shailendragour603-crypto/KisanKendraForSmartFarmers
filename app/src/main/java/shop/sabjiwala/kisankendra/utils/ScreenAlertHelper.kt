package shop.sabjiwala.kisankendra.utils

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.View
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import shop.sabjiwala.kisankendra.databinding.LayoutScreenAlertBannerBinding
import shop.sabjiwala.kisankendra.model.ScreenAlert

object ScreenAlertHelper {

    private const val COLLECTION_ALERTS = "app_screen_alerts"

    /**
     * Binds real-time Firestore screen alert updates to the provided banner binding.
     * @return ListenerRegistration which can be removed when activity/fragment is destroyed.
     */
    fun setupScreenAlert(
        context: Context,
        screenId: String,
        bannerBinding: LayoutScreenAlertBannerBinding
    ): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection(COLLECTION_ALERTS)
            .document(screenId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ScreenAlertHelper", "Error listening for screen alert on screen '$screenId'", error)
                    bannerBinding.cardScreenAlertBanner.visibility = View.GONE
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    bannerBinding.cardScreenAlertBanner.visibility = View.GONE
                    return@addSnapshotListener
                }

                val rawActive = snapshot.getBoolean("active") ?: snapshot.getBoolean("isActive") ?: false
                val rawBlocking = snapshot.getBoolean("blocking") ?: snapshot.getBoolean("isBlocking") ?: false

                val alert = snapshot.toObject(ScreenAlert::class.java)?.apply {
                    this.isActive = rawActive
                    this.isBlocking = rawBlocking
                }

                if (alert == null || !alert.isActive) {
                    bannerBinding.cardScreenAlertBanner.visibility = View.GONE
                    return@addSnapshotListener
                }

                // Determine language
                val lang = LocaleHelper.getPersistedLanguage(context)
                val title = if (lang == "hi") {
                    alert.titleHi.ifBlank { alert.titleEn }
                } else {
                    alert.titleEn.ifBlank { alert.titleHi }
                }

                val message = if (lang == "hi") {
                    alert.messageHi.ifBlank { alert.messageEn }
                } else {
                    alert.messageEn.ifBlank { alert.messageHi }
                }

                if (title.isBlank() && message.isBlank()) {
                    bannerBinding.cardScreenAlertBanner.visibility = View.GONE
                    return@addSnapshotListener
                }

                // Bind text
                bannerBinding.tvAlertTitle.text = title
                bannerBinding.tvAlertMessage.text = message

                // Style based on Alert Type
                applyAlertTypeStyling(bannerBinding, alert.alertType)

                // Show banner
                bannerBinding.cardScreenAlertBanner.visibility = View.VISIBLE

                // Dismiss listener
                bannerBinding.btnAlertDismiss.setOnClickListener {
                    bannerBinding.cardScreenAlertBanner.visibility = View.GONE
                }
            }
    }

    private fun applyAlertTypeStyling(
        binding: LayoutScreenAlertBannerBinding,
        alertType: String
    ) {
        val bgColor: Int
        val strokeColor: Int
        val textColor: Int
        val iconRes: Int

        when (alertType.uppercase()) {
            "WARNING" -> {
                bgColor = Color.parseColor("#FFF8E1") // Light Yellow/Amber
                strokeColor = Color.parseColor("#FFB300") // Amber Stroke
                textColor = Color.parseColor("#5D4037") // Dark Brown Text
                iconRes = R.drawable.ic_dialog_alert
            }
            "MAINTENANCE" -> {
                bgColor = Color.parseColor("#FFEBEE") // Light Red
                strokeColor = Color.parseColor("#E53935") // Red Stroke
                textColor = Color.parseColor("#B71C1C") // Dark Red Text
                iconRes = R.drawable.ic_delete // Alert / Maintenance Icon
            }
            else -> { // "INFO" or default
                bgColor = Color.parseColor("#E3F2FD") // Light Blue
                strokeColor = Color.parseColor("#1E88E5") // Blue Stroke
                textColor = Color.parseColor("#0D47A1") // Dark Blue Text
                iconRes = R.drawable.ic_dialog_info
            }
        }

        binding.cardScreenAlertBanner.setCardBackgroundColor(bgColor)
        binding.cardScreenAlertBanner.strokeColor = strokeColor
        binding.tvAlertTitle.setTextColor(textColor)
        binding.tvAlertMessage.setTextColor(textColor)
        binding.imgAlertIcon.setImageResource(iconRes)
        binding.imgAlertIcon.imageTintList = ColorStateList.valueOf(strokeColor)
        binding.btnAlertDismiss.imageTintList = ColorStateList.valueOf(textColor)
    }
}
