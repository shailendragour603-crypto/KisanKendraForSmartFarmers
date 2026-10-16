package shop.sabjiwala.kisankendra

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.yalantis.ucrop.UCrop
import ja.burhanrashid52.photoeditor.OnPhotoEditorListener
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.TextStyleBuilder
import ja.burhanrashid52.photoeditor.ViewType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var photoEditor: PhotoEditor
    private lateinit var panelSubToolsContainer: FrameLayout
    private var sourceUri: Uri? = null

    private var currentBitmap: Bitmap? = null

    // Available Preset Colors
    private val colorPalette = intArrayOf(
        Color.WHITE, Color.BLACK, Color.parseColor("#E53935"), Color.parseColor("#43A047"),
        Color.parseColor("#1E88E5"), Color.parseColor("#FDD835"), Color.parseColor("#FB8C00"),
        Color.parseColor("#8E24AA"), Color.parseColor("#D81B60"), Color.parseColor("#00ACC1"),
        Color.parseColor("#7CB342"), Color.parseColor("#424242")
    )

    // Available Text Background Colors (Including None/Transparent)
    private val bgPalette = intArrayOf(
        Color.TRANSPARENT, Color.parseColor("#CC000000"), Color.parseColor("#CCFFFFFF"),
        Color.parseColor("#CCE53935"), Color.parseColor("#CC43A047"), Color.parseColor("#CC1E88E5"),
        Color.parseColor("#CCFDD835")
    )

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        sourceUri = intent.getParcelableExtra(EXTRA_IMAGE_URI)
        if (sourceUri == null) {
            Toast.makeText(this, "Image unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        photoEditorView = findViewById(R.id.photoEditorView)
        panelSubToolsContainer = findViewById(R.id.panelSubToolsContainer)

        setupWindowInsets()
        loadInitialImage(sourceUri!!)

        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()

        setupToolbarAndListeners()
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(R.id.rootImageEditor)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            findViewById<Toolbar>(R.id.toolbarEditor).setPadding(0, statusBarInsets.top, 0, 0)
            findViewById<View>(R.id.toolsScrollView).setPadding(0, 0, 0, navBarInsets.bottom)

            insets
        }
    }

    private fun loadInitialImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            currentBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (currentBitmap != null) {
                photoEditorView.source.setImageBitmap(currentBitmap)
            } else {
                photoEditorView.source.setImageURI(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            photoEditorView.source.setImageURI(uri)
        }
    }

    private fun setupToolbarAndListeners() {
        findViewById<Toolbar>(R.id.toolbarEditor).setNavigationOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener {
            photoEditor.undo()
        }

        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener {
            photoEditor.redo()
        }

        findViewById<ImageButton>(R.id.btnReset).setOnClickListener {
            confirmReset()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveImage()
        }

        // Tools Bar Items (Show Inline Sub-Panels so Image Canvas stays 100% visible)
        findViewById<View>(R.id.btnText).setOnClickListener {
            showAddTextPanel(null, null)
        }

        findViewById<View>(R.id.btnCropRotate).setOnClickListener {
            showCropRotatePanel()
        }

        findViewById<View>(R.id.btnAdjust).setOnClickListener {
            showAdjustmentsPanel()
        }

        findViewById<View>(R.id.btnBrush).setOnClickListener {
            showBrushSettingsPanel()
        }

        findViewById<View>(R.id.btnSticker).setOnClickListener {
            showEmojiPickerPanel()
        }

        // PhotoEditor Listener for editing existing text
        photoEditor.setOnPhotoEditorListener(object : OnPhotoEditorListener {
            override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
                if (text.isNotEmpty()) {
                    showAddTextPanel(rootView, text)
                }
            }

            override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {}
            override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {}
            override fun onStartViewChangeListener(viewType: ViewType) {}
            override fun onStopViewChangeListener(viewType: ViewType) {}
            override fun onTouchSourceImage(event: MotionEvent) {}
        })
    }

    private fun closeSubPanel() {
        panelSubToolsContainer.removeAllViews()
        panelSubToolsContainer.visibility = View.GONE
    }

    // --- 1. INLINE STYLED TEXT PANEL ---
    private fun showAddTextPanel(existingView: View?, existingText: String?) {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_add_text_styled, panelSubToolsContainer, false)
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val etInputText = panelView.findViewById<EditText>(R.id.etInputText)
        val layoutTextColors = panelView.findViewById<LinearLayout>(R.id.layoutTextColors)
        val layoutBgColors = panelView.findViewById<LinearLayout>(R.id.layoutBgColors)
        val seekBarTextSize = panelView.findViewById<SeekBar>(R.id.seekBarTextSize)
        val tvTextSizeVal = panelView.findViewById<TextView>(R.id.tvTextSizeVal)
        val btnCancel = panelView.findViewById<Button>(R.id.btnTextCancel)
        val btnApply = panelView.findViewById<Button>(R.id.btnTextApply)

        if (!existingText.isNullOrEmpty()) {
            etInputText.setText(existingText)
            etInputText.setSelection(existingText.length)
        }

        var selectedTextColor = Color.WHITE
        var selectedBgColor = Color.TRANSPARENT
        var selectedTypeface: Typeface = Typeface.DEFAULT
        var selectedSizeSp = 32f

        seekBarTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 12) 12 else progress
                selectedSizeSp = size.toFloat()
                tvTextSizeVal.text = size.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        populateColorPalette(layoutTextColors, colorPalette) { color ->
            selectedTextColor = color
            etInputText.setTextColor(color)
        }

        populateColorPalette(layoutBgColors, bgPalette) { color ->
            selectedBgColor = color
            if (color == Color.TRANSPARENT) {
                etInputText.setBackgroundColor(Color.parseColor("#333333"))
            } else {
                etInputText.setBackgroundColor(color)
            }
        }

        btnCancel.setOnClickListener { closeSubPanel() }

        btnApply.setOnClickListener {
            val text = etInputText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedTypeface = when {
                panelView.findViewById<Chip>(R.id.chipFontSerif)?.isChecked == true -> Typeface.SERIF
                panelView.findViewById<Chip>(R.id.chipFontSans)?.isChecked == true -> Typeface.SANS_SERIF
                panelView.findViewById<Chip>(R.id.chipFontMono)?.isChecked == true -> Typeface.MONOSPACE
                panelView.findViewById<Chip>(R.id.chipFontBold)?.isChecked == true -> Typeface.DEFAULT_BOLD
                panelView.findViewById<Chip>(R.id.chipFontItalic)?.isChecked == true -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }

            val styleBuilder = TextStyleBuilder().apply {
                withTextColor(selectedTextColor)
                withTextSize(selectedSizeSp)
                withTextFont(selectedTypeface)
                if (selectedBgColor != Color.TRANSPARENT) {
                    withBackgroundColor(selectedBgColor)
                }
            }

            if (existingView != null) {
                photoEditor.editText(existingView, text, styleBuilder)
            } else {
                photoEditor.addText(text, styleBuilder)
            }

            closeSubPanel()
        }
    }

    private fun populateColorPalette(
        parentLayout: LinearLayout,
        colors: IntArray,
        onColorSelected: (Int) -> Unit
    ) {
        parentLayout.removeAllViews()
        for (color in colors) {
            val colorView = View(this).apply {
                val params = LinearLayout.LayoutParams(90, 90).apply {
                    setMargins(10, 8, 10, 8)
                }
                layoutParams = params
                background = createCircleDrawable(color)
                setOnClickListener {
                    onColorSelected(color)
                }
            }
            parentLayout.addView(colorView)
        }
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(3, if (color == Color.WHITE || color == Color.TRANSPARENT) Color.GRAY else Color.WHITE)
        }
    }

    // --- 2. CROP & ROTATE PANEL ---
    private fun showCropRotatePanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_image_crop, panelSubToolsContainer, false)
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val chipGroupImageRatio = panelView.findViewById<ChipGroup>(R.id.chipGroupImageRatio)
        val btnRotateRight = panelView.findViewById<Button>(R.id.btnRotateRight)
        val btnFlipHorizontal = panelView.findViewById<Button>(R.id.btnFlipHorizontal)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneCrop)

        chipGroupImageRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: R.id.chipRatioFree
            when (id) {
                R.id.chipRatio11 -> startCrop(sourceUri, 1f, 1f)
                R.id.chipRatio45 -> startCrop(sourceUri, 4f, 5f)
                R.id.chipRatio916 -> startCrop(sourceUri, 9f, 16f)
                R.id.chipRatio169 -> startCrop(sourceUri, 16f, 9f)
                else -> startCrop(sourceUri, 0f, 0f)
            }
        }

        btnRotateRight.setOnClickListener { rotateBitmap(90f) }
        btnFlipHorizontal.setOnClickListener { flipBitmapHorizontal() }
        btnDone.setOnClickListener { closeSubPanel() }
    }

    private fun rotateBitmap(degrees: Float) {
        if (currentBitmap == null) return
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(currentBitmap!!, 0, 0, currentBitmap!!.width, currentBitmap!!.height, matrix, true)
        currentBitmap = rotated
        photoEditorView.source.setImageBitmap(currentBitmap)
        saveTempBitmapToFile(currentBitmap!!, "rotated_temp.jpg")?.let {
            sourceUri = Uri.fromFile(it)
        }
    }

    private fun flipBitmapHorizontal() {
        if (currentBitmap == null) return
        val matrix = Matrix().apply { postScale(-1f, 1f, currentBitmap!!.width / 2f, currentBitmap!!.height / 2f) }
        val flipped = Bitmap.createBitmap(currentBitmap!!, 0, 0, currentBitmap!!.width, currentBitmap!!.height, matrix, true)
        currentBitmap = flipped
        photoEditorView.source.setImageBitmap(currentBitmap)
        saveTempBitmapToFile(currentBitmap!!, "flipped_temp.jpg")?.let {
            sourceUri = Uri.fromFile(it)
        }
    }

    private fun startCrop(uri: Uri?, aspectX: Float = 0f, aspectY: Float = 0f) {
        if (uri == null) return
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${UUID.randomUUID()}.jpg"))
        val ucropOptions = UCrop.Options().apply {
            setToolbarColor(ContextCompat.getColor(this@ImageEditorActivity, R.color.farmer_green_primary))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@ImageEditorActivity, R.color.farmer_green_primary))
            setToolbarTitle("Crop Image")
            setAspectRatioOptions(0,
                com.yalantis.ucrop.model.AspectRatio("1:1", 1f, 1f),
                com.yalantis.ucrop.model.AspectRatio("4:5", 4f, 5f),
                com.yalantis.ucrop.model.AspectRatio("9:16", 9f, 16f),
                com.yalantis.ucrop.model.AspectRatio("16:9", 16f, 9f)
            )
        }

        val ucrop = UCrop.of(uri, destinationUri).withOptions(ucropOptions)
        if (aspectX > 0f && aspectY > 0f) {
            ucrop.withAspectRatio(aspectX, aspectY)
        }
        ucrop.start(this)
    }

    // --- 3. ADJUSTMENTS & PRESET FILTERS PANEL ---
    private fun showAdjustmentsPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_image_adjustments, panelSubToolsContainer, false)
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val seekBarBrightness = panelView.findViewById<SeekBar>(R.id.seekBarBrightness)
        val tvBrightnessVal = panelView.findViewById<TextView>(R.id.tvBrightnessVal)
        val chipGroupFilters = panelView.findViewById<ChipGroup>(R.id.chipGroupFilters)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneAdjustments)

        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val brightnessVal = progress - 100
                tvBrightnessVal.text = brightnessVal.toString()

                if (brightnessVal == 0) {
                    photoEditorView.source.colorFilter = null
                } else {
                    val colorMatrix = android.graphics.ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, brightnessVal.toFloat(),
                        0f, 1f, 0f, 0f, brightnessVal.toFloat(),
                        0f, 0f, 1f, 0f, brightnessVal.toFloat(),
                        0f, 0f, 0f, 1f, 0f
                    ))
                    photoEditorView.source.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            when (checkedId) {
                R.id.chipFilterNone -> photoEditor.setFilterEffect(PhotoFilter.NONE)
                R.id.chipFilterAutoFix -> photoEditor.setFilterEffect(PhotoFilter.AUTO_FIX)
                R.id.chipFilterBright -> photoEditor.setFilterEffect(PhotoFilter.BRIGHTNESS)
                R.id.chipFilterGrayscale -> photoEditor.setFilterEffect(PhotoFilter.GRAY_SCALE)
                R.id.chipFilterSepia -> photoEditor.setFilterEffect(PhotoFilter.SEPIA)
                R.id.chipFilterVignette -> photoEditor.setFilterEffect(PhotoFilter.VIGNETTE)
            }
        }

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 4. BRUSH & ERASER SETTINGS PANEL ---
    private fun showBrushSettingsPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_brush_settings, panelSubToolsContainer, false)
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val btnModeDraw = panelView.findViewById<Button>(R.id.btnModeDraw)
        val btnModeEraser = panelView.findViewById<Button>(R.id.btnModeEraser)
        val layoutBrushColors = panelView.findViewById<LinearLayout>(R.id.layoutBrushColors)
        val seekBarBrushSize = panelView.findViewById<SeekBar>(R.id.seekBarBrushSize)
        val tvBrushSizeVal = panelView.findViewById<TextView>(R.id.tvBrushSizeVal)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneBrush)

        var isEraser = false
        var selectedBrushColor = Color.RED
        var selectedBrushSize = 25f

        photoEditor.setBrushDrawingMode(true)
        photoEditor.brushColor = selectedBrushColor
        photoEditor.brushSize = selectedBrushSize

        populateColorPalette(layoutBrushColors, colorPalette) { color ->
            selectedBrushColor = color
            if (!isEraser) {
                photoEditor.brushColor = color
            }
        }

        btnModeDraw.setOnClickListener {
            isEraser = false
            photoEditor.setBrushDrawingMode(true)
            photoEditor.brushColor = selectedBrushColor
            btnModeDraw.setBackgroundColor(ContextCompat.getColor(this, R.color.farmer_green_primary))
            btnModeEraser.setBackgroundColor(Color.TRANSPARENT)
        }

        btnModeEraser.setOnClickListener {
            isEraser = true
            photoEditor.brushEraser()
            btnModeEraser.setBackgroundColor(Color.RED)
            btnModeDraw.setBackgroundColor(Color.TRANSPARENT)
        }

        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 5) 5 else progress
                selectedBrushSize = size.toFloat()
                tvBrushSizeVal.text = size.toString()
                if (isEraser) {
                    photoEditor.setBrushEraserSize(selectedBrushSize)
                } else {
                    photoEditor.brushSize = selectedBrushSize
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 5. EMOJI & STICKER PICKER PANEL ---
    private fun showEmojiPickerPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_emoji_picker, panelSubToolsContainer, false)
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val rvEmojis = panelView.findViewById<RecyclerView>(R.id.rvEmojis)
        val btnClose = panelView.findViewById<Button>(R.id.btnCloseEmoji)

        val agriculturalEmojis = listOf(
            "🌾", "🚜", "🌽", "🍎", "🥦", "🥕", "🥑", "🌶️", "🍋", "🍇",
            "🥭", "🥔", "🥬", "🍅", "🍓", "🪴", "☀️", "🌧️", "💧", "⚡",
            "🐮", "🐓", "🐝", "📍", "👍", "❤️", "⭐", "💡", "🔥", "💯", "😊", "🌱"
        )

        rvEmojis.layoutManager = GridLayoutManager(this, 6)
        rvEmojis.adapter = object : RecyclerView.Adapter<EmojiViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
                val tv = TextView(parent.context).apply {
                    textSize = 26f
                    setPadding(12, 12, 12, 12)
                    gravity = android.view.Gravity.CENTER
                }
                return EmojiViewHolder(tv)
            }

            override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
                val emoji = agriculturalEmojis[position]
                holder.textView.text = emoji
                holder.textView.setOnClickListener {
                    photoEditor.addEmoji(emoji)
                }
            }

            override fun getItemCount(): Int = agriculturalEmojis.size
        }

        btnClose.setOnClickListener { closeSubPanel() }
    }

    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    // --- RESET / CLEAR ---
    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Edits")
            .setMessage("Are you sure you want to clear all texts, drawings, and stickers?")
            .setPositiveButton("Reset") { _, _ ->
                photoEditor.clearAllViews()
                closeSubPanel()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- SAVE IMAGE ---
    private fun saveImage() {
        val file = File(cacheDir, "edited_${UUID.randomUUID()}.jpg")
        try {
            photoEditor.saveAsFile(file.absolutePath, object : PhotoEditor.OnSaveListener {
                override fun onSuccess(imagePath: String) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_RESULT_URI, Uri.fromFile(File(imagePath)))
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    Toast.makeText(this@ImageEditorActivity, "Image saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onFailure(exception: Exception) {
                    Toast.makeText(this@ImageEditorActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                    exception.printStackTrace()
                }
            })
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "Permission error saving image", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTempBitmapToFile(bitmap: Bitmap, filename: String): File? {
        return try {
            val file = File(cacheDir, filename)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            fos.flush()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP && data != null) {
            val resultUri = UCrop.getOutput(data)
            if (resultUri != null) {
                sourceUri = resultUri
                loadInitialImage(resultUri)
            }
        } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
            val cropError = UCrop.getError(data)
            cropError?.printStackTrace()
        }
    }
}
