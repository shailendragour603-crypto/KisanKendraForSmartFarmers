package shop.sabjiwala.kisankendra.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.NumberFormat
import java.util.Locale

class FinancialDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var totalIncome: Double = 0.0
    private var totalExpenses: Double = 0.0
    private var netProfit: Double = 0.0

    private val rectF = RectF()

    private val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 38f
        color = if (isNightMode) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")
        strokeCap = Paint.Cap.ROUND
    }

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 38f
        color = if (isNightMode) Color.parseColor("#FF8A80") else Color.parseColor("#E53935")
        strokeCap = Paint.Cap.ROUND
    }

    private val emptyArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 38f
        color = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
    }

    private val centerAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.WHITE else Color.parseColor("#1B1B1B")
        textSize = 46f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNightMode) Color.parseColor("#B0B0B0") else Color.parseColor("#666666")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    fun setData(income: Double, expenses: Double, profit: Double) {
        this.totalIncome = income.coerceAtLeast(0.0)
        this.totalExpenses = expenses.coerceAtLeast(0.0)
        this.netProfit = profit.coerceAtLeast(0.0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        if (size <= 0) return

        val padding = 32f
        val diameter = size - (padding * 2)
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        rectF.set(left, top, left + diameter, top + diameter)

        val total = totalIncome.coerceAtLeast(totalExpenses + netProfit)

        if (total <= 0.0) {
            canvas.drawArc(rectF, 0f, 360f, false, emptyArcPaint)
            drawCenterText(canvas, "₹0", "Total Income")
            return
        }

        val expenseRatio = (totalExpenses / total).coerceIn(0.0, 1.0).toFloat()
        val expenseSweep = (expenseRatio * 360f).coerceIn(10f, 350f)
        val profitSweep = 360f - expenseSweep - 12f

        val startAngle = -90f

        if (profitSweep > 0) {
            canvas.drawArc(rectF, startAngle, profitSweep, false, incomePaint)
        }

        if (expenseSweep > 0) {
            val expenseStart = startAngle + profitSweep + 6f
            canvas.drawArc(rectF, expenseStart, expenseSweep, false, expensePaint)
        }

        val formattedAmount = formatCompactRupees(totalIncome)
        drawCenterText(canvas, formattedAmount, "Total Income")
    }

    private fun drawCenterText(canvas: Canvas, mainText: String, subText: String) {
        val centerX = width / 2f
        val centerY = height / 2f

        canvas.drawText(mainText, centerX, centerY - 6f, centerAmountPaint)
        canvas.drawText(subText, centerX, centerY + 32f, centerLabelPaint)
    }

    private fun formatCompactRupees(amount: Double): String {
        return when {
            amount >= 10_00_000 -> String.format(Locale.US, "₹%.2fCr", amount / 10_00_000)
            amount >= 100_000 -> String.format(Locale.US, "₹%.2fL", amount / 100_000)
            amount >= 1_000 -> String.format(Locale.US, "₹%.1fk", amount / 1000)
            else -> NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
                maximumFractionDigits = 0
            }.format(amount)
        }
    }
}