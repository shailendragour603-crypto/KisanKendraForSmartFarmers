package shop.sabjiwala.kisankendra

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import shop.sabjiwala.kisankendra.model.MandiRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var autoRefreshJob: Job? = null
    private val apiKey = "579b464db66ec23bdd000001f0acdf7b0b4943fe5b9998c1304ba55f"

    fun getDisplayDate(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)

        return if (currentHour < 16) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            dateFormat.format(calendar.time)
        } else {
            dateFormat.format(calendar.time)
        }
    }

    fun loadMandiRates(forceRefresh: Boolean = false) {
        if (!forceRefresh && !_mandiData.value.isNullOrEmpty()) {
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            val (todayStr, yesterdayStr, _) = getDailySnapshotDates()

            // ⚡ INSTANT PRE-LOAD: Load mandi_rates from Firestore instantly (<0.1s)
            val initialFirestoreList = repository.fetchMandiRatesFromFirestore(forceRefresh)
            if (initialFirestoreList.isNotEmpty()) {
                _mandiData.value = initialFirestoreList
                // Set the date based on the cached data's arrival date, or fallback to today
                val cachedDate = initialFirestoreList.firstOrNull()?.arrivalDate
                _mandiDate.value = cachedDate ?: getDisplayDate()
                
                _isLoading.value = false
            } else {
                _mandiDate.value = getDisplayDate()
                _isLoading.value = true
            }

            val currentHour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).get(Calendar.HOUR_OF_DAY)

            if (currentHour < 16) {
                val snapshot = repository.fetchDailySnapshotFromFirestore(yesterdayStr)
                val displayList = if (snapshot.isNotEmpty()) {
                    snapshot
                } else {
                    initialFirestoreList.ifEmpty { repository.fetchMandiRatesFromFirestore() }
                }

                if (displayList.isNotEmpty()) {
                    _mandiData.value = displayList
                }
                _isLoading.value = false
                return@launch
            }

            try {
                val currentList = repository.fetchMandiRatesFromApi(apiKey = apiKey, limit = 2500)
                if (currentList.isNotEmpty()) {
                    val mappedList = processPriceDifferences(currentList, initialFirestoreList)
                    _mandiData.value = mappedList
                    _mandiDate.value = getDisplayDate() // Update to today's date ONLY on success
                    _isLoading.value = false

                    // Save to Firestore in background without delaying UI render
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.saveMandiRatesToFirestore(mappedList)
                        repository.saveDailySnapshotToFirestore(todayStr, mappedList)
                    }
                } else {
                    // API returned empty/failed, keep showing cached data and its date
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }

            scheduleAutoRefresh()
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

            val currentPrice = current.modalPrice?.toDoubleOrNull() ?: 0.0
            val previousPrice = previousRecord?.modalPrice?.toDoubleOrNull()

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

    private fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

                if (currentHour in 16..23) {
                    delay(15 * 60 * 1000L)
                    loadMandiRates(forceRefresh = true)
                } else {
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
