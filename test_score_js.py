import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
}

url = "https://cdorgapi.b-cdn.net/widgets/score.js"
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        js_code = resp.read().decode('utf-8')
        print("score.js length:", len(js_code))

        # Look for element IDs or document.getElementById in score.js
        ids = re.findall(r'document\.getElementById\([\'"]([^\'"]+)[\'"]\)', js_code)
        print("Element IDs searched in score.js:", list(set(ids)))

        # Look for querySelector or div IDs
        selectors = re.findall(r'#([a-zA-Z0-9_\-]+)', js_code)
        print("CSS IDs in score.js:", list(set(selectors))[:10])

except Exception as e:
    print("Error:", e)
