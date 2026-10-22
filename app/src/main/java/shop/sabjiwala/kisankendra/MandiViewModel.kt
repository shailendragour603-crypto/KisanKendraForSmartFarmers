package shop.sabjiwala.kisankendra

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import shop.sabjiwala.kisankendra.model.MandiRecord
import shop.sabjiwala.kisankendra.utils.MandiCacheHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MandiViewModel(private val repository: MandiRepository) : ViewModel() {

    private val _mandiData = MutableLiveData<List<MandiRecord>>()
    val mandiData: LiveData<List<MandiRecord>> = _mandiData

    private val _mandiDate = MutableLiveData<String>()
    val mandiDate: LiveData<String> = _mandiDate

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val apiKey = "579b464db66ec23bdd000001f0acdf7b0b4943fe5b9998c1304ba55f"

    fun getDisplayDate(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)

        // Fresh daily mandi rates land on data.gov.in starting after 1:30 PM IST (13:30)
        val isBefore130PM = currentHour < 13 || (currentHour == 13 && currentMinute < 30)
        return if (isBefore130PM) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            dateFormat.format(calendar.time)
        } else {
            dateFormat.format(calendar.time)
        }
    }

    private fun parseAndFormatDate(dateStr: String): String {
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        )
        for (fmt in formats) {
            try {
                val parsed = fmt.parse(dateStr.trim())
                if (parsed != null) {
                    val outFmt = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)
                    return outFmt.format(parsed)
                }
            } catch (_: Exception) {}
        }
        return dateStr
    }

    private fun getLatestArrivalDate(records: List<MandiRecord>): String? {
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        )

        var latestDate: java.util.Date? = null
        var latestRawDateStr: String? = null

        for (record in records.take(100)) {
            val rawDate = record.arrivalDate?.trim() ?: continue
            if (rawDate.isBlank()) continue

            for (fmt in formats) {
                try {
                    val parsed = fmt.parse(rawDate)
                    if (parsed != null) {
                        if (latestDate == null || parsed.after(latestDate)) {
                            latestDate = parsed
                            latestRawDateStr = rawDate
                        }
                        break
                    }
                } catch (_: Exception) {}
            }
        }
        return latestRawDateStr ?: records.firstOrNull()?.arrivalDate
    }

    fun loadMandiRates(preferredStateName: String? = null, forceRefresh: Boolean = false, context: Context? = null) {
        if (!forceRefresh && !_mandiData.value.isNullOrEmpty()) {
            val latestDate = getLatestArrivalDate(_mandiData.value!!)
            val expectedDisplayDate = getDisplayDate()
            val formattedLatestDate = if (!latestDate.isNullOrBlank()) parseAndFormatDate(latestDate) else null

            if (formattedLatestDate == expectedDisplayDate) {
                _isLoading.value = false
                return
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            if (forceRefresh && context != null) {
                if (!preferredStateName.isNullOrBlank() && preferredStateName != "All States" && preferredStateName != "⭐ Saved") {
                    MandiCacheHelper.clearMandiStateCache(context, MandiRepository.getCleanStateKey(preferredStateName))
                }
                MandiCacheHelper.clearMandiStateCache(context, "latest_cache")
            }

            // Load preferred user state document directly (<0.1s) for 100% complete rates and markets
            val listToLoad = if (!preferredStateName.isNullOrBlank() && preferredStateName != "All States" && preferredStateName != "⭐ Saved") {
                repository.fetchMandiRatesByStateFromFirestore(preferredStateName, context, forceRefresh)
            } else {
                repository.fetchMandiRatesFromFirestore(context, forceRefresh)
            }

            if (listToLoad.isNotEmpty()) {
                _mandiData.value = listToLoad
                val cachedDate = getLatestArrivalDate(listToLoad)
                if (!cachedDate.isNullOrBlank()) {
                    _mandiDate.value = parseAndFormatDate(cachedDate)
                } else {
                    _mandiDate.value = getDisplayDate()
                }
            } else {
                _mandiDate.value = getDisplayDate()
            }
            _isLoading.value = false
        }
    }

    fun loadMandiRatesByState(stateName: String, context: Context? = null, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            if (forceRefresh && context != null) {
                MandiCacheHelper.clearMandiStateCache(context, MandiRepository.getCleanStateKey(stateName))
            }
            val stateRecords = repository.fetchMandiRatesByStateFromFirestore(stateName, context, forceRefresh)
            if (stateRecords.isNotEmpty()) {
                _mandiData.value = stateRecords
            } else {
                _mandiData.value = emptyList()
            }
            val cachedDate = getLatestArrivalDate(stateRecords)
            if (!cachedDate.isNullOrBlank()) {
                _mandiDate.value = parseAndFormatDate(cachedDate)
            } else {
                _mandiDate.value = getDisplayDate()
            }
            _isLoading.value = false
        }
    }

    private fun getDailySnapshotDates(): Triple<String, String, String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        val todayStr = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dayBeforeYesterdayStr = dateFormat.format(calendar.time)

        return Triple(todayStr, yesterdayStr, dayBeforeYesterdayStr)
    }

    private fun processPriceDifferences(
        currentList: List<MandiRecord>,
        previousList: List<MandiRecord>
    ): List<MandiRecord> {
        val previousPriceMap = previousList.associateBy { record ->
            "${record.state.orEmpty().trim()}_${record.market.orEmpty().trim()}_${record.commodity.orEmpty().trim()}_${record.variety.orEmpty().trim()}".lowercase()
        }

        return currentList.map { current ->
            val key = "${current.state.orEmpty().trim()}_${current.market.orEmpty().trim()}_${current.commodity.orEmpty().trim()}_${current.variety.orEmpty().trim()}".lowercase()
            val previousRecord = previousPriceMap[key]

            val currentPrice = current.maxPrice?.toDoubleOrNull() ?: current.modalPrice?.toDoubleOrNull() ?: 0.0
            val previousPrice = previousRecord?.maxPrice?.toDoubleOrNull() ?: previousRecord?.modalPrice?.toDoubleOrNull()

            val computedDiff = if (previousPrice != null && previousPrice > 0.0) {
                currentPrice - previousPrice
            } else {
                null
            }

            current.apply {
                priceDiff = computedDiff
                if (formattedDate.isNullOrBlank()) {
                    formattedDate = getDisplayDate()
                }
            }
        }
    }


}
