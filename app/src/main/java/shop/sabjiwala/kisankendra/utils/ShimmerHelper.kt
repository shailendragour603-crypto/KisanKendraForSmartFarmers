package shop.sabjiwala.kisankendra.utils

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import shop.sabjiwala.kisankendra.views.ShimmerBackgroundDrawable
import java.util.WeakHashMap

object ShimmerHelper {

    private val originalBackgroundMap = WeakHashMap<View, Drawable?>()

    /**
     * Standardized pull-to-refresh color palette for the entire app.
     */
    fun setupSwipeRefresh(swipeRefreshLayout: SwipeRefreshLayout) {
        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#1B5E20"), // Green Dark
            Color.parseColor("#2E7D32"), // Green Primary
            Color.parseColor("#FF8F00")  // Harvest Gold
        )
    }

    /**
     * Applies an animated shimmer gradient background drawable to a view.
     */
    fun applyShimmer(
        view: View,
        cornerRadiusDp: Float = 12f,
        baseColor: Int = Color.parseColor("#E0E0E0")
    ) {
        if (!originalBackgroundMap.containsKey(view)) {
            originalBackgroundMap[view] = view.background
        }
        val density = view.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val shimmerDrawable = ShimmerBackgroundDrawable(radiusPx, baseColor)
        view.background = shimmerDrawable
        view.tag = shimmerDrawable
    }

    /**
     * Applies shimmer to a collection of views simultaneously.
     */
    fun applyShimmerToGroup(
        views: List<View>,
        cornerRadiusDp: Float = 12f,
        baseColor: Int = Color.parseColor("#E0E0E0")
    ) {
        views.forEach { view ->
            applyShimmer(view, cornerRadiusDp, baseColor)
        }
    }

    /**
     * Stops the shimmer animation and restores the original background.
     */
    fun stopShimmer(view: View) {
        (view.tag as? ShimmerBackgroundDrawable)?.stop()
        view.tag = null
        if (originalBackgroundMap.containsKey(view)) {
            view.background = originalBackgroundMap.remove(view)
        }
    }

    /**
     * Stops shimmer on a collection of views.
     */
    fun stopShimmerFromGroup(views: List<View>) {
        views.forEach { view ->
            stopShimmer(view)
        }
    }
}
