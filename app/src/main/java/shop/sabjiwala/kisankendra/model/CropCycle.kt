package shop.sabjiwala.kisankendra.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class CropCycle(
    var id: String = "",
    var userId: String = "",
    var cropName: String = "Wheat / गेहूँ",
    var seasonName: String = "Rabi 2026",
    var areaAcres: Double = 1.0,
    var startDateStr: String = "",
    var isCompleted: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
)
