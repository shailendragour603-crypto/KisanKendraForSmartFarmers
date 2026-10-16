package shop.sabjiwala.kisankendra

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.google.android.material.chip.ChipGroup
import java.io.File
import java.util.Stack
import java.util.UUID

data class VideoEditState(
    val rotation: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val filterType: Int = FILTER_NONE,
    val aspectRatioRatio: Float = RATIO_ORIGINAL,
    val speed: Float = 1.0f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val selectedAudioUri: Uri? = null,
    val selectedAudioName: String? = null,
    val isExtractedFromVideo: Boolean = false,
    val isMuted: Boolean = false,
    val audioVolume: Float = 1.0f,
    val audioStartMs: Long = 0L
) {
    companion object {
        const val FILTER_NONE = 0
        const val FILTER_VIVID = 1
        const val FILTER_BW = 2
        const val FILTER_WARM = 3
        const val FILTER_INVERT = 4

        const val RATIO_ORIGINAL = 0f
        const val RATIO_1_1 = 1.0f
        const val RATIO_4_5 = 0.8f
        const val RATIO_9_16 = 9f / 16f
        const val RATIO_16_9 = 16f / 9f
    }
}

@OptIn(UnstableApi::class)
class VideoEditorActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private var sourceUri: Uri? = null

    private var videoDurationMs: Long = 0L
    private var audioDurationMs: Long = 0L

    // Dynamic Sub-Panel Container
    private lateinit var panelSubToolsContainer: FrameLayout

    // Undo / Redo History Stack
    private val undoStack = Stack<VideoEditState>()
    private val redoStack = Stack<VideoEditState>()
    private var currentState = VideoEditState()

    // Top Toolbar & Common UI Elements
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnReset: ImageButton
    private lateinit var btnExport: Button
    private lateinit var layoutExportProgress: LinearLayout
    private lateinit var tvExportStatus: TextView
    private lateinit var progressBarExport: ProgressBar

    private var transformer: Transformer? = null

    // Speed values mapping for Line View SeekBar (0..7)
    private val speedValues = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    // Activity Result Launcher for selecting Audio/Music file
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            saveStateToUndo()
            currentState = currentState.copy(
                selectedAudioUri = uri,
                selectedAudioName = getFileNameFromUri(uri, false),
                isExtractedFromVideo = false,
                audioStartMs = 0L
            )
            fetchAudioDuration(uri)
            refreshActiveSubPanel()
            setupPlayer()
        }
    }

    // Activity Result Launcher for selecting Video file to extract audio from
    private val videoPickerForAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            saveStateToUndo()
            currentState = currentState.copy(
                selectedAudioUri = uri,
                selectedAudioName = getFileNameFromUri(uri, true),
                isExtractedFromVideo = true,
                audioStartMs = 0L
            )
            fetchAudioDuration(uri)
            refreshActiveSubPanel()
            setupPlayer()
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        sourceUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)
        if (sourceUri == null) {
            Toast.makeText(this, "Video unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWindowInsets()
        initViews()
        setupListeners()
        fetchVideoDurationAndSetup()
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(R.id.rootVideoEditor)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            findViewById<Toolbar>(R.id.toolbarEditor).setPadding(0, statusBarInsets.top, 0, 0)
            findViewById<View>(R.id.toolsScrollView).setPadding(0, 0, 0, navBarInsets.bottom)

            insets
        }
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        panelSubToolsContainer = findViewById(R.id.panelSubToolsContainer)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnReset = findViewById(R.id.btnReset)
        btnExport = findViewById(R.id.btnExport)
        layoutExportProgress = findViewById(R.id.layoutExportProgress)
        tvExportStatus = findViewById(R.id.tvExportStatus)
        progressBarExport = findViewById(R.id.progressBarExport)

        updateUndoRedoButtons()
    }

    private fun setupListeners() {
        findViewById<Toolbar>(R.id.toolbarEditor).setNavigationOnClickListener {
            finish()
        }

        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }
        btnReset.setOnClickListener { resetAll() }

        // Horizontal Tools Bar Buttons
        findViewById<View>(R.id.btnCropRatio).setOnClickListener {
            showCropRatioPanel()
        }

        findViewById<View>(R.id.btnAudio).setOnClickListener {
            showAudioPanel()
        }

        findViewById<View>(R.id.btnSpeed).setOnClickListener {
            showSpeedPanel()
        }

        findViewById<View>(R.id.btnTrim).setOnClickListener {
            showTrimPanel()
        }

        findViewById<View>(R.id.btnRotate).setOnClickListener {
            showRotatePanel()
        }

        findViewById<View>(R.id.btnAdjust).setOnClickListener {
            showAdjustPanel()
        }

        btnExport.setOnClickListener {
            exportVideo()
        }
    }

    private fun closeSubPanel() {
        panelSubToolsContainer.removeAllViews()
        panelSubToolsContainer.visibility = View.GONE
    }

    private fun refreshActiveSubPanel() {
        if (panelSubToolsContainer.childCount > 0) {
            val currentView = panelSubToolsContainer.getChildAt(0)
            val tag = currentView.tag as? String
            when (tag) {
                "panel_crop" -> showCropRatioPanel()
                "panel_audio" -> showAudioPanel()
                "panel_speed" -> showSpeedPanel()
                "panel_trim" -> showTrimPanel()
                "panel_rotate" -> showRotatePanel()
                "panel_adjust" -> showAdjustPanel()
            }
        }
    }

    // --- 0. CROP & ASPECT RATIO PANEL ---
    private fun showCropRatioPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_crop, panelSubToolsContainer, false)
        panelView.tag = "panel_crop"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val chipGroupVideoRatio = panelView.findViewById<ChipGroup>(R.id.chipGroupVideoRatio)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneVideoCrop)

        when (currentState.aspectRatioRatio) {
            VideoEditState.RATIO_1_1 -> chipGroupVideoRatio.check(R.id.chipVideoRatio11)
            VideoEditState.RATIO_4_5 -> chipGroupVideoRatio.check(R.id.chipVideoRatio45)
            VideoEditState.RATIO_9_16 -> chipGroupVideoRatio.check(R.id.chipVideoRatio916)
            VideoEditState.RATIO_16_9 -> chipGroupVideoRatio.check(R.id.chipVideoRatio169)
            else -> chipGroupVideoRatio.check(R.id.chipVideoRatioOriginal)
        }

        chipGroupVideoRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: R.id.chipVideoRatioOriginal
            val newRatio = when (id) {
                R.id.chipVideoRatio11 -> VideoEditState.RATIO_1_1
                R.id.chipVideoRatio45 -> VideoEditState.RATIO_4_5
                R.id.chipVideoRatio916 -> VideoEditState.RATIO_9_16
                R.id.chipVideoRatio169 -> VideoEditState.RATIO_16_9
                else -> VideoEditState.RATIO_ORIGINAL
            }
            if (newRatio != currentState.aspectRatioRatio) {
                saveStateToUndo()
                currentState = currentState.copy(aspectRatioRatio = newRatio)
                applyVideoEffects()
            }
        }

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 1. AUDIO & MUSIC PANEL ---
    private fun showAudioPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_audio, panelSubToolsContainer, false)
        panelView.tag = "panel_audio"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val btnAddAudio = panelView.findViewById<Button>(R.id.btnAddAudio)
        val btnExtractAudio = panelView.findViewById<Button>(R.id.btnExtractAudio)
        val layoutSelectedAudio = panelView.findViewById<LinearLayout>(R.id.layoutSelectedAudio)
        val tvAudioName = panelView.findViewById<TextView>(R.id.tvAudioName)
        val btnRemoveAudio = panelView.findViewById<ImageButton>(R.id.btnRemoveAudio)

        val layoutAudioControls = panelView.findViewById<LinearLayout>(R.id.layoutAudioControls)
        val seekBarAudioVolume = panelView.findViewById<SeekBar>(R.id.seekBarAudioVolume)
        val tvAudioVolumeVal = panelView.findViewById<TextView>(R.id.tvAudioVolumeVal)
        val seekBarAudioStart = panelView.findViewById<SeekBar>(R.id.seekBarAudioStart)
        val tvAudioStartVal = panelView.findViewById<TextView>(R.id.tvAudioStartVal)

        val cbMuteOriginalAudio = panelView.findViewById<CheckBox>(R.id.cbMuteOriginalAudio)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneAudio)

        // Bind State
        if (currentState.selectedAudioUri != null) {
            layoutSelectedAudio.visibility = View.VISIBLE
            layoutAudioControls.visibility = View.VISIBLE
            val prefix = if (currentState.isExtractedFromVideo) "🎬 Extracted Audio: " else "🎵 Music: "
            tvAudioName.text = prefix + (currentState.selectedAudioName ?: "Selected Track")

            // Volume
            val volProg = (currentState.audioVolume * 100f).toInt()
            seekBarAudioVolume.progress = volProg
            tvAudioVolumeVal.text = "${volProg}%"

            // Start Seek Offset
            val effectiveMaxMs = if (audioDurationMs > 0) audioDurationMs else videoDurationMs
            if (effectiveMaxMs > 0) {
                val startProg = (currentState.audioStartMs.toFloat() / effectiveMaxMs * 100f).toInt()
                seekBarAudioStart.progress = startProg
                tvAudioStartVal.text = formatMsToTime(currentState.audioStartMs)
            }
        } else {
            layoutSelectedAudio.visibility = View.GONE
            layoutAudioControls.visibility = View.GONE
        }

        cbMuteOriginalAudio.isChecked = currentState.isMuted

        btnAddAudio.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "audio/*", "audio/mpeg", "audio/mp3", "audio/x-wav", "audio/wav",
                    "audio/ogg", "audio/aac", "audio/m4a", "audio/x-m4a"
                ))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            audioPickerLauncher.launch(Intent.createChooser(intent, "Select MP3 / Audio File"))
        }

        btnExtractAudio.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "video/*", "video/mp4", "video/mkv", "video/3gpp", "video/webm"
                ))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            videoPickerForAudioLauncher.launch(Intent.createChooser(intent, "Select Video to Extract Audio"))
        }

        btnRemoveAudio.setOnClickListener {
            saveStateToUndo()
            currentState = currentState.copy(
                selectedAudioUri = null,
                selectedAudioName = null,
                isExtractedFromVideo = false,
                audioVolume = 1.0f,
                audioStartMs = 0L
            )
            layoutSelectedAudio.visibility = View.GONE
            layoutAudioControls.visibility = View.GONE
            setupPlayer()
        }

        seekBarAudioVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100f
                tvAudioVolumeVal.text = "${progress}%"
                currentState = currentState.copy(audioVolume = volume)
                if (currentState.selectedAudioUri != null) {
                    exoPlayer?.volume = volume
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarAudioStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val effectiveMaxMs = if (audioDurationMs > 0) audioDurationMs else videoDurationMs
                if (effectiveMaxMs > 0) {
                    val startMs = (progress / 100f * effectiveMaxMs).toLong()
                    currentState = currentState.copy(audioStartMs = startMs)
                    tvAudioStartVal.text = formatMsToTime(startMs)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { setupPlayer() }
        })

        cbMuteOriginalAudio.setOnCheckedChangeListener { _, isChecked ->
            saveStateToUndo()
            currentState = currentState.copy(isMuted = isChecked)
            setupPlayer()
        }

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 2. SPEED PANEL (INLINE LINE VIEW SLIDER) ---
    private fun showSpeedPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_speed, panelSubToolsContainer, false)
        panelView.tag = "panel_speed"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val seekBarSpeed = panelView.findViewById<SeekBar>(R.id.seekBarSpeed)
        val tvSpeedVal = panelView.findViewById<TextView>(R.id.tvSpeedVal)
        val btnSpeed05 = panelView.findViewById<Button>(R.id.btnSpeed05)
        val btnSpeed10 = panelView.findViewById<Button>(R.id.btnSpeed10)
        val btnSpeed15 = panelView.findViewById<Button>(R.id.btnSpeed15)
        val btnSpeed20 = panelView.findViewById<Button>(R.id.btnSpeed20)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneSpeed)

        fun updateSpeedUI(speed: Float) {
            var index = 3 // default 1.0x
            var minDiff = Float.MAX_VALUE
            for (i in speedValues.indices) {
                val diff = Math.abs(speedValues[i] - speed)
                if (diff < minDiff) {
                    minDiff = diff
                    index = i
                }
            }
            seekBarSpeed.progress = index
            val label = if (speed == 1.0f) "1.0x (Normal)" else String.format(java.util.Locale.US, "%.2fx", speed)
            tvSpeedVal.text = label
        }

        updateSpeedUI(currentState.speed)

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress in speedValues.indices) {
                    val speed = speedValues[progress]
                    val label = if (speed == 1.0f) "1.0x (Normal)" else String.format(java.util.Locale.US, "%.2fx", speed)
                    tvSpeedVal.text = label
                    if (speed != currentState.speed) {
                        currentState = currentState.copy(speed = speed)
                        exoPlayer?.playbackParameters = PlaybackParameters(speed)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSpeed05.setOnClickListener {
            saveStateToUndo()
            currentState = currentState.copy(speed = 0.5f)
            updateSpeedUI(0.5f)
            exoPlayer?.playbackParameters = PlaybackParameters(0.5f)
        }

        btnSpeed10.setOnClickListener {
            saveStateToUndo()
            currentState = currentState.copy(speed = 1.0f)
            updateSpeedUI(1.0f)
            exoPlayer?.playbackParameters = PlaybackParameters(1.0f)
        }

        btnSpeed15.setOnClickListener {
            saveStateToUndo()
            currentState = currentState.copy(speed = 1.5f)
            updateSpeedUI(1.5f)
            exoPlayer?.playbackParameters = PlaybackParameters(1.5f)
        }

        btnSpeed20.setOnClickListener {
            saveStateToUndo()
            currentState = currentState.copy(speed = 2.0f)
            updateSpeedUI(2.0f)
            exoPlayer?.playbackParameters = PlaybackParameters(2.0f)
        }

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 3. TRIM PANEL ---
    private fun showTrimPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_trim, panelSubToolsContainer, false)
        panelView.tag = "panel_trim"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val seekBarTrimStart = panelView.findViewById<SeekBar>(R.id.seekBarTrimStart)
        val tvTrimStartVal = panelView.findViewById<TextView>(R.id.tvTrimStartVal)
        val seekBarTrimEnd = panelView.findViewById<SeekBar>(R.id.seekBarTrimEnd)
        val tvTrimEndVal = panelView.findViewById<TextView>(R.id.tvTrimEndVal)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneTrim)

        if (videoDurationMs > 0) {
            val startProg = (currentState.trimStartMs.toFloat() / videoDurationMs * 100).toInt()
            val endProg = (currentState.trimEndMs.toFloat() / videoDurationMs * 100).toInt()
            seekBarTrimStart.progress = startProg
            seekBarTrimEnd.progress = endProg
            tvTrimStartVal.text = formatMsToTime(currentState.trimStartMs)
            tvTrimEndVal.text = formatMsToTime(currentState.trimEndMs)
        }

        seekBarTrimStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (videoDurationMs > 0) {
                    val startMs = (progress / 100f * videoDurationMs).toLong()
                    if (startMs < currentState.trimEndMs || currentState.trimEndMs == 0L) {
                        currentState = currentState.copy(trimStartMs = startMs)
                        tvTrimStartVal.text = formatMsToTime(startMs)
                        if (fromUser) {
                            exoPlayer?.seekTo(startMs)
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarTrimEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (videoDurationMs > 0) {
                    val endMs = (progress / 100f * videoDurationMs).toLong()
                    if (endMs > currentState.trimStartMs) {
                        currentState = currentState.copy(trimEndMs = endMs)
                        tvTrimEndVal.text = formatMsToTime(endMs)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 4. ROTATE PANEL ---
    private fun showRotatePanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_rotate, panelSubToolsContainer, false)
        panelView.tag = "panel_rotate"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val btnRotateVideo = panelView.findViewById<Button>(R.id.btnRotateVideo)
        val tvRotationStatus = panelView.findViewById<TextView>(R.id.tvRotationStatus)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneRotate)

        tvRotationStatus.text = "${currentState.rotation}° Clockwise"

        btnRotateVideo.setOnClickListener {
            saveStateToUndo()
            val newRotation = (currentState.rotation + 90) % 360
            currentState = currentState.copy(rotation = newRotation)
            tvRotationStatus.text = "${newRotation}° Clockwise"
            applyVideoEffects()
        }

        btnDone.setOnClickListener { closeSubPanel() }
    }

    // --- 5. ADJUSTMENTS & FILTERS PANEL ---
    private fun showAdjustPanel() {
        panelSubToolsContainer.removeAllViews()
        val panelView = LayoutInflater.from(this).inflate(R.layout.dialog_video_adjust, panelSubToolsContainer, false)
        panelView.tag = "panel_adjust"
        panelSubToolsContainer.addView(panelView)
        panelSubToolsContainer.visibility = View.VISIBLE

        val chipGroupVideoFilters = panelView.findViewById<ChipGroup>(R.id.chipGroupVideoFilters)
        val seekBarBrightness = panelView.findViewById<SeekBar>(R.id.seekBarBrightness)
        val tvBrightnessVal = panelView.findViewById<TextView>(R.id.tvBrightnessVal)
        val seekBarContrast = panelView.findViewById<SeekBar>(R.id.seekBarContrast)
        val tvContrastVal = panelView.findViewById<TextView>(R.id.tvContrastVal)
        val btnDone = panelView.findViewById<Button>(R.id.btnDoneAdjustments)

        // Select active filter chip
        when (currentState.filterType) {
            VideoEditState.FILTER_VIVID -> chipGroupVideoFilters.check(R.id.chipVideoFilterVivid)
            VideoEditState.FILTER_BW -> chipGroupVideoFilters.check(R.id.chipVideoFilterBW)
            VideoEditState.FILTER_WARM -> chipGroupVideoFilters.check(R.id.chipVideoFilterWarm)
            VideoEditState.FILTER_INVERT -> chipGroupVideoFilters.check(R.id.chipVideoFilterInvert)
            else -> chipGroupVideoFilters.check(R.id.chipVideoFilterNone)
        }

        chipGroupVideoFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: R.id.chipVideoFilterNone
            val newFilter = when (id) {
                R.id.chipVideoFilterVivid -> VideoEditState.FILTER_VIVID
                R.id.chipVideoFilterBW -> VideoEditState.FILTER_BW
                R.id.chipVideoFilterWarm -> VideoEditState.FILTER_WARM
                R.id.chipVideoFilterInvert -> VideoEditState.FILTER_INVERT
                else -> VideoEditState.FILTER_NONE
            }
            if (newFilter != currentState.filterType) {
                saveStateToUndo()
                currentState = currentState.copy(filterType = newFilter)
                applyVideoEffects()
            }
        }

        val brightnessProgress = ((currentState.brightness * 100f) + 50f).toInt()
        seekBarBrightness.progress = brightnessProgress
        tvBrightnessVal.text = (brightnessProgress - 50).toString()

        val contrastProgress = ((currentState.contrast * 100f) + 50f).toInt()
        seekBarContrast.progress = contrastProgress
        tvContrastVal.text = (contrastProgress - 50).toString()

        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val displayVal = progress - 50
                tvBrightnessVal.text = displayVal.toString()
                val bVal = displayVal / 100f
                currentState = currentState.copy(brightness = bVal)
                applyVideoEffects()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val displayVal = progress - 50
                tvContrastVal.text = displayVal.toString()
                val cVal = displayVal / 100f
                currentState = currentState.copy(contrast = cVal)
                applyVideoEffects()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { saveStateToUndo() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnDone.setOnClickListener { closeSubPanel() }
    }

    private fun fetchVideoDurationAndSetup() {
        val tempPlayer = ExoPlayer.Builder(this).build()
        tempPlayer.setMediaItem(MediaItem.fromUri(sourceUri!!))
        tempPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    videoDurationMs = tempPlayer.duration
                    if (currentState.trimEndMs == 0L) {
                        currentState = currentState.copy(trimEndMs = videoDurationMs)
                    }
                    tempPlayer.release()
                    setupPlayer()
                }
            }
        })
        tempPlayer.prepare()
    }

    private fun fetchAudioDuration(uri: Uri) {
        val tempPlayer = ExoPlayer.Builder(this).build()
        tempPlayer.setMediaItem(MediaItem.fromUri(uri))
        tempPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    audioDurationMs = tempPlayer.duration
                    tempPlayer.release()
                }
            }
        })
        tempPlayer.prepare()
    }

    private fun saveStateToUndo() {
        undoStack.push(currentState)
        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.push(currentState)
            currentState = undoStack.pop()
            applyStateToUI()
            setupPlayer()
            updateUndoRedoButtons()
        }
    }

    private fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.push(currentState)
            currentState = redoStack.pop()
            applyStateToUI()
            setupPlayer()
            updateUndoRedoButtons()
        }
    }

    private fun resetAll() {
        if (undoStack.isNotEmpty() || currentState != VideoEditState(trimEndMs = videoDurationMs)) {
            saveStateToUndo()
            currentState = VideoEditState(trimEndMs = videoDurationMs)
            applyStateToUI()
            setupPlayer()
            updateUndoRedoButtons()
        }
    }

    private fun updateUndoRedoButtons() {
        btnUndo.isEnabled = undoStack.isNotEmpty()
        btnUndo.alpha = if (btnUndo.isEnabled) 1.0f else 0.4f

        btnRedo.isEnabled = redoStack.isNotEmpty()
        btnRedo.alpha = if (btnRedo.isEnabled) 1.0f else 0.4f
    }

    private fun applyStateToUI() {
        refreshActiveSubPanel()
    }

    private fun applyVideoEffects() {
        val videoEffects = mutableListOf<Effect>()

        // 0. Aspect Ratio Cropping
        if (currentState.aspectRatioRatio > 0f) {
            videoEffects.add(Presentation.createForAspectRatio(currentState.aspectRatioRatio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        }

        // 1. Live Rotation on PlayerView & GL transformation
        playerView.rotation = currentState.rotation.toFloat()
        if (currentState.rotation != 0) {
            videoEffects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(currentState.rotation.toFloat()).build())
        }

        // 2. Brightness
        if (currentState.brightness != 0f) {
            videoEffects.add(Brightness(currentState.brightness))
        }

        // 3. Contrast
        if (currentState.contrast != 0f) {
            videoEffects.add(Contrast(currentState.contrast))
        }

        // 4. Video Filters
        when (currentState.filterType) {
            VideoEditState.FILTER_BW -> videoEffects.add(RgbFilter.createGrayscaleFilter())
            VideoEditState.FILTER_INVERT -> videoEffects.add(RgbFilter.createInvertedFilter())
            VideoEditState.FILTER_VIVID -> {
                videoEffects.add(Contrast(0.25f))
                videoEffects.add(HslAdjustment.Builder().adjustSaturation(30f).build())
            }
            VideoEditState.FILTER_WARM -> {
                videoEffects.add(HslAdjustment.Builder().adjustHue(15f).adjustSaturation(10f).build())
            }
        }

        try {
            exoPlayer?.setVideoEffects(videoEffects)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupPlayer() {
        exoPlayer?.release()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setEnableDecoderFallback(true)
        }

        val dataSourceFactory = DefaultDataSource.Factory(this)
        val videoMediaItem = MediaItem.fromUri(sourceUri!!)

        exoPlayer = ExoPlayer.Builder(this, renderersFactory).build().apply {
            if (currentState.selectedAudioUri != null) {
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(videoMediaItem)

                val audioMediaItemBuilder = MediaItem.Builder().setUri(currentState.selectedAudioUri!!)
                if (currentState.audioStartMs > 0) {
                    val clipConfig = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(currentState.audioStartMs)
                        .build()
                    audioMediaItemBuilder.setClippingConfiguration(clipConfig)
                }

                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(audioMediaItemBuilder.build())

                val mergedSource = MergingMediaSource(videoSource, audioSource)
                setMediaSource(mergedSource)
            } else {
                setMediaItem(videoMediaItem)
            }

            playbackParameters = PlaybackParameters(currentState.speed)

            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (currentState.selectedAudioUri == null) return
                    val builder = trackSelectionParameters.buildUpon()

                    val audioGroups = mutableListOf<Tracks.Group>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            audioGroups.add(group)
                        }
                    }

                    if (audioGroups.isNotEmpty()) {
                        val targetAudioGroup = audioGroups.last()
                        builder.setOverrideForType(
                            TrackSelectionOverride(targetAudioGroup.mediaTrackGroup, 0)
                        )
                        trackSelectionParameters = builder.build()
                    }
                }
            })

            volume = if (currentState.selectedAudioUri == null && currentState.isMuted) 0f else currentState.audioVolume
            prepare()
            playWhenReady = true
        }

        playerView.player = exoPlayer
        applyVideoEffects()
    }

    private fun exportVideo() {
        btnExport.isEnabled = false
        closeSubPanel()
        layoutExportProgress.visibility = View.VISIBLE
        tvExportStatus.text = "Processing video export..."
        exoPlayer?.pause()

        val outputFile = File(cacheDir, "exported_video_${UUID.randomUUID()}.mp4")

        transformer = Transformer.Builder(this)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    runOnUiThread {
                        layoutExportProgress.visibility = View.GONE
                        Toast.makeText(this@VideoEditorActivity, "Video exported successfully!", Toast.LENGTH_SHORT).show()
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_RESULT_URI, Uri.fromFile(outputFile))
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    runOnUiThread {
                        btnExport.isEnabled = true
                        layoutExportProgress.visibility = View.GONE
                        Toast.makeText(this@VideoEditorActivity, "Export failed: ${exportException.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
            .setVideoMimeType("video/avc")
            .build()

        // Apply Video Effects (Aspect Ratio Crop, Rotation, Brightness, Contrast, Preset Filters)
        val videoEffects = mutableListOf<Effect>()
        if (currentState.aspectRatioRatio > 0f) {
            videoEffects.add(Presentation.createForAspectRatio(currentState.aspectRatioRatio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        }
        if (currentState.rotation != 0) {
            videoEffects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(currentState.rotation.toFloat()).build())
        }
        if (currentState.brightness != 0f) {
            videoEffects.add(Brightness(currentState.brightness))
        }
        if (currentState.contrast != 0f) {
            videoEffects.add(Contrast(currentState.contrast))
        }

        when (currentState.filterType) {
            VideoEditState.FILTER_BW -> videoEffects.add(RgbFilter.createGrayscaleFilter())
            VideoEditState.FILTER_INVERT -> videoEffects.add(RgbFilter.createInvertedFilter())
            VideoEditState.FILTER_VIVID -> {
                videoEffects.add(Contrast(0.25f))
                videoEffects.add(HslAdjustment.Builder().adjustSaturation(30f).build())
            }
            VideoEditState.FILTER_WARM -> {
                videoEffects.add(HslAdjustment.Builder().adjustHue(15f).adjustSaturation(10f).build())
            }
        }

        // Configure Video MediaItem with Trimming
        val videoMediaItemBuilder = MediaItem.Builder()
            .setUri(sourceUri!!)

        if (currentState.trimStartMs > 0 || (currentState.trimEndMs > 0 && currentState.trimEndMs < videoDurationMs)) {
            val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(currentState.trimStartMs)
            if (currentState.trimEndMs > 0) {
                clipBuilder.setEndPositionMs(currentState.trimEndMs)
            }
            videoMediaItemBuilder.setClippingConfiguration(clipBuilder.build())
        }

        val videoEditedMediaItem = EditedMediaItem.Builder(videoMediaItemBuilder.build())
            .setRemoveAudio(currentState.isMuted)
            .apply {
                if (videoEffects.isNotEmpty()) {
                    setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
                }
            }
            .build()

        val videoSequence = EditedMediaItemSequence(listOf(videoEditedMediaItem))
        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences.add(videoSequence)

        // Add Selected Music Track / Video Extracted Audio Track
        if (currentState.selectedAudioUri != null) {
            val audioMediaItemBuilder = MediaItem.Builder().setUri(currentState.selectedAudioUri!!)
            if (currentState.audioStartMs > 0) {
                val clipConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(currentState.audioStartMs)
                    .build()
                audioMediaItemBuilder.setClippingConfiguration(clipConfig)
            }
            val audioEditedMediaItem = EditedMediaItem.Builder(audioMediaItemBuilder.build())
                .setRemoveVideo(true)
                .build()
            val audioSequence = EditedMediaItemSequence(listOf(audioEditedMediaItem))
            sequences.add(audioSequence)
        }

        val composition = Composition.Builder(sequences).build()
        transformer?.start(composition, outputFile.absolutePath)
    }

    private fun getFileNameFromUri(uri: Uri, isExtracted: Boolean): String {
        var name = if (isExtracted) "Extracted_Audio.mp4" else "Music_Track.mp3"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun formatMsToTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        transformer?.cancel()
    }
}
