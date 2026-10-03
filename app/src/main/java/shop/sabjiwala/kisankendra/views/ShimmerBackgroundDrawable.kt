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

class ShimmerBackgroundDrawable(
    private val cornerRadius: Float,
    private val baseColor: Int,
    private val shimmerColor: Int = Color.parseColor("#55FFFFFF")
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = baseColor
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shaderMatrix = Matrix()
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            if (currentWidth > 0) {
                val translate = fraction * (currentWidth * 2.5f) - currentWidth * 0.5f
                shaderMatrix.setTranslate(translate, 0f)
                shimmerPaint.shader?.setLocalMatrix(shaderMatrix)
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
            val gradient = LinearGradient(
                -w.toFloat(), 0f, 0f, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    shimmerColor,
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            shimmerPaint.shader = gradient
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val rectF = RectF(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, shimmerPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        shimmerPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        shimmerPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun stop() {
        animator.cancel()
    }
}
