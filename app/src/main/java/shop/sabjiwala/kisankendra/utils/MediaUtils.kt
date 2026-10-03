package shop.sabjiwala.kisankendra.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import shop.sabjiwala.kisankendra.views.ShimmerBackgroundDrawable
import java.io.File
import java.io.FileOutputStream

object MediaUtils {

    /**
     * Safely copies a content Uri (such as Android Photo Picker or Gallery content Uri)
     * into a local application cache file immediately while permission is active.
     * Returns a Uri.fromFile(targetFile) pointing to the local cache file, which
     * avoids SecurityException when accessed later or passed across components.
     */
    fun copyUriToAppCache(context: Context, uri: Uri?): Uri? {
        if (uri == null) return null

        // If it's already a file Uri, check if file exists and return as is
        if (uri.scheme == "file") {
            return uri
        }

        return try {
            val contentResolver = context.contentResolver

            // Try to resolve MIME type to pick the correct file extension
            val mimeType = try {
                contentResolver.getType(uri)
            } catch (_: Exception) {
                null
            }

            val extension = getExtensionFromMimeOrUri(mimeType, uri)

            val cacheDir = File(context.cacheDir, "picked_media").apply {
                if (!exists()) mkdirs()
            }

            val fileName = "media_${System.currentTimeMillis()}_${(1000..9999).random()}$extension"
            val targetFile = File(cacheDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else {
                uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If copy fails due to SecurityException or other reasons, return original Uri or null safely
            uri
        }
    }

    /**
     * Checks if a Uri can be opened and accessed safely without throwing SecurityException.
     */
    fun isUriAccessible(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme == "file") {
            return try {
                val path = uri.path ?: return false
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun getExtensionFromMimeOrUri(mimeType: String?, uri: Uri): String {
        if (!mimeType.isNullOrBlank()) {
            val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!extFromMime.isNullOrBlank()) {
                return ".$extFromMime"
            }
            if (mimeType.startsWith("video/")) return ".mp4"
            if (mimeType.startsWith("image/")) return ".jpg"
        }

        val uriStr = uri.toString().lowercase()
        return when {
            uriStr.contains("video") || uriStr.endsWith(".mp4") -> ".mp4"
            uriStr.endsWith(".png") -> ".png"
            uriStr.endsWith(".gif") -> ".gif"
            else -> ".jpg"
        }
    }

    fun takeScreenshot(view: View, context: Context): File? {
        return try {
            val rootView = view.rootView
            val w = if (rootView.width > 0) rootView.width else 1080
            val h = if (rootView.height > 0) rootView.height else 1920
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)
            val file = File(context.cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun startPulseAnimation(view: View) {
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.02f, 1f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.02f, 1f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleXHolder, scaleYHolder).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        animator.start()
        view.tag = animator
    }

    fun startCompactGreenPulseAnimation(pulseRingView: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.8f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.8f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)

        val ringAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseRingView, scaleX, scaleY, alpha).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        ringAnimator.start()
        pulseRingView.tag = ringAnimator
    }

    fun applyShimmerAnimation(view: View, cornerRadiusDp: Float, baseColor: Int) {
        val density = view.resources.displayMetrics.density
        val radius = cornerRadiusDp * density
        val shimmerDrawable = ShimmerBackgroundDrawable(radius, baseColor)
        view.background = shimmerDrawable
        view.tag = shimmerDrawable
        if (view is MaterialCardView) {
            view.setCardBackgroundColor(Color.TRANSPARENT)
            view.strokeWidth = 0
        }
    }

    fun playLikeRippleAnimation(view: View) {
        val context = view.context ?: return
        val parent = view.parent as? ViewGroup ?: return

        val size = (view.width * 1.5f).toInt().coerceAtLeast(100)
        val ripple = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                color = ColorStateList.valueOf(Color.parseColor("#E53935"))
            }
            alpha = 0.8f
            scaleX = 0.2f
            scaleY = 0.2f
            x = view.x + (view.width - size) / 2f
            y = view.y + (view.height - size) / 2f
            translationZ = 10f
        }

        parent.addView(ripple)

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.2f, 1.8f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.2f, 1.8f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f)

        val anim = ObjectAnimator.ofPropertyValuesHolder(ripple, scaleX, scaleY, alpha).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
        }

        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                parent.removeView(ripple)
            }
        })
        anim.start()
    }

    fun takeFullScrollScreenshot(scrollView: ViewGroup, context: Context): File? {
        return try {
            val child = scrollView.getChildAt(0) ?: return takeScreenshot(scrollView, context)
            val width = if (scrollView.width > 0) scrollView.width else 1080

            // Temporarily replace RecyclerViews with LinearLayouts containing all item views so they render fully
            val replacements = mutableListOf<RecyclerViewReplacement>()
            replaceRecyclerViewsRecursively(child, width, replacements)

            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            child.measure(widthSpec, heightSpec)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)

            val bitmap = Bitmap.createBitmap(
                child.measuredWidth.coerceAtLeast(100),
                child.measuredHeight.coerceAtLeast(100),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#F5F5F5"))
            child.draw(canvas)

            // Restore original RecyclerViews
            for (rep in replacements) {
                val parent = rep.parent
                val index = parent.indexOfChild(rep.temporaryView)
                if (index >= 0) {
                    parent.removeViewAt(index)
                    parent.addView(rep.recyclerView, index, rep.layoutParams)
                }
            }

            val file = File(context.cacheDir, "full_screenshot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            takeScreenshot(scrollView, context)
        }
    }

    private data class RecyclerViewReplacement(
        val parent: ViewGroup,
        val recyclerView: RecyclerView,
        val temporaryView: View,
        val layoutParams: ViewGroup.LayoutParams
    )

    private fun replaceRecyclerViewsRecursively(view: View, parentWidth: Int, replacements: MutableList<RecyclerViewReplacement>) {
        if (view is ViewGroup) {
            if (view is RecyclerView) {
                val parent = view.parent as? ViewGroup ?: return
                val index = parent.indexOfChild(view)
                if (index < 0) return

                val adapter = view.adapter
                val layoutManager = view.layoutManager
                if (adapter == null || layoutManager == null || adapter.itemCount == 0) return

                val isHorizontal = (layoutManager as? LinearLayoutManager)?.orientation == LinearLayoutManager.HORIZONTAL
                val context = view.context

                val container = LinearLayout(context).apply {
                    orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    layoutParams = view.layoutParams
                    setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                    background = view.background
                }

                val itemCount = adapter.itemCount
                val widthSpec = if (isHorizontal) {
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                } else {
                    View.MeasureSpec.makeMeasureSpec(parentWidth.coerceAtLeast(100), View.MeasureSpec.EXACTLY)
                }
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                for (i in 0 until itemCount) {
                    try {
                        val holder = adapter.createViewHolder(container, adapter.getItemViewType(i))
                        adapter.bindViewHolder(holder, i)
                        val itemView = holder.itemView
                        itemView.measure(widthSpec, heightSpec)
                        container.addView(itemView)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                parent.removeViewAt(index)
                parent.addView(container, index, view.layoutParams)

                replacements.add(RecyclerViewReplacement(parent, view, container, view.layoutParams))
            } else {
                val children = mutableListOf<View>()
                for (i in 0 until view.childCount) {
                    children.add(view.getChildAt(i))
                }
                for (child in children) {
                    replaceRecyclerViewsRecursively(child, parentWidth, replacements)
                }
            }
        }
    }
}
