package shop.sabjiwala.kisankendra.model

import com.google.gson.annotations.SerializedName

data class MandiResponse(
    @SerializedName("records", alternate = ["Records", "RECORDS"])
    val records: List<MandiRecord>? = null
)

data class MandiRecord(
    @SerializedName(value = "state", alternate = ["State", "STATE"])
    val state: String? = null,

    @SerializedName(value = "district", alternate = ["District", "DISTRICT"])
    val district: String? = null,

    @SerializedName(value = "market", alternate = ["Market", "MARKET"])
    val market: String? = null,

    @SerializedName(value = "commodity", alternate = ["Commodity", "COMMODITY"])
    val commodity: String? = null,

    @SerializedName(value = "variety", alternate = ["Variety", "VARIETY"])
    val variety: String? = null,

    @SerializedName(value = "min_price", alternate = ["minPrice", "Min Price", "Min_Price", "MIN_PRICE", "minprice"])
    val minPrice: String? = null,

    @SerializedName(value = "max_price", alternate = ["maxPrice", "Max Price", "Max_Price", "MAX_PRICE", "maxprice"])
    val maxPrice: String? = null,

    @SerializedName(value = "modal_price", alternate = ["modalPrice", "Modal Price", "Modal_x0020_Price", "modal_x0020_price", "Modal_Price", "MODAL_PRICE", "modalprice"])
    val modalPrice: String? = null,

    @SerializedName(value = "arrival_date", alternate = ["arrivalDate", "Arrival Date", "Arrival_x0020_Date", "Arrival_Date", "ARRIVAL_DATE", "arrivaldate"])
    val arrivalDate: String? = null,

    // Calculated field for stock-market style price diff (+/-)
    var priceDiff: Double? = null,

    // Formatted date string for display (e.g. 22 Sep, 2026)
    var formattedDate: String? = null
)

fun parseMandiRecordFromMap(map: Map<String, Any>): MandiRecord {
    fun getString(vararg keys: String): String? {
        for (k in keys) {
            val v = map[k]
            if (v != null) return v.toString()
        }
        for ((mapKey, mapVal) in map) {
            val cleanMapKey = mapKey.replace("_x0020_", " ").trim()
            for (k in keys) {
                val cleanKey = k.replace("_x0020_", " ").trim()
                if (cleanMapKey.equals(cleanKey, ignoreCase = true)) {
                    if (mapVal != null) return mapVal.toString()
                }
            }
        }
        return null
    }

    val pd = map["priceDiff"] ?: map["price_diff"] ?: map["Price Diff"]
    val priceDiff = (pd as? Number)?.toDouble() ?: pd?.toString()?.toDoubleOrNull()

    return MandiRecord(
        state = getString("state", "State", "STATE"),
        district = getString("district", "District", "DISTRICT"),
        market = getString("market", "Market", "MARKET"),
        commodity = getString("commodity", "Commodity", "COMMODITY"),
        variety = getString("variety", "Variety", "VARIETY"),
        minPrice = getString("min_price", "minPrice", "Min Price", "Min_Price", "MIN_PRICE", "minprice"),
        maxPrice = getString("max_price", "maxPrice", "Max Price", "Max_Price", "MAX_PRICE", "maxprice"),
        modalPrice = getString("modal_price", "modalPrice", "Modal Price", "Modal_x0020_Price", "modal_x0020_price", "Modal_Price", "MODAL_PRICE", "modalprice"),
        arrivalDate = getString("arrival_date", "arrivalDate", "Arrival Date", "Arrival_x0020_Date", "Arrival_Date", "ARRIVAL_DATE", "arrivaldate"),
        priceDiff = priceDiff
    )
}
