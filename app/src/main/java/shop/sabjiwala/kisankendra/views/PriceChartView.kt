package shop.sabjiwala.kisankendra.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import shop.sabjiwala.kisankendra.model.PriceDataPoint

class PriceChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<PriceDataPoint> = emptyList()
    var onPointSelectedListener: ((PriceDataPoint) -> Unit)? = null

    private var selectedIndex: Int = -1

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33888888")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun setDataPoints(points: List<PriceDataPoint>) {
        this.dataPoints = points
        this.selectedIndex = if (points.isNotEmpty()) points.lastIndex else -1
        invalidate()
        if (selectedIndex in points.indices) {
            onPointSelectedListener?.invoke(points[selectedIndex])
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val paddingLeft = 100f
        val paddingRight = 40f
        val paddingTop = 70f
        val paddingBottom = 60f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val minPrice = dataPoints.minOf { it.price }
        val maxPrice = dataPoints.maxOf { it.price }
        val priceRange = (maxPrice - minPrice).coerceAtLeast(1.0)

        val firstPrice = dataPoints.first().price
        val lastPrice = dataPoints.last().price
        val isUpTrend = lastPrice >= firstPrice

        val themeColor = if (isUpTrend) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        val gradientStart = if (isUpTrend) Color.parseColor("#4D2E7D32") else Color.parseColor("#4DC62828")
        val gradientEnd = if (isUpTrend) Color.parseColor("#002E7D32") else Color.parseColor("#00C62828")

        linePaint.color = themeColor
        dotPaint.color = themeColor
        tooltipBgPaint.color = themeColor

        // Y-Axis Grid Lines & Y-Axis Scale Text
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..3) {
            val y = paddingTop + (chartHeight / 3f) * i
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)

            val priceVal = maxPrice - (priceRange / 3f) * i
            canvas.drawText("₹${priceVal.toInt()}", paddingLeft - 12f, y + 8f, textPaint)
        }

        val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else 0f

        linePath.reset()
        fillPath.reset()

        val pointsXY = mutableListOf<Pair<Float, Float>>()

        for (i in dataPoints.indices) {
            val x = paddingLeft + i * stepX
            val price = dataPoints[i].price
            val normalizedPrice = (price - minPrice) / priceRange
            val y = paddingTop + chartHeight - (normalizedPrice * chartHeight).toFloat()
            pointsXY.add(Pair(x, y))

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + chartHeight)
                fillPath.lineTo(x, y)
            } else {
                val prevX = pointsXY[i - 1].first
                val prevY = pointsXY[i - 1].second
                val controlX1 = prevX + (x - prevX) / 2f
                val controlY1 = prevY
                val controlX2 = prevX + (x - prevX) / 2f
                val controlY2 = y

                linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
            }
        }

        if (pointsXY.isNotEmpty()) {
            val lastX = pointsXY.last().first
            fillPath.lineTo(lastX, paddingTop + chartHeight)
            fillPath.close()

            fillPaint.shader = LinearGradient(
                0f, paddingTop, 0f, paddingTop + chartHeight,
                gradientStart, gradientEnd,
                Shader.TileMode.CLAMP
            )

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)
        }

        // Draw dots and X-axis date labels
        textPaint.textAlign = Paint.Align.CENTER
        val stepLabel = (dataPoints.size / 5).coerceAtLeast(1)
        for (i in pointsXY.indices) {
            val (x, y) = pointsXY[i]

            if (i % stepLabel == 0 || i == pointsXY.lastIndex) {
                canvas.drawText(dataPoints[i].dateLabel, x, height - 10f, textPaint)
            }

            if (i == selectedIndex) {
                canvas.drawCircle(x, y, 14f, dotPaint)
                canvas.drawCircle(x, y, 8f, dotBorderPaint)

                // Tooltip Callout above selected point
                val tooltipText = "${dataPoints[i].dateLabel}: ₹${dataPoints[i].price.toInt()}"
                val textWidth = tooltipTextPaint.measureText(tooltipText)
                val tooltipRect = RectF(
                    (x - textWidth / 2f - 16f).coerceIn(10f, width - textWidth - 30f),
                    (y - 50f).coerceAtLeast(10f),
                    (x + textWidth / 2f + 16f).coerceIn(textWidth + 30f, width - 10f),
                    (y - 10f).coerceAtLeast(50f)
                )
                canvas.drawRoundRect(tooltipRect, 16f, 16f, tooltipBgPaint)
                canvas.drawText(tooltipText, tooltipRect.centerX(), tooltipRect.centerY() + 8f, tooltipTextPaint)
            } else if (i == 0 || i == pointsXY.lastIndex) {
                canvas.drawCircle(x, y, 10f, dotPaint)
                canvas.drawCircle(x, y, 5f, dotBorderPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dataPoints.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val paddingLeft = 100f
                val paddingRight = 40f
                val chartWidth = width - paddingLeft - paddingRight
                val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else 1f

                val touchX = (event.x - paddingLeft).coerceIn(0f, chartWidth)
                val index = Math.round(touchX / stepX).coerceIn(0, dataPoints.lastIndex)

                if (index != selectedIndex) {
                    selectedIndex = index
                    invalidate()
                    onPointSelectedListener?.invoke(dataPoints[index])
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
