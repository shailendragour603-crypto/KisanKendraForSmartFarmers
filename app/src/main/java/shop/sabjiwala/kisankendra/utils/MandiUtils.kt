package shop.sabjiwala.kisankendra.utils

object MandiUtils {

    // 🌾 Fast In-Memory Reverse Translation Dictionary (English to Hindi)
    private val englishToHindiMap = mapOf(
        "wheat" to "गेहूँ",
        "soyabean" to "सोयाबीन",
        "soybean" to "सोयाबीन",
        "onion" to "प्याज़",
        "potato" to "आलू",
        "tomato" to "टमाटर",
        "gram" to "चना",
        "chana" to "चना",
        "mustard" to "सरसों",
        "paddy" to "धान",
        "rice" to "चावल",
        "maize" to "मक्का",
        "garlic" to "लहसुन",
        "ginger" to "अदरक",
        "chilli" to "मिर्च",
        "chillies" to "मिर्च",
        "dry chillies" to "सूखी मिर्च",
        "cotton" to "कपास",
        "sugarcane" to "गन्ना",
        "groundnut" to "मूंगफली",
        "moong" to "मूंग",
        "urad" to "उड़द",
        "arhar" to "अरहर / तुअर",
        "tur" to "तुअर",
        "coriander" to "धनिया",
        "cumin" to "जीरा",
        "turmeric" to "हल्दी",
        "bajra" to "बाजरा",
        "jowar" to "ज्वार",
        "barley" to "जौ",
        "peas" to "मटर",
        "brinjal" to "बैंगन",
        "cauliflower" to "फूलगोभी",
        "cabbage" to "पत्तागोभी",
        "bhindi" to "भिंडी",
        "indore" to "इंदौर",
        "ujjain" to "उज्जैन",
        "ratlam" to "रतलाम",
        "khandwa" to "खंडवा",
        "bhopal" to "भोपाल",
        "jaora" to "जावरा",
        "dewas" to "देवास",
        "dhar" to "धार",
        "mandsaur" to "मंदसौर",
        "neemuch" to "नीमच",
        "shajapur" to "शाजापुर",
        "khargone" to "खरगोन",
        "madhya pradesh" to "मध्य प्रदेश",
        "rajasthan" to "राजस्थान",
        "maharashtra" to "महाराष्ट्र",
        "gujarat" to "गुजरात",
        "punjab" to "पंजाब",
        "haryana" to "हरियाणा",
        "uttar pradesh" to "उत्तर प्रदेश"
    )

    fun translateFast(englishText: String?): String {
        if (englishText.isNullOrBlank()) return ""
        val key = englishText.lowercase().trim()
        return englishToHindiMap[key] ?: englishText
    }

    // 🌾 Comprehensive Hindi to English Agri & Mandi Dictionary
    private val hindiToEnglishMap = mapOf(
        "गेहूं" to "wheat", "गेहुं" to "wheat", "गेहू" to "wheat", "गेहु" to "wheat",
        "सोयाबीन" to "soyabean", "सोयाबिन" to "soyabean", "सोया" to "soyabean",
        "प्याज" to "onion", "कांदा" to "onion", "प्याज़" to "onion",
        "आलू" to "potato", "आलु" to "potato",
        "टमाटर" to "tomato",
        "चना" to "gram", "चने" to "gram", "छोला" to "gram",
        "सरसों" to "mustard", "सरसो" to "mustard", "राई" to "mustard",
        "धान" to "paddy", "चावल" to "rice", "बासमती" to "paddy",
        "मक्का" to "maize", "मक्की" to "maize", "भुट्टा" to "maize",
        "लहसुन" to "garlic", "लहुसन" to "garlic",
        "अदरक" to "ginger",
        "मिर्च" to "chilli", "मिर्ची" to "chilli", "हरी मिर्च" to "chilli", "लाल मिर्च" to "chilli",
        "कपास" to "cotton", "कॉटन" to "cotton", "कोटन" to "cotton", "रुई" to "cotton",
        "गन्ना" to "sugarcane",
        "मूंगफली" to "groundnut", "मुंगफली" to "groundnut", "फली" to "groundnut",
        "मूंग" to "moong", "मुंग" to "moong",
        "उड़द" to "urad", "उडद" to "urad",
        "अरहर" to "arhar", "तुअर" to "arhar", "तुवर" to "arhar", "तूर" to "arhar",
        "धनिया" to "coriander",
        "जीरा" to "cumin",
        "सौंफ" to "fennel",
        "मेथी" to "fenugreek",
        "हल्दी" to "turmeric",
        "तिल" to "sesamum",
        "बाजरा" to "bajra",
        "ज्वार" to "jowar",
        "जौ" to "barley",
        "मटर" to "peas",
        "बैंगन" to "brinjal", "भाटा" to "brinjal",
        "गोभी" to "cauliflower", "फूलगोभी" to "cauliflower",
        "पत्तागोभी" to "cabbage",
        "भिंडी" to "bhindi",
        "गाजर" to "carrot",
        "मूली" to "radish",
        "पालक" to "spinach",
        "खीरा" to "cucumber", "ककड़ी" to "cucumber",
        "केला" to "banana",
        "सेब" to "apple",
        "आम" to "mango",
        "संतरा" to "orange",
        "नींबू" to "lemon", "निंबू" to "lemon",
        "अनार" to "pomegranate",
        "तरबूज" to "watermelon",
        "पपीता" to "papaya",
        "अमरूद" to "guava",
        "अंगूर" to "grapes",

        "इंदौर" to "indore", "इंदोर" to "indore",
        "उज्जैन" to "ujjain",
        "रतलाम" to "ratlam",
        "खंडवा" to "khandwa",
        "भोपाल" to "bhopal",
        "जावरा" to "jaora",
        "देवास" to "dewas",
        "धार" to "dhar",
        "मंदसौर" to "mandsaur", "मंदसोर" to "mandsaur",
        "नीमच" to "neemuch",
        "शाजापुर" to "shajapur",
        "खरगोन" to "khargone",
        "बड़वानी" to "barwani",
        "सीहोर" to "sehore",
        "विदिशा" to "vidisha",
        "रायसेन" to "raisen",
        "जबलपुर" to "jabalpur",
        "ग्वालियर" to "gwalior",
        "सागर" to "sagar",
        "सतना" to "satna",
        "रीवा" to "rewa",
        "जयपुर" to "jaipur",
        "कोटा" to "kota",
        "जोधपुर" to "jodhpur",
        "बीकानेर" to "bikaner",
        "उदयपुर" to "udaipur",
        "दिल्ली" to "delhi",
        "नागपुर" to "nagpur",
        "नासिक" to "nashik", "नाशिक" to "nashik",
        "पुणे" to "pune", "पुणा" to "pune",
        "मुंबई" to "mumbai",
        "अहमदाबाद" to "ahmedabad",
        "राजकोट" to "rajkot",
        "सूरत" to "surat",
        "लखनऊ" to "lucknow",
        "कानपुर" to "kanpur",
        "वाराणसी" to "varanasi",
        "आगरा" to "agra",
        "लुधियाना" to "ludhiana",
        "अमृतसर" to "amritsar",

        "मध्य प्रदेश" to "madhya pradesh", "एमपी" to "madhya pradesh",
        "राजस्थान" to "rajasthan",
        "महाराष्ट्र" to "maharashtra",
        "गुजरात" to "gujarat",
        "पंजाब" to "punjab",
        "हरियाणा" to "haryana",
        "उत्तर प्रदेश" to "uttar pradesh", "यूपी" to "uttar pradesh",
        "बिहार" to "bihar",
        "छत्तीसगढ़" to "chhattisgarh"
    )

    private val emojiMap = mapOf(
        "wheat" to "🌾", "rice" to "🍚", "paddy" to "🌾", "maize" to "🌽", "corn" to "🌽",
        "soyabean" to "🌱", "cotton" to "🧵", "sugarcane" to "🎋", "mustard" to "🌼",
        "gram" to "🫘", "pulses" to "🫘", "lentil" to "🫘", "arhar" to "🫘", "tur" to "🫘",
        "onion" to "🧅", "potato" to "🥔", "tomato" to "🍅", "peas" to "🫛",
        "cabbage" to "🥬", "cauliflower" to "🥦", "garlic" to "🧄", "ginger" to "🫚",
        "chilli" to "🌶️", "dry chillies" to "🌶️", "brinjal" to "🍆",
        "banana" to "🍌", "apple" to "🍎", "apple (green)" to "🍏", "grapes" to "🍇",
        "mango" to "🥭", "orange" to "🍊", "lemon" to "🍋", "pomegranate" to "🍎",
        "watermelon" to "🍉", "papaya" to "🟧", "groundnut" to "🥜",
        "jowar" to "🌾", "bajra" to "🌾", "barley" to "🌾", "sunflower" to "🌻",
        "carrot" to "🥕", "radish" to "🥕", "spinach" to "🥬", "cucumber" to "🥒",
        "pineapple" to "🍍", "strawberry" to "🍓", "raspberry" to "🍓", "blueberries" to "🫐",
        "avocado" to "🥑", "kiwi" to "🥝", "guava" to "🍐", "coconut" to "🥥", "cherry" to "🍒",
        "coriander" to "🌿", "fenugreek" to "🌿", "mint" to "🌿"
    )

    fun getEmoji(commodity: String?): String {
        if (commodity.isNullOrBlank()) return "🚜"
        val key = commodity.lowercase().trim()

        if (emojiMap.containsKey(key)) {
            return emojiMap[key]!!
        }

        return emojiMap.entries.firstOrNull { key.contains(it.key) }?.value ?: "🚜"
    }

    fun getSearchKeywords(query: String): List<String> {
        val cleaned = query.replace(Regex("[।?!.,]"), " ").trim()
        if (cleaned.isBlank()) return emptyList()

        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val resultKeywords = mutableListOf<String>()

        for (token in tokens) {
            val lower = token.lowercase()
            if (hindiToEnglishMap.containsKey(lower)) {
                val translated = hindiToEnglishMap[lower]!!
                resultKeywords.addAll(translated.split(" "))
            } else if (hindiToEnglishMap.containsKey(token)) {
                val translated = hindiToEnglishMap[token]!!
                resultKeywords.addAll(translated.split(" "))
            } else {
                val phonetics = safeSearch(token)
                resultKeywords.add(phonetics)
                if (lower.length >= 3) {
                    resultKeywords.add(lower)
                }
            }
        }

        return resultKeywords.distinct().filter { it.length >= 2 }
    }

    fun safeSearch(text: String): String {
        val cleaned = text.replace(Regex("[।?!.,]"), "").trim()
        val lower = cleaned.lowercase()

        if (hindiToEnglishMap.containsKey(lower)) return hindiToEnglishMap[lower]!!

        val phonetics = mapOf(
            'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
            'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'क' to "k", 'خ' to "kh",
            'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t",
            'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "f",
            'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l",
            'व' to "v", 'श' to "sh", 'स' to "s", 'ह' to "h", 'ा' to "a", 'ि' to "i",
            'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o",
            'ौ' to "au", 'ं' to "n"
        )

        val sb = StringBuilder()
        for (char in cleaned) {
            sb.append(phonetics[char] ?: char)
        }
        return sb.toString().lowercase()
    }
}
