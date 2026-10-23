import urllib.request
import json
import time

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://cricketdata.org/'
}

ts = int(time.time() / 5)
url = f"https://cricketdata.org/apis/prepscores.aspx?k={ts}&r=https%3A%2F%2Fcricketdata.org%2F"

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        data_str = resp.read().decode('utf-8')
        print("Response Length:", len(data_str))
        print("Response Text:", data_str)

except Exception as e:
    print("Error:", e)
