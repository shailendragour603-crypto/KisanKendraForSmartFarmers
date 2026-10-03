package shop.sabjiwala.kisankendra.utils

import shop.sabjiwala.kisankendra.model.MandiRecord

object CategoryUtils {

    val MAJOR_CATEGORIES = listOf(
        "🌾 All Crops / Grains",
        "🥬 All Vegetables",
        "🌶️ All Spices & Fruits"
    )

    val POPULAR_ITEMS = listOf(
        "Wheat (गेहूँ)",
        "Soyabean (सोयाबीन)",
        "Onion (प्याज़)",
        "Potato (आलू)",
        "Garlic (लहसुन)",
        "Tomato (टमाटर)",
        "Gram / Chana (चना)",
        "Mustard (सरसों)",
        "Cotton (कपास)",
        "Paddy (धान)"
    )

    val CATEGORIES = MAJOR_CATEGORIES

    private val VEGETABLES = setOf("potato", "onion", "tomato", "brinjal", "cabbage", "cauliflower", "carrot", "radish", "spinach", "bitter gourd", "bottle gourd", "capsicum", "green chilli", "cucumber", "peas", "pumpkin", "lady finger", "bhindi")
    private val FRUITS = setOf("apple", "banana", "mango", "orange", "grapes", "papaya", "pomegranate", "watermelon", "guava", "pineapple", "lemon", "mousambi")
    private val CROPS = setOf("wheat", "paddy", "rice", "maize", "barley", "jowar", "bajra", "mustard", "soyabean", "soybean", "chana", "gram", "arhar", "moong", "urad", "cotton", "sugarcane")
    private val SPICES = setOf("garlic", "ginger", "turmeric", "coriander", "cumin", "cardamom", "black pepper", "red chilli", "fenugreek", "fennel")

    fun buildFullCategoryDropdown(allRecords: List<MandiRecord>): List<String> {
        val list = mutableListOf<String>()

        // 1. Major Category Groups
        list.addAll(MAJOR_CATEGORIES)

        // 2. Popular Crops
        list.add("--- Popular Crops ---")
        list.addAll(POPULAR_ITEMS)

        // 3. All Other Dynamic Available Items from API Data
        val dynamicItems = allRecords
            .mapNotNull { it.commodity?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val popularKeys = setOf("wheat", "soyabean", "soybean", "onion", "potato", "garlic", "tomato", "gram", "chana", "mustard", "cotton", "paddy")
        val otherItems = dynamicItems.filterNot { item ->
            val lower = item.lowercase()
            popularKeys.any { lower.contains(it) }
        }

        if (otherItems.isNotEmpty()) {
            list.add("--- All Other Commodities ---")
            list.addAll(otherItems)
        }

        return list
    }

    fun matchesSelection(commodityName: String, selectedOption: String): Boolean {
        if (selectedOption.isBlank() || selectedOption.startsWith("---")) return true

        val raw = commodityName.lowercase().trim()
        val option = selectedOption.lowercase().trim()

        return when {
            option.contains("all crops") -> {
                CROPS.any { raw.contains(it) }
            }
            option.contains("all vegetables") -> {
                VEGETABLES.any { raw.contains(it) }
            }
            option.contains("all spices") -> {
                SPICES.any { raw.contains(it) } || FRUITS.any { raw.contains(it) }
            }
            option.contains("(") -> {
                val englishName = option.substringBefore("(").substringBefore("/").trim().lowercase()
                raw.contains(englishName) || englishName.contains(raw)
            }
            else -> {
                raw.contains(option) || option.contains(raw)
            }
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