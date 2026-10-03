package shop.sabjiwala.kisankendra.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object TranslationHelper {

    private val translationCache = ConcurrentHashMap<String, String>()

    private val localizedDictionary = mapOf(
        "hi" to mapOf(
            "Add New Entry" to "नई एंट्री जोड़ें",
            "Expense" to "खर्च",
            "expense" to "खर्च",
            "Expenses" to "खर्च",
            "expenses" to "खर्च",
            "Total Expenses" to "कुल खर्च",
            "Total Expense" to "कुल खर्च",
            "Crop Expense" to "फसल खर्च",
            "Highest Expense Crop" to "सबसे ज्यादा खर्च वाली फसल",
            "Amount" to "Amount",
            "amount" to "Amount",
            "Agri Gyan" to "कृषि ज्ञान",
            "Govt Scheme" to "सरकारी योजना",
            "Agri Market" to "कृषि बाज़ार",
            "Crop Protection" to "फसल सुरक्षा",
            "Technology" to "कृषि तकनीक",
            "Success Stories" to "सफलता की कहानियाँ",
            "All News" to "सभी समाचार",
            "Kisan Diary" to "किसान डायरी",
            "Calculator" to "कैलकुलेटर",
            "Crop Care" to "फसल देखभाल",
            "Govt Schemes" to "सरकारी योजनाएँ",
            "Comment your thoughts..." to "अपने विचार कमेंट करें...",
            "Sign in to comment" to "साइन इन करें",
            "Spray" to "छिड़काव",
            "Spraying" to "छिड़काव",
            "Pesticide Spray" to "कीटनाशक छिड़काव",
            "Pesticide Spray Advisory" to "कीटनाशक छिड़काव सलाह",
            "Chemical Spray" to "रासायनिक छिड़काव",
            "Chemical Spraying" to "रासायनिक छिड़काव",
            "Organic Spray" to "जैविक छिड़काव",
            "Pesticides" to "कीटनाशक",
            "Pesticide" to "कीटनाशक",
            "Avoid Chemical Spraying" to "रासायनिक छिड़काव से बचें",
            "Safe Spraying Window" to "सुरक्षित छिड़काव का समय",
            "Crop Protection & Spray Calculator" to "फसल सुरक्षा और छिड़काव कैलकुलेटर",
            "Estimated Chemical Spray Cost" to "अनुमानित रासायनिक छिड़काव लागत",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 फसल सुरक्षा और छिड़काव कैलकुलेटर",
            "Sowing" to "बुआई",
            "Irrigation" to "सिंचाई",
            "Harvesting" to "कटाई",
            "Sale" to "बिक्री",
            "Seeds" to "बीज",
            "Fertilizer" to "खाद / उर्वरक",
            "Fertiliser" to "खाद / उर्वरक",
            "Labor" to "मजदूरी",
            "Labour" to "मजदूरी",
            "Machinery & Fuel" to "मशीनरी और ईंधन",
            "Machinery & Tractor" to "मशीनरी और ट्रैक्टर",
            "Diesel & Irrigation" to "डीजल और सिंचाई",
            "Electricity & Water" to "बिजली और पानी",
            "General Expense" to "सामान्य खर्च",
            "Miscellaneous" to "अन्य / विविध"
        ),
        "mr" to mapOf(
            "Agri Gyan" to "कृषी ज्ञान",
            "Govt Scheme" to "सरकारी योजना",
            "Agri Market" to "कृषी बाजार",
            "Crop Protection" to "पीक संरक्षण",
            "Technology" to "तंत्रज्ञान",
            "Success Stories" to "यशस्वी गाथा",
            "All News" to "सर्व बातम्या",
            "Kisan Diary" to "किसान डायरी",
            "Calculator" to "कॅल्क्युलेटर",
            "Crop Care" to "पीक काळजी",
            "Govt Schemes" to "सरकारी योजना",
            "Comment your thoughts..." to "तुमचे विचार कमेंट करा...",
            "Sign in to comment" to "साइन इन करा",
            "Spray" to "फवारणी",
            "Spraying" to "फवारणी",
            "Pesticide Spray" to "कीटकनाशक फवारणी",
            "Pesticide Spray Advisory" to "कीटकनाशक फवारणी सल्ला",
            "Chemical Spray" to "रासायनिक फवारणी",
            "Chemical Spraying" to "रासायनिक फवारणी",
            "Organic Spray" to "जैविक फवारणी",
            "Pesticides" to "कीटकनाशके",
            "Pesticide" to "कीटकनाशक",
            "Avoid Chemical Spraying" to "रासायनिक फवारणी टाळा",
            "Safe Spraying Window" to "सुरक्षित फवारणी वेळ",
            "Crop Protection & Spray Calculator" to "पीक संरक्षण आणि फवारणी कॅल्क्युलेटर",
            "Estimated Chemical Spray Cost" to "अंदाजित रासायनिक फवारणी खर्च",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 पीक संरक्षण आणि फवारणी कॅल्क्युलेटर",
            "Sowing" to "पेरणी",
            "Irrigation" to "सिंचन",
            "Harvesting" to "कापणी",
            "Sale" to "विक्री",
            "Seeds" to "बियाणे",
            "Fertilizer" to "खते",
            "Fertiliser" to "खते",
            "Labor" to "मजुरी",
            "Labour" to "मजुरी",
            "Machinery & Fuel" to "यंत्रसामग्री आणि इंधन",
            "Machinery & Tractor" to "यंत्रसामग्री आणि ट्रॅक्टर",
            "Diesel & Irrigation" to "डिझेल आणि सिंचन",
            "Electricity & Water" to "वीज आणि पाणी",
            "General Expense" to "सामान्य खर्च",
            "Miscellaneous" to "इतर खर्च"
        ),
        "pa" to mapOf(
            "Agri Gyan" to "ਖੇਤੀ ਗਿਆਨ",
            "Govt Scheme" to "ਸਰਕਾਰੀ ਸਕੀਮ",
            "Agri Market" to "ਖੇਤੀ ਬਾਜ਼ਾਰ",
            "Crop Protection" to "ਫਸਲ ਸੁਰੱਖਿਆ",
            "Technology" to "ਤਕਨਾਲੋਜੀ",
            "Success Stories" to "ਸਫਲਤਾ ਦੀਆਂ ਕਹਾਣੀਆਂ",
            "All News" to "ਸਾਰੀਆਂ ਖ਼ਬਰਾਂ",
            "Kisan Diary" to "ਕਿਸਾਨ ਡਾਇਰੀ",
            "Calculator" to "ਕੈਲਕੁਲੇਟਰ",
            "Crop Care" to "ਫਸਲ ਦੇਖਭਾਲ",
            "Govt Schemes" to "ਸਰਕਾਰੀ ਸਕੀਮਾਂ",
            "Comment your thoughts..." to "ਆਪਣੇ ਵਿਚਾਰ ਕਮੈਂਟ ਕਰੋ...",
            "Sign in to comment" to "ਸਾਈਨ ਇਨ ਕਰੋ",
            "Spray" to "ਛਿੜਕਾਅ",
            "Spraying" to "ਛਿੜਕਾਅ",
            "Pesticide Spray" to "ਕੀਟਨਾਸ਼ਕ ਛਿੜਕਾਅ",
            "Pesticide Spray Advisory" to "ਕੀਟਨਾਸ਼ਕ ਛਿੜਕਾਅ ਸਲਾਹ",
            "Chemical Spray" to "ਰਸਾਇਣਕ ਛਿੜਕਾਅ",
            "Chemical Spraying" to "ਰਸਾਇਣਕ ਛਿੜਕਾਅ",
            "Organic Spray" to "ਜੈਵਿਕ ਛਿੜਕਾਅ",
            "Pesticides" to "ਕੀਟਨਾਸ਼ਕ",
            "Pesticide" to "ਕੀਟਨਾਸ਼ਕ",
            "Avoid Chemical Spraying" to "ਰਸਾਇਣਕ ਛਿੜਕਾਅ ਤੋਂ ਬਚੋ",
            "Safe Spraying Window" to "ਸੁਰੱਖਿਅਤ ਛਿੜਕਾਅ ਦਾ ਸਮਾਂ",
            "Crop Protection & Spray Calculator" to "ਫਸਲ ਸੁਰੱਖਿਆ ਅਤੇ ਛਿੜਕਾਅ ਕੈਲਕੁਲੇਟਰ",
            "Estimated Chemical Spray Cost" to "ਅਨੁਮਾਨਿਤ ਰਸਾਇਣਕ ਛਿੜਕਾਅ ਲਾਗਤ",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 ਫਸਲ ਸੁਰੱਖਿਆ ਅਤੇ ਛਿੜਕਾਅ ਕੈਲਕੁਲੇਟਰ",
            "Sowing" to "ਬੀਜਾਈ",
            "Irrigation" to "ਸਿੰਚਾਈ",
            "Harvesting" to "ਵਾਢੀ",
            "Sale" to "ਵੇਚ",
            "Seeds" to "ਬੀਜ",
            "Fertilizer" to "ਖਾਦ",
            "Fertiliser" to "ਖਾਦ",
            "Labor" to "ਮਜ਼ਦੂਰੀ",
            "Labour" to "ਮਜ਼ਦੂਰੀ",
            "Machinery & Fuel" to "ਮਸ਼ੀਨਰੀ ਅਤੇ ਬਾਲਣ",
            "Machinery & Tractor" to "ਮਸ਼ੀਨਰੀ ਅਤੇ ਟ੍ਰੈਕਟਰ",
            "Diesel & Irrigation" to "ਡੀਜ਼ਲ ਅਤੇ ਸਿੰਚਾਈ",
            "Electricity & Water" to "ਬਿਜਲੀ ਅਤੇ ਪਾਣੀ",
            "General Expense" to "ਆਮ ਖਰਚਾ",
            "Miscellaneous" to "ਹੋਰ ਖਰਚੇ"
        ),
        "gu" to mapOf(
            "Agri Gyan" to "કૃષિ જ્ઞાન",
            "Govt Scheme" to "સરકારી યોજના",
            "Agri Market" to "કૃષિ બજાર",
            "Crop Protection" to "પાક સુરક્ષા",
            "Technology" to "ટેકનોલોજી",
            "Success Stories" to "સફળતાની વાતો",
            "All News" to "બધા સમાચાર",
            "Kisan Diary" to "કિસાન ડાયરી",
            "Calculator" to "કેલ્ક્યુલેટર",
            "Crop Care" to "પાક સંભાળ",
            "Govt Schemes" to "સરકારી યોજનાઓ",
            "Comment your thoughts..." to "તમારા વિચારો કમેન્ટ કરો...",
            "Sign in to comment" to "સાઇન ઇન કરો",
            "Spray" to "છંટકાવ",
            "Spraying" to "છંટકાવ",
            "Pesticide Spray" to "જંતુનાશક છંટકાવ",
            "Pesticide Spray Advisory" to "જંતુનાશક છંટકાવ સલાહ",
            "Chemical Spray" to "રાસાયણિક છંટકાવ",
            "Chemical Spraying" to "રાસાયણિક છંટકાવ",
            "Organic Spray" to "જૈવિક છંટકાવ",
            "Pesticides" to "જંતુનાશકો",
            "Pesticide" to "જંતુનાશક",
            "Avoid Chemical Spraying" to "રાસાયણિક છંટકાવ ટાળો",
            "Safe Spraying Window" to "સુરક્ષિત છંટકાવ સમય",
            "Crop Protection & Spray Calculator" to "પાક સુરક્ષા અને છંટકાવ કેલ્ક્યુલેટર",
            "Estimated Chemical Spray Cost" to "અંદાજિત રાસાયણિક છંટકાવ ખર્ચ",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 પાક સુરક્ષા અને છંટકાવ કેલ્ક્યુલેટર",
            "Sowing" to "વાવણી",
            "Irrigation" to "પિયત",
            "Harvesting" to "લણણી",
            "Sale" to "વેચાણ",
            "Seeds" to "બિયારણ",
            "Fertilizer" to "ખાતર",
            "Fertiliser" to "ખાતર",
            "Labor" to "મજૂરી",
            "Labour" to "મજૂરી",
            "Machinery & Fuel" to "મશીનરી અને બળતણ",
            "Machinery & Tractor" to "મશીનરી અને ટ્રેક્ટર",
            "Diesel & Irrigation" to "ડીઝલ અને પિયત",
            "Electricity & Water" to "વીજળી અને પાણી",
            "General Expense" to "સામાન્ય ખર્ચ",
            "Miscellaneous" to "પરચુરણ ખર્ચ"
        ),
        "bn" to mapOf(
            "Agri Gyan" to "কৃষি জ্ঞান",
            "Govt Scheme" to "সরকারি প্রকল্প",
            "Agri Market" to "কৃষি বাজার",
            "Crop Protection" to "শস্য সুরক্ষা",
            "Technology" to "প্রযুক্তি",
            "Success Stories" to "সফলতার গল্প",
            "All News" to "সব খবর",
            "Kisan Diary" to "কৃষক ডায়েরি",
            "Calculator" to "ক্যালকুলেটর",
            "Crop Care" to "শস্য যত্ন",
            "Govt Schemes" to "সরকারি প্রকল্পসমূহ",
            "Comment your thoughts..." to "আপনার মতামত কমেন্ট করুন...",
            "Sign in to comment" to "সাইন ইন করুন",
            "Spray" to "স্প্রে",
            "Spraying" to "স্প্রে করা",
            "Pesticide Spray" to "কীটনাশক স্প্রে",
            "Pesticide Spray Advisory" to "কীটনাশক স্প্রে পরামর্শ",
            "Chemical Spray" to "রাসায়নিক স্প্রে",
            "Chemical Spraying" to "রাসায়নিক স্প্রে",
            "Organic Spray" to "জৈব স্প্রে",
            "Pesticides" to "কীটনাশক",
            "Pesticide" to "কীটনাশক",
            "Avoid Chemical Spraying" to "রাসায়নিক স্প্রে এড়িয়ে চলুন",
            "Safe Spraying Window" to "নিরাপদ স্প্রে করার সময়",
            "Crop Protection & Spray Calculator" to "শস্য সুরক্ষা ও স্প্রে ক্যালকুলেটর",
            "Estimated Chemical Spray Cost" to "আনুমানিক রাসায়নিক স্প্রে খরচ",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 শস্য সুরক্ষা ও স্প্রে ক্যালকুলেটর",
            "Sowing" to "বপন",
            "Irrigation" to "সেচ",
            "Harvesting" to "ফসল কাটা",
            "Sale" to "বিক্রয়",
            "Seeds" to "বীজ",
            "Fertilizer" to "সার",
            "Fertiliser" to "সার",
            "Labor" to "শ্রমিক",
            "Labour" to "শ্রমিক",
            "Machinery & Fuel" to "যন্ত্রপাতি ও জ্বালানি",
            "Machinery & Tractor" to "যন্ত্রপাতি ও ট্রাক্টর",
            "Diesel & Irrigation" to "ডিজেল ও সেচ",
            "Electricity & Water" to "বিদ্যুৎ ও জল",
            "General Expense" to "সাধারণ খরচ",
            "Miscellaneous" to "অন্যান্য খরচ"
        ),
        "ta" to mapOf(
            "Agri Gyan" to "வேளாண் ஞானம்",
            "Govt Scheme" to "அரசு திட்டம்",
            "Agri Market" to "வேளாண் சந்தை",
            "Crop Protection" to "பயிர் பாதுகாப்பு",
            "Technology" to "தொழில்நுட்பம்",
            "Success Stories" to "வெற்றி கதைகள்",
            "All News" to "அனைத்து செய்திகள்",
            "Kisan Diary" to "விவசாய டைரி",
            "Calculator" to "கால்குலேட்டர்",
            "Crop Care" to "பயிர் பராமரிப்பு",
            "Govt Schemes" to "அரசு திட்டங்கள்",
            "Comment your thoughts..." to "உங்கள் கருத்துக்களை தெரிவிக்கவும்...",
            "Sign in to comment" to "உள்நுழையவும்",
            "Spray" to "தெளிப்பு",
            "Spraying" to "தெளித்தல்",
            "Pesticide Spray" to "பூச்சிக்கொல்லி தெளிப்பு",
            "Pesticide Spray Advisory" to "பூச்சிக்கொல்லி தெளிப்பு ஆலோசனை",
            "Chemical Spray" to "ரசாயன தெளிப்பு",
            "Chemical Spraying" to "ரசாயன தெளிப்பு",
            "Organic Spray" to "இயற்கை தெளிப்பு",
            "Pesticides" to "பூச்சிக்கொல்லிகள்",
            "Pesticide" to "பூச்சிக்கொல்லி",
            "Avoid Chemical Spraying" to "ரசாயன தெளிப்பைத் தவிர்க்கவும்",
            "Safe Spraying Window" to "பாதுகாப்பான தெளிப்பு நேரம்",
            "Crop Protection & Spray Calculator" to "பயிர் பாதுகாப்பு மற்றும் தெளிப்பு கால்குலேட்டர்",
            "Estimated Chemical Spray Cost" to "மதிப்பிடப்பட்ட ரசாயன தெளிப்பு செலவு",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 பயிர் பாதுகாப்பு மற்றும் தெளிப்பு கால்குலேட்டர்",
            "Sowing" to "விதைப்பு",
            "Irrigation" to "பாசனம்",
            "Harvesting" to "அறுவடை",
            "Sale" to "விற்பனை",
            "Seeds" to "விதைகள்",
            "Fertilizer" to "உரங்கள்",
            "Fertiliser" to "உரங்கள்",
            "Labor" to "கூலி",
            "Labour" to "கூலி",
            "Machinery & Fuel" to "இயந்திரம் மற்றும் எரிபொருள்",
            "Machinery & Tractor" to "இயந்திரம் மற்றும் டிராக்டர்",
            "Diesel & Irrigation" to "டீசல் மற்றும் பாசனம்",
            "Electricity & Water" to "மின்சாரம் மற்றும் தண்ணீர்",
            "General Expense" to "பொதுவான செலவு",
            "Miscellaneous" to "இதர செலவுகள்"
        ),
        "te" to mapOf(
            "Agri Gyan" to "వ్యవసాయ జ్ఞానం",
            "Govt Scheme" to "ప్రభుత్వ పథకం",
            "Agri Market" to "వ్యవసాయ మార్కెట్",
            "Crop Protection" to "పంట సంరక్షణ",
            "Technology" to "సాంకేతికత",
            "Success Stories" to "విజయ గాథలు",
            "All News" to "అన్ని వార్తలు",
            "Kisan Diary" to "రైతు డైరీ",
            "Calculator" to "కాల్క్యులేటర్",
            "Crop Care" to "పంట సంరక్షణ",
            "Govt Schemes" to "ప్రభుత్వ పథకాలు",
            "Comment your thoughts..." to "మీ అభిప్రాయాలను కామెంట్ చేయండి...",
            "Sign in to comment" to "సైన్ ఇన్ చేయండి",
            "Spray" to "పిచికారీ",
            "Spraying" to "పిచికారీ",
            "Pesticide Spray" to "పురుగుమందు పిచికారీ",
            "Pesticide Spray Advisory" to "పురుగుమందు పిచికారీ సలహా",
            "Chemical Spray" to "రసాయన పిచికారీ",
            "Chemical Spraying" to "రసాయన పిచికారీ",
            "Organic Spray" to "సేంద్రీయ పిచికారీ",
            "Pesticides" to "పురుగుమందులు",
            "Pesticide" to "పురుగుమందు",
            "Avoid Chemical Spraying" to "రసాయన పిచికారీ నివారించండి",
            "Safe Spraying Window" to "సురక్షిత పిచికారీ సమయం",
            "Crop Protection & Spray Calculator" to "పంట సంరక్షణ మరియు పిచికారీ కాల్క్యులేటర్",
            "Estimated Chemical Spray Cost" to "అంచనా వేసిన రసాయన పిచికారీ ఖర్చు",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 పంట సంరక్షణ మరియు పిచికారీ కాల్క్యులేటర్",
            "Sowing" to "విత్తనం వెయ్యడం",
            "Irrigation" to "నీటి పారుదల",
            "Harvesting" to "కోత",
            "Sale" to "అమ్మకం",
            "Seeds" to "విత్తనాలు",
            "Fertilizer" to "ఎరువులు",
            "Fertiliser" to "ఎరువులు",
            "Labor" to "కూలీ",
            "Labour" to "కూలీ",
            "Machinery & Fuel" to "యంత్రాలు మరియు ఇంధనం",
            "Machinery & Tractor" to "యంత్రాలు మరియు ట్రాక్టర్",
            "Diesel & Irrigation" to "డీజిల్ మరియు నీటి పారుదల",
            "Electricity & Water" to "విద్యుత్ మరియు నీరు",
            "General Expense" to "సాధారణ ఖర్చు",
            "Miscellaneous" to "ఇతర ఖర్చులు"
        ),
        "kn" to mapOf(
            "Agri Gyan" to "ಕೃಷಿ ಜ್ಞಾನ",
            "Govt Scheme" to "ಸರ್ಕಾರಿ ಯೋಜನೆ",
            "Agri Market" to "ಕೃಷಿ ಮಾರುಕಟ್ಟೆ",
            "Crop Protection" to "ಬೆಳೆ ರಕ್ಷಣೆ",
            "Technology" to "ತಂತ್ರಜ್ಞಾನ",
            "Success Stories" to "ಯಶಸ್ಸಿನ ಕಥೆಗಳು",
            "All News" to "ಎಲ್ಲಾ ಸುದ್ದಿ",
            "Kisan Diary" to "ಕಿಸಾನ್ ಡೈರಿ",
            "Calculator" to "ಕ್ಯಾಲ್ಕುಲೇಟರ್",
            "Crop Care" to "ಬೆಳೆ ಕಾಳಜಿ",
            "Govt Schemes" to "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು",
            "Comment your thoughts..." to "ನಿಮ್ಮ ಅಭಿಪ್ರಾಯಗಳನ್ನು ಕಾಮೆಂಟ್ ಮಾಡಿ...",
            "Sign in to comment" to "ಸೈನ್ ಇನ್ ಮಾಡಿ",
            "Spray" to "ಸಿಂಪಡಣೆ",
            "Spraying" to "ಸಿಂಪಡಣೆ",
            "Pesticide Spray" to "ಕೀಟನಾಶಕ ಸಿಂಪಡಣೆ",
            "Pesticide Spray Advisory" to "ಕೀಟನಾಶಕ ಸಿಂಪಡಣೆ ಸಲಹೆ",
            "Chemical Spray" to "ರಾಸಾಯನಿಕ ಸಿಂಪಡಣೆ",
            "Chemical Spraying" to "ರಾಸಾಯನಿಕ ಸಿಂಪಡಣೆ",
            "Organic Spray" to "ಸಾವಯವ ಸಿಂಪಡಣೆ",
            "Pesticides" to "ಕೀಟನಾಶಕಗಳು",
            "Pesticide" to "ಕೀಟನಾಶಕ",
            "Avoid Chemical Spraying" to "ರಾಸಾಯನಿಕ ಸಿಂಪಡಣೆ ತಪ್ಪಿಸಿ",
            "Safe Spraying Window" to "ಸುರಕ್ಷಿತ ಸಿಂಪಡಣೆ ಸಮಯ",
            "Crop Protection & Spray Calculator" to "ಬೆಳೆ ರಕ್ಷಣೆ ಮತ್ತು ಸಿಂಪಡಣೆ ಕ್ಯಾಲ್ಕುಲೇಟರ್",
            "Estimated Chemical Spray Cost" to "ಅಂದಾಜು ರಾಸಾಯನಿಕ ಸಿಂಪಡಣೆ ವೆಚ್ಚ",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 ಬೆಳೆ ರಕ್ಷಣೆ ಮತ್ತು ಸಿಂಪಡಣೆ ಕ್ಯಾಲ್ಕುಲೇಟರ್",
            "Sowing" to "ಬಿತ್ತನೆ",
            "Irrigation" to "ನೀರಾವರಿ",
            "Harvesting" to "ಕೊಯ್ಲು",
            "Sale" to "ಮಾರಾಟ",
            "Seeds" to "ಬೀಜಗಳು",
            "Fertilizer" to "ರಸಗೊಬ್ಬರ",
            "Fertiliser" to "ರಸಗೊಬ್ಬರ",
            "Labor" to "ಕೂಲಿ",
            "Labour" to "ಕೂಲಿ",
            "Machinery & Fuel" to "ಯಂತ್ರೋಪಕರಣ ಮತ್ತು ಇಂಧನ",
            "Machinery & Tractor" to "ಯಂತ್ರೋಪಕರಣ ಮತ್ತು ಟ್ರ್ಯಾಕ್ಟರ್",
            "Diesel & Irrigation" to "ಡೀಸಲ್ ಮತ್ತು ನೀರಾವರಿ",
            "Electricity & Water" to "ವಿದ್ಯುತ್ ಮತ್ತು ನೀರು",
            "General Expense" to "ಸಾಮಾನ್ಯ ವೆಚ್ಚ",
            "Miscellaneous" to "ಇತರ ವೆಚ್ಚಗಳು"
        ),
        "ml" to mapOf(
            "Agri Gyan" to "കാർഷിക ജ്ഞാനം",
            "Govt Scheme" to "സർക്കാർ പദ്ധതി",
            "Agri Market" to "കാർഷിക മാർക്കറ്റ്",
            "Crop Protection" to "വിള സംരക്ഷണം",
            "Technology" to "സാങ്കേതികവിദ്യ",
            "Success Stories" to "വിജയഗാഥകൾ",
            "All News" to "എല്ലാ വാർത്തകളും",
            "Kisan Diary" to "കർഷക ഡയറി",
            "Calculator" to "കാൽക്കുലേറ്റർ",
            "Crop Care" to "വിള പരിപാലനം",
            "Govt Schemes" to "സർക്കാർ പദ്ധതികൾ",
            "Comment your thoughts..." to "നിങ്ങളുടെ അഭിപ്രായങ്ങൾ കമന്റ് ചെയ്യുക...",
            "Sign in to comment" to "സൈൻ ഇൻ ചെയ്യുക",
            "Spray" to "തളിക്കൽ",
            "Spraying" to "തളിക്കൽ",
            "Pesticide Spray" to "കീടനാശിനി തളിക്കൽ",
            "Pesticide Spray Advisory" to "കീടനാശിനി തളിക്കൽ ഉപദേശം",
            "Chemical Spray" to "രാസതളിക്കൽ",
            "Chemical Spraying" to "രാസതളിക്കൽ",
            "Organic Spray" to "ജൈവ തളിക്കൽ",
            "Pesticides" to "കീടനാശിനികൾ",
            "Pesticide" to "കീടനാശിനി",
            "Avoid Chemical Spraying" to "രാസതളിക്കൽ ഒഴിവാക്കുക",
            "Safe Spraying Window" to "സുരക്ഷിത തളിക്കൽ സമയം",
            "Crop Protection & Spray Calculator" to "വിള സംരക്ഷണവും തളിക്കൽ കാൽക്കുലേറ്ററും",
            "Estimated Chemical Spray Cost" to "ഏകദേശ രാസതളിക്കൽ ചെലവ്",
            "5. 💊 Crop Protection & Spray Calculator" to "5. 💊 വിള സംരക്ഷണവും തളിക്കൽ കാൽക്കുലേറ്ററും",
            "Sowing" to "വിത",
            "Irrigation" to "നനയ്ക്കൽ",
            "Harvesting" to "വിളവെടുപ്പ്",
            "Sale" to "വില്പന",
            "Seeds" to "വിത്തുകൾ",
            "Fertilizer" to "വളങ്ങൾ",
            "Fertiliser" to "വളങ്ങൾ",
            "Labor" to "തൊഴിൽ കൂലി",
            "Labour" to "തൊഴിൽ കൂലി",
            "Machinery & Fuel" to "യന്ത്രസാമഗ്രികളും ഇന്ധനവും",
            "Machinery & Tractor" to "യന്ത്രസാമഗ്രികളും ട്രാക്ടറും",
            "Diesel & Irrigation" to "ഡീസലും നനയ്ക്കലും",
            "Electricity & Water" to "വൈദ്യുതിയും വെള്ളവും",
            "General Expense" to "പൊതുവായ ചെലവ്",
            "Miscellaneous" to "മറ്റു ചെലവുകൾ"
        )
    )

    private val protectedActionWords = setOf(
        "my farming dashboard",
        "amount",
        "save",
        "submit",
        "update",
        "save draft",
        "post",
        "share",
        "cancel",
        "confirm",
        "back",
        "next",
        "skip",
        "done",
        "apply",
        "search",
        "clear",
        "reset",
        "retry",
        "refresh",
        "close",
        "ok",
        "yes",
        "no",
        "view",
        "delete",
        "edit",
        "download",
        "upload",
        "start",
        "stop",
        "select",
        "filter",
        "sort",
        "save entry",
        "update entry",
        "save name",
        "save key",
        "submit question",
        "submit otp",
        "post question",
        "share app",
        "share news",
        "share dashboard",
        "share price chart",
        "start season",
        "edit entry",
        "see translation",
        "see original",
        "send sms otp",
        "verify otp & continue",
        "login with email",
        "sign up",
        "sign in",
        "logout",
        "central (all india)",
        "all states",
        "select category",
        "select category:"
    )

    private fun isProtectedActionWord(text: String): Boolean {
        val cleaned = text.replace(Regex("[\\p{So}\\p{Cn}]"), "").trim().lowercase()
        return protectedActionWords.contains(cleaned)
    }

    fun getDirectTranslation(text: String, targetLangCode: String): String? {
        if (text.isBlank() || targetLangCode == "en") return text
        val cleanKey = text.trim()

        localizedDictionary[targetLangCode]?.get(cleanKey)?.let { return it }

        // Case-insensitive lookup
        localizedDictionary[targetLangCode]?.entries?.firstOrNull {
            it.key.equals(cleanKey, ignoreCase = true)
        }?.value?.let { return it }

        return null
    }

    private fun sanitizeAgriTranslation(text: String, targetLangCode: String): String {
        if (text.isBlank()) return text
        var result = text

        when (targetLangCode) {
            "hi" -> {
                result = result
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
            "mr" -> {
                result = result
                    .replace("कारंजे", "फवारणी")
                    .replace("तुषार", "फवारणी")
                    .replace("धुकं", "फवारणी")
                    .replace("फुवारा", "फवारणी")
            }
            "pa" -> {
                result = result
                    .replace("ਫੁਹਾਰਾ", "ਛਿੜਕਾਅ")
                    .replace("ਫੁਹਾਰ", "ਛਿੜਕਾਅ")
            }
            "gu" -> {
                result = result
                    .replace("ફુવારો", "છંટકાવ")
                    .replace("બૌછાર", "છંટકાવ")
            }
            "bn" -> {
                result = result
                    .replace("ফোয়ারা", "স্প্রে")
                    .replace("জল ছেটানো", "স্প্রে করা")
            }
            "ta" -> {
                result = result
                    .replace("நீர் தெளிப்பு", "தெளிப்பு")
            }
            "te" -> {
                result = result
                    .replace("తుంపర", "పిచికారీ")
            }
            "kn" -> {
                result = result
                    .replace("ನೀರಿನ ಸಿಂಪಣೆ", "ಸಿಂಪಡಣೆ")
            }
            "ml" -> {
                result = result
                    .replace("വെള്ളം തളിക്കൽ", "തളിക്കൽ")
            }
        }
        return result
    }

    fun getLanguageName(code: String): String {
        return when (code) {
            "te" -> "Telugu (తెలుగు)"
            "mr" -> "Marathi (मराठी)"
            "pa" -> "Punjabi (ਪੰਜਾਬੀ)"
            "gu" -> "Gujarati (ગુજરાતી)"
            "bn" -> "Bengali (বাংলা)"
            "ta" -> "Tamil (தமிழ்)"
            "kn" -> "Kannada (ಕನ್ನಡ)"
            "ml" -> "Malayalam (മലയാളം)"
            "hi" -> "Hindi (हिंदी)"
            else -> "English"
        }
    }

    suspend fun translateText(context: Context, text: String, targetLangCode: String): String {
        if (text.isBlank() || targetLangCode == "en" || isProtectedActionWord(text)) return text

        getDirectTranslation(text, targetLangCode)?.let { return it }

        val cacheKey = "$targetLangCode:$text"
        translationCache[cacheKey]?.let { return it }

        val translated = translateWithGoogle(text, targetLangCode)
        if (translated.isNotBlank() && translated != text) {
            val sanitized = sanitizeAgriTranslation(translated, targetLangCode)
            translationCache[cacheKey] = sanitized
            return sanitized
        }

        return text
    }

    private suspend fun translateWithGoogle(text: String, targetLangCode: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        try {
            val protectedList = mutableListOf<String>()
            var protectedText = text

            // 1. Protect URLs, Markdown links, and HTML tags
            val linkPattern = "(?i)(\\[.*?\\]\\(https?://.*?\\)|<a\\s+href=[\"'].*?[\"']>.*?</a>|https?://[a-zA-Z0-9./_\\-?=%&#]+)".toRegex()
            val matches = linkPattern.findAll(protectedText).toList()

            for ((index, match) in matches.withIndex()) {
                val token = "___LINK_${index}___"
                protectedList.add(match.value)
                protectedText = protectedText.replace(match.value, token)
            }

            // 2. Protect Technical Keywords, Acronyms, and Short Names (PM-Kisan, DAP, NPK, Urea, Mandi, FTO, SBI, IFFCO, NABARD, etc.)
            val termProtectedList = mutableListOf<String>()
            val termPattern = "\\b(?:PM-Kisan|PMKisan|DAP|NPK|Urea|FTO|SBI|IFFCO|NABARD|KCC|MSP|AQI|hPa|km/h|m/s|Mandi|Jaora|Khandwa|Ratlam|Indore|Ujjain|[A-Z]{2,10})\\b".toRegex()
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

                // Restore protected Technical Keywords & Short Names
                for ((index, originalTerm) in termProtectedList.withIndex()) {
                    val token = "___TERM_${index}___"
                    result = result.replace(token, originalTerm)
                        .replace("___ TERM_${index} ___", originalTerm)
                        .replace("___ TERM _ ${index} ___", originalTerm)
                        .replace("___TERM _ ${index}___", originalTerm)
                }

                // Restore protected URLs and HTML links
                for ((index, originalLink) in protectedList.withIndex()) {
                    val token = "___LINK_${index}___"
                    result = result.replace(token, originalLink)
                        .replace("___ LINK_${index} ___", originalLink)
                        .replace("___ LINK _ ${index} ___", originalLink)
                        .replace("___LINK _ ${index}___", originalLink)
                }

                if (result.isNotBlank()) return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext text
    }
}
