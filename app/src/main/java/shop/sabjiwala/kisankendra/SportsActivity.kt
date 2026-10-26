package shop.sabjiwala.kisankendra

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.ActivitySportsBinding
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.SportsConfigHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.ConcurrentHashMap

class SportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySportsBinding
    private var sportsInterstitialAd: InterstitialAd? = null
    private var lastInterstitialTime = 0L

    companion object {
        private const val BANNER_AD_ID = "ca-app-pub-3054731531860427/9592937417"
        private const val INTERSTITIAL_AD_ID = "ca-app-pub-3054731531860427/5297828555"
        private const val MIN_INTERSTITIAL_GAP_MS = 60_000L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = ActivitySportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainSports) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        binding.btnBackSports.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val activeLang = LocaleHelper.getPersistedLanguage(this)

        binding.btnRefreshSports.setOnClickListener {
            binding.progressBarSports.visibility = View.VISIBLE
            SportsConfigHelper.fetchSportsConfigOnce(this) { _, updatedUrl ->
                setupWebView(activeLang, updatedUrl)
            }
        }

        loadBannerAd()
        loadInterstitialAd()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                TranslationHelper.syncMasterDictionaryFromFirestore(this@SportsActivity, activeLang)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                val configuredUrl = SportsConfigHelper.getSportsWebUrl(this@SportsActivity)
                setupWebView(activeLang, configuredUrl)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webViewSports.canGoBack()) {
                    binding.webViewSports.goBack()
                } else {
                    showInterstitialAdAndFinish()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(activeLang: String, sportsUrl: String = "") {
        val translationMap = buildCricketTranslationMap(activeLang)
        val dictJson = Gson().toJson(translationMap)

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webViewSports, true)
        if (activeLang == "hi") {
            cookieManager.setCookie("https://crictimes.org", "googtrans=/en/hi")
            cookieManager.setCookie("https://cwidget.crictimes.org", "googtrans=/en/hi")
        } else {
            cookieManager.setCookie("https://crictimes.org", "googtrans=/en/en")
            cookieManager.setCookie("https://cwidget.crictimes.org", "googtrans=/en/en")
        }

        binding.webViewSports.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.webViewSports.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        binding.webViewSports.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 90) {
                    binding.progressBarSports.visibility = View.GONE
                }
            }
        }

        binding.webViewSports.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request?.url?.toString() ?: return false
                if (urlStr.contains("crictimes.org/cricket-scores") || urlStr.contains("widgets-for-your-website")) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBarSports.visibility = View.GONE
                injectTranslationScript(view, dictJson, activeLang)
            }
        }

        val activeUrl = if (sportsUrl.isNotBlank()) sportsUrl else SportsConfigHelper.getSportsWebUrl(this)

        if (activeUrl.contains("cwidget.crictimes.org") || activeUrl.contains("widget")) {
            val htmlContent = buildWidgetHtml(activeUrl)
            binding.webViewSports.loadDataWithBaseURL(
                "https://cwidget.crictimes.org/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        } else {
            binding.webViewSports.loadUrl(activeUrl)
        }
    }

    private fun injectTranslationScript(view: WebView?, dictJson: String, activeLang: String) {
        val js = """
            (function() {
                var activeLang = '$activeLang';
                var translationDict = $dictJson;

                var MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
                var MONTH_MAP = {
                    "jan": 0, "january": 0, "feb": 1, "february": 1, "mar": 2, "march": 2,
                    "apr": 3, "april": 3, "may": 4, "june": 5, "jun": 5,
                    "jul": 6, "july": 6, "aug": 7, "august": 7, "sep": 8, "september": 8, "sept": 8,
                    "oct": 9, "october": 9, "nov": 10, "november": 10, "dec": 11, "december": 11
                };
                var DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

                function pad2(n) {
                    return (n < 10 ? "0" : "") + n;
                }

                function convertGmtToIst(match, pDay, pMonth, pYear, pDayOfWeek, pHours, pMins, pAmPm) {
                    var day = parseInt(pDay, 10);
                    var monthIdx = MONTH_MAP[pMonth.toLowerCase()];
                    if (monthIdx === undefined) return match;
                    var year = pYear ? parseInt(pYear, 10) : new Date().getFullYear();

                    var hours = parseInt(pHours, 10);
                    var mins = parseInt(pMins, 10);
                    if (pAmPm) {
                        var ampm = pAmPm.toUpperCase();
                        if (ampm === "PM" && hours < 12) hours += 12;
                        if (ampm === "AM" && hours === 12) hours = 0;
                    }

                    var utcMs = Date.UTC(year, monthIdx, day, hours, mins, 0);
                    var istMs = utcMs + (5 * 60 + 30) * 60 * 1000;
                    var istDate = new Date(istMs);

                    var resYear = istDate.getUTCFullYear();
                    var resMonth = MONTH_NAMES[istDate.getUTCMonth()];
                    var resDayNum = istDate.getUTCDate();
                    var resDayStr = (pDay.length === 2 && resDayNum < 10) ? ("0" + resDayNum) : ("" + resDayNum);
                    var resDayOfWeek = DAY_NAMES[istDate.getUTCDay()];

                    var resH24 = istDate.getUTCHours();
                    var resMins = pad2(istDate.getUTCMinutes());
                    var resAmPm = resH24 >= 12 ? "PM" : "AM";
                    var resH12 = resH24 % 12 === 0 ? 12 : resH24 % 12;
                    var resHStr = (pHours.length === 2 && resH12 < 10) ? ("0" + resH12) : ("" + resH12);

                    var timeStr = resHStr + ":" + resMins + " " + resAmPm + " IST";

                    if (pYear && pDayOfWeek) {
                        return resDayStr + " " + resMonth + ", " + resYear + ", " + resDayOfWeek + " " + timeStr;
                    } else if (pYear) {
                        return resDayStr + " " + resMonth + ", " + resYear + " " + timeStr;
                    } else if (pDayOfWeek) {
                        return resDayStr + " " + resMonth + ", " + resDayOfWeek + " " + timeStr;
                    } else {
                        return resDayStr + " " + resMonth + " " + timeStr;
                    }
                }

                function convertTimeOnlyToIst(match, pHours, pMins, pAmPm) {
                    var hours = parseInt(pHours, 10);
                    var mins = parseInt(pMins, 10);
                    if (pAmPm) {
                        var ampm = pAmPm.toUpperCase();
                        if (ampm === "PM" && hours < 12) hours += 12;
                        if (ampm === "AM" && hours === 12) hours = 0;
                    }
                    var totalMins = hours * 60 + mins + 330;
                    var newMins = (totalMins % (24 * 60) + 24 * 60) % (24 * 60);
                    var resH24 = Math.floor(newMins / 60);
                    var resMins = pad2(newMins % 60);
                    var resAmPm = resH24 >= 12 ? "PM" : "AM";
                    var resH12 = resH24 % 12 === 0 ? 12 : resH24 % 12;
                    var resHStr = (pHours.length === 2 && resH12 < 10) ? ("0" + resH12) : ("" + resH12);
                    return resHStr + ":" + resMins + " " + resAmPm + " IST";
                }

                function transformTextToIst(text) {
                    if (!text || typeof text !== 'string') return text;
                    if (text.indexOf('GMT') === -1 && text.indexOf('UTC') === -1) return text;

                    var s = text;

                    // 1. Full date with year, day of week, time, GMT/UTC
                    s = s.replace(/\b(\d{1,2})\s+([A-Za-z]{3}),?\s+(\d{4}),?\s+([A-Za-z]{3})\s+(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\s*(?:GMT|UTC)\b/gi, function(m, d, mo, y, dw, h, mi, ap) {
                        return convertGmtToIst(m, d, mo, y, dw, h, mi, ap);
                    });

                    // 2. Date with year, time, GMT/UTC (no day of week)
                    s = s.replace(/\b(\d{1,2})\s+([A-Za-z]{3}),?\s+(\d{4})\s+(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\s*(?:GMT|UTC)\b/gi, function(m, d, mo, y, h, mi, ap) {
                        return convertGmtToIst(m, d, mo, y, null, h, mi, ap);
                    });

                    // 3. Date with day of week, time, GMT/UTC (no year)
                    s = s.replace(/\b(\d{1,2})\s+([A-Za-z]{3}),?\s+([A-Za-z]{3})\s+(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\s*(?:GMT|UTC)\b/gi, function(m, d, mo, dw, h, mi, ap) {
                        return convertGmtToIst(m, d, mo, null, dw, h, mi, ap);
                    });

                    // 4. Date with time, GMT/UTC (no year, no day of week)
                    s = s.replace(/\b(\d{1,2})\s+([A-Za-z]{3})\s+(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\s*(?:GMT|UTC)\b/gi, function(m, d, mo, h, mi, ap) {
                        return convertGmtToIst(m, d, mo, null, null, h, mi, ap);
                    });

                    // 5. Time only GMT/UTC
                    s = s.replace(/\b(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\s*(?:GMT|UTC)\b/gi, function(m, h, mi, ap) {
                        return convertTimeOnlyToIst(m, h, mi, ap);
                    });

                    // 6. Labels like "Time(GMT)" -> "Time(IST)"
                    s = s.replace(/Time\s*\(\s*GMT\s*\)/gi, "Time(IST)");

                    return s;
                }

                function convertDocTimes(doc) {
                    if (!doc) return;
                    try {
                        var root = doc.body || doc;
                        var walker = doc.createTreeWalker(root, 4, null, false);
                        var n;
                        while (n = walker.nextNode()) {
                            var orig = n.nodeValue;
                            if (orig && (orig.indexOf('GMT') !== -1 || orig.indexOf('UTC') !== -1)) {
                                var converted = transformTextToIst(orig);
                                if (converted !== orig) {
                                    n.nodeValue = converted;
                                }
                            }
                        }

                        var attrEls = root.querySelectorAll('[aria-label*="GMT"], [aria-label*="UTC"], [title*="GMT"], [title*="UTC"]');
                        for (var i = 0; i < attrEls.length; i++) {
                            var el = attrEls[i];
                            if (el.hasAttribute('aria-label')) {
                                var al = el.getAttribute('aria-label');
                                if (al && (al.indexOf('GMT') !== -1 || al.indexOf('UTC') !== -1)) {
                                    el.setAttribute('aria-label', transformTextToIst(al));
                                }
                            }
                            if (el.hasAttribute('title')) {
                                var t = el.getAttribute('title');
                                if (t && (t.indexOf('GMT') !== -1 || t.indexOf('UTC') !== -1)) {
                                    el.setAttribute('title', transformTextToIst(t));
                                }
                            }
                        }
                    } catch(e) {}
                }

                function injectAdBlockCss(doc) {
                    if (!doc || doc._adBlockCss) return;
                    doc._adBlockCss = true;
                    try {
                        var style = doc.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = 'header, .header, #header, .site-header, .top-header, .navbar, .nav-bar, nav, .top-nav, .logo-bar, .breadcrumb, .breadcrumbs, iframe[src*="google"], iframe[src*="doubleclick"], iframe[src*="amazon"], iframe[src*="taboola"], div[id*="google_ads"], div[id*="div-gpt-ad"], ins.adsbygoogle, .adsbygoogle, .top-ad, .header-ad, .bottom-ad, .footer-ad, .sponsored, .sponsored-links, #sponsored-links, .discover-more, #discover-more, .taboola, .outbrain, .mgid, [class*="paloalto"], [id*="paloalto"], [class*="smartworld"], [id*="smartworld"], [class*="centurian"], [id*="centurian"], a[href*="tickets"], a[href*="jerseys"], footer, .footer, #footer, .copyright, .crictimes-footer, .sidebar, .right-sidebar, #sidebar, .col-md-4, .crictimes-sidebar, div[class*="sidebar"], .widget-sidebar, .share-btn, .social-share, .share-buttons, .share-box, div[class*="share"], div[class*="social"], .shares-count, div[class*="Shares"], .st-custom-button, .st-btn, #st-1, a[href*="whatsapp.com"], a[href*="facebook.com"], a[href*="twitter.com"], a[href*="telegram"] { display: none !important; visibility: hidden !important; height: 0px !important; width: 0px !important; max-width: 0px !important; opacity: 0 !important; pointer-events: none !important; } .col-md-8, .col-lg-8, .col-sm-8, .main-content, #main-content, div[class*="col-md-8"], div[class*="col-lg-8"], div[class*="col-sm-8"] { width: 100% !important; max-width: 100% !important; flex: 0 0 100% !important; }';
                        (doc.head || doc.documentElement).appendChild(style);
                    } catch(e) {}
                }

                function cleanAndRemoveThirdPartyAds(doc) {
                    if (!doc) return;
                    injectAdBlockCss(doc);
                    try {
                        var selectors = [
                            'header', '.header', '#header', '.site-header', '.top-header', '.navbar', '.nav-bar', 'nav', '.top-nav', '.logo-bar', '.breadcrumb', '.breadcrumbs',
                            'ins.adsbygoogle', '.adsbygoogle',
                            'div[class*="ad-"]', 'div[id*="ad-"]',
                            'div[class*="sponsor"]', 'div[id*="sponsor"]',
                            'div[class*="banner"]', 'div[id*="banner"]',
                            '.sponsored-links', '#sponsored-links', '.sponsored',
                            '.crictimes-ad', '.taboola', '.outbrain', '.mgid',
                            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                            'iframe[src*="amazon-adsystem"]', 'iframe[src*="taboola"]',
                            'a[href*="crictimes.org/widgets-for-your-website"]',
                            'a[href*="crictimes.org/embed"]',
                            '.discover-more', '[class*="paloalto"]', '[id*="paloalto"]',
                            '[class*="smartworld"]', '[id*="smartworld"]',
                            '[class*="centurian"]', '[id*="centurian"]',
                            'footer', '.footer', '#footer', '.copyright', '.crictimes-footer', '.widget-footer',
                            '.sidebar', '.right-sidebar', '#sidebar', '.col-md-4', '.crictimes-sidebar', 'div[class*="sidebar"]', '.widget-sidebar',
                            '.share-btn', '.social-share', '.share-buttons', '.share-box', 'div[class*="share"]', 'div[class*="social"]', '.shares-count', 'div[class*="Shares"]', '.st-custom-button', '.st-btn', '#st-1'
                        ];
                        var root = doc.body || doc;
                        var els = root.querySelectorAll(selectors.join(','));
                        for (var i = 0; i < els.length; i++) {
                            try {
                                els[i].style.display = 'none';
                                els[i].style.height = '0px';
                                els[i].style.opacity = '0';
                                els[i].style.pointerEvents = 'none';
                                els[i].remove();
                            } catch(e){}
                        }
                    } catch(e) {}
                }

                function fixScorecardHeaders(doc) {
                    if (!doc) return;
                    try {
                        var root = doc.body || doc;
                        var headers = root.querySelectorAll('th, td, span, div');
                        for (var i = 0; i < headers.length; i++) {
                            var el = headers[i];
                            if (el.children && el.children.length > 0) continue;
                            var txt = (el.innerText || el.textContent || '').trim();
                            if (txt === 'R') {
                                el.innerText = 'रन';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'B') {
                                el.innerText = 'गेंद';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === '4s') {
                                el.innerText = 'चौके';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === '6s') {
                                el.innerText = 'छक्के';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'SR') {
                                el.innerText = 'स्ट्राइक रेट';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'O') {
                                el.innerText = 'ओवर';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'M') {
                                el.innerText = 'मेडन';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'W') {
                                el.innerText = 'विकेट';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'Econ' || txt === 'EC' || txt === 'eco' || txt === 'ECO') {
                                el.innerText = 'इकोनॉमी';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'NB' || txt === 'nb') {
                                el.innerText = 'नो बॉल';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'WD' || txt === 'wd') {
                                el.innerText = 'वाइड';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'Extras' || txt === 'extras') {
                                el.innerText = 'अतिरिक्त';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            } else if (txt === 'LB' || txt === 'lb') {
                                el.innerText = 'लेग बाय';
                                el.classList.add('notranslate');
                                el.setAttribute('translate', 'no');
                            }
                        }
                    } catch(e) {}
                }

                function fixMatchHeaderAndScores(doc) {
                    if (!doc) return;
                    try {
                        var root = doc.body || doc;
                        var scoreEls = root.querySelectorAll('.match-header, .score-box, .match-status, .match-result, .result, .team-score, .score-summary, .score, [class*="score"], [class*="result"], [class*="status"]');
                        for (var i = 0; i < scoreEls.length; i++) {
                            var el = scoreEls[i];
                            if (!el.classList.contains('adsbygoogle') && !el.classList.contains('taboola') && !el.classList.contains('mgid')) {
                                el.style.display = 'block';
                                el.style.visibility = 'visible';
                                el.style.opacity = '1';
                                el.style.height = 'auto';
                            }
                        }
                    } catch(e) {}
                }

                function injectGoogleTranslateEngine(doc) {
                    if (!doc || doc._gtInjected || activeLang !== 'hi') return;
                    doc._gtInjected = true;
                    try {
                        document.cookie = "googtrans=/en/" + activeLang + "; path=/; domain=.crictimes.org";
                        document.cookie = "googtrans=/en/" + activeLang + "; path=/;";

                        var gtDiv = doc.createElement('div');
                        gtDiv.id = 'google_translate_element';
                        gtDiv.style.display = 'none';
                        (doc.body || doc.documentElement).appendChild(gtDiv);

                        window.googleTranslateInit = function() {
                            try {
                                new google.translate.TranslateElement({
                                    pageLanguage: 'en',
                                    includedLanguages: activeLang + ',en',
                                    autoDisplay: false
                                }, 'google_translate_element');

                                setTimeout(function() {
                                    var selectEl = doc.querySelector('.goog-te-combo');
                                    if (selectEl) {
                                        selectEl.value = activeLang;
                                        selectEl.dispatchEvent(new Event('change'));
                                    }
                                }, 500);
                            } catch(e) {}
                        };

                        var gtScript = doc.createElement('script');
                        gtScript.type = 'text/javascript';
                        gtScript.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateInit';
                        (doc.head || doc.documentElement).appendChild(gtScript);
                    } catch(e) {}
                }

                function escapeRegExp(string) {
                    var specials = "[.*+?^" + "$" + "{}()|[]\\\\";
                    return String(string).split('').map(function(c) {
                        return (specials.indexOf(c) !== -1) ? '\\' + c : c;
                    }).join('');
                }

                function translateCricketPhrases(text) {
                    if (!text || typeof text !== 'string') return text;
                    var s = text;

                    s = s.replace(/\b(\d+)(st|nd|rd|th)\s+T20I?\b/gi, "$1वाँ टी20");
                    s = s.replace(/\btour\s+of\b/gi, "का दौरा");
                    s = s.replace(/\bwon\s+the\s+toss\b/gi, "टॉस जीता");
                    s = s.replace(/\bopted\s+to\s+bat\b/gi, "बल्लेबाजी चुनी");
                    s = s.replace(/\bopted\s+to\s+bowl\b/gi, "गेंदबाजी चुनी");
                    s = s.replace(/\bRecent\b/gi, "हालिया");
                    s = s.replace(/\bLive\b/gi, "लाइव");
                    s = s.replace(/\bUpcoming\b/gi, "आगामी");
                    s = s.replace(/\bBatsman\b/gi, "बल्लेबाज़");
                    s = s.replace(/\bBowler\b/gi, "गेंदबाज़");
                    s = s.replace(/\bOvers?\b/gi, "ओवर");
                    s = s.replace(/\bRuns?\b/gi, "रन");
                    s = s.replace(/\bWickets?\b/gi, "विकेट");
                    s = s.replace(/\bDetails\b/gi, "विवरण");
                    s = s.replace(/\bScorecard\b/gi, "स्कोरकार्ड");
                    s = s.replace(/\bCommentary\b/gi, "कमेंट्री");
                    s = s.replace(/\bPlaying\s+XI\b/gi, "प्लेइंग 11");

                    return s;
                }

                function translateText(text) {
                    if (!text || activeLang !== 'hi') return text;
                    var str = String(text);
                    var trimmed = str.trim();
                    if (!trimmed) return text;

                    var phrased = translateCricketPhrases(trimmed);
                    if (translationDict[phrased]) return translationDict[phrased];
                    if (translationDict[trimmed]) return translationDict[trimmed];

                    var lower = phrased.toLowerCase();
                    if (translationDict[lower]) return translationDict[lower];

                    var result = phrased;
                    var keys = Object.keys(translationDict).sort(function(a, b) { return b.length - a.length; });
                    for (var i = 0; i < keys.length; i++) {
                        var k = keys[i];
                        if (!k || k.length < 2) continue;
                        var val = translationDict[k];
                        if (!val) continue;
                        var reg = new RegExp('\\b' + escapeRegExp(k) + '\\b', 'gi');
                        result = result.replace(reg, val);
                    }
                    return result;
                }

                function translateTree(doc) {
                    if (!doc || activeLang !== 'hi') return;
                    try {
                        var root = doc.body || doc;
                        var walker = doc.createTreeWalker(root, 4, null, false);
                        var n;
                        while (n = walker.nextNode()) {
                            var orig = n.nodeValue;
                            if (orig && orig.trim()) {
                                var trans = translateText(orig);
                                if (trans !== orig) {
                                    n.nodeValue = trans;
                                }
                            }
                        }
                    } catch(e) {}
                }

                function processAllDocs() {
                    cleanAndRemoveThirdPartyAds(document);
                    fixMatchHeaderAndScores(document);
                    if (activeLang === 'hi') {
                        fixScorecardHeaders(document);
                        injectGoogleTranslateEngine(document);
                        convertDocTimes(document);
                        translateTree(document);
                    }
                    try {
                        var iframes = document.getElementsByTagName('iframe');
                        for (var i = 0; i < iframes.length; i++) {
                            try {
                                var fDoc = iframes[i].contentDocument || (iframes[i].contentWindow && iframes[i].contentWindow.document);
                                if (fDoc) {
                                    cleanAndRemoveThirdPartyAds(fDoc);
                                    fixMatchHeaderAndScores(fDoc);
                                    if (activeLang === 'hi') {
                                        fixScorecardHeaders(fDoc);
                                        injectGoogleTranslateEngine(fDoc);
                                        convertDocTimes(fDoc);
                                        translateTree(fDoc);
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                processAllDocs();
                if (!window._transInterval) {
                    window._transInterval = setInterval(processAllDocs, 400);
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    private fun buildCricketTranslationMap(langCode: String): Map<String, String> {
        val dict = ConcurrentHashMap<String, String>()

        // 1. Fetch Firebase & Master Dictionary Cache
        val master = TranslationHelper.getOrLoadLangCache(this, langCode)
        dict.putAll(master)

        // 2. Add Specialized Cricket Teams, Countries, Tournaments & Match Terms (Hindi)
        if (langCode == "hi") {
            val cricketMap = mapOf(
                // International Countries
                "India" to "भारत",
                "IND" to "भारत",
                "Australia" to "ऑस",
                "AUS" to "ऑस",
                "England" to "इंग्लैंड",
                "ENG" to "इंग्लैंड",
                "Pakistan" to "पाकिस्तान",
                "PAK" to "पाक",
                "South Africa" to "दक्षिण अफ्रीका",
                "SA" to "दक्षिण अफ्रीका",
                "New Zealand" to "न्यूजीलैंड",
                "NZ" to "न्यूजीलैंड",
                "Sri Lanka" to "श्रीलंका",
                "SL" to "श्रीलंका",
                "Bangladesh" to "बांग्लादेश",
                "BAN" to "बांग्लादेश",
                "Afghanistan" to "अफगानिस्तान",
                "AFG" to "अफगानिस्तान",
                "West Indies" to "वेस्ट इंडीज",
                "WI" to "वेस्ट इंडीज",
                "Zimbabwe" to "जिम्बाब्वे",
                "ZIM" to "जिम्बाब्वे",
                "Ireland" to "आयरलैंड",
                "IRE" to "आयरलैंड",
                "Netherlands" to "नीदरलैंड",
                "NED" to "नीदरलैंड",
                "Nepal" to "नेपाल",
                "NEP" to "नेपाल",
                "USA" to "अमेरिका",
                "United States" to "अमेरिका",
                "UAE" to "संयुक्त अरब अमीरात",
                "United Arab Emirates" to "संयुक्त अरब अमीरात",
                "Scotland" to "स्कॉटलैंड",
                "SCO" to "स्कॉटलैंड",
                "Oman" to "ओमान",
                "Canada" to "कनाडा",
                "Namibia" to "नामीबिया",
                "Uganda" to "युगांडा",
                "Papua New Guinea" to "पापुआ न्यू गिनी",

                // IPL Teams
                "Mumbai Indians" to "मुंबई इंडियंस",
                "MI" to "मुंबई इंडियंस",
                "Chennai Super Kings" to "चेन्नई सुपर किंग्स",
                "CSK" to "चेन्नई सुपर किंग्स",
                "Royal Challengers Bengaluru" to "रॉयल चैलेंजर्स बेंगलुरु",
                "Royal Challengers Bangalore" to "रॉयल चैलेंजर्स बेंगलुरु",
                "RCB" to "रॉयल चैलेंजर्स बेंगलुरु",
                "Kolkata Knight Riders" to "कोलकाता नाइट राइडर्स",
                "KKR" to "कोलकाता नाइट राइडर्स",
                "Rajasthan Royals" to "राजस्थान रॉयल्स",
                "RR" to "राजस्थान रॉयल्स",
                "Sunrisers Hyderabad" to "सनराइजर्स हैदराबाद",
                "SRH" to "सनराइजर्स हैदराबाद",
                "Delhi Capitals" to "दिल्ली कैपिटल्स",
                "DC" to "दिल्ली कैपिटल्स",
                "Punjab Kings" to "पंजाब किंग्स",
                "PBKS" to "पंजाब किंग्स",
                "Gujarat Titans" to "गुजरात टाइटंस",
                "GT" to "गुजरात टाइटंस",
                "Lucknow Super Giants" to "लखनऊ सुपर जायंट्स",
                "LSG" to "लखनऊ सुपर जायंट्स",

                // Women Teams
                "India Women" to "भारत महिला",
                "Australia Women" to "ऑस महिला",
                "England Women" to "इंग्लैंड महिला",
                "Pakistan Women" to "पाकिस्तान महिला",
                "South Africa Women" to "दक्षिण अफ्रीका महिला",
                "New Zealand Women" to "न्यूजीलैंड महिला",
                "Sri Lanka Women" to "श्रीलंका महिला",
                "West Indies Women" to "वेस्ट इंडीज महिला",
                "Bangladesh Women" to "बांग्लादेश महिला",
                "Mumbai Indians Women" to "मुंबई इंडियंस महिला",
                "Delhi Capitals Women" to "दिल्ली कैपिटल्स महिला",
                "Royal Challengers Bengaluru Women" to "रॉयल चैलेंजर्स बेंगलुरु महिला",
                "Gujarat Giants" to "गुजरात जाइंट्स",
                "UP Warriorz" to "यूपी वारियर्स",

                // Match Formats & Series
                "T20I" to "टी20आई",
                "T20" to "टी20",
                "ODI" to "वनडे",
                "Test" to "टेस्ट",
                "vs" to "बनाम",
                "v" to "बनाम",

                // Widget Tabs & Cricket UI Terms
                "LIVE MATCHES" to "🔴 लाइव मैच",
                "LIVE & FEATURED MATCHES" to "🔴 लाइव एवं प्रमुख मैच",
                "LIVE" to "लाइव",
                "Live" to "लाइव",
                "Live Cricket Scores" to "🔴 लाइव क्रिकेट स्कोर",
                "Fixtures" to "आगामी मैच",
                "Results" to "परिणाम / नतीजे",
                "Matches" to "मैच",
                "Upcoming" to "आगामी",
                "Completed" to "समाप्त",
                "In Progress" to "जारी है",
                "Toss" to "टॉस",
                "won by" to "से जीता",
                "won" to "जीता",
                "runs" to "रन",
                "run" to "रन",
                "wickets" to "विकेट",
                "wicket" to "विकेट",
                "overs" to "ओवर",
                "over" to "ओवर",
                "balls" to "गेंदें",
                "ball" to "गेंद",
                "Match Tied" to "मैच टाई रहा",
                "No Result" to "कोई परिणाम नहीं",
                "Stumps" to "दिन का खेल समाप्त",
                "Innings" to "पारी",
                "Target" to "लक्ष्य",
                "CRR" to "वर्तमान रन रेट",
                "RRR" to "आवश्यक रन रेट",
                "Lead by" to "की बढ़त",
                "Trail by" to "पीछे",
                "Need" to "जरूरत है",
                "Day" to "दिन",
                "Session" to "सत्र",
                "opted to" to "ने चुना",
                "bat" to "बल्लेबाजी",
                "bowl" to "गेंदबाजी",
                "first" to "पहले",
                "Rain Delay" to "बारिश के कारण रुकावट",
                "Match Delayed" to "मैच में देरी",
                "Abandoned" to "मैच रद्द",
                "GMT" to "IST",
                "UTC" to "IST",
                "Time(GMT)" to "समय (IST)",
                "Time (GMT)" to "समय (IST)",
                "Indian Standard Time" to "भारतीय समयानुसार (IST)",

                // Detail Page & Scorecard Terms
                "RECENT" to "🔴 हालिया",
                "UPCOMING" to "आगामी",
                "DETAILS" to "विवरण",
                "Details" to "विवरण",
                "Batsman" to "बल्लेबाज़",
                "Bowler" to "गेंदबाज़",
                "Batting" to "बल्लेबाज़ी",
                "Bowling" to "गेंदबाज़ी",
                "Score Card" to "स्कोरकार्ड",
                "Scorecard" to "स्कोरकार्ड",
                "Commentary" to "कमेंट्री",
                "Match Info" to "मैच जानकारी",
                "Playing XI" to "प्लेइंग 11",
                "Fall of Wickets" to "विकेट पतन",
                "Yet to bat" to "बल्लेबाज़ी बाकी",
                "tour of" to "का दौरा",
                "win by" to "से जीता",
                "Cricket match tickets" to "मैच टिकट",
                "cricket jerseys" to "क्रिकेट जर्सी",
                "Discover more" to "अन्य जानकारी",
                "Home" to "होम",
                "Cricket Scores" to "क्रिकेट स्कोर",
                "LIVE SCORES" to "🔴 लाइव स्कोर"
            )
            dict.putAll(cricketMap)
        }

        return dict
    }

    private fun buildWidgetHtml(widgetUrl: String = ""): String {
        val iframeSrc = if (widgetUrl.isNotBlank()) widgetUrl else SportsConfigHelper.DEFAULT_WEB_URL
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        background-color: #F8F9FA !important;
                        font-family: Arial, sans-serif;
                        width: 100% !important;
                        overflow-x: hidden !important;
                    }
                    * { box-sizing: border-box !important; }

                    #score-frame {
                        width: 100% !important;
                        margin: 0 !important;
                        border-radius: 0 !important;
                        overflow: hidden !important;
                    }

                    #score-frame iframe {
                        width: 100% !important;
                        border: 0 !important;
                        min-height: 520px !important;
                    }

                    footer,
                    .footer,
                    #footer,
                    .copyright,
                    .crictimes-footer,
                    .widget-footer,
                    .embed-link,
                    .embed-btn {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                </style>
            </head>
            <body>
                <!-- Crictimes Live Scores Widget Frame -->
                <div id="score-frame"></div>
                <script type="text/javascript">
                    var html = document.getElementById('score-frame');
                    html.innerHTML = '<iframe src="$iframeSrc" style="width:100%;min-height: 520px;" frameborder="0" scrolling="yes"></iframe>';
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun loadBannerAd() {
        binding.adContainerSports.post {
            try {
                binding.cardAdSports.visibility = View.GONE
                val adView = AdView(this).apply {
                    adUnitId = BANNER_AD_ID
                    val displayMetrics = resources.displayMetrics
                    val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt().coerceAtLeast(300)
                    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@SportsActivity, adWidth)
                    setAdSize(adSize)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            binding.cardAdSports.visibility = View.VISIBLE
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            binding.cardAdSports.visibility = View.GONE
                        }
                    }
                }
                binding.adContainerSports.removeAllViews()
                binding.adContainerSports.addView(adView)
                adView.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadInterstitialAd() {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                this,
                INTERSTITIAL_AD_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        sportsInterstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        sportsInterstitialAd = null
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showInterstitialAdAndFinish() {
        val currentTime = System.currentTimeMillis()
        if (sportsInterstitialAd != null && currentTime - lastInterstitialTime >= MIN_INTERSTITIAL_GAP_MS) {
            sportsInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    sportsInterstitialAd = null
                    lastInterstitialTime = System.currentTimeMillis()
                    finish()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    sportsInterstitialAd = null
                    lastInterstitialTime = System.currentTimeMillis()
                    finish()
                }
            }
            sportsInterstitialAd?.show(this)
        } else {
            finish()
        }
    }
}
