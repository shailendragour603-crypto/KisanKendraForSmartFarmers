package shop.sabjiwala.kisankendra.utils

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TranslationHelper {

    private val translationCache = ConcurrentHashMap<String, String>()
    private val masterDictCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val currentLang = LocaleHelper.getPersistedLanguage(appContext!!)
        getOrLoadLangCache(appContext, currentLang)
        getOrLoadLangCache(appContext, "hi")
        getOrLoadLangCache(appContext, "en")
    }

    fun extractMapEntries(map: Map<*, *>, targetMap: MutableMap<String, String>, prefix: String = "") {
        for ((k, v) in map) {
            if (k == null || v == null) continue
            val keyStr = if (prefix.isEmpty()) k.toString() else "$prefix.$k"
            if (v is Map<*, *>) {
                extractMapEntries(v, targetMap, keyStr)
            } else {
                targetMap[keyStr] = v.toString()
            }
        }
    }

    fun getOrLoadLangCache(context: Context? = null, langCode: String): ConcurrentHashMap<String, String> {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
        val langCache = masterDictCache.getOrPut(langCode) { ConcurrentHashMap() }
        if (langCache.isEmpty()) {
            val ctx = context?.applicationContext ?: appContext
            if (ctx != null) {
                try {
                    val prefs = ctx.getSharedPreferences("master_dict_prefs", Context.MODE_PRIVATE)
                    val cachedJson = prefs.getString("dict_$langCode", null)
                    if (!cachedJson.isNullOrBlank()) {
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val map: Map<String, String>? = Gson().fromJson(cachedJson, type)
                        if (!map.isNullOrEmpty()) {
                            for ((k, v) in map) {
                                if (!v.contains("महिलाओं", ignoreCase = true) && !v.contains("उंगलियां", ignoreCase = true)) {
                                    val lKey = k.lowercase().trim()
                                    langCache[lKey] = v
                                    langCache[k] = v
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return langCache
    }

    val DEFAULT_HINDI_CROPS_LIST = listOf(
        "✍️ कोई अन्य फसल लिखें...",
        "--- 🌾 फसलें व अनाज ---",
        "गेहूँ", "धान / चावल", "सोयाबीन", "कपास", "सरसों", "चना", "मक्का", "गन्ना", "बाजरा", "ज्वार", "जौ", "अरहर / तुअर", "मूंग", "उड़द", "मूंगफली", "तिल", "सूरजमुखी", "कुसुम", "अरंडी", "अलसी", "रामतिल", "ग्वार", "मसूर", "मटर", "राजमा", "लोबिया", "मोठ", "रागी", "कांगनी", "कोदो", "कुटकी", "सांवा", "कुट्टू", "राजगिरा", "तंबाकू", "जूट",
        "--- 🥬 सब्जियाँ ---",
        "प्याज़", "आलू", "लहसुन", "टमाटर", "हरी मिर्च", "बैंगन", "भिंडी", "फूलगोभी", "पत्तागोभी", "हरी मटर", "गाजर", "मूली", "पालक", "करेला", "लौकी", "तोरई", "गिलकी", "चिचिंडा", "कद्दू", "खीरा", "शिमला मिर्च", "धनिया", "अदरक", "हल्दी", "शकरकंद", "चुकंदर", "अरबी", "जिमीकंद / सूरन", "सहजन / मुनगा", "मेथी साग", "सरसों साग", "चौलाई", "बथुआ", "ग्वार फली", "फ्रेंच बीन्स", "बरबटी", "परवल", "कुंदरू", "कच्चा केला", "कच्चा पपीता", "मशरूम", "बेबी कॉर्न", "ब्रोकली", "ज़ुकीनी",
        "--- 🍎 फल ---",
        "आम", "केला", "सेब", "पपीता", "अमरूद", "अनार", "तरबूज", "खरबूजा", "संतरा", "मौसंबी", "नींबू", "अंगूर", "ड्रैगन फ्रूट", "स्ट्रॉबेरी", "कटहल", "लीची", "चीकू", "अनानास", "सीताफल", "अंजीर", "खजूर", "आलू बुखारा", "आड़ू", "नाशपाती", "चेरी", "कीवी", "एवोकाडो", "आँवला", "बेल", "इमली", "जामुन", "सिंघाड़ा", "शहतूत", "बेर", "करौंदा",
        "--- 🌶️ मसाले व नगदी फसलें ---",
        "जीरा", "सौंफ", "मेथी दाना", "धनिया साबुत", "अजवायन", "काली मिर्च", "इलायची", "दालचीनी", "लौंग", "लाल मिर्च", "सोंठ", "हींग", "जायफल", "जावित्री", "तेजपत्ता", "राई", "कलौंजी", "खसखस", "सुपारी", "पान का पत्ता", "केसर", "काजू", "बादाम", "अखरोट", "पिस्ता", "नारियल", "रबर", "चाय पत्ती", "कॉफी",
        "--- 🌸 फूल व अन्य ---",
        "गेंदा", "गुलाब", "चमेली / मोगरा", "रजनीगंधा", "ग्लेडियोलस", "जरबेरा", "कार्नेशन", "कमल", "ऑर्किड", "सेवंती", "हरा चारा", "भूसा / कड़बी", "शहद", "गुड़"
    )

    private var cachedLocalizedDict: Map<String, Map<String, String>>? = null

    fun getLocalizedDictionary(context: Context?): Map<String, Map<String, String>> {
        if (cachedLocalizedDict != null) return cachedLocalizedDict!!
        val ctx = context?.applicationContext ?: appContext ?: return emptyMap()

        return try {
            val jsonStr = ctx.assets.open("local_translation_dictionary.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            val map: Map<String, Map<String, String>>? = Gson().fromJson(jsonStr, type)
            if (map != null) {
                cachedLocalizedDict = map
                map
            } else emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun syncMasterDictionaryFromFirestore(context: Context, langCode: String = "hi") = withContext(Dispatchers.IO) {
        try {
            if (appContext == null) {
                appContext = context.applicationContext
            }
            val langCache = getOrLoadLangCache(context, langCode)

            val doc = FirebaseFirestore.getInstance()
                .collection("app_translations")
                .document(langCode)
                .get()
                .await()

            if (doc.exists()) {
                val remoteMap = mutableMapOf<String, String>()

                // Extract entries defensively from "dictionary" map (handles sub-maps or non-string values)
                val rawDict = doc.get("dictionary") as? Map<*, *>
                if (rawDict != null) {
                    extractMapEntries(rawDict, remoteMap)
                }

                // Recover any top-level "dictionary.xyz" fields from legacy buggy saves
                val docData = doc.data
                var legacyRootFieldsFound = false
                if (docData != null) {
                    for ((fKey, fVal) in docData) {
                        if (fKey.startsWith("dictionary.") && fVal != null) {
                            val cleanKey = fKey.removePrefix("dictionary.")
                            if (cleanKey.isNotBlank()) {
                                remoteMap[cleanKey] = fVal.toString()
                                legacyRootFieldsFound = true
                            }
                        }
                    }
                }

                val badKeys = remoteMap.filter { it.value.contains("महिलाओं", ignoreCase = true) || it.value.contains("उंगलियां", ignoreCase = true) }.keys
                val needsUpdate = badKeys.isNotEmpty()
                for (bk in badKeys) {
                    remoteMap.remove(bk)
                }

                if (needsUpdate || legacyRootFieldsFound) {
                    try {
                        val docRef = FirebaseFirestore.getInstance().collection("app_translations").document(langCode)
                        docRef.set(
                            hashMapOf(
                                "dictionary" to remoteMap,
                                "updatedAt" to com.google.firebase.Timestamp.now()
                            ),
                            SetOptions.merge()
                        ).await()

                        if (legacyRootFieldsFound && docData != null) {
                            val deleteUpdates = hashMapOf<String, Any>()
                            for ((fKey, _) in docData) {
                                if (fKey.startsWith("dictionary.")) {
                                    deleteUpdates[fKey] = com.google.firebase.firestore.FieldValue.delete()
                                }
                            }
                            if (deleteUpdates.isNotEmpty()) {
                                docRef.update(deleteUpdates).await()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (remoteMap.isNotEmpty()) {
                    for ((k, v) in remoteMap) {
                        val lKey = k.lowercase().trim()
                        langCache[lKey] = v
                        langCache[k] = v
                    }
                    val prefs = context.getSharedPreferences("master_dict_prefs", Context.MODE_PRIVATE)
                    val json = Gson().toJson(remoteMap)
                    prefs.edit().putString("dict_$langCode", json).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateMasterCache(key: String, value: String, langCode: String = "hi") {
        if (key.isBlank() || value.isBlank()) return
        if (value.contains("महिलाओं", ignoreCase = true) || value.contains("उंगलियां", ignoreCase = true)) {
            return
        }
        val langCache = masterDictCache.getOrPut(langCode) { ConcurrentHashMap() }
        val lKey = key.lowercase().trim()
        langCache[lKey] = value
        langCache[key] = value
    }

    fun removeFromMasterCache(key: String, langCode: String = "hi") {
        if (key.isBlank()) return
        val langCache = masterDictCache[langCode]
        if (langCache != null) {
            langCache.remove(key.lowercase().trim())
            langCache.remove(key)
        }
    }

    fun updateMasterCacheAndPrefs(context: Context, key: String, value: String, langCode: String = "hi") {
        if (key.isBlank() || value.isBlank()) return
        if (appContext == null) appContext = context.applicationContext
        updateMasterCache(key, value, langCode)
        try {
            val prefs = context.getSharedPreferences("master_dict_prefs", Context.MODE_PRIVATE)
            val cachedJson = prefs.getString("dict_$langCode", null)
            val type = object : TypeToken<Map<String, String>>() {}.type
            val map: MutableMap<String, String> = if (!cachedJson.isNullOrBlank()) {
                Gson().fromJson<Map<String, String>>(cachedJson, type)?.toMutableMap() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }
            map[key.lowercase().trim()] = value
            map[key] = value
            prefs.edit().putString("dict_$langCode", Gson().toJson(map)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeMasterCacheAndPrefs(context: Context, key: String, langCode: String = "hi") {
        if (key.isBlank()) return
        if (appContext == null) appContext = context.applicationContext
        removeFromMasterCache(key, langCode)
        try {
            val prefs = context.getSharedPreferences("master_dict_prefs", Context.MODE_PRIVATE)
            val cachedJson = prefs.getString("dict_$langCode", null)
            if (!cachedJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map: MutableMap<String, String>? = Gson().fromJson<Map<String, String>>(cachedJson, type)?.toMutableMap()
                if (map != null) {
                    map.remove(key.lowercase().trim())
                    map.remove(key)
                    prefs.edit().putString("dict_$langCode", Gson().toJson(map)).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDirectTranslation(text: String, targetLangCode: String, context: Context? = null): String? {
        if (text.isBlank()) return null
        val cleanText = text.trim()
        val cleanKey = cleanText.lowercase()

        // 1. Check Language-Specific Master Remote Dictionary Cache from Firebase
        val langCache = getOrLoadLangCache(context, targetLangCode)
        if (langCache.isNotEmpty()) {
            langCache[cleanKey]?.let { return it }
            langCache[cleanText]?.let { return it }
            if (cleanKey.contains("ladies")) {
                langCache[cleanKey.replace("ladies", "lady")]?.let { return it }
            }
            if (cleanKey.contains("lady")) {
                langCache[cleanKey.replace("lady", "ladies")]?.let { return it }
            }
        }

        // 2. Check Localized Asset Dictionary
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null) {
            val dict = getLocalizedDictionary(ctx)
            val langDict = dict[targetLangCode]
            if (langDict != null) {
                langDict[cleanText]?.let { return it }
                langDict.entries.firstOrNull { it.key.equals(cleanText, ignoreCase = true) }?.value?.let { return it }
            }

            if (targetLangCode != "en") {
                // Inverse lookup: if text is English or alternative, find corresponding key in "en" or target dict
                val sourceKey = dict["en"]?.entries?.firstOrNull { it.value.equals(cleanText, ignoreCase = true) }?.key
                if (sourceKey != null) {
                    dict[targetLangCode]?.get(sourceKey)?.let { return it }
                    dict[targetLangCode]?.entries?.firstOrNull { it.key.equals(sourceKey, ignoreCase = true) }?.value?.let { return it }
                }
            } else {
                val valMatch = dict["en"]?.entries?.firstOrNull { it.key.equals(cleanText, ignoreCase = true) }?.value
                if (valMatch != null) return valMatch
                val keyMatch = dict["en"]?.entries?.firstOrNull { it.value.equals(cleanText, ignoreCase = true) }?.value
                if (keyMatch != null) return keyMatch
                if (!hasNonEnglishCharacters(cleanText)) {
                    return cleanText
                }
            }
        }

        return null
    }

    fun getEnglishCropName(crop: String): String {
        if (crop.isBlank()) return crop
        val lower = crop.trim().lowercase(Locale.ENGLISH)
        return when {
            lower.contains("wheat") || lower.contains("गेहूँ") -> "Wheat"
            lower.contains("paddy") || lower.contains("rice") || lower.contains("धान") || lower.contains("चावल") -> "Paddy / Rice"
            lower.contains("soybean") || lower.contains("soyabean") || lower.contains("सोयाबीन") -> "Soybean"
            lower.contains("cotton") || lower.contains("कपास") -> "Cotton"
            lower.contains("mustard") || lower.contains("सरसों") -> "Mustard"
            lower.contains("gram") || lower.contains("chana") || lower.contains("chickpea") || lower.contains("चना") -> "Gram / Chana"
            lower.contains("maize") || lower.contains("मक्का") -> "Maize"
            lower.contains("sugarcane") || lower.contains("गन्ना") -> "Sugarcane"
            lower.contains("bajra") || lower.contains("pearl millet") || lower.contains("बाजरा") -> "Pearl Millet / Bajra"
            lower.contains("jowar") || lower.contains("sorghum") || lower.contains("ज्वार") -> "Sorghum / Jowar"
            lower.contains("barley") || lower.contains("जौ") -> "Barley"
            lower.contains("arhar") || lower.contains("tur") || lower.contains("pigeon pea") || lower.contains("तुअर") -> "Arhar / Pigeon Pea"
            lower.contains("moong") || lower.contains("green gram") || lower.contains("मूंग") -> "Green Gram / Moong"
            lower.contains("urad") || lower.contains("black gram") || lower.contains("उड़द") -> "Black Gram / Urad"
            lower.contains("groundnut") || lower.contains("peanut") || lower.contains("मूंगफली") -> "Groundnut / Peanut"
            lower.contains("til") || lower.contains("sesame") || lower.contains("तिल") -> "Sesame / Til"
            lower.contains("sunflower") || lower.contains("सूरजमुखी") -> "Sunflower"
            lower.contains("onion") || lower.contains("प्याज़") || lower.contains("प्याज") || lower.contains("pyaz") || lower.contains("pyaaz") || lower.contains("kanda") || lower.contains("कांदा") -> "Onion"
            lower.contains("potato") || lower.contains("आलू") -> "Potato"
            lower.contains("garlic") || lower.contains("लहसुन") -> "Garlic"
            lower.contains("tomato") || lower.contains("टमाटर") -> "Tomato"
            lower.contains("chilli") || lower.contains("मिर्च") -> "Green Chilli"
            lower.contains("brinjal") || lower.contains("eggplant") || lower.contains("बैंगन") -> "Brinjal"
            lower.contains("bhindi") || lower.contains("okra") || lower.contains("lady finger") -> "Okra / Bhindi"
            lower.contains("cauliflower") || lower.contains("फूलगोभी") -> "Cauliflower"
            lower.contains("cabbage") || lower.contains("पत्तागोभी") -> "Cabbage"
            lower.contains("peas") || lower.contains("मटर") -> "Green Peas"
            lower.contains("carrot") || lower.contains("गाजर") -> "Carrot"
            lower.contains("radish") || lower.contains("मूली") -> "Radish"
            lower.contains("spinach") || lower.contains("पालक") -> "Spinach"
            lower.contains("bitter gourd") || lower.contains("करेला") -> "Bitter Gourd"
            lower.contains("bottle gourd") || lower.contains("लौकी") -> "Bottle Gourd"
            lower.contains("ridge gourd") || lower.contains("तोरई") -> "Ridge Gourd"
            lower.contains("pumpkin") || lower.contains("कद्दू") -> "Pumpkin"
            lower.contains("cucumber") || lower.contains("खीरा") -> "Cucumber"
            lower.contains("capsicum") || lower.contains("शिमला") -> "Capsicum"
            lower.contains("coriander") || lower.contains("धनिया") -> "Coriander"
            lower.contains("ginger") || lower.contains("अदरक") -> "Ginger"
            lower.contains("turmeric") || lower.contains("हल्दी") -> "Turmeric"
            lower.contains("sweet potato") || lower.contains("शकरकंद") -> "Sweet Potato"
            lower.contains("beetroot") || lower.contains("चुकंदर") -> "Beetroot"
            lower.contains("arbi") || lower.contains("taro") || lower.contains("अरबी") -> "Taro / Arbi"
            lower.contains("mango") || lower.contains("आम") -> "Mango"
            lower.contains("banana") || lower.contains("केला") -> "Banana"
            lower.contains("apple") || lower.contains("सेब") -> "Apple"
            lower.contains("papaya") || lower.contains("पपीता") -> "Papaya"
            lower.contains("guava") || lower.contains("अमरूद") -> "Guava"
            lower.contains("pomegranate") || lower.contains("अनार") -> "Pomegranate"
            lower.contains("watermelon") || lower.contains("तरबूज") -> "Watermelon"
            lower.contains("muskmelon") || lower.contains("खरबूजा") -> "Muskmelon"
            lower.contains("orange") || lower.contains("संतरा") -> "Orange"
            lower.contains("lemon") || lower.contains("नींबू") -> "Lemon"
            lower.contains("grapes") || lower.contains("अंगूर") -> "Grapes"
            lower.contains("dragon fruit") || lower.contains("ड्रैगन") -> "Dragon Fruit"
            lower.contains("strawberry") || lower.contains("स्ट्रॉबेरी") -> "Strawberry"
            lower.contains("jackfruit") || lower.contains("कटहल") -> "Jackfruit"
            lower.contains("litchi") || lower.contains("लीची") -> "Litchi"
            lower.contains("chikoo") || lower.contains("sapodilla") || lower.contains("चीकू") -> "Sapodilla / Chikoo"
            else -> crop
        }
    }

    fun getHindiCropName(crop: String): String {
        if (crop.isBlank()) return crop
        val lower = crop.trim().lowercase(Locale.ENGLISH)
        return when {
            lower.contains("wheat") || lower.contains("गेहूँ") -> "गेहूँ"
            lower.contains("paddy") || lower.contains("rice") || lower.contains("धान") || lower.contains("चावल") -> "धान / चावल"
            lower.contains("soybean") || lower.contains("soyabean") || lower.contains("सोयाबीन") -> "सोयाबीन"
            lower.contains("cotton") || lower.contains("कपास") -> "कपास"
            lower.contains("mustard") || lower.contains("सरसों") -> "सरसों"
            lower.contains("gram") || lower.contains("chana") || lower.contains("chickpea") || lower.contains("चना") -> "चना"
            lower.contains("maize") || lower.contains("मक्का") -> "मक्का"
            lower.contains("sugarcane") || lower.contains("गन्ना") -> "गन्ना"
            lower.contains("bajra") || lower.contains("pearl millet") || lower.contains("बाजरा") -> "बाजरा"
            lower.contains("jowar") || lower.contains("sorghum") || lower.contains("ज्वार") -> "ज्वार"
            lower.contains("barley") || lower.contains("जौ") -> "जौ"
            lower.contains("arhar") || lower.contains("tur") || lower.contains("pigeon pea") || lower.contains("तुअर") -> "अरहर / तुअर"
            lower.contains("moong") || lower.contains("green gram") || lower.contains("मूंग") -> "मूंग"
            lower.contains("urad") || lower.contains("black gram") || lower.contains("उड़द") -> "उड़द"
            lower.contains("groundnut") || lower.contains("peanut") || lower.contains("मूंगफली") -> "मूंगफली"
            lower.contains("til") || lower.contains("sesame") || lower.contains("तिल") -> "तिल"
            lower.contains("sunflower") || lower.contains("सूरजमुखी") -> "सूरजमुखी"
            lower.contains("onion") || lower.contains("प्याज़") || lower.contains("प्याज") || lower.contains("pyaz") || lower.contains("pyaaz") || lower.contains("kanda") || lower.contains("कांदा") -> "प्याज़"
            lower.contains("potato") || lower.contains("आलू") -> "आलू"
            lower.contains("garlic") || lower.contains("लहसुन") -> "लहसुन"
            lower.contains("tomato") || lower.contains("टमाटर") -> "टमाटर"
            lower.contains("chilli") || lower.contains("मिर्च") -> "हरी मिर्च"
            lower.contains("brinjal") || lower.contains("eggplant") || lower.contains("बैंगन") -> "बैंगन"
            lower.contains("bhindi") || lower.contains("okra") || lower.contains("lady finger") -> "भिंडी"
            lower.contains("cauliflower") || lower.contains("फूलगोभी") -> "फूलगोभी"
            lower.contains("cabbage") || lower.contains("पत्तागोभी") -> "पत्तागोभी"
            lower.contains("peas") || lower.contains("मटर") -> "हरी मटर"
            lower.contains("carrot") || lower.contains("गाजर") -> "गाजर"
            lower.contains("radish") || lower.contains("मूली") -> "मूली"
            lower.contains("spinach") || lower.contains("पालक") -> "पालक"
            lower.contains("bitter gourd") || lower.contains("करेला") -> "करेला"
            lower.contains("bottle gourd") || lower.contains("लौकी") -> "लौकी"
            lower.contains("ridge gourd") || lower.contains("तोरई") -> "तोरई"
            lower.contains("pumpkin") || lower.contains("कद्दू") -> "कद्दू"
            lower.contains("cucumber") || lower.contains("खीरा") -> "खीरा"
            lower.contains("capsicum") || lower.contains("शिमला") -> "शिमला मिर्च"
            lower.contains("coriander") || lower.contains("धनिया") -> "धनिया"
            lower.contains("ginger") || lower.contains("अदरक") -> "अदरक"
            lower.contains("turmeric") || lower.contains("हल्दी") -> "हल्दी"
            lower.contains("sweet potato") || lower.contains("शकरकंद") -> "शकरकंद"
            lower.contains("beetroot") || lower.contains("चुकंदर") -> "चुकंदर"
            lower.contains("arbi") || lower.contains("taro") || lower.contains("अरबी") -> "अरबी"
            lower.contains("mango") || lower.contains("आम") -> "आम"
            lower.contains("banana") || lower.contains("केला") -> "केला"
            lower.contains("apple") || lower.contains("सेब") -> "सेब"
            lower.contains("papaya") || lower.contains("पपीता") -> "पपीता"
            lower.contains("guava") || lower.contains("अमरूद") -> "अमरूद"
            lower.contains("pomegranate") || lower.contains("अनार") -> "अनार"
            lower.contains("watermelon") || lower.contains("तरबूज") -> "तरबूज"
            lower.contains("muskmelon") || lower.contains("खरबूजा") -> "खरबूजा"
            lower.contains("orange") || lower.contains("संतरा") -> "संतरा"
            lower.contains("lemon") || lower.contains("नींबू") -> "नींबू"
            lower.contains("grapes") || lower.contains("अंगूर") -> "अंगूर"
            lower.contains("dragon fruit") || lower.contains("ड्रैगन") -> "ड्रैगन फ्रूट"
            lower.contains("strawberry") || lower.contains("स्ट्रॉबेरी") -> "स्ट्रॉबेरी"
            lower.contains("jackfruit") || lower.contains("कटहल") -> "कटहल"
            lower.contains("litchi") || lower.contains("लीची") -> "लीची"
            lower.contains("chikoo") || lower.contains("sapodilla") || lower.contains("चीकू") -> "चीकू"
            else -> crop
        }
    }

    fun getTranslatedCropName(cropName: String, targetLangCode: String, context: Context? = null): String {
        if (cropName.isBlank()) return cropName

        val parentheticalRegex = "(.*)\\((.*)\\)".toRegex()
        val match = parentheticalRegex.matchEntire(cropName.trim())
        if (match != null) {
            val rawCrop = match.groupValues[1].trim()
            val rawSeason = match.groupValues[2].trim()
            val transCrop = getTranslatedCropName(rawCrop, targetLangCode, context)
            val transSeason = getTranslatedSeasonName(rawSeason, targetLangCode, context)
            return "$transCrop ($transSeason)"
        }

        val cleanName = cropName.split("/").firstOrNull()?.trim() ?: cropName

        if (targetLangCode == "en") {
            val directMatch = getDirectTranslation(cleanName, targetLangCode, context)
            if (directMatch != null && !hasNonEnglishCharacters(directMatch)) return directMatch
            return getEnglishCropName(cropName)
        }

        if (targetLangCode == "hi") {
            val directMatch = getDirectTranslation(cleanName, targetLangCode, context)
            if (directMatch != null) return directMatch
            return getHindiCropName(cleanName)
        }

        val directMatch = getDirectTranslation(cleanName, targetLangCode, context)
        if (directMatch != null) return directMatch

        return getEnglishCropName(cleanName)
    }

    fun getTranslatedSeasonName(seasonName: String, targetLangCode: String, context: Context? = null): String {
        if (seasonName.isBlank()) return seasonName
        val kharifTrans = getDirectTranslation("Kharif", targetLangCode, context) ?: if (targetLangCode == "hi") "खरीफ" else "Kharif"
        val rabiTrans = getDirectTranslation("Rabi", targetLangCode, context) ?: if (targetLangCode == "hi") "रबी" else "Rabi"
        val zaidTrans = getDirectTranslation("Zaid", targetLangCode, context) ?: if (targetLangCode == "hi") "जायद" else "Zaid"

        return seasonName
            .replace("Kharif", kharifTrans, ignoreCase = true)
            .replace("खरीफ", kharifTrans, ignoreCase = true)
            .replace("Rabi", rabiTrans, ignoreCase = true)
            .replace("रबी", rabiTrans, ignoreCase = true)
            .replace("Zaid", zaidTrans, ignoreCase = true)
            .replace("जायद", zaidTrans, ignoreCase = true)
    }

    fun getCleanMarketName(rawMarket: String): String {
        if (rawMarket.isBlank()) return rawMarket
        var cleaned = rawMarket
            .replace("A.P.M.C.", "", ignoreCase = true)
            .replace("a.p.m.c.", "", ignoreCase = true)
            .replace("APMC", "", ignoreCase = true)
            .replace("apmc", "", ignoreCase = true)
            .replace(Regex("\\s*\\((?:F&V|Fruit & Vegetable|Vegetable|Grain|Krishi Upaj Mandi|Krishi Upaj|Market|Ag Market|Fruits|Vegetables)\\s*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            .replace("()", "")
            .replace("( )", "")
            .trim()
        cleaned = cleaned.replace(Regex("^[,\\s-]+|[,\\s-]+$"), "").trim()
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        return cleaned.ifBlank { rawMarket }
    }

    private val commonMandiNamesHindi = mapOf(
        "indore" to "इंदौर", "ujjain" to "उज्जैन", "bhopal" to "भोपाल", "dewas" to "देवास", "ratlam" to "रतलाम",
        "neemuch" to "नीमच", "mandsaur" to "मंदसौर", "dhar" to "धार", "khargone" to "खरगोन", "khandwa" to "खंडवा",
        "badnawar" to "बदनावर", "badnagar" to "बड़नगर", "jaora" to "जावरा", "shajapur" to "शाजापुर", "sehore" to "सीहोर",
        "raisen" to "रायसेन", "vidisha" to "विदिशा", "sagar" to "सागर", "satna" to "सतना", "rewa" to "रीवा",
        "chhindwara" to "छिंदवाड़ा", "burhanpur" to "बुरहानपुर", "harda" to "हरदा", "hoshangabad" to "होशंगाबाद", "narsinghpur" to "नरसिंहपुर",
        "gwalior" to "ग्वालियर", "jabalpur" to "जबलपुर", "katni" to "कटनी", "seoni" to "सिवनी", "balaghat" to "बालाघाट",
        "mandla" to "मंडला", "dindori" to "डिंडौरी", "umaria" to "उमरिया", "shahdol" to "शहडोल", "anuppur" to "अनूपपुर",
        "sidhi" to "सीधी", "singrauli" to "सिंगरौली", "panna" to "पन्ना", "chhatarpur" to "छतरपुर", "tikamgarh" to "टीकमगढ़",
        "niwari" to "निवाड़ी", "damoh" to "दमोह", "bhind" to "भिंड", "morena" to "मुरैना", "sheopur" to "श्योपुर",
        "shivpuri" to "शिवपुरी", "guna" to "गुणा", "ashoknagar" to "अशोकनगर", "datia" to "दतिया", "betul" to "बैतूल",
        "agar malwa" to "आगर मालवा", "rajgarh" to "राजगढ़", "jhabua" to "झाबुआ", "alirajpur" to "अलीराजपुर", "barwani" to "बड़वानी",
        "sanwer" to "सांवेर", "mhow" to "महू", "depalpur" to "देपालपुर", "tarana" to "तराना", "nagda" to "नागदा",
        "khachrod" to "खाचरौद", "mahidpur" to "महिदपुर", "anjad" to "अंजड़", "sendhwa" to "सेंधवा", "alot" to "आलोट",
        "sailana" to "सैलाना", "manawar" to "मनावर", "kukshi" to "कुक्षी", "gautampura" to "गौतमपुरा", "malthone" to "मल्थोन",
        "bina" to "बीना", "gadarwara" to "गाडरवारा", "gotegaon" to "गोटेगांव", "multai" to "मुलताई", "pandhurna" to "पांढुर्णा",
        "sausar" to "सौसर", "piploda" to "पिपलोदा", "shujalpur" to "शुजालपुर", "ashta" to "आष्टा", "ganj basoda" to "गंज बासोदा",
        "khategaon" to "खातेगांव", "bagli" to "बागली", "kannod" to "कन्नौद", "hatpipalya" to "हाटपिपल्या", "sonkatch" to "सोनकच्छ",
        "ichhawar" to "इच्छावर", "budhni" to "बुधनी", "bareli" to "बरेली", "silwani" to "सिलवानी", "udaipura" to "उदयपुरा",
        "gyaraspur" to "ग्यारसपुर", "sironj" to "सिरोंज", "basoda" to "बासोदा", "khurai" to "खुरई", "banda" to "बंडा",
        "rehli" to "रहली", "deori" to "देवरी", "shahgarh" to "शाहगढ़", "agra" to "आगरा", "kanpur" to "कानपुर", "lucknow" to "लखनऊ",
        "jaipur" to "जयपुर", "kota" to "कोटा", "jodhpur" to "जोधपुर", "bikaner" to "बीकानेर", "udaipur" to "उदयपुर",
        "ahmedabad" to "अहमदाबाद", "surat" to "सूरत", "rajkot" to "राजकोट", "vadodara" to "वडोदरा", "nagpur" to "नागपुर",
        "nashik" to "नाशिक", "pune" to "पुणे", "mumbai" to "मुंबई", "delhi" to "दिल्ली", "mathura" to "मथुरा", "bareilly" to "बरेली",
        "aligarh" to "अलीगढ़", "meerut" to "मेरठ", "varanasi" to "वाराणसी", "prayagraj" to "प्रयागराज", "gorakhpur" to "गोरखपुर",
        "samastipur" to "समस्तीपुर", "patna" to "पटना", "muzaffarpur" to "मुजफ्फरपुर", "gaya" to "गया", "bhagalpur" to "भागलपुर",
        "purnia" to "पूर्णिया", "darbhanga" to "दरभंगा"
    )

    fun getTranslatedMarketName(marketName: String, targetLangCode: String, context: Context? = null): String {
        if (marketName.isBlank()) return marketName
        val cleanMarket = getCleanMarketName(marketName)

        // 1. Check Master Remote Dictionary Cache from Firebase / Assets first
        getDirectTranslation(cleanMarket, targetLangCode, context)?.let { return it }

        if (targetLangCode == "hi") {
            val lower = cleanMarket.lowercase(Locale.ENGLISH)
            for ((eng, hindi) in commonMandiNamesHindi) {
                if (lower == eng || lower.contains(eng)) {
                    return cleanMarket.replace(eng, hindi, ignoreCase = true)
                }
            }
            return cleanMarket
        }

        if (targetLangCode == "en") {
            var result = cleanMarket
            for ((eng, hindi) in commonMandiNamesHindi) {
                if (result.contains(hindi, ignoreCase = true)) {
                    val formattedEng = eng.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                    result = result.replace(hindi, formattedEng, ignoreCase = true)
                }
            }
            return result
        }

        return getDirectTranslation(cleanMarket, targetLangCode, context) ?: cleanMarket
    }

    fun getTranslatedLocationName(locationName: String, targetLangCode: String, context: Context? = null): String {
        if (locationName.isBlank()) return locationName
        return getTranslatedMarketName(locationName, targetLangCode, context)
    }

    fun getNormalizedCategoryName(rawCategory: String): String {
        val lower = rawCategory.trim().lowercase(Locale.ENGLISH)
        return when {
            lower.contains("seed") || lower.contains("बीज") -> "Seeds"
            lower.contains("fertilis") || lower.contains("fertiliz") || lower.contains("खाद") || lower.contains("उर्वरक") -> "Fertilizer"
            lower.contains("pesticid") || lower.contains("spray") || lower.contains("कीटनाशक") -> "Pesticide"
            lower.contains("irrig") || lower.contains("water") || lower.contains("सिंचाई") || lower.contains("सिंचन") || lower.contains("सिंचाइ") -> "Irrigation"
            lower.contains("machin") || lower.contains("मशीनरी") -> "Machinery"
            lower.contains("fuel") || lower.contains("diesel") || lower.contains("ईंधन") -> "Fuel"
            lower.contains("labour") || lower.contains("labor") || lower.contains("मजदूरी") || lower.contains("मजुरी") -> "Labour"
            lower.contains("till") || lower.contains("जुताई") || lower.contains("नांगरणी") -> "Tillage"
            lower.contains("stor") || lower.contains("भंडारण") || lower.contains("साठवणूक") -> "Storage"
            lower.contains("transport") || lower.contains("परिवहन") || lower.contains("वाहतूक") -> "Transport"
            lower.contains("insur") || lower.contains("बीमा") || lower.contains("विमा") -> "Insurance"
            lower.contains("loan") || lower.contains("interest") || lower.contains("ऋण") || lower.contains("कर्ज") -> "Loan Interest"
            lower.contains("crop sale") || lower.contains("फसल बिक्री") -> "Crop Sale"
            lower.contains("vegetable sale") || lower.contains("सब्जी बिक्री") -> "Vegetable Sale"
            lower.contains("rental") || lower.contains("किराया") || lower.contains("भाडे") -> "Tractor / Equipment Rental"
            lower.contains("fodder") || lower.contains("चारा") -> "Fodder Sale"
            else -> rawCategory.trim()
        }
    }

    fun getNormalizedStageName(rawStage: String): String {
        val lower = rawStage.trim().lowercase(Locale.ENGLISH)
        return when {
            lower.contains("sow") || lower.contains("बुआई") || lower.contains("पेरणी") || lower.contains("વાવણી") || lower.contains("ਬਿਜਾਈ") || lower.contains("বপন") -> "Sowing"
            lower.contains("irrig") || lower.contains("water") || lower.contains("सिंचाई") || lower.contains("सिंचन") || lower.contains("સિંચાઈ") || lower.contains("ਸਿੰਚਾਈ") || lower.contains("সেচ") -> "Irrigation"
            lower.contains("spray") || lower.contains("pesticide") || lower.contains("छिड़काव") || lower.contains("फवारणी") || lower.contains("છંટકાવ") || lower.contains("ਛਿੜਕਾਅ") || lower.contains("স্প্রে") -> "Spray"
            lower.contains("harvest") || lower.contains("cut") || lower.contains("कटाई") || lower.contains("काढणी") || lower.contains("કાપણી") || lower.contains("ਵਾਢੀ") || lower.contains("ফসল কাটা") -> "Harvesting"
            lower.contains("sale") || lower.contains("बिक्री") || lower.contains("विक्री") || lower.contains("વેચાણ") || lower.contains("ਵਿਕਰੀ") -> "Sale"
            else -> "Other"
        }
    }

    private val hindiCategoryMap = mapOf(
        "Seeds" to "बीज",
        "Fertilizer" to "उर्वरक",
        "Fertiliser" to "उर्वरक",
        "Pesticides" to "कीटनाशक",
        "Pesticide" to "कीटनाशक",
        "Irrigation" to "सिंचाई",
        "Machinery" to "मशीनरी",
        "Machinery & Fuel" to "मशीनरी व ईंधन",
        "Fuel" to "ईंधन",
        "Labour" to "मजदूरी",
        "Tillage" to "जुताई",
        "Storage" to "भंडारण",
        "Transport" to "परिवहन",
        "Insurance" to "बीमा",
        "Loan Interest" to "ऋण ब्याज",
        "Crop Sale" to "फसल बिक्री",
        "Vegetable Sale" to "सब्जी बिक्री",
        "Tractor / Equipment Rental" to "ट्रैक्टर/उपकरण किराया",
        "Fodder Sale" to "चारा बिक्री",
        "General" to "सामान्य"
    )

    private val hindiStageMap = mapOf(
        "Sowing" to "बुआई",
        "Irrigation" to "सिंचाई",
        "Spray" to "छिड़काव",
        "Harvesting" to "कटाई",
        "Sale" to "बिक्री",
        "Land Preparation" to "खेत तैयारी",
        "Other" to "अन्य",
        "General" to "सामान्य"
    )

    private val englishCategoryMap = mapOf(
        "बीज" to "Seeds",
        "Seeds" to "Seeds",
        "उर्वरक" to "Fertilizer",
        "खाद" to "Fertilizer",
        "Fertilizer" to "Fertilizer",
        "Fertiliser" to "Fertilizer",
        "कीटनाशक" to "Pesticides",
        "दवा" to "Pesticides",
        "Pesticide" to "Pesticides",
        "Pesticides" to "Pesticides",
        "सिंचाई" to "Irrigation",
        "Irrigation" to "Irrigation",
        "मशीनरी" to "Machinery",
        "मशीनरी व ईंधन" to "Machinery & Fuel",
        "Machinery" to "Machinery",
        "Fuel" to "Fuel",
        "ईंधन" to "Fuel",
        "मजदूरी" to "Labour",
        "Labour" to "Labour",
        "जुताई" to "Tillage",
        "Tillage" to "Tillage",
        "भंडारण" to "Storage",
        "Storage" to "Storage",
        "परिवहन" to "Transport",
        "Transport" to "Transport",
        "बीमा" to "Insurance",
        "Insurance" to "Insurance",
        "ऋण ब्याज" to "Loan Interest",
        "ऋण" to "Loan Interest",
        "ब्याज" to "Loan Interest",
        "Loan Interest" to "Loan Interest",
        "फसल बिक्री" to "Crop Sale",
        "Crop Sale" to "Crop Sale",
        "सब्जी बिक्री" to "Vegetable Sale",
        "Vegetable Sale" to "Vegetable Sale",
        "ट्रैक्टर/उपकरण किराया" to "Tractor / Equipment Rental",
        "किराया" to "Tractor / Equipment Rental",
        "Tractor / Equipment Rental" to "Tractor / Equipment Rental",
        "चारा बिक्री" to "Fodder Sale",
        "Fodder Sale" to "Fodder Sale",
        "सामान्य" to "General",
        "General" to "General"
    )

    private val englishStageMap = mapOf(
        "बुआई" to "Sowing",
        "Sowing" to "Sowing",
        "सिंचाई" to "Irrigation",
        "Irrigation" to "Irrigation",
        "छिड़काव" to "Spray",
        "Spray" to "Spray",
        "कटाई" to "Harvesting",
        "Harvesting" to "Harvesting",
        "बिक्री" to "Sale",
        "Sale" to "Sale",
        "खेत तैयारी" to "Land Preparation",
        "Land Preparation" to "Land Preparation",
        "अन्य" to "Other",
        "Other" to "Other",
        "सामान्य" to "General",
        "General" to "General"
    )

    fun getHindiCategoryName(category: String): String {
        return getTranslatedCategoryName(category, "hi")
    }

    fun getTranslatedCategoryName(category: String, targetLangCode: String, context: Context? = null): String {
        if (category.isBlank()) return category
        val normalized = getNormalizedCategoryName(category)
        if (targetLangCode == "hi") {
            return getDirectTranslation(normalized, targetLangCode, context)
                ?: getDirectTranslation(category.trim(), targetLangCode, context)
                ?: hindiCategoryMap[normalized]
                ?: hindiCategoryMap[category.trim()]
                ?: category.trim()
        }
        if (targetLangCode == "en") {
            return englishCategoryMap[category.trim()]
                ?: englishCategoryMap[normalized]
                ?: getDirectTranslation(normalized, targetLangCode, context)
                ?: getDirectTranslation(category.trim(), targetLangCode, context)
                ?: normalized
        }
        return getDirectTranslation(normalized, targetLangCode, context)
            ?: getDirectTranslation(category.trim(), targetLangCode, context)
            ?: normalized
    }

    fun getTranslatedStageName(stage: String, targetLangCode: String, context: Context? = null): String {
        if (stage.isBlank()) return stage
        val normalized = getNormalizedStageName(stage)
        if (targetLangCode == "hi") {
            return getDirectTranslation(normalized, targetLangCode, context)
                ?: getDirectTranslation(stage.trim(), targetLangCode, context)
                ?: hindiStageMap[normalized]
                ?: hindiStageMap[stage.trim()]
                ?: stage.trim()
        }
        if (targetLangCode == "en") {
            return englishStageMap[stage.trim()]
                ?: englishStageMap[normalized]
                ?: getDirectTranslation(normalized, targetLangCode, context)
                ?: getDirectTranslation(stage.trim(), targetLangCode, context)
                ?: normalized
        }
        return getDirectTranslation(normalized, targetLangCode, context)
            ?: getDirectTranslation(stage.trim(), targetLangCode, context)
            ?: stage.trim()
    }

    fun getTranslatedTypeBadge(isIncome: Boolean, targetLangCode: String, context: Context? = null): String {
        if (targetLangCode == "hi") {
            return if (isIncome) "💰 आय (+)" else "💸 खर्च (-)"
        } else if (targetLangCode == "en") {
            return if (isIncome) "💰 Income (+)" else "💸 Expense (-)"
        }
        val key = if (isIncome) "Income" else "Expense"
        val trans = getDirectTranslation(key, targetLangCode, context)
        return if (isIncome) "💰 ${trans ?: "Income"} (+)" else "💸 ${trans ?: "Expense"} (-)"
    }

    fun getTranslatedDate(dateStr: String, targetLangCode: String): String {
        if (dateStr.isBlank() || targetLangCode == "en") return dateStr
        var result = dateStr
        val monthMap = mapOf(
            "Jan" to "जनवरी", "Feb" to "फरवरी", "Mar" to "मार्च", "Apr" to "अप्रैल",
            "May" to "मई", "Jun" to "जून", "Jul" to "जुलाई", "Aug" to "अगस्त",
            "Sep" to "सितंबर", "Oct" to "अक्टूबर", "Nov" to "नवंबर", "Dec" to "दिसंबर"
        )
        for ((eng, hi) in monthMap) {
            if (result.contains(eng, ignoreCase = true)) {
                result = result.replace(eng, hi, ignoreCase = true)
            }
        }
        return result
    }

    fun hasNonEnglishCharacters(text: String): Boolean {
        for (char in text) {
            val code = char.code
            if (code in 0x0900..0x097F || (Character.UnicodeBlock.of(char) != Character.UnicodeBlock.BASIC_LATIN && Character.UnicodeBlock.of(char) != Character.UnicodeBlock.LATIN_1_SUPPLEMENT)) {
                return true
            }
        }
        return false
    }

    suspend fun translateText(context: Context, text: String, targetLangCode: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || isProtectedActionWord(text)) return@withContext text

        getDirectTranslation(text, targetLangCode, context)?.let { return@withContext it }

        if (targetLangCode == "en" && !hasNonEnglishCharacters(text)) {
            return@withContext text
        }

        val cacheKey = "$targetLangCode:$text"
        translationCache[cacheKey]?.let { return@withContext it }

        val translated = translateWithGoogle(text, targetLangCode)
        if (translated.isNotBlank() && translated != text) {
            val sanitized = sanitizeAgriTranslation(translated, targetLangCode)
            translationCache[cacheKey] = sanitized
            return@withContext sanitized
        }

        return@withContext text
    }

    private suspend fun translateWithGoogle(text: String, targetLangCode: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        try {
            val protectedList = mutableListOf<String>()
            var protectedText = text

            val linkPattern = "(?i)(\\[.*?\\]\\(https?://.*?\\)|<a\\s+href=[\"'].*?[\"']>.*?</a>|https?://[a-zA-Z0-9./_\\-?=%&#]+)".toRegex()
            val matches = linkPattern.findAll(protectedText).toList()

            for ((index, match) in matches.withIndex()) {
                val token = "___LINK_${index}___"
                protectedList.add(match.value)
                protectedText = protectedText.replace(match.value, token)
            }

            val termProtectedList = mutableListOf<String>()
            val termPattern = "\\b(?:PM-Kisan|PMKisan|DAP|NPK|Urea|FTO|SBI|IFFCO|NABARD|KCC|MSP|AQI|hPa|km/h|m/s|[A-Z]{2,10})\\b".toRegex()
            val termMatches = termPattern.findAll(protectedText).toList()

            for ((index, match) in termMatches.withIndex()) {
                val token = "___TERM_${index}___"
                termProtectedList.add(match.value)
                protectedText = protectedText.replace(match.value, token)
            }

            val encoded = URLEncoder.encode(protectedText, "UTF-8")
            val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t&q=$encoded"

            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArr = JSONArray(responseStr)
                val sentencesArr = jsonArr.optJSONArray(0)

                val sb = StringBuilder()
                if (sentencesArr != null) {
                    for (i in 0 until sentencesArr.length()) {
                        val sentence = sentencesArr.optJSONArray(i)
                        if (sentence != null && sentence.length() > 0) {
                            sb.append(sentence.optString(0, ""))
                        }
                    }
                }

                var result = sb.toString().trim()

                for ((index, originalTerm) in termProtectedList.withIndex()) {
                    val token = "___TERM_${index}___"
                    result = result.replace(token, originalTerm)
                        .replace("___ TERM_${index} ___", originalTerm)
                        .replace("___ TERM _ ${index} ___", originalTerm)
                        .replace("___TERM _ ${index}___", originalTerm)
                }

                for ((index, originalLink) in protectedList.withIndex()) {
                    val token = "___LINK_${index}___"
                    result = result.replace(token, originalLink)
                        .replace("___ LINK_${index} ___", originalLink)
                        .replace("___ LINK _ ${index} ___", originalLink)
                        .replace("___LINK _ ${index}___", originalLink)
                }

                return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext text
    }

    private fun sanitizeAgriTranslation(text: String, targetLangCode: String): String {
        if (text.isBlank()) return text
        var result = text

        when (targetLangCode) {
            "hi" -> {
                result = result
                    .replace("इंदूर", "इंदौर")
                    .replace("उज्जयिनी", "उज्जैन")
                    .replace("फुहार बहाव", "छिड़काव बहाव")
                    .replace("फुहार ड्रिफ्ट", "छिड़काव बहाव")
                    .replace("केमिकल फुहार", "रासायनिक छिड़काव")
                    .replace("रासायनिक फुहार", "रासायनिक छिड़काव")
                    .replace("कीटनाशक फुहार", "कीटनाशक छिड़काव")
                    .replace("सुरक्षित फुहार", "सुरक्षित छिड़काव")
                    .replace("फुहार सलाह", "छिड़काव सलाह")
                    .replace("फुहार समय", "छिड़काव समय")
                    .replace("फुहार की आवश्यकता", "छिड़काव की आवश्यकता")
                    .replace("फुहार से बचें", "छिड़काव से बचें")
                    .replace("फुहार", "छिड़काव")
                    .replace("फुआरा", "छिड़काव")
                    .replace("बौछार", "छिड़काव")
            }
        }

        return result
    }

    private val protectedActionWords = setOf(
        "sign in", "sign up", "log in", "log out", "cancel", "save", "delete", "edit", "submit", "share"
    )

    private fun isProtectedActionWord(text: String): Boolean {
        val cleaned = text.replace(Regex("[\\p{So}\\p{Cn}]"), "").trim().lowercase()
        return protectedActionWords.contains(cleaned)
    }
}
