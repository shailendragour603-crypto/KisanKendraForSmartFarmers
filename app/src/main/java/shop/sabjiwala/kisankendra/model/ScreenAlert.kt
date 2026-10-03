package shop.sabjiwala.kisankendra.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Data model for dynamic in-app screen notices, status alerts, and feature maintenance messages.
 * Managed by Admin and fetched by app screens from Firestore collection: app_screen_alerts
 */
@IgnoreExtraProperties
data class ScreenAlert(
    val screenId: String = "",
    @get:PropertyName("active") @set:PropertyName("active") @field:PropertyName("active")
    var isActive: Boolean = false,
    val alertType: String = "INFO", // "INFO", "WARNING", "MAINTENANCE"
    val titleEn: String = "",
    val titleHi: String = "",
    val messageEn: String = "",
    val messageHi: String = "",
    @get:PropertyName("blocking") @set:PropertyName("blocking") @field:PropertyName("blocking")
    var isBlocking: Boolean = false,
    val updatedAt: Long = 0L
)
