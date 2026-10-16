package shop.sabjiwala.kisankendra.utils

import android.text.Html
import android.text.Spanned

object RichTextHelper {

    /**
     * Removes the 1st subtitle/header line (e.g. "### 📌 Overview", "📌 Overview", "# Title", "**Subtitle**")
     * from a news article description text to produce a clean excerpt for news feed cards.
     */
    fun getExcerptWithoutFirstSubtitle(rawText: String): String {
        if (rawText.isBlank()) return ""

        val lines = rawText.trim().lines().toMutableList()
        if (lines.isEmpty()) return ""

        val firstNonBlankIndex = lines.indexOfFirst { it.trim().isNotBlank() }
        if (firstNonBlankIndex == -1) return ""

        val firstLine = lines[firstNonBlankIndex].trim()

        // Check if the first line is a header/subtitle
        val isHeader = firstLine.startsWith("#") ||
                firstLine.startsWith("📌") ||
                firstLine.startsWith("🎁") ||
                firstLine.startsWith("📋") ||
                firstLine.startsWith("📝") ||
                firstLine.startsWith("ℹ️") ||
                firstLine.startsWith("📢") ||
                firstLine.matches(Regex("^\\*\\*.*?\\*\\*$")) ||
                firstLine.matches(Regex("^<[hH][1-6]>.*?</[hH][1-6]>$")) ||
                firstLine.matches(Regex("^(?i)(Overview|Summary|Highlights|Details|अवलोकन|विवरण|रूपरेखा|महत्वपूर्ण जानकारी)[:\\s]*$"))

        if (isHeader) {
            lines.removeAt(firstNonBlankIndex)
        }

        return lines.joinToString("\n").trim()
    }

    fun formatRichText(rawText: String): Spanned {
        if (rawText.isBlank()) {
            return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY)
        }

        var html = rawText
            // Remove 'Overview' headers for past posts
            .replace("(?mi)^#+\\s*(?:📌\\s*)?(?:Overview|Summary|अवलोकन|विवरण|रूपरेखा)\\s*(\r?\n)?".toRegex(), "")
            // Convert Markdown headers (Vibrant theme-neutral green for high contrast in both Light & Dark modes)
            .replace("(?m)^### (.*?)$".toRegex(), "<b><font color=\"#4CAF50\">$1</font></b>")
            .replace("(?m)^## (.*?)$".toRegex(), "<b><font color=\"#4CAF50\">$1</font></b>")
            .replace("(?m)^# (.*?)$".toRegex(), "<b><font color=\"#4CAF50\">$1</font></b>")
            // Convert Markdown bold
            .replace("\\*\\*(.*?)\\*\\*".toRegex(), "<b>$1</b>")
            // Convert Markdown links [Text](http://...) -> <a href="http://...">Text</a>
            .replace("\\[(.*?)\\]\\((https?://.*?)\\)".toRegex(), "<a href=\"$2\">$1</a>")
            // Convert raw URLs (http:// or https://) not in HTML tags or markdown links into clickable links
            .replace("(?<!href=\")(?<!\">)(https?://[a-zA-Z0-9./_\\-?=%&]+)".toRegex(), "<a href=\"$1\">$1</a>")
            // Convert bullet points
            .replace("(?m)^[-*] (.*?)$".toRegex(), "• $1")
            // Convert newlines
            .replace("\n", "<br/>")

        // Strip leading and trailing <br/> or <br> to ensure text has no extra blank lines at ends
        html = html.replace("^(<br\\s*/?>)+".toRegex(), "")
            .replace("(<br\\s*/?>)+$".toRegex(), "")
            .trim()

        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    /**
     * Constructs a clean, concise news share caption containing:
     * - Exactly 1 full paragraph
     * - First 2 lines of the 2nd paragraph + "... [Read More / पूरा पढ़ें]"
     * - Official Play Store app download link
     */
    fun buildTruncatedNewsShareText(title: String, fullDescription: String): String {
        val appUrl = "https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"

        val cleanTitle = title
            .replace("<[^>]*>".toRegex(), "")
            .replace("\n", " ")
            .trim()

        val cleanDesc = fullDescription
            .replace("<br/>", "\n")
            .replace("<br>", "\n")
            .replace("</p>", "\n\n")
            .replace("<[^>]*>".toRegex(), "")
            .replace("### ", "")
            .replace("## ", "")
            .replace("# ", "")
            .replace("**", "")
            .trim()

        val paragraphs = cleanDesc.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val shareDescBuilder = StringBuilder()

        if (paragraphs.isNotEmpty()) {
            shareDescBuilder.append(paragraphs[0])
        }

        if (paragraphs.size >= 2) {
            if (shareDescBuilder.isNotEmpty()) shareDescBuilder.append("\n\n")
            val p2Lines = paragraphs[1].lines().map { it.trim() }.filter { it.isNotBlank() }
            val p2Snippet = if (p2Lines.size >= 2) {
                p2Lines.take(2).joinToString("\n")
            } else {
                val p2Text = paragraphs[1]
                if (p2Text.length > 120) p2Text.take(120) else p2Text
            }
            shareDescBuilder.append(p2Snippet)
        }

        shareDescBuilder.append(" ... [Read More / पूरा पढ़ें]")

        return "📰 *$cleanTitle*\n\n$shareDescBuilder\n\n📲 Read Full Article on Kisan Kendra App on Google Play Store:\n$appUrl"
    }
}
