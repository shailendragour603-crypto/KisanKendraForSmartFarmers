package shop.sabjiwala.kisankendra.model

data class PushNotification(
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var topic: String = "all_users",
    var targetScreen: String = "home",
    var timestamp: Long = System.currentTimeMillis(),
    var sentBy: String = "",
    var status: String = "SENT"
)
