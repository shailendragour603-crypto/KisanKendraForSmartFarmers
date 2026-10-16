package shop.sabjiwala.kisankendra.utils

import shop.sabjiwala.kisankendra.model.MandiRecord

object CategoryUtils {

    val MAJOR_CATEGORIES = listOf(
        "All",
        "🌾 Crops & Grains (अनाज व फसलें)",
        "🥬 Vegetables (सब्जियाँ)",
        "🍎 Fruits (फल)",
        "🌶️ Spices (मसाले)"
    )

    val POPULAR_ITEMS = listOf(
        "गेहूँ (Wheat)",
        "धान / चावल (Paddy / Rice)",
        "सोयाबीन (Soybean)",
        "प्याज़ (Onion)",
        "आलू (Potato)",
        "लहसुन (Garlic)",
        "टमाटर (Tomato)",
        "चना (Gram / Chana)",
        "सरसों (Mustard)",
        "कपास (Cotton)",
        "मक्का (Maize)",
        "गन्ना (Sugarcane)",
        "हरी मिर्च (Green Chilli)",
        "बैंगन (Brinjal)",
        "भिंडी (Okra / Bhindi)",
        "आम (Mango)",
        "केला (Banana)",
        "सेब (Apple)"
    )

    val CATEGORIES = MAJOR_CATEGORIES

    private val VEGETABLES = setOf(
        "potato", "onion", "pyaz", "pyaaz", "kanda", "tomato", "brinjal", "cabbage", "cauliflower", "carrot", "radish", "spinach",
        "bitter gourd", "bottle gourd", "ridge gourd", "capsicum", "green chilli", "cucumber", "peas",
        "pumpkin", "lady finger", "bhindi", "coriander", "ginger", "turmeric", "sweet potato", "beetroot", "arbi", "taro",
        "आलू", "प्याज़", "प्याज", "कांदा", "टमाटर", "बैंगन", "पत्तागोभी", "फूलगोभी", "गाजर", "मूली", "पालक",
        "करेला", "लौकी", "तोरई", "शिमला मिर्च", "हरी मिर्च", "खीरा", "मटर",
        "कद्दू", "भिंडी", "धनिया", "अदरक", "हल्दी", "शकरकंद", "चुकंदर", "अरबी"
    )

    private val FRUITS = setOf(
        "apple", "banana", "mango", "orange", "grapes", "papaya", "pomegranate", "watermelon", "muskmelon",
        "guava", "pineapple", "lemon", "mousambi", "dragon fruit", "strawberry", "jackfruit", "litchi", "chikoo", "sapodilla",
        "सेब", "केला", "आम", "संतरा", "अंगूर", "पपीता", "अनार", "तरबूज", "खरबूजा",
        "अमरूद", "अनानास", "नींबू", "मौसंबी", "ड्रैगन फ्रूट", "स्ट्रॉबेरी", "कटहल", "लीची", "चीकू"
    )

    private val CROPS = setOf(
        "wheat", "paddy", "rice", "maize", "barley", "jowar", "bajra", "mustard", "soyabean", "soybean",
        "chana", "gram", "chickpea", "arhar", "tur", "pigeon pea", "moong", "urad", "cotton", "sugarcane",
        "groundnut", "peanut", "til", "sesame", "sunflower",
        "गेहूँ", "धान", "चावल", "मक्का", "जौ", "ज्वार", "बाजरा", "सरसों", "सोयाबीन",
        "चना", "अरहर", "तुअर", "मूंग", "उड़द", "कपास", "गन्ना", "मूंगफली", "तिल", "सूरजमुखी"
    )

    private val SPICES = setOf(
        "garlic", "ginger", "turmeric", "coriander", "cumin", "cardamom", "black pepper", "red chilli", "fenugreek", "fennel",
        "लहसुन", "अदरक", "हल्दी", "धनिया", "जीरा", "इलायची", "काली मिर्च", "लाल मिर्च", "मेथी", "सौंफ"
    )

    fun getMajorCategories(activeLang: String): List<String> {
        return if (activeLang == "hi") {
            listOf(
                "सभी",
                "🌾 अनाज व प्रमुख फसलें",
                "🥬 सभी सब्जियाँ",
                "🍎 सभी फल",
                "🌶️ सभी मसाले"
            )
        } else if (activeLang == "en") {
            MAJOR_CATEGORIES
        } else {
            listOf(
                "All",
                TranslationHelper.getDirectTranslation("🌾 Crops & Grains (अनाज व फसलें)", activeLang) ?: "🌾 Crops & Grains (अनाज व फसलें)",
                TranslationHelper.getDirectTranslation("🥬 Vegetables (सब्जियाँ)", activeLang) ?: "🥬 Vegetables (सब्जियाँ)",
                TranslationHelper.getDirectTranslation("🍎 Fruits (फल)", activeLang) ?: "🍎 Fruits (फल)",
                TranslationHelper.getDirectTranslation("🌶️ Spices (मसाले)", activeLang) ?: "🌶️ Spices (मसाले)"
            )
        }
    }

    fun buildFullCategoryDropdown(allRecords: List<MandiRecord>, activeLang: String = "hi"): List<String> {
        val list = mutableListOf<String>()

        // 1. Major Category Groups
        list.addAll(getMajorCategories(activeLang))

        // 2. Popular Crops
        val headerPopular = if (activeLang == "hi") "--- लोकप्रिय फसलें ---" else if (activeLang == "en") "--- Popular Crops ---" else (TranslationHelper.getDirectTranslation("--- Popular Crops ---", activeLang) ?: "--- Popular Crops ---")
        list.add(headerPopular)

        for (item in POPULAR_ITEMS) {
            val transItem = if (activeLang == "hi") {
                item
            } else {
                val parenthesisRegex = "\\(([^)]+)\\)".toRegex()
                val match = parenthesisRegex.find(item)
                if (match != null) {
                    match.groupValues[1].trim()
                } else {
                    val clean = item.split("(").firstOrNull()?.trim() ?: item
                    TranslationHelper.getTranslatedCropName(clean, activeLang)
                }
            }
            list.add(transItem)
        }

        // 3. All Other Dynamic Available Items from API Data
        val dynamicItems = allRecords
            .mapNotNull { it.commodity?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val popularKeys = setOf("wheat", "soyabean", "soybean", "onion", "potato", "garlic", "tomato", "gram", "chana", "mustard", "cotton", "paddy", "गेहूँ", "सोयाबीन", "प्याज़", "आलू", "लहसुन", "टमाटर", "चना")
        val otherItems = dynamicItems.filterNot { item ->
            val lower = item.lowercase()
            popularKeys.any { lower.contains(it) }
        }

        if (otherItems.isNotEmpty()) {
            val headerOther = if (activeLang == "hi") "--- अन्य सभी जिंस (फसलें) ---" else if (activeLang == "en") "--- All Other Commodities ---" else (TranslationHelper.getDirectTranslation("--- All Other Commodities ---", activeLang) ?: "--- All Other Commodities ---")
            list.add(headerOther)
            for (other in otherItems) {
                val transOther = if (activeLang == "en") other else TranslationHelper.getTranslatedCropName(other, activeLang)
                list.add(transOther)
            }
        }

        return list
    }

    fun matchesSelection(commodityName: String, selectedOption: String): Boolean {
        if (selectedOption.isBlank() || selectedOption.startsWith("---")) return true

        val rawCommodity = commodityName.lowercase().trim()
        val option = selectedOption.lowercase().trim()

        // 1. "All / सभी" matches 100% of all commodities
        if (option == "all" || option == "सभी" || option.contains("all") || option.contains("सभी") || option.contains("all items") || option.contains("सभी जिंस") || option.contains("फसल, सब्जी व फल")) {
            return true
        }

        // 2. Major Category Groups (Crops/Grains, Vegetables, Fruits, Spices)
        if (option.contains("crops") || option.contains("अनाज") || option.contains("फसल")) {
            return CROPS.any { rawCommodity.contains(it) } || (!VEGETABLES.any { rawCommodity.contains(it) } && !FRUITS.any { rawCommodity.contains(it) } && !SPICES.any { rawCommodity.contains(it) })
        }
        if (option.contains("vegetables") || option.contains("सब्जि") || option.contains("भाजी")) {
            return VEGETABLES.any { rawCommodity.contains(it) }
        }
        if (option.contains("fruits") || option.contains("फल")) {
            return FRUITS.any { rawCommodity.contains(it) }
        }
        if (option.contains("spices") || option.contains("मसाले")) {
            return SPICES.any { rawCommodity.contains(it) }
        }

        // 3. Individual Crop Selection in ANY App Language
        // Extract English part if inside parenthesis e.g. "प्याज़ (Onion)" -> "Onion"
        val englishInParen = if (option.contains("(")) {
            option.substringAfter("(").substringBefore(")").trim()
        } else {
            option
        }

        // Canonical English Translation of the selected option
        val canonicalEnglishOption = TranslationHelper.getTranslatedCropName(englishInParen, "en").lowercase().trim()
        val rawHindiOption = TranslationHelper.getHindiCropName(selectedOption).lowercase().trim()

        // Collect search tokens
        val searchTokens = mutableSetOf<String>()
        if (canonicalEnglishOption.length >= 2) searchTokens.add(canonicalEnglishOption)
        if (englishInParen.length >= 2) searchTokens.add(englishInParen)
        if (rawHindiOption.length >= 2) searchTokens.add(rawHindiOption)

        // Split slash/comma variants e.g. "paddy / rice" -> ["paddy", "rice"]
        val splitTokens = searchTokens.flatMap { it.split("/", ",", " ").map { word -> word.trim() } }
            .filter { it.length >= 2 && !it.startsWith("---") }

        // Expand regional variants for top commodities
        val expandedTokens = mutableSetOf<String>()
        expandedTokens.addAll(splitTokens)

        for (token in splitTokens) {
            when {
                token.contains("onion") || token.contains("प्याज़") || token.contains("प्याज") || token.contains("kanda") -> {
                    expandedTokens.addAll(listOf("onion", "pyaz", "pyaaz", "kanda", "प्याज़", "प्याज", "कांदा"))
                }
                token.contains("potato") || token.contains("आलू") -> {
                    expandedTokens.addAll(listOf("potato", "alu", "aloo", "आलू", "आलु"))
                }
                token.contains("garlic") || token.contains("लहसुन") -> {
                    expandedTokens.addAll(listOf("garlic", "lahsun", "lahsoom", "लहसुन", "लहुसन"))
                }
                token.contains("tomato") || token.contains("टमाटर") -> {
                    expandedTokens.addAll(listOf("tomato", "tamatar", "टमाटर"))
                }
                token.contains("wheat") || token.contains("गेहूँ") || token.contains("गेहूं") -> {
                    expandedTokens.addAll(listOf("wheat", "gehu", "gehun", "गेहूँ", "गेहूं", "गेहु"))
                }
                token.contains("paddy") || token.contains("rice") || token.contains("धान") || token.contains("चावल") -> {
                    expandedTokens.addAll(listOf("paddy", "rice", "dhan", "chawal", "धान", "चावल"))
                }
                token.contains("soybean") || token.contains("soyabean") || token.contains("सोयाबीन") -> {
                    expandedTokens.addAll(listOf("soybean", "soyabean", "soya", "सोयाबीन", "सोया"))
                }
                token.contains("gram") || token.contains("chana") || token.contains("चना") -> {
                    expandedTokens.addAll(listOf("gram", "chana", "chickpea", "चना", "चने"))
                }
                token.contains("mustard") || token.contains("सरसों") -> {
                    expandedTokens.addAll(listOf("mustard", "sarson", "sarso", "rai", "सरसों", "सरसो", "राई"))
                }
            }
        }

        val canonicalRecordEnglish = TranslationHelper.getTranslatedCropName(commodityName, "en").lowercase().trim()

        return expandedTokens.any { token ->
            rawCommodity.contains(token) ||
            token.contains(rawCommodity) ||
            canonicalRecordEnglish.contains(token) ||
            token.contains(canonicalRecordEnglish)
        }
    }

    fun getCategory(commodityName: String): String {
        val lower = commodityName.lowercase()
        return when {
            VEGETABLES.any { lower.contains(it) } -> "Vegetables"
            FRUITS.any { lower.contains(it) } -> "Fruits"
            CROPS.any { lower.contains(it) } -> "Crops/Grains"
            SPICES.any { lower.contains(it) } -> "Spices"
            else -> "Other"
        }
    }
}