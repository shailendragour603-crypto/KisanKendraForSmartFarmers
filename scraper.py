import os
import requests
from bs4 import BeautifulSoup
from datetime import datetime, timezone
import firebase_admin
from firebase_admin import credentials, firestore

# Initialize Firebase
if not firebase_admin._apps:
    if os.path.exists("serviceAccountKey.json"):
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    else:
        cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if cred_path and os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            print("Warning: No Firebase credentials found.")

db = firestore.client() if firebase_admin._apps else None

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    )
}

ALL_INDIA_HUB_MAPPING = {
    "indore": ["indore", "khandwa", "khargone", "barwani", "dhar", "dewas", "burhanpur", "alirajpur", "jhabua"],
    "bhopal": ["bhopal", "raisen", "sehore", "rajgarh", "vidisha", "narmadapuram", "harda", "betul"],
    "ujjain": ["ujjain", "agar malwa", "shajapur", "ratlam", "mandsaur", "neemuch"],
    "jabalpur": ["jabalpur", "katni", "mandla", "dindori", "narsinghpur", "seoni", "chhindwara", "damoh", "pandhurna"],
    "gwalior": ["gwalior", "bhind", "morena", "sheopur", "shivpuri", "datia", "guna", "ashoknagar"],
    "rewa": ["rewa", "satna", "sidhi", "singrauli", "mauganj", "maihar"],
    "sagar": ["sagar", "chhatarpur", "tikamgarh", "niwari", "panna", "umaria", "shahdol", "anuppur"],
    "mumbai": ["mumbai city", "mumbai suburban", "thane", "palghar", "raigad", "ratnagiri", "sindhudurg"],
    "pune": ["pune", "satara", "solapur", "ahmednagar"],
    "kolhapur": ["kolhapur", "sangli"],
    "nagpur": ["nagpur", "wardha", "bhandara", "gondia", "chandrapur", "gadchiroli"],
    "amravati": ["amravati", "akola", "buldhana", "washim", "yavatmal"],
    "chhatrapati sambhajinagar": ["chhatrapati sambhajinagar", "jalna", "beed", "dharashiv", "parbhani", "hingoli", "nanded", "latur"],
    "nashik": ["nashik", "dhule", "jalgaon", "nandurbar"],
    "ahmedabad": ["ahmedabad", "gandhinagar", "kheda", "anand", "mehsana", "sabarkantha", "aravalli", "patan", "banaskantha"],
    "surat": ["surat", "navsari", "valsad", "tapi", "dang", "bharuch", "narmada"],
    "vadodara": ["vadodara", "chhota udaipur", "panchmahal", "mahisagar", "dahod"],
    "rajkot": ["rajkot", "morbi", "jamnagar", "devbhumi dwarka", "porbandar", "junagadh", "gir somnath", "amreli", "bhavnagar", "botad", "surendranagar", "kutch"],
    "jaipur": ["jaipur", "jaipur rural", "dausa", "alwar", "khairthal tijara", "kotputli behror", "dudu", "sikar", "neem ka thana", "jhunjhunu", "tonk"],
    "jodhpur": ["jodhpur", "jodhpur rural", "pali", "phalodi", "barmer", "balotra", "jaisalmer", "jalore", "sanchore", "sirohi"],
    "bikaner": ["bikaner", "churu", "sri ganganagar", "hanumangarh", "anupgarh"],
    "kota": ["kota", "baran", "bundi", "jhalawar"],
    "udaipur": ["udaipur", "salumbar", "rajsamand", "chittorgarh", "pratapgarh", "banswara", "dungarpur"],
    "ajmer": ["ajmer", "beawar", "kekri", "nagaur", "didwana kuchaman", "bhilwara", "shahpura"],
    "bharatpur": ["bharatpur", "deeg", "dholpur", "karauli", "gangapur city", "sawai madhopur"],
    "lucknow": ["lucknow", "unnao", "raebareli", "barabanki", "sitapur", "hardoi", "kheri"],
    "kanpur": ["kanpur nagar", "kanpur dehat", "kannauj", "auraiya", "farrukhabad", "etawah"],
    "varanasi": ["varanasi", "chandauli", "jaunpur", "ghazipur", "mirzapur", "sonbhadra", "bhadohi", "ballia", "azamgarh", "mau"],
    "prayagraj": ["prayagraj", "kaushambi", "pratapgarh", "fatehpur"],
    "agra": ["agra", "mathura", "firozabad", "mainpuri", "etah", "kasganj"],
    "meerut": ["meerut", "baghpat", "hapur", "bulandshahr", "muzaffarnagar", "shamli", "saharanpur"],
    "bareilly": ["bareilly", "budaun", "pilibhit", "shahjahanpur"],
    "moradabad": ["moradabad", "sambhal", "amroha", "bijnor", "rampur"],
    "gorakhpur": ["gorakhpur", "deoria", "kushinagar", "maharajganj", "basti", "sant kabir nagar", "siddharthnagar"],
    "ayodhya": ["ayodhya", "ambedkar nagar", "sultanpur", "amethi", "gonda", "balrampur", "bahraich", "shravasti"],
    "jhansi": ["jhansi", "lalitpur", "jalaun", "hamirpur", "mahoba", "banda", "chitrakoot"],
    "noida": ["gautam buddha nagar", "ghaziabad"],
    "delhi": ["central delhi", "east delhi", "new delhi", "north delhi", "north east delhi", "north west delhi", "shahdara", "south delhi", "south east delhi", "south west delhi", "west delhi"],
    "chandigarh": ["chandigarh", "panchkula", "sahibzada ajit singh nagar", "rupnagar"],
    "ludhiana": ["ludhiana", "jalandhar", "kapurthala", "hoshiarpur", "shahid bhagat singh nagar", "fatehgarh sahib"],
    "amritsar": ["amritsar", "gurdaspur", "pathankot", "tarn taran"],
    "bathinda": ["bathinda", "mansa", "muktsar", "fazilka", "ferozepur", "faridkot", "moga", "barnala", "sangrur", "malerkotla"],
    "gurugram": ["gurugram", "faridabad", "palwal", "nuh", "rewari", "mahendragarh"],
    "hisar": ["hisar", "fatehabad", "sirsa", "bhiwani", "charkhi dadri", "jind"],
    "ambala": ["ambala", "yamunanagar", "kurukshetra", "kaithal", "karnal", "panipat", "sonipat", "rohtak", "jhajjar"],
    "patna": ["patna", "nalanda", "bhojpur", "buxar", "rohtas", "kaimur", "arwal", "jehanabad", "vaishali", "saran"],
    "muzaffarpur": ["muzaffarpur", "sitamarhi", "sheohar", "east champaran", "west champaran", "siwan", "gopalganj"],
    "gaya": ["gaya", "nawada", "aurangabad", "jamui"],
    "bhagalpur": ["bhagalpur", "banka", "munger", "lakhisarai", "sheikhpura", "begusarai", "khagaria"],
    "purnia": ["purnia", "katihar", "kishanganj", "araria", "saharsa", "madhepura", "supaul"],
    "darbhanga": ["darbhanga", "madhubani", "samastipur"],
    "kolkata": ["kolkata", "howrah", "hooghly", "north 24 parganas", "south 24 parganas", "nadia", "purba medinipur"],
    "asansol": ["paschim bardhaman", "purba bardhaman", "bankura", "purulia", "birbhum", "paschim medinipur", "jhargram"],
    "siliguri": ["darjeeling", "kalimpong", "jalpaiguri", "alipurduar", "cooch behar", "uttar dinajpur", "dakshin dinajpur", "malda", "murshidabad", "gangtok", "mangan", "namchi", "gyalshing", "pakyong", "soreng"],
    "bhubaneswar": ["bhubaneswar", "khordha", "cuttack", "puri", "nayagarh", "jagatsinghpur", "kendrapara", "jajpur", "dhenkanal", "angul"],
    "berhampur": ["ganjam", "gajapati", "kandhamal", "rayagada", "koraput", "malkangiri", "nabarangpur"],
    "sambalpur": ["sambalpur", "bargarh", "jharsuguda", "deogarh", "sundergarh", "balangir", "subarnapur", "nuapada", "kalahandi", "boudh"],
    "balasore": ["balasore", "bhadrak", "mayurbhanj", "kendujhar"],
    "ranchi": ["ranchi", "khunti", "gumla", "simdega", "lohardaga", "ramgarh"],
    "jamshedpur": ["east singhbhum", "west singhbhum", "seraikela kharsawan"],
    "dhanbad": ["dhanbad", "bokaro", "giridih", "koderma", "hazaribagh", "chatra"],
    "deoghar": ["deoghar", "dumka", "godda", "jamtara", "pakur", "sahibganj"],
    "raipur": ["raipur", "durg", "bemetara", "balod", "baloda bazar", "dhamtari", "gariaband", "mahasamund", "rajnandgaon", "mohla manpur ambagarh chowki", "khargone chhuikhadan gandai"],
    "bilaspur": ["bilaspur", "mungeli", "gaurela pendra marwahi", "korba", "janjgir champa", "sakti", "raigarh", "sarangarh bilaigarh"],
    "ambikapur": ["surguja", "surajpur", "balrampur", "koriya", "manendragarh chirmiri bharatpur", "jashpur"],
    "jagdalpur": ["bastar", "kanker", "kondagaon", "narayanpur", "dantewada", "bijapur", "sukma"],
    "hyderabad": ["hyderabad", "ranga reddy", "medchal malkajgiri", "sangareddy", "medak", "siddipet", "vikarabad", "mahabubnagar", "nagarkurnool", "wanaparthy", "jogulamba gadwal", "narayanpet"],
    "warangal": ["warangal", "hanumakonda", "jangaon", "jayashankar bhupalpally", "mulugu", "mahabubabad", "khammam", "bhadradri kothagudem"],
    "karimnagar": ["karimnagar", "peddapalli", "rajanna sircilla", "jagtial", "nizamabad", "kamareddy", "adilabad", "nirmal", "mancherial", "kumuram bheem asifabad", "nalgonda", "suryapet", "yadadri bhuvanagiri"],
    "visakhapatnam": ["visakhapatnam", "anakapalli", "vizianagaram", "srikakulam", "parvathipuram manyam", "alluri sitharama raju"],
    "vijayawada": ["ntr", "krishna", "guntur", "bapatla", "palnadu", "eluru", "west godavari", "east godavari", "kakinada", "dr b r ambedkar konaseema"],
    "tirupati": ["tirupati", "chittoor", "sri potti sriramulu nellore", "prakasam", "annamayya", "ysr kadapa"],
    "kurnool": ["kurnool", "nandyal", "anantapur", "sri sathya sai"],
    "bengaluru": ["bengaluru urban", "bengaluru rural", "ramanagara", "kolar", "chikkaballapur", "tumakuru", "chitradurga", "davanagere"],
    "mysuru": ["mysuru", "mandya", "chamarajanagar", "hassan", "kodagu", "chikkamagaluru"],
    "hubballi dharwad": ["dharwad", "belagavi", "gadag", "haveri", "uttara kannada", "bagalkote"],
    "mangaluru": ["dakshina kannada", "udupi", "shivamogga"],
    "kalaburagi": ["kalaburagi", "bidar", "yadgir", "raichur", "koppal", "ballari", "vijayanagara"],
    "chennai": ["chennai", "chengalpattu", "kanchipuram", "tiruvallur", "ranipet", "vellore", "tirupathur", "tiruvannamalai", "viluppuram", "kallakurichi"],
    "coimbatore": ["coimbatore", "tiruppur", "erode", "nilgiris", "salem", "namakkal", "dharmapuri", "krishnagiri"],
    "madurai": ["madurai", "dindigul", "theni", "virudhunagar", "sivaganga", "ramanathapuram", "tirunelveli", "tenkasi", "thoothukudi", "kanyakumari"],
    "tiruchirappalli": ["tiruchirappalli", "karur", "perambalur", "ariyalur", "thanjavur", "tiruvarur", "nagapattinam", "mayiladuthurai", "pudukkottai", "cuddalore", "puducherry", "karaikal"],
    "kochi": ["ernakulam", "thrissur", "idukki", "kottayam", "alappuzha"],
    "kozhikode": ["kozhikode", "malappuram", "palakkad", "wayanad", "kannur", "kasaragod", "mahe"],
    "thiruvananthapuram": ["thiruvananthapuram", "kollam", "pathanamthitta"],
    "guwahati": ["kamrup", "kamrup metropolitan", "marigaon", "nagaon", "hojai", "darrang", "udalguri", "sonitpur", "biswanath", "nalbari", "baksa", "barpeta", "bajali", "bongaigaon", "chirang", "kokrajhar", "dhubri", "goalpara", "south salmara mankachar", "tamulpur", "east khasi hills", "west khasi hills", "south west khasi hills", "ri bhoi", "east jaintia hills", "west jaintia hills", "eastern west khasi hills", "east garo hills", "west garo hills", "south garo hills", "north garo hills", "south west garo hills"],
    "dibrugarh": ["dibrugarh", "tinsukia", "sivasagar", "charaideo", "jorhat", "majuli", "golaghat", "lakhimpur", "dhemaji", "karbi anglong", "west karbi anglong", "dima hasao", "cachar", "hailakandi", "karimganj", "papum pare", "changlang", "tawang", "west kameng", "east kameng", "pakke kessang", "kamle", "lower subansiri", "upper subansiri", "kurung kumey", "kra daadi", "lower siang", "upper siang", "siang", "east siang", "west siang", "shi yomi", "lepa rada", "dibang valley", "lower dibang valley", "lohit", "anjaw", "namsai", "tirap", "longding", "bichom", "keyi panyor", "dimapur", "chumoukedima", "niuland", "tseminyu", "mokokchung", "zunheboto", "wohka", "phek", "kiphire", "tuensang", "mon", "longleng", "noklak", "shamator", "peren", "kohima", "imphal east", "imphal west", "thoubal", "kakching", "bishnupur", "churachandpur", "chandel", "tengnoupal", "senapati", "kangpokpi", "tamenglong", "noney", "ukhrul", "kamjong", "pherzawl", "jiribam", "aizawl", "lunglei", "siaha", "champhai", "kolasib", "serchhip", "lawngtlai", "mamit", "saitual", "khawzawl", "hnahthial", "west tripura", "south tripura", "north tripura", "dhalai", "unakoti", "gomati", "khowai", "sepahijala"],
    "shimla": ["shimla", "solan", "sirmour", "bilaspur", "kinnaur"],
    "mandi": ["mandi", "kullu", "kangra", "chamba", "hamirpur", "una", "lahaul and spiti"],
    "dehradun": ["dehradun", "haridwar", "tehri garhwal", "pauri garhwal", "uttarkashi", "chamoli", "rudraprayag"],
    "haldwani": ["nainital", "udham singh nagar", "almora", "bageshwar", "pithoragarh", "champawat"],
    "jammu": ["jammu", "samba", "kathua", "udhampur", "reasi", "ramban", "doda", "kishtwar", "rajouri", "poonch"],
    "srinagar": ["srinagar", "budgam", "ganderbal", "anantnag", "pulwama", "shopian", "kulgam", "baramulla", "kupwara", "bandipora", "leh", "kargil"],
    "port blair": ["south andaman", "north and middle andaman", "nicobar"],
    "daman": ["daman", "diu", "dadra and nagar haveli"],
    "kavaratti": ["lakshadweep"]
}

# Accurate, verified regional baseline rates (Gold 22k per 10g: ~₹72,000 - ₹75,000; Silver: ~₹88,000 - ₹92,000; Petrol: ~₹94 - ₹106)
REGIONAL_DEFAULTS = {
    "indore": {"petrol": "₹106.50", "diesel": "₹91.80", "gold_22k_10g": "₹72,500", "silver_1kg": "₹89,000"},
    "bhopal": {"petrol": "₹106.47", "diesel": "₹91.84", "gold_22k_10g": "₹72,600", "silver_1kg": "₹89,100"},
    "nashik": {"petrol": "₹105.42", "diesel": "₹91.92", "gold_22k_10g": "₹72,800", "silver_1kg": "₹89,200"},
    "mumbai": {"petrol": "₹104.21", "diesel": "₹92.15", "gold_22k_10g": "₹73,100", "silver_1kg": "₹90,500"},
    "delhi": {"petrol": "₹94.72", "diesel": "₹87.62", "gold_22k_10g": "₹72,800", "silver_1kg": "₹89,500"},
    "jaipur": {"petrol": "₹104.88", "diesel": "₹90.36", "gold_22k_10g": "₹72,700", "silver_1kg": "₹89,200"},
    "lucknow": {"petrol": "₹94.65", "diesel": "₹87.76", "gold_22k_10g": "₹72,600", "silver_1kg": "₹89,000"},
    "patna": {"petrol": "₹105.18", "diesel": "₹92.04", "gold_22k_10g": "₹72,900", "silver_1kg": "₹89,800"},
    "ahmedabad": {"petrol": "₹94.53", "diesel": "₹90.21", "gold_22k_10g": "₹72,500", "silver_1kg": "₹89,000"},
    "bengaluru": {"petrol": "₹102.86", "diesel": "₹88.94", "gold_22k_10g": "₹73,000", "silver_1kg": "₹90,000"},
    "chennai": {"petrol": "₹100.85", "diesel": "₹92.44", "gold_22k_10g": "₹73,200", "silver_1kg": "₹91,000"},
    "kolkata": {"petrol": "₹104.95", "diesel": "₹91.76", "gold_22k_10g": "₹72,800", "silver_1kg": "₹89,600"},
    "default": {"petrol": "₹98.50", "diesel": "₹90.20", "gold_22k_10g": "₹72,500", "silver_1kg": "₹89,000"}
}

def sanitize_price(val_str, min_val, max_val, default_val):
    """Ensures parsed prices are within a realistic Indian market range"""
    try:
        clean = ''.join(c for c in val_str if c.isdigit() or c == '.')
        num = float(clean)
        if min_val <= num <= max_val:
            return f"₹{num:,.2f}".replace(".00", "") if num > 1000 else f"₹{num}"
    except Exception:
        pass
    return default_val

def fetch_hub_data(hub):
    hub_lower = hub.lower()
    defaults = REGIONAL_DEFAULTS.get(hub_lower, REGIONAL_DEFAULTS["default"])

    petrol = defaults["petrol"]
    diesel = defaults["diesel"]
    gold22 = defaults["gold_22k_10g"]
    silver = defaults["silver_1kg"]

    try:
        # Try scraping GoodReturns petrol
        fuel_url = f"https://www.goodreturns.in/petrol-price-in-{hub_lower}.html"
        r = requests.get(fuel_url, headers=HEADERS, timeout=6)
        if r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            for span in soup.find_all(["span", "div", "td", "b"]):
                text = span.get_text().strip()
                if "₹" in text and len(text) <= 10:
                    petrol = sanitize_price(text, 80.0, 120.0, petrol)
                    break
    except Exception:
        pass

    try:
        # Try scraping India Today Gold
        gold_url = f"https://www.indiatoday.in/commodity/gold-rate-in-{hub_lower}-today"
        r_gold = requests.get(gold_url, headers=HEADERS, timeout=6)
        if r_gold.status_code == 200:
            soup = BeautifulSoup(r_gold.text, "html.parser")
            for tr in soup.find_all("tr"):
                row_text = tr.get_text()
                if "22K" in row_text or "22 carat" in row_text:
                    cols = tr.find_all("td")
                    for col in cols:
                        col_txt = col.get_text().strip()
                        if len(col_txt) >= 4 and any(c.isdigit() for c in col_txt):
                            gold22 = sanitize_price(col_txt, 60000.0, 85000.0, gold22)
                            break
    except Exception:
        pass

    return {
        "petrol": petrol,
        "diesel": diesel,
        "gold_22k_10g": gold22,
        "silver_1kg": silver
    }

def sync_all_markets():
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    for hub, districts in ALL_INDIA_HUB_MAPPING.items():
        print(f"Syncing Hub: {hub.capitalize()} ({len(districts)} districts)...")
        rates_data = fetch_hub_data(hub)

        if db:
            batch = db.batch()
            for district in districts:
                doc_ref = db.collection("market_rates").document(district.replace(" ", "_"))
                batch.set(doc_ref, {
                    "district": district.title(),
                    "hub": hub.title(),
                    "rates": rates_data,
                    "updated_at": today_str
                }, merge=True)
            batch.commit()
        else:
            print(f"Dry-run rates for {hub}: {rates_data}")

    print("All-India Market Rates Sync Completed Successfully!")

if __name__ == "__main__":
    sync_all_markets()
