package shop.sabjiwala.kisankendra.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class KhataItem(
    var id: String = "",
    var userId: String = "",
    var cropCycleId: String = "",
    var cropName: String = "Wheat / गेहूँ",
    var category: String = "Seeds",
    var stage: String = "Sowing",
    var amount: Double = 0.0,
    var isIncome: Boolean = false,
    var dateStr: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var note: String = ""
)
