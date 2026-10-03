package shop.sabjiwala.kisankendra.views

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class RunningBorderDrawable(
    private val cornerRadius: Float,
    private val strokeWidth: Float,
    private val backgroundColor: Int,
    private val gradientColors: IntArray = intArrayOf(
        Color.parseColor("#FFC107"),
        Color.parseColor("#FFD54F"),
        Color.parseColor("#FFF176"),
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#FFF176"),
        Color.parseColor("#FFD54F"),
        Color.parseColor("#FFC107")
    )
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val shaderMatrix = Matrix()
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            if (currentWidth > 0) {
                val translate = fraction * (currentWidth * 2f)
                shaderMatrix.setTranslate(translate, 0f)
                strokePaint.shader?.setLocalMatrix(shaderMatrix)
                invalidateSelf()
            }
        }
    }

    init {
        animator.start()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        if (w > 0 && h > 0 && (w != currentWidth || h != currentHeight)) {
            currentWidth = w
            currentHeight = h
            val positions = FloatArray(gradientColors.size) { i ->
                i.toFloat() / (gradientColors.size - 1).toFloat()
            }
            val gradient = LinearGradient(
                -w.toFloat(), 0f, w.toFloat(), 0f,
                gradientColors,
                positions,
                Shader.TileMode.CLAMP
            )
            strokePaint.shader = gradient
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val halfStroke = strokeWidth / 2f
        val rectF = RectF(
            halfStroke,
            halfStroke,
            bounds.width().toFloat() - halfStroke,
            bounds.height().toFloat() - halfStroke
        )
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun stop() {
        animator.cancel()
    }
}
