package shop.sabjiwala.kisankendra.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.NumberFormat
import java.util.Locale

data class StageExpenseDataPoint(
    val stageName: String,
    val totalExpense: Double
)

class CropStageExpenseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<StageExpenseDataPoint> = emptyList()

    private val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#E53935")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#C62828")
    }

    private val dotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isNightMode) Color.parseColor("#121212") else Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#E0E0E0") else Color.parseColor("#1B1B1B")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#C62828")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setData(points: List<StageExpenseDataPoint>) {
        this.dataPoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) {
            drawEmptyChart(canvas)
            return
        }

        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 60f
        val paddingBottom = 70f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        val maxExpense = dataPoints.maxOfOrNull { it.totalExpense }?.coerceAtLeast(100.0) ?: 100.0

        // Draw horizontal grid lines
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + (chartHeight / gridLines) * i
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
        }

        val pointCount = dataPoints.size
        val stepX = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth / 2f

        val linePath = Path()
        val fillPath = Path()

        val pointsXY = mutableListOf<Pair<Float, Float>>()

        for (i in dataPoints.indices) {
            val x = if (pointCount > 1) paddingLeft + i * stepX else width / 2f
            val ratio = (dataPoints[i].totalExpense / maxExpense).toFloat().coerceIn(0f, 1f)
            val y = paddingTop + chartHeight * (1f - ratio)

            pointsXY.add(Pair(x, y))

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + chartHeight)
                fillPath.lineTo(x, y)
            } else {
                val prevX = pointsXY[i - 1].first
                val prevY = pointsXY[i - 1].second
                val cx = (prevX + x) / 2f
                linePath.cubicTo(cx, prevY, cx, y, x, y)
                fillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
        }

        val lastX = pointsXY.last().first
        fillPath.lineTo(lastX, paddingTop + chartHeight)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            if (isNightMode) Color.parseColor("#33FF8A80") else Color.parseColor("#33E53935"),
            if (isNightMode) Color.parseColor("#00121212") else Color.parseColor("#00FFFFFF"),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        // Draw dots and text labels
        for (i in dataPoints.indices) {
            val (x, y) = pointsXY[i]
            val item = dataPoints[i]

            // Dot
            canvas.drawCircle(x, y, 11f, dotPaint)
            canvas.drawCircle(x, y, 5f, dotInnerPaint)

            // Amount Text above point
            val formattedAmt = formatCompactRupees(item.totalExpense)
            canvas.drawText(formattedAmt, x, y - 16f, amountPaint)

            // Stage Name below chart
            val activeLang = LocaleHelper.getPersistedLanguage(context)
            val displayStageName = TranslationHelper.getTranslatedStageName(item.stageName, activeLang, context)
            canvas.drawText(displayStageName, x, height - 16f, textPaint)
        }
    }

    private fun drawEmptyChart(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText("No Stage Expense Data Recorded Yet", centerX, centerY, textPaint)
    }

    private fun formatCompactRupees(amount: Double): String {
        val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        return NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 0
        }.format(amount)
    }
}
