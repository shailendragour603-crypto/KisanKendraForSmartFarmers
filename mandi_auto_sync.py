import os
import re
import json
import time
import requests
from datetime import datetime, timezone, timedelta
import firebase_admin
from firebase_admin import credentials, firestore

# ---------------------------------------------------------------------------
# FIREBASE INITIALIZATION
# ---------------------------------------------------------------------------
def init_firebase():
    if firebase_admin._apps:
        return firestore.client()

    # 1. Try local serviceAccountKey.json
    if os.path.exists("serviceAccountKey.json"):
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
        print("Initialized Firebase using local serviceAccountKey.json")
        return firestore.client()

    # 2. Try environment variable FIREBASE_SERVICE_ACCOUNT (JSON string from GitHub Secret)
    service_account_json = os.getenv("FIREBASE_SERVICE_ACCOUNT")
    if service_account_json:
        try:
            clean_str = service_account_json.strip()
            if not clean_str.startswith("{") and clean_str:
                import base64
                clean_str = base64.b64decode(clean_str).decode("utf-8")
            cred_dict = json.loads(clean_str, strict=False)
            cred = credentials.Certificate(cred_dict)
            firebase_admin.initialize_app(cred)
            print("Initialized Firebase using FIREBASE_SERVICE_ACCOUNT env var")
            return firestore.client()
        except Exception as e:
            print(f"Error parsing FIREBASE_SERVICE_ACCOUNT JSON: {e}")

    # 3. Try GOOGLE_APPLICATION_CREDENTIALS file path
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if cred_path and os.path.exists(cred_path):
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
        print(f"Initialized Firebase using GOOGLE_APPLICATION_CREDENTIALS path: {cred_path}")
        return firestore.client()

    print("❌ CRITICAL ERROR: No Firebase credentials found! Please configure FIREBASE_SERVICE_ACCOUNT in GitHub Repository Secrets.")
    return None

db = init_firebase()

# ---------------------------------------------------------------------------
# CONSTANTS & CONFIGURATION
# ---------------------------------------------------------------------------
API_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070"
API_KEY = "579b464db66ec23bdd000001f0acdf7b0b4943fe5b9998c1304ba55f"
PAGE_LIMIT = 2500   # Stable page limit to avoid connection drops from data.gov.in
MAX_PAGES = 20      # Up to 20 x 2,500 = 50,000 records max
MAX_DOC_RECORDS = 2000 # Keep document payload safely under Firestore 1 MB limit

# Load Hindi Translations (Admin Source of Truth from Firebase)
HINDI_TRANSLATIONS = {}
if db:
    try:
        print("Fetching latest translations from Firebase Admin Panel...")
        trans_doc = db.collection("app_translations").document("hi").get()
        if trans_doc.exists:
            dict_data = trans_doc.to_dict() or {}

            # 1. First, load everything from the nested "dictionary" map (Correct format)
            if "dictionary" in dict_data and isinstance(dict_data["dictionary"], dict):
                HINDI_TRANSLATIONS.update(dict_data["dictionary"])

            # 2. Second, scan for any buggy flat fields like "dictionary.pandhana" and extract them
            for key, value in dict_data.items():
                if key.startswith("dictionary.") and isinstance(value, str):
                    clean_key = key.replace("dictionary.", "").strip()
                    HINDI_TRANSLATIONS[clean_key] = value
    except Exception as e:
        print(f"Warning: Could not load translations from Firebase: {e}")

def get_hindi_name(english_name: str) -> str:
    """Returns Hindi translation if available in Admin Translations dictionary, else original."""
    if not english_name:
        return english_name
    key = english_name.lower().strip()
    return HINDI_TRANSLATIONS.get(key, english_name.title())

def get_clean_state_key(state_name: str) -> str:
    if not state_name or not state_name.strip():
        return "unknown_state"
    clean = state_name.lower().strip()
    clean = re.sub(r'\s+', '_', clean)
    clean = re.sub(r'[^a-z0-9_]', '', clean)
    return clean or "unknown_state"

# ---------------------------------------------------------------------------
# API PAGINATED FETCHING
# ---------------------------------------------------------------------------
def fetch_all_mandi_records_from_api() -> list:
    all_records = []
    current_offset = 0
    page_index = 0

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "application/json"
    }

    print("⚡ Starting Mandi API Paginated Fetch from data.gov.in...")

    while page_index < MAX_PAGES:
        page_index += 1
        params = {
            "api-key": API_KEY,
            "format": "json",
            "limit": PAGE_LIMIT,
            "offset": current_offset
        }

        success = False
        is_last_page = False

        for attempt in range(1, 4):  # Up to 3 retries
            try:
                print(f"Fetching Page {page_index} (Offset: {current_offset}, Limit: {PAGE_LIMIT}, Attempt: {attempt})...")
                response = requests.get(API_URL, params=params, headers=headers, timeout=30)

                if response.status_code == 200:
                    data = response.json()
                    records = data.get("records", [])
                    batch_len = len(records)
                    print(f"✅ Page {page_index} received {batch_len} records.")

                    if not records:
                        print("Reached end of available records.")
                        success = True
                        is_last_page = True
                        break

                    all_records.extend(records)
                    current_offset += batch_len

                    if batch_len < PAGE_LIMIT:
                        print(f"Final batch received ({batch_len} < {PAGE_LIMIT}).")
                        success = True
                        is_last_page = True
                        break

                    success = True
                    break
                else:
                    print(f"⚠️ Page {page_index} HTTP {response.status_code}: {response.text[:200]}")
                    time.sleep(2 * attempt)
            except Exception as e:
                print(f"❌ Error fetching Page {page_index} (Attempt {attempt}): {e}")
                time.sleep(2 * attempt)

        if not success or is_last_page:
            break

    print(f"🎉 Total Unique Mandi Records Fetched from API: {len(all_records)}")
    return all_records

# ---------------------------------------------------------------------------
# RECORD NORMALIZATION & PRICE DIFF COMPUTATION
# ---------------------------------------------------------------------------
def clean_price_str(val) -> str:
    if val is None:
        return "0"
    s = str(val).strip()
    try:
        f = float(s)
        return str(int(f)) if f.is_integer() else f"{f:.2f}"
    except (ValueError, TypeError):
        return s

def normalize_record(raw: dict) -> dict:
    state = str(raw.get("state", "") or raw.get("State", "")).strip()
    district = str(raw.get("district", "") or raw.get("District", "")).strip()
    market = str(raw.get("market", "") or raw.get("Market", "")).strip()
    commodity = str(raw.get("commodity", "") or raw.get("Commodity", "")).strip()
    variety = str(raw.get("variety", "") or raw.get("Variety", "")).strip()
    min_price = clean_price_str(raw.get("min_price", "") or raw.get("Min_Price", "") or raw.get("minPrice", ""))
    max_price = clean_price_str(raw.get("max_price", "") or raw.get("Max_Price", "") or raw.get("maxPrice", ""))
    modal_price = clean_price_str(raw.get("modal_price", "") or raw.get("Modal_Price", "") or raw.get("modalPrice", ""))
    arrival_date = str(raw.get("arrival_date", "") or raw.get("Arrival_Date", "") or raw.get("arrivalDate", "")).strip()

    rec = {
        "state": state,
        "district": district,
        "district_hi": get_hindi_name(district),
        "market": market,
        "market_hi": get_hindi_name(market),
        "commodity": commodity,
        "variety": variety,
        "minPrice": min_price,
        "maxPrice": max_price,
        "modalPrice": modal_price,
        "arrivalDate": arrival_date,
        "priceDiff": None
    }
    return rec

def compute_price_differences(current_records: list, previous_records: list) -> list:
    prev_map = {}
    for r in previous_records:
        st = str(r.get("state", "")).strip().lower()
        mkt = str(r.get("market", "")).strip().lower()
        cmd = str(r.get("commodity", "")).strip().lower()
        var = str(r.get("variety", "")).strip().lower()
        key = f"{st}_{mkt}_{cmd}_{var}"
        try:
            # Use Highest Price (maxPrice) for price difference comparison
            prev_price = float(r.get("maxPrice") or r.get("max_price") or r.get("modalPrice") or r.get("modal_price") or 0.0)
            if prev_price > 0:
                prev_map[key] = prev_price
        except (ValueError, TypeError):
            pass

    processed = []
    for cur in current_records:
        rec = normalize_record(cur)
        st = rec["state"].lower()
        mkt = rec["market"].lower()
        cmd = rec["commodity"].lower()
        var = rec["variety"].lower()
        key = f"{st}_{mkt}_{cmd}_{var}"

        try:
            # Use Highest Price (maxPrice) for price difference comparison
            cur_price = float(rec.get("maxPrice") or rec.get("modalPrice") or 0.0)
            if key in prev_map:
                diff = round(cur_price - prev_map[key], 2)
                rec["priceDiff"] = diff
        except (ValueError, TypeError):
            pass

        processed.append(rec)

    return processed

# ---------------------------------------------------------------------------
# PRICE HISTORY CHARTS (TODAY, 7-DAY, 30-DAY TRENDS)
# ---------------------------------------------------------------------------
def update_price_history_in_firestore(records: list):
    if not db or not records:
        return

    ist_tz = timezone(timedelta(hours=5, minutes=30))
    now_ist = datetime.now(ist_tz)
    today_label = now_ist.strftime("%d %b")  # e.g., "16 May"
    today_ts = int(now_ist.timestamp() * 1000)

    print("📈 Updating 30-day crop price trend charts in Firestore ('price_history')...")

    # Deduplicate by doc_id to avoid multiple batch updates on same doc
    unique_docs = {}
    for rec in records:
        st = rec.get("state", "")
        mkt = rec.get("market", "")
        cmd = rec.get("commodity", "")
        var = rec.get("variety", "")
        doc_raw = f"{st}_{mkt}_{cmd}_{var}".lower()
        doc_id = re.sub(r'[^a-z0-9_]', '', doc_raw) or "unknown_crop"

        try:
            p_val = float(rec.get("maxPrice") or rec.get("modalPrice") or 0.0)
            if p_val > 0 and doc_id not in unique_docs:
                unique_docs[doc_id] = (st, mkt, cmd, var, p_val)
        except (ValueError, TypeError):
            pass

    batch = db.batch()
    batch_count = 0

    # Batch write points (up to 1,200 unique crops/markets per sync run)
    for doc_id, (st, mkt, cmd, var, p_val) in list(unique_docs.items())[:1200]:
        doc_ref = db.collection("price_history").document(doc_id)
        today_point = {
            "dateLabel": today_label,
            "price": p_val,
            "timestamp": today_ts
        }
        batch.set(doc_ref, {
            "state": st,
            "market": mkt,
            "commodity": cmd,
            "variety": var,
            "lastUpdated": firestore.SERVER_TIMESTAMP,
            "points": firestore.ArrayUnion([today_point])
        }, merge=True)
        batch_count += 1

        if batch_count >= 350:
            batch.commit()
            batch = db.batch()
            batch_count = 0

    if batch_count > 0:
        batch.commit()
    print("✅ Price history charts updated successfully!")

# ---------------------------------------------------------------------------
# FIRESTORE SAVE & SYNC
# ---------------------------------------------------------------------------
def save_records_to_firestore(records: list):
    if not db:
        msg = "❌ CRITICAL: No Firestore database handle available. If running on GitHub Actions, ensure FIREBASE_SERVICE_ACCOUNT is set in Repository Secrets!"
        print(msg)
        if os.getenv("GITHUB_ACTIONS") == "true":
            raise RuntimeError(msg)
        return

    if not records:
        print("No records to save.")
        return

    now_timestamp = firestore.SERVER_TIMESTAMP

    # 1. Fetch previous cache from mandi_cache/latest to handle daily snapshot and price diffs
    previous_records = []
    try:
        latest_doc = db.collection("mandi_cache").document("latest").get()
        if latest_doc.exists:
            prev_data = latest_doc.to_dict() or {}
            previous_records = prev_data.get("records", [])
            prev_date = previous_records[0].get("arrivalDate", "") if previous_records else ""
            new_date = records[0].get("arrivalDate", "") if records else ""

            # Save yesterday's snapshot if dates differ
            if prev_date and new_date and prev_date != new_date:
                ist_tz = timezone(timedelta(hours=5, minutes=30))
                yest_str = (datetime.now(ist_tz) - timedelta(days=1)).strftime("%Y-%m-%d")
                print(f"Saving daily snapshot for date {yest_str} ({len(previous_records)} records)...")

                # Save snapshot by state
                prev_grouped = {}
                for pr in previous_records:
                    sk = get_clean_state_key(pr.get("state", ""))
                    prev_grouped.setdefault(sk, []).append(pr)

                for sk, sk_recs in prev_grouped.items():
                    db.collection(f"mandi_snapshots_{yest_str}").document(sk).set({
                        "dateStr": yest_str,
                        "records": sk_recs[:MAX_DOC_RECORDS]
                    }, merge=True)

                db.collection("mandi_snapshots").document(yest_str).set({
                    "dateStr": yest_str,
                    "records": previous_records[:MAX_DOC_RECORDS]
                }, merge=True)
    except Exception as e:
        print(f"Note on prior snapshot check: {e}")

    # 2. Compute price differences vs previous records
    records_with_diff = compute_price_differences(records, previous_records)

    # 3. Group by clean state key and save to 'mandi_by_state/{stateKey}'
    grouped_by_state = {}
    for rec in records_with_diff:
        sk = get_clean_state_key(rec["state"])
        grouped_by_state.setdefault(sk, []).append(rec)

    print(f"Saving state documents across {len(grouped_by_state)} states to Firestore 'mandi_by_state'...")
    for sk, state_recs in grouped_by_state.items():
        if sk and sk != "unknown_state":
            doc_ref = db.collection("mandi_by_state").document(sk)
            doc_ref.set({
                "stateKey": sk,
                "updatedAt": now_timestamp,
                "records": state_recs[:MAX_DOC_RECORDS]
            }, merge=True)

    # 4. Save summary payload (up to 2000 records) to 'mandi_cache/latest'
    print("Updating 'mandi_cache/latest' document in Firestore...")
    db.collection("mandi_cache").document("latest").set({
        "updatedAt": now_timestamp,
        "records": records_with_diff[:MAX_DOC_RECORDS]
    }, merge=True)

    # 5. Append today's prices to 'price_history/{docId}' for 7-day & 30-day crop charts
    update_price_history_in_firestore(records_with_diff)

    print("🔥 Successfully synced all Mandi records & price trend charts to Firestore!")

# ---------------------------------------------------------------------------
# MAIN EXECUTION
# ---------------------------------------------------------------------------
def main():
    print(f"=== Mandi Auto-Sync Started at {datetime.now(timezone.utc).isoformat()} ===")
    records = fetch_all_mandi_records_from_api()

    if records:
        save_records_to_firestore(records)
    else:
        print("⚠️ API returned 0 records. Firestore was not modified.")

    print("=== Mandi Auto-Sync Finished ===")

if __name__ == "__main__":
    main()
