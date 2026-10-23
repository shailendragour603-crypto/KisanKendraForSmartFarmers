package shop.sabjiwala.kisankendra.utils

import android.content.Context
import android.text.Html
import android.text.Spanned
import android.widget.TextView
import androidx.core.content.ContextCompat
import shop.sabjiwala.kisankendra.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class MarketTickerItem(
    val symbol: String,
    val name: String,
    val price: String,
    val change: Double,
    val changePercent: Double
)

object MarketTickerHelper {

    private const val PREFS_TICKER = "market_ticker_cache"
    private const val KEY_TICKER_JSON = "ticker_json_data"

    fun getDefaultMarketItems(): List<MarketTickerItem> {
        return listOf(
            MarketTickerItem("GOLD", "Gold 24K (10g)", "₹81,950", 380.0, 0.47),
            MarketTickerItem("SILVER", "Silver (1kg)", "₹99,800", 420.0, 0.42),
            MarketTickerItem("USDINR", "USD/INR", "₹84.38", 0.04, 0.05),
            MarketTickerItem("NIFTY", "Nifty 50", "24,180.50", 148.20, 0.62),
            MarketTickerItem("SENSEX", "Sensex", "79,480.20", 432.10, 0.55)
        )
    }

    fun getCachedMarketItems(context: Context): List<MarketTickerItem> {
        val prefs = context.getSharedPreferences(PREFS_TICKER, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TICKER_JSON, null) ?: return getDefaultMarketItems()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<MarketTickerItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MarketTickerItem(
                        symbol = obj.optString("symbol"),
                        name = obj.optString("name"),
                        price = obj.optString("price"),
                        change = obj.optDouble("change", 0.0),
                        changePercent = obj.optDouble("changePercent", 0.0)
                    )
                )
            }
            if (list.size >= 5) list else getDefaultMarketItems()
        } catch (e: Exception) {
            getDefaultMarketItems()
        }
    }

    private fun saveMarketItemsToCache(context: Context, items: List<MarketTickerItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("symbol", item.symbol)
                obj.put("name", item.name)
                obj.put("price", item.price)
                obj.put("change", item.change)
                obj.put("changePercent", item.changePercent)
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_TICKER, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TICKER_JSON, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getItemDisplayName(item: MarketTickerItem, langCode: String): String {
        if (langCode == "hi") {
            return when (item.symbol) {
                "GOLD" -> "सोना 24K (10g)"
                "SILVER" -> "चांदी (1kg)"
                "USDINR" -> "डॉलर / रुपया"
                "NIFTY" -> "निफ्टी 50"
                "SENSEX" -> "सेंसेक्स"
                else -> item.name
            }
        }
        return item.name
    }

    fun buildTickerHtml(
        items: List<MarketTickerItem> = getDefaultMarketItems(),
        context: Context? = null
    ): Spanned {
        val langCode = context?.let { LocaleHelper.getPersistedLanguage(it) } ?: "hi"
        val sb = StringBuilder()
        val repeatedList = items + items

        for ((index, item) in repeatedList.withIndex()) {
            val isPositive = item.change >= 0
            val sign = if (isPositive) "▲ +" else "▼ "
            val changeColor = if (isPositive) "#4ADE80" else "#F87171"

            val formattedChangePercent = String.format(Locale.ENGLISH, "%.2f", Math.abs(item.changePercent))
            val formattedChangeVal = if (item.symbol == "USDINR") {
                String.format(Locale.ENGLISH, "%.2f", Math.abs(item.change))
            } else {
                String.format(Locale.ENGLISH, "%,.0f", Math.abs(item.change))
            }

            val imgSrc = when (item.symbol) {
                "GOLD" -> "gold_brick"
                "SILVER" -> "silver_brick"
                "USDINR" -> "usd_icon"
                "NIFTY" -> "chart_icon"
                "SENSEX" -> "chart_icon"
                else -> "chart_icon"
            }

            val displayName = getItemDisplayName(item, langCode)

            sb.append("<img src='$imgSrc'/>&nbsp;<b>$displayName</b>: ${item.price} ")
            sb.append("<font color='$changeColor'><b>$sign$formattedChangePercent%</b> ($sign₹$formattedChangeVal)</font>")

            if (index < repeatedList.size - 1) {
                sb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;•&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
            }
        }

        val imageGetter = if (context != null) {
            Html.ImageGetter { source ->
                val resId = when (source) {
                    "gold_brick" -> R.drawable.ic_gold_brick
                    "silver_brick" -> R.drawable.ic_silver_brick
                    "chart_icon" -> R.drawable.ic_chart_ticker
                    "usd_icon" -> R.drawable.ic_usd_ticker
                    else -> 0
                }
                if (resId != 0) {
                    val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
                    if (drawable != null) {
                        val density = context.resources.displayMetrics.density
                        val widthPx = (18 * density).toInt()
                        val heightPx = (18 * density).toInt()
                        drawable.setBounds(0, 0, widthPx, heightPx)
                        return@ImageGetter drawable
                    }
                }
                null
            }
        } else null

        return Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
    }

    private fun fetchYahooChartQuote(symbol: String): Pair<Double, Double>? {
        val hosts = listOf("query2.finance.yahoo.com", "query1.finance.yahoo.com")
        for (host in hosts) {
            try {
                val url = URL("https://$host/v8/finance/chart/$symbol?range=5d&interval=1d")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.setRequestProperty("Accept", "application/json")

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val chart = json.optJSONObject("chart")
                    val resultArr = chart?.optJSONArray("result")
                    val resultObj = resultArr?.optJSONObject(0) ?: continue

                    val meta = resultObj.optJSONObject("meta")
                    val regularPrice = meta?.optDouble("regularMarketPrice", 0.0) ?: 0.0

                    val indicators = resultObj.optJSONObject("indicators")
                    val quoteArr = indicators?.optJSONArray("quote")
                    val closeArr = quoteArr?.optJSONObject(0)?.optJSONArray("close")

                    var current = regularPrice
                    var prevClose = meta?.optDouble("chartPreviousClose", 0.0) ?: (meta?.optDouble("previousClose", 0.0) ?: 0.0)

                    if (closeArr != null && closeArr.length() >= 2) {
                        val nonNullCloses = mutableListOf<Double>()
                        for (i in 0 until closeArr.length()) {
                            if (!closeArr.isNull(i)) {
                                val v = closeArr.getDouble(i)
                                if (v > 0.0) nonNullCloses.add(v)
                            }
                        }
                        if (nonNullCloses.size >= 2) {
                            current = nonNullCloses.last()
                            prevClose = nonNullCloses[nonNullCloses.size - 2]
                        }
                    }

                    if (current > 0.0 && prevClose > 0.0) {
                        return Pair(current, prevClose)
                    }
                }
            } catch (e: Exception) {
                // Try next host
            }
        }
        return null
    }

    suspend fun fetchLiveMarketTicker(context: Context): List<MarketTickerItem> = withContext(Dispatchers.IO) {
        val cachedList = getCachedMarketItems(context)
        val liveItemsMap = cachedList.associateBy { it.symbol }.toMutableMap()

        try {
            val niftyQuote = fetchYahooChartQuote("%5ENSEI")
            if (niftyQuote != null) {
                val current = niftyQuote.first
                val prev = niftyQuote.second
                val change = current - prev
                val changePct = if (prev > 0) (change / prev) * 100.0 else 0.0
                val formattedPrice = String.format(Locale.ENGLISH, "%,.2f", current)
                liveItemsMap["NIFTY"] = MarketTickerItem("NIFTY", "Nifty 50", formattedPrice, change, changePct)
            }

            val sensexQuote = fetchYahooChartQuote("%5EBSESN")
            if (sensexQuote != null) {
                val current = sensexQuote.first
                val prev = sensexQuote.second
                val change = current - prev
                val changePct = if (prev > 0) (change / prev) * 100.0 else 0.0
                val formattedPrice = String.format(Locale.ENGLISH, "%,.2f", current)
                liveItemsMap["SENSEX"] = MarketTickerItem("SENSEX", "Sensex", formattedPrice, change, changePct)
            }

            val usdInrQuote = fetchYahooChartQuote("USDINR%3DX")
            val usdinrRate = usdInrQuote?.first ?: 84.38
            val usdinrPrevRate = usdInrQuote?.second ?: 84.34
            if (usdInrQuote != null) {
                val current = usdInrQuote.first
                val prev = usdInrQuote.second
                val change = current - prev
                val changePct = if (prev > 0) (change / prev) * 100.0 else 0.0
                val formattedPrice = String.format(Locale.ENGLISH, "₹%.2f", current)
                liveItemsMap["USDINR"] = MarketTickerItem("USDINR", "USD/INR", formattedPrice, change, changePct)
            }

            val mcxGoldQuote = fetchYahooChartQuote("GOLD1.MCX") ?: fetchYahooChartQuote("GOLD.MCX")
            if (mcxGoldQuote != null && mcxGoldQuote.first > 10000.0) {
                val current = mcxGoldQuote.first
                val prev = mcxGoldQuote.second
                val change = current - prev
                val changePct = if (prev > 0) (change / prev) * 100.0 else 0.0
                val formattedPrice = String.format(Locale.ENGLISH, "₹%,.0f", current)
                liveItemsMap["GOLD"] = MarketTickerItem("GOLD", "Gold 24K (10g)", formattedPrice, change, changePct)
            } else {
                val goldQuote = fetchYahooChartQuote("GC%3DF")
                if (goldQuote != null) {
                    val currentUsd = goldQuote.first
                    val prevUsd = goldQuote.second
                    // Convert Gold Troy oz (31.1035g) to INR 10g 24K with Indian tariff & IBJA retail premium (12.2%)
                    val goldInr10g = (currentUsd / 31.1035) * 10.0 * usdinrRate * 1.122
                    val goldPrevInr10g = (prevUsd / 31.1035) * 10.0 * usdinrPrevRate * 1.122
                    val change = goldInr10g - goldPrevInr10g
                    val changePct = if (goldPrevInr10g > 0) (change / goldPrevInr10g) * 100.0 else 0.0
                    val formattedPrice = String.format(Locale.ENGLISH, "₹%,.0f", goldInr10g)
                    liveItemsMap["GOLD"] = MarketTickerItem("GOLD", "Gold 24K (10g)", formattedPrice, change, changePct)
                }
            }

            val mcxSilverQuote = fetchYahooChartQuote("SILVER1.MCX") ?: fetchYahooChartQuote("SILVER.MCX")
            if (mcxSilverQuote != null && mcxSilverQuote.first > 10000.0) {
                val current = mcxSilverQuote.first
                val prev = mcxSilverQuote.second
                val change = current - prev
                val changePct = if (prev > 0) (change / prev) * 100.0 else 0.0
                val formattedPrice = String.format(Locale.ENGLISH, "₹%,.0f", current)
                liveItemsMap["SILVER"] = MarketTickerItem("SILVER", "Silver (1kg)", formattedPrice, change, changePct)
            } else {
                val silverQuote = fetchYahooChartQuote("SI%3DF")
                if (silverQuote != null) {
                    val currentUsd = silverQuote.first
                    val prevUsd = silverQuote.second
                    // Convert Silver Troy oz (32.1507 per kg) to INR 1kg with Indian tariff BCD (10%), AIDC (5%), GST (3%) = 18%
                    val silverInr1kg = currentUsd * 32.1507 * usdinrRate * 1.180
                    val silverPrevInr1kg = prevUsd * 32.1507 * usdinrPrevRate * 1.180
                    val change = silverInr1kg - silverPrevInr1kg
                    val changePct = if (silverPrevInr1kg > 0) (change / silverPrevInr1kg) * 100.0 else 0.0
                    val formattedPrice = String.format(Locale.ENGLISH, "₹%,.0f", silverInr1kg)
                    liveItemsMap["SILVER"] = MarketTickerItem("SILVER", "Silver (1kg)", formattedPrice, change, changePct)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val orderedKeys = listOf("GOLD", "SILVER", "USDINR", "NIFTY", "SENSEX")
        val resultList = orderedKeys.mapNotNull { liveItemsMap[it] }

        if (resultList.isNotEmpty()) {
            saveMarketItemsToCache(context, resultList)
            resultList
        } else {
            cachedList
        }
    }

    fun setupTickerTextView(textView: TextView, htmlText: Spanned) {
        textView.text = htmlText
        textView.isSelected = true
    }
}