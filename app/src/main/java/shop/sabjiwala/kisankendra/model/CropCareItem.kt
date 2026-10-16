package shop.sabjiwala.kisankendra.model

data class CropCareItem(
    val id: String = System.currentTimeMillis().toString(),
    val title: String = "",
    val problem: String = "",
    val solution: String = "",
    val category: String = "General",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "problem" to problem,
            "solution" to solution,
            "category" to category,
            "imageUrl" to imageUrl,
            "timestamp" to timestamp
        )
    }

    companion object {
        fun fromMap(map: Map<*, *>): CropCareItem {
            return CropCareItem(
                id = map["id"]?.toString() ?: System.currentTimeMillis().toString(),
                title = map["title"]?.toString() ?: "",
                problem = map["problem"]?.toString() ?: "",
                solution = map["solution"]?.toString() ?: "",
                category = map["category"]?.toString() ?: "General",
                imageUrl = map["imageUrl"]?.toString() ?: "",
                timestamp = map["timestamp"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }
}
