package shop.sabjiwala.kisankendra.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

enum class ExpenseChartMode {
    TOTAL_EXPENSE,
    EXPENSE_PER_ACRE
}

data class CropExpenseDataPoint(
    val cropName: String,
    val totalExpense: Double,
    val expensePerAcre: Double,
    val areaAcres: Double = 0.0
)

class CropExpenseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rawDataPoints: List<CropExpenseDataPoint> = emptyList()
    private var sortedDataPoints: List<CropExpenseDataPoint> = emptyList()
    private var chartMode: ExpenseChartMode = ExpenseChartMode.TOTAL_EXPENSE

    private val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isNightMode) Color.parseColor("#222222") else Color.parseColor("#F0F4F1")
    }

    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val cropNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.WHITE else Color.parseColor("#1B1B1B")
        textSize = 30f
        isFakeBoldText = true
    }

    private val expenseTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#C62828")
        textSize = 28f
        isFakeBoldText = true
    }

    private val areaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#B0B0B0") else Color.parseColor("#666666")
        textSize = 24f
    }

    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#888888") else Color.parseColor("#666666")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#AAAAAA") else Color.parseColor("#666666")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val barRect = RectF()
    private val trackRect = RectF()

    fun setData(points: List<CropExpenseDataPoint>) {
        this.rawDataPoints = points
        sortAndApplyPoints()
        requestLayout()
        invalidate()
    }

    fun setChartMode(mode: ExpenseChartMode) {
        if (this.chartMode != mode) {
            this.chartMode = mode
            sortAndApplyPoints()
            requestLayout()
            invalidate()
        }
    }

    fun getChartMode(): ExpenseChartMode = chartMode

    private fun sortAndApplyPoints() {
        sortedDataPoints = if (chartMode == ExpenseChartMode.EXPENSE_PER_ACRE) {
            rawDataPoints.sortedByDescending { it.expensePerAcre }
        } else {
            rawDataPoints.sortedByDescending { it.totalExpense }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density

        val calculatedHeight = if (sortedDataPoints.isEmpty()) {
            (140 * density).toInt()
        } else {
            val rowHeight = (64 * density).toInt()
            val paddingTopBottom = (40 * density).toInt()
            paddingTopBottom + (sortedDataPoints.size * rowHeight)
        }

        val height = resolveSize(calculatedHeight, heightMeasureSpec)
        setMeasuredDimension(desiredWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (sortedDataPoints.isEmpty()) {
            drawEmptyChart(canvas)
            return
        }

        val density = resources.displayMetrics.density
        val paddingLeft = 16f * density
        val paddingRight = 16f * density
        val paddingTop = 16f * density
        val paddingBottom = 28f * density

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0f || chartHeight <= 0f) return

        val maxVal = if (chartMode == ExpenseChartMode.EXPENSE_PER_ACRE) {
            sortedDataPoints.maxOfOrNull { abs(it.expensePerAcre) }?.coerceAtLeast(100.0) ?: 100.0
        } else {
            sortedDataPoints.maxOfOrNull { abs(it.totalExpense) }?.coerceAtLeast(100.0) ?: 100.0
        }

        // Vertical grid lines (0%, 50%, 100%)
        val gridLines = 2
        for (i in 0..gridLines) {
            val gx = paddingLeft + (chartWidth / gridLines) * i
            canvas.drawLine(gx, paddingTop, gx, height - paddingBottom, gridPaint)

            val gridVal = (maxVal / gridLines) * i
            val suffix = if (chartMode == ExpenseChartMode.EXPENSE_PER_ACRE) "/Acre" else ""
            val gridLabel = formatCompactRupees(gridVal) + suffix
            canvas.drawText(gridLabel, gx, height - 8f * density, axisLabelPaint)
        }

        val rowHeight = chartHeight / sortedDataPoints.size
        val barThickness = 20f * density

        for (i in sortedDataPoints.indices) {
            val item = sortedDataPoints[i]
            val rowTop = paddingTop + (i * rowHeight)

            // 1. Crop Name & Rank (e.g., "#1 Wheat (2.0 Acre)")
            val rankPrefix = "#${i + 1} "
            val cleanCropName = getCleanCropName(item.cropName)
            val cropLabel = "$rankPrefix$cleanCropName"
            val areaLabel = if (item.areaAcres > 0) " (${String.format(Locale.ENGLISH, "%.1f", item.areaAcres)} Acre)" else ""

            canvas.drawText(cropLabel, paddingLeft, rowTop + 18f * density, cropNamePaint)

            val cropTextWidth = cropNamePaint.measureText(cropLabel)
            canvas.drawText(areaLabel, paddingLeft + cropTextWidth + 6f * density, rowTop + 18f * density, areaTextPaint)

            // 2. Value text on right: Total Expense vs Expense / Acre
            val targetVal = if (chartMode == ExpenseChartMode.EXPENSE_PER_ACRE) item.expensePerAcre else item.totalExpense
            val formattedVal = if (chartMode == ExpenseChartMode.EXPENSE_PER_ACRE) {
                formatSignedExpensePerAcre(item.expensePerAcre)
            } else {
                formatSignedExpense(item.totalExpense)
            }

            val textWidth = expenseTextPaint.measureText(formattedVal)
            canvas.drawText(formattedVal, width - paddingRight - textWidth, rowTop + 18f * density, expenseTextPaint)

            // 3. Bar Track
            val barTop = rowTop + 26f * density
            val barBottom = barTop + barThickness

            trackRect.set(paddingLeft, barTop, paddingLeft + chartWidth, barBottom)
            canvas.drawRoundRect(trackRect, 10f * density, 10f * density, barTrackPaint)

            // 4. Bar Fill (Crimson Red / Orange gradient for Expenses)
            val ratio = (abs(targetVal) / maxVal).coerceIn(0.05, 1.0).toFloat()
            val fillWidth = chartWidth * ratio

            barRect.set(paddingLeft, barTop, paddingLeft + fillWidth, barBottom)

            barFillPaint.shader = LinearGradient(
                paddingLeft, barTop, paddingLeft + fillWidth, barBottom,
                if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#E53935"),
                if (isNightMode) Color.parseColor("#FFAB91") else Color.parseColor("#D84315"),
                Shader.TileMode.CLAMP
            )

            canvas.drawRoundRect(barRect, 10f * density, 10f * density, barFillPaint)
        }
    }

    private fun drawEmptyChart(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText("No Crop Expenses Recorded for Selected Season", centerX, centerY, emptyTextPaint)
    }

    private fun getCleanCropName(rawName: String): String {
        val clean = rawName.split("/").firstOrNull()?.trim() ?: rawName
        val activeLang = LocaleHelper.getPersistedLanguage(context)
        return TranslationHelper.getTranslatedCropName(clean, activeLang, context)
    }

    private fun formatCompactRupees(amount: Double): String {
        return when {
            amount <= 0 -> "₹0"
            amount >= 100_000 -> String.format(Locale.US, "₹%.1fL", amount / 100_000)
            amount >= 1_000 -> String.format(Locale.US, "₹%.1fk", amount / 1000)
            else -> "₹${amount.toInt()}"
        }
    }

    private fun formatSignedExpense(amount: Double): String {
        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val formatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }
        return formatter.format(abs(amount))
    }

    private fun formatSignedExpensePerAcre(amount: Double): String {
        val formatted = formatCompactRupees(abs(amount))
        return "$formatted / Acre"
    }
}
