package shop.sabjiwala.kisankendra

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.FragmentForumBinding
import shop.sabjiwala.kisankendra.model.ForumComment
import shop.sabjiwala.kisankendra.model.ForumPost
import shop.sabjiwala.kisankendra.utils.ForumBlockHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.ScreenAlertHelper
import com.google.firebase.firestore.ListenerRegistration
import shop.sabjiwala.kisankendra.views.RunningBorderDrawable
import shop.sabjiwala.kisankendra.utils.ForumCacheHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.LocationHelper
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.io.File

class ForumFragment : Fragment() {

    private var _binding: FragmentForumBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var allPostsList: List<ForumPost> = emptyList()
    private var adapter: ForumAdapter? = null
    private var isUserAdmin = false
    private var forumRunningBorderDrawable: RunningBorderDrawable? = null

    private val localCommentCounts = mutableMapOf<String, Int>()
    private val localLikesMap = mutableMapOf<String, Int>()

    private var selectedMediaUri: Uri? = null
    private var activeDialogMediaPreview: ImageView? = null
    private var activeDialogMediaContainer: FrameLayout? = null

    private var cameraPhotoUri: Uri? = null
    private var cameraVideoUri: Uri? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraPhotoUri != null) {
            selectedMediaUri = cameraPhotoUri
            showCreatePostDialog(preAttachedUri = cameraPhotoUri)
        }
    }

    private val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && cameraVideoUri != null) {
            selectedMediaUri = cameraVideoUri
            showCreatePostDialog(preAttachedUri = cameraVideoUri)
        }
    }

    private val pickGalleryMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val safeUri = MediaUtils.copyUriToAppCache(requireContext(), uri) ?: uri
            selectedMediaUri = safeUri
            if (activeDialogMediaPreview != null) {
                activeDialogMediaContainer?.visibility = View.VISIBLE
                activeDialogMediaPreview?.let { iv ->
                    Glide.with(this).load(safeUri).centerCrop().into(iv)
                }
            } else {
                showCreatePostDialog(preAttachedUri = safeUri)
            }
        }
    }

    private var alertListenerRegistration: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertListenerRegistration = ScreenAlertHelper.setupScreenAlert(requireContext(), "forum_screen", binding.alertBanner)

        binding.rvForumPosts.layoutManager = LinearLayoutManager(requireContext())
        adapter = ForumAdapter(
            posts = emptyList(),
            onLikeClick = { post -> handleLikePost(post) },
            onReplyClick = { post -> showCommentsDialog(post) },
            onShareClick = { post -> sharePostOnWhatsApp(post) },
            onMediaClick = { url, isVideo -> showOriginalMediaDialog(url, isVideo) },
            onOptionsClick = { post, anchor -> showPostOptionsMenu(post, anchor) }
        )
        binding.rvForumPosts.adapter = adapter

        binding.rvForumPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    autoPlayFirstVisibleVideo()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible > 2 || recyclerView.computeVerticalScrollOffset() > 300) {
                    if (binding.fabScrollTop.visibility != View.VISIBLE) {
                        binding.fabScrollTop.show()
                    }
                } else {
                    if (binding.fabScrollTop.visibility == View.VISIBLE) {
                        binding.fabScrollTop.hide()
                    }
                }
            }
        })

        binding.fabScrollTop.setOnClickListener {
            binding.rvForumPosts.smoothScrollToPosition(0)
        }

        ShimmerHelper.setupSwipeRefresh(binding.swipeRefreshForum)
        binding.swipeRefreshForum.setOnRefreshListener { fetchForumPosts(forceRefresh = true) }
        binding.fabAskQuestion.setOnClickListener { showCreatePostDialog() }
        MediaUtils.startPulseAnimation(binding.fabAskQuestion)

        // Top Creation Bar Direct Actions
        binding.ivUserPostAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.cardCreatePostBar.setOnClickListener { showCreatePostDialog() }
        val cornerRadius = 22 * resources.displayMetrics.density
        val strokeWidth = 3f * resources.displayMetrics.density
        val surfaceColor = try {
            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            typedValue.data
        } catch (e: Exception) {
            Color.WHITE
        }
        val greenColors = intArrayOf(
            Color.parseColor("#1B5E20"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#81C784"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#81C784"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#1B5E20")
        )
        forumRunningBorderDrawable = RunningBorderDrawable(
            cornerRadius = cornerRadius,
            strokeWidth = strokeWidth,
            backgroundColor = surfaceColor,
            gradientColors = greenColors
        )
        binding.cardCreatePostBar.background = forumRunningBorderDrawable
        binding.cardCreatePostBar.strokeWidth = 0

        binding.tvCreatePostHint.setOnClickListener { showCreatePostDialog() }
        binding.btnPostGallery.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add(0, 1, 1, "🖼️ Photo from Gallery")
            popup.menu.add(0, 2, 2, "🎥 Video from Gallery")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> pickGalleryMediaLauncher.launch("image/*")
                    2 -> pickGalleryMediaLauncher.launch("video/*")
                }
                true
            }
            popup.show()
        }

        binding.btnPostCamera.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add(0, 1, 1, "📸 Take Photo")
            popup.menu.add(0, 2, 2, "📹 Record Video")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> launchCameraCapture()
                    2 -> launchVideoRecord()
                }
                true
            }
            popup.show()
        }

        setupCategoryChips()
        translateForumUiElements()
        updateCreationBarUserInfo()
        checkAdminRole()
        fetchForumPosts()
    }

    private var currentlyPlayingHolder: ForumAdapter.PostViewHolder? = null

    private fun autoPlayFirstVisibleVideo() {
        val lm = binding.rvForumPosts.layoutManager as? LinearLayoutManager ?: return
        val firstPos = lm.findFirstCompletelyVisibleItemPosition().coerceAtLeast(lm.findFirstVisibleItemPosition())
        val lastPos = lm.findLastVisibleItemPosition()

        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) return

        var targetHolder: ForumAdapter.PostViewHolder? = null
        var targetVideoUrl = ""

        for (pos in firstPos..lastPos) {
            val item = adapter?.getItemAt(pos)
            if (item != null && item.videoUrl.isNotBlank()) {
                val vh = binding.rvForumPosts.findViewHolderForAdapterPosition(pos) as? ForumAdapter.PostViewHolder
                if (vh != null) {
                    targetHolder = vh
                    targetVideoUrl = item.videoUrl
                    break
                }
            }
        }

        if (targetHolder != currentlyPlayingHolder) {
            currentlyPlayingHolder?.vvInFeedVideo?.stopPlayback()
            currentlyPlayingHolder?.ivVideoThumbnail?.visibility = View.VISIBLE
            currentlyPlayingHolder?.btnPlayVideo?.visibility = View.VISIBLE
            currentlyPlayingHolder = null
        }

        if (targetHolder != null && targetVideoUrl.isNotBlank()) {
            currentlyPlayingHolder = targetHolder
            targetHolder.ivVideoThumbnail.visibility = View.GONE
            targetHolder.btnPlayVideo.visibility = View.GONE
            targetHolder.vvInFeedVideo.visibility = View.VISIBLE
            targetHolder.vvInFeedVideo.setVideoURI(Uri.parse(targetVideoUrl))
            targetHolder.vvInFeedVideo.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                targetHolder.vvInFeedVideo.start()
            }
            targetHolder.vvInFeedVideo.setOnErrorListener { _, _, _ ->
                targetHolder.ivVideoThumbnail.visibility = View.VISIBLE
                targetHolder.btnPlayVideo.visibility = View.VISIBLE
                true
            }
        }
    }

    private fun checkAdminRole() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                if (role.equals("admin", ignoreCase = true) || isAdmin) {
                    isUserAdmin = true
                }
            }
    }

    override fun onResume() {
        super.onResume()
        updateCreationBarUserInfo()
    }

    private fun launchCameraCapture() {
        try {
            val photoFile = File(requireContext().cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val authority = "${requireContext().packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(requireContext(), authority, photoFile)
            cameraPhotoUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Camera error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchVideoRecord() {
        try {
            val videoFile = File(requireContext().cacheDir, "camera_video_${System.currentTimeMillis()}.mp4")
            val authority = "${requireContext().packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(requireContext(), authority, videoFile)
            cameraVideoUri = uri
            takeVideoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Video recording error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCreationBarUserInfo() {
        if (_binding == null) return
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.ifBlank { "Farmer" } ?: "Farmer"
        val firstName = name.split(" ").firstOrNull() ?: "Farmer"

        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        val baseHint = "Write Your post..."

        if (activeLang != "en") {
            lifecycleScope.launch(Dispatchers.Main) {
                val prompt = TranslationHelper.translateText(ctx, baseHint, activeLang)
                if (_binding != null && isAdded) {
                    binding.tvCreatePostHint.text = prompt
                }
            }
        } else {
            binding.tvCreatePostHint.text = baseHint
        }

        val photoUrl = user?.photoUrl?.toString().orEmpty()
        if (photoUrl.isNotBlank()) {
            Glide.with(this)
                .load(photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.ivUserPostAvatar)
        } else if (user != null) {
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (_binding == null) return@addOnSuccessListener
                    val dbPhoto = doc.getString("photoUrl").orEmpty()
                    if (activeLang != "en") {
                        lifecycleScope.launch(Dispatchers.Main) {
                            val prompt = TranslationHelper.translateText(ctx, baseHint, activeLang)
                            if (_binding != null && isAdded) {
                                binding.tvCreatePostHint.text = prompt
                            }
                        }
                    } else {
                        binding.tvCreatePostHint.text = baseHint
                    }
                    if (dbPhoto.isNotBlank()) {
                        Glide.with(this)
                            .load(dbPhoto)
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_avatar)
                            .into(binding.ivUserPostAvatar)
                    }
                }
        }
    }

    private fun fetchForumPosts(forceRefresh: Boolean = false) {
        if (_binding == null) return
        val ctx = context ?: return

        // 1. ⚡ INSTANT LOCAL CACHE DISPLAY (<10ms)
        if (!forceRefresh) {
            val cachedPosts = ForumCacheHelper.getLocalPosts(ctx)
            if (cachedPosts.isNotEmpty()) {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                allPostsList = cachedPosts.map { rawPost ->
                    val post = rawPost.copy()
                    if (currentUid.isNotBlank()) {
                        val isLiked = ForumCacheHelper.isUserLikedStatus(ctx, post.id, currentUid, post.likedBy.contains(currentUid))
                        val likedList = post.likedBy.toMutableList()
                        if (isLiked && !likedList.contains(currentUid)) {
                            likedList.add(currentUid)
                        } else if (!isLiked && likedList.contains(currentUid)) {
                            likedList.remove(currentUid)
                        }
                        post.likedBy = likedList
                    }
                    post.repliesCount = ForumCacheHelper.getLocalReplyCount(ctx, post.id, post.repliesCount)
                    post.likesCount = ForumCacheHelper.getLocalLikeCount(ctx, post.id, post.likesCount)
                    post
                }
                filterPostsByCategory()
                binding.pbForum.visibility = View.GONE
            } else {
                binding.pbForum.visibility = View.VISIBLE
                ShimmerHelper.applyShimmer(binding.rvForumPosts)
            }
        } else {
            binding.swipeRefreshForum.isRefreshing = true
            ShimmerHelper.applyShimmer(binding.rvForumPosts)
        }

        fun performFullServerFetch() {
            firestore.collection("forum_posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    binding.pbForum.visibility = View.GONE
                    binding.swipeRefreshForum.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.rvForumPosts)

                    if (snapshot.isEmpty) {
                        seedDummyPostsToFirestore()
                    } else {
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        allPostsList = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ForumPost::class.java)?.let { rawPost ->
                                val post = if (rawPost.id.isBlank()) rawPost.copy(id = doc.id) else rawPost
                                if (currentUid.isNotBlank()) {
                                    val isLiked = ForumCacheHelper.isUserLikedStatus(requireContext(), post.id, currentUid, post.likedBy.contains(currentUid))
                                    val likedList = post.likedBy.toMutableList()
                                    if (isLiked && !likedList.contains(currentUid)) {
                                        likedList.add(currentUid)
                                    } else if (!isLiked && likedList.contains(currentUid)) {
                                        likedList.remove(currentUid)
                                    }
                                    post.likedBy = likedList
                                }
                                post
                            }
                        }
                        for (p in allPostsList) {
                            p.repliesCount = ForumCacheHelper.getLocalReplyCount(requireContext(), p.id, p.repliesCount)
                            p.likesCount = ForumCacheHelper.getLocalLikeCount(requireContext(), p.id, p.likesCount)
                        }
                        ForumCacheHelper.savePostsLocally(ctx, allPostsList)
                        filterPostsByCategory()
                    }
                }
                .addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    binding.pbForum.visibility = View.GONE
                    binding.swipeRefreshForum.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.rvForumPosts)
                    if (allPostsList.isEmpty()) loadSampleForumPosts()
                }
        }

        // 2. ⚡ LIGHTWEIGHT DELTA CHECK (1 Read Cost)
        if (forceRefresh || allPostsList.isEmpty()) {
            performFullServerFetch()
        } else {
            firestore.collection("forum_posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { topSnapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    binding.swipeRefreshForum.isRefreshing = false

                    val topDoc = topSnapshot.documents.firstOrNull()
                    val topPostId = topDoc?.id.orEmpty()
                    val localTopPostId = allPostsList.firstOrNull()?.id.orEmpty()

                    // If local cache is up to date -> STOP (1 read cost instead of 30!)
                    if (topPostId.isNotBlank() && topPostId == localTopPostId) {
                        binding.pbForum.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    performFullServerFetch()
                }
                .addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    binding.swipeRefreshForum.isRefreshing = false
                }
        }
    }

    private fun seedDummyPostsToFirestore() {
        val dummyPosts = listOf(
            hashMapOf(
                "userId" to "dummy_user_01",
                "authorName" to "Ramesh Patel",
                "authorLocation" to "Khandwa, MP",
                "category" to "Crop Diseases",
                "description" to "I noticed yellowing on my 40-day old wheat crop after heavy morning fog and dew. Is this yellow rust or nitrogen deficiency? Please suggest a safe fungicide or remedy.",
                "photoUrl" to "",
                "videoUrl" to "",
                "likesCount" to 18,
                "repliesCount" to 6,
                "timestamp" to Timestamp.now()
            ),
            hashMapOf(
                "userId" to "dummy_user_02",
                "authorName" to "Sunita Bai",
                "authorLocation" to "Ratlam, MP",
                "category" to "Mandi Advice",
                "description" to "Current rate in Jaora mandi is ₹5200/quintal. Should I sell my 50 quintal stock now or wait till next month for better rates?",
                "photoUrl" to "",
                "videoUrl" to "",
                "likesCount" to 32,
                "repliesCount" to 14,
                "timestamp" to Timestamp.now()
            ),
            hashMapOf(
                "userId" to "dummy_user_03",
                "authorName" to "Mahesh Sharma",
                "authorLocation" to "Indore, MP",
                "category" to "Fertilizers",
                "description" to "My cotton crop is around 60 days old. There was light rain yesterday. Is today a good time for top dressing with Urea, or should I wait for soil to dry?",
                "photoUrl" to "",
                "videoUrl" to "",
                "likesCount" to 9,
                "repliesCount" to 4,
                "timestamp" to Timestamp.now()
            )
        )

        val batch = firestore.batch()
        for (postMap in dummyPosts) {
            val docRef = firestore.collection("forum_posts").document()
            batch.set(docRef, postMap)
        }

        batch.commit().addOnSuccessListener {
            fetchForumPosts()
        }.addOnFailureListener {
            loadSampleForumPosts()
        }
    }

    private fun loadSampleForumPosts() {
        allPostsList = listOf(
            ForumPost(
                id = "sample1",
                authorName = "Ramesh Kumar",
                authorLocation = "Khandwa, MP",
                category = "Crop Diseases",
                description = "I noticed yellowing on my 40-day old wheat crop after heavy morning dew. Is this yellow rust or nitrogen deficiency? Suggest remedy.",
                likesCount = 14,
                repliesCount = 6
            ),
            ForumPost(
                id = "sample2",
                authorName = "Suresh Patel",
                authorLocation = "Ratlam, MP",
                category = "Mandi Advice",
                description = "Current rate in Jaora mandi is ₹5200/quintal. Should I sell my 50 quintal stock now or wait till next month?",
                likesCount = 22,
                repliesCount = 11
            )
        )
        for (p in allPostsList) {
            p.repliesCount = ForumCacheHelper.getLocalReplyCount(requireContext(), p.id, p.repliesCount)
            p.likesCount = ForumCacheHelper.getLocalLikeCount(requireContext(), p.id, p.likesCount)
        }
        filterPostsByCategory()
    }

    private fun setupCategoryChips() {
        translateForumUiElements()
        binding.chipGroupForumCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            filterPostsByCategory(checkedIds.firstOrNull())
        }
    }

    private fun translateForumUiElements() {
        val ctx = context ?: return
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.Main) {
            if (_binding == null || !isAdded) return@launch

            if (_binding != null && isAdded) {
                binding.chipForumAll.text = TranslationHelper.translateText(ctx, "All Topics", activeLang)
                binding.chipForumDiseases.text = TranslationHelper.translateText(ctx, "Crop Diseases", activeLang)
                binding.chipForumFertilizer.text = TranslationHelper.translateText(ctx, "Fertilizers", activeLang)
                binding.chipForumMandi.text = TranslationHelper.translateText(ctx, "Mandi Advice", activeLang)
                binding.chipForumYojana.text = TranslationHelper.translateText(ctx, "Govt Schemes", activeLang)
                binding.chipForumCrops.text = TranslationHelper.translateText(ctx, "Seeds & Varieties", activeLang)
                binding.chipForumWeather.text = TranslationHelper.translateText(ctx, "Weather", activeLang)
                binding.chipForumDairy.text = TranslationHelper.translateText(ctx, "Dairy Farming", activeLang)
                binding.chipForumMachinery.text = TranslationHelper.translateText(ctx, "Machinery & Tractors", activeLang)
            }
        }
    }

    private fun filterPostsByCategory(checkedId: Int? = binding.chipGroupForumCategories.checkedChipId) {
        val selectedCategory = when (checkedId) {
            R.id.chipForumDiseases -> "Crop Diseases"
            R.id.chipForumFertilizer -> "Fertilizers"
            R.id.chipForumMandi -> "Mandi Advice"
            R.id.chipForumYojana -> "Govt Schemes"
            R.id.chipForumCrops -> "Seeds & Varieties"
            R.id.chipForumWeather -> "Weather"
            R.id.chipForumDairy -> "Dairy Farming"
            R.id.chipForumMachinery -> "Machinery & Tractors"
            else -> "All Topics"
        }

        val filtered = if (selectedCategory == "All Topics") {
            allPostsList
        } else {
            allPostsList.filter { item ->
                val cat = item.category.trim()
                cat.equals(selectedCategory, ignoreCase = true) ||
                (selectedCategory == "Crop Diseases" && (cat.contains("Disease", ignoreCase = true) || cat.contains("बीमारी", ignoreCase = true) || cat.contains("ਬੀਮਾਰੀ", ignoreCase = true))) ||
                (selectedCategory == "Fertilizers" && (cat.contains("Fertilizer", ignoreCase = true) || cat.contains("खाद", ignoreCase = true) || cat.contains("ਖਾਦ", ignoreCase = true))) ||
                (selectedCategory == "Mandi Advice" && (cat.contains("Mandi", ignoreCase = true) || cat.contains("मंडी", ignoreCase = true) || cat.contains("ਮੰਡੀ", ignoreCase = true))) ||
                (selectedCategory == "Govt Schemes" && (cat.contains("Scheme", ignoreCase = true) || cat.contains("Yojana", ignoreCase = true) || cat.contains("योजना", ignoreCase = true) || cat.contains("ਸਕੀਮ", ignoreCase = true))) ||
                (selectedCategory == "Seeds & Varieties" && (cat.contains("Seed", ignoreCase = true) || cat.contains("Crop", ignoreCase = true) || cat.contains("बीज", ignoreCase = true) || cat.contains("फ़सल", ignoreCase = true))) ||
                (selectedCategory == "Weather" && (cat.contains("Weather", ignoreCase = true) || cat.contains("मौसम", ignoreCase = true) || cat.contains("ਮੌਸਮ", ignoreCase = true))) ||
                (selectedCategory == "Dairy Farming" && (cat.contains("Dairy", ignoreCase = true) || cat.contains("पशु", ignoreCase = true) || cat.contains("ਡੇਅਰੀ", ignoreCase = true))) ||
                (selectedCategory == "Machinery & Tractors" && (cat.contains("Machinery", ignoreCase = true) || cat.contains("Tractor", ignoreCase = true) || cat.contains("ट्रैक्टर", ignoreCase = true)))
            }
        }

        adapter?.updateData(filtered)
    }

    private fun handleLikePost(post: ForumPost) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🔒 Sign In Required")
                .setMessage("Please sign in to like posts. (लाइक करने के लिए साइन इन करें)")
                .setPositiveButton("Sign In") { _, _ ->
                    startActivity(Intent(requireContext(), SignInActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val currentUid = user.uid
        val likedList = post.likedBy.toMutableList()
        val isNowLiked: Boolean

        if (likedList.contains(currentUid)) {
            likedList.remove(currentUid)
            post.likesCount = maxOf(0, post.likesCount - 1)
            isNowLiked = false
        } else {
            likedList.add(currentUid)
            post.likesCount += 1
            isNowLiked = true
        }
        post.likedBy = likedList
        localLikesMap[post.id] = post.likesCount
        ForumCacheHelper.saveUserLikedStatus(requireContext(), post.id, currentUid, isNowLiked)
        ForumCacheHelper.saveLocalLikeCount(requireContext(), post.id, post.likesCount)

        adapter?.notifyItemLiked(post.id)

        if (post.id.isNotBlank()) {
            val docRef = firestore.collection("forum_posts").document(post.id)
            val updates = mapOf(
                "likesCount" to FieldValue.increment(if (isNowLiked) 1 else -1),
                "likedBy" to if (isNowLiked) FieldValue.arrayUnion(currentUid) else FieldValue.arrayRemove(currentUid)
            )
            docRef.update(updates).addOnFailureListener {
                docRef.set(updates, SetOptions.merge())
            }
        }
    }

    private fun showCommentsDialog(post: ForumPost) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_post_comments, null)
        val rvComments = dialogView.findViewById<RecyclerView>(R.id.rvComments)
        val etInput = dialogView.findViewById<EditText>(R.id.etCommentInput)
        val btnSend = dialogView.findViewById<Button>(R.id.btnSendComment)
        val ivCommentAvatar = dialogView.findViewById<ImageView>(R.id.ivCommentUserAvatar)
        val layoutReplyingBanner = dialogView.findViewById<View>(R.id.layoutReplyingBanner)
        val tvReplyingTo = dialogView.findViewById<TextView>(R.id.tvReplyingTo)
        val btnCancelReply = dialogView.findViewById<ImageButton>(R.id.btnCancelReply)

        var activeReplyAuthor = ""

        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid.orEmpty()
        val isGuest = currentUser == null || currentUser.isAnonymous

        if (currentUser?.photoUrl != null) {
            Glide.with(requireContext()).load(currentUser.photoUrl).circleCrop().into(ivCommentAvatar)
        }
        ivCommentAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        rvComments.layoutManager = LinearLayoutManager(requireContext())
        val commentsRef = firestore.collection("forum_posts").document(post.id).collection("comments")

        lateinit var commentAdapter: ForumCommentAdapter
        commentAdapter = ForumCommentAdapter(
            comments = emptyList(),
            isAdmin = isUserAdmin,
            currentUserId = currentUid,
            onDeleteClick = { commentToDelete ->
                confirmAndDeleteForumComment(post, commentToDelete, commentsRef, commentAdapter)
            },
            onLikeClick = { commentToLike ->
                handleLikeForumComment(commentsRef, commentToLike, commentAdapter)
            },
            onReplyClick = { commentToReply ->
                activeReplyAuthor = commentToReply.authorName
                layoutReplyingBanner.visibility = View.VISIBLE
                tvReplyingTo.text = "Replying to @${commentToReply.authorName}"
                etInput.hint = "Replying to @${commentToReply.authorName}..."
                etInput.requestFocus()
            }
        )
        rvComments.adapter = commentAdapter

        val ctx = requireContext()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        btnCancelReply.setOnClickListener {
            activeReplyAuthor = ""
            layoutReplyingBanner.visibility = View.GONE
            if (isGuest) {
                etInput.hint = "🔒 Sign in to comment"
            } else {
                etInput.hint = "Comment your thoughts..."
                if (activeLang != "en") {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (isAdded) {
                            val trans = TranslationHelper.translateText(ctx, "Comment your thoughts...", activeLang)
                            etInput.hint = trans
                        }
                    }
                }
            }
        }

        val tvCommentsHeaderTitle = dialogView.findViewById<TextView>(R.id.tvCommentsHeaderTitle)

        // Wire up Quick Emoji Bar
        val emojiIds = intArrayOf(
            R.id.tvEmoji1, R.id.tvEmoji2, R.id.tvEmoji3, R.id.tvEmoji4,
            R.id.tvEmoji5, R.id.tvEmoji6, R.id.tvEmoji7, R.id.tvEmoji8
        )
        for (emojiId in emojiIds) {
            dialogView.findViewById<TextView>(emojiId)?.setOnClickListener { v ->
                if (isGuest) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🔒 Sign In Required")
                        .setMessage("Guest users can read comments. Please sign in to write comments or replies.")
                        .setPositiveButton("Sign In") { _, _ ->
                            startActivity(Intent(requireContext(), SignInActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val emoji = (v as TextView).text.toString()
                    etInput.append(emoji)
                    etInput.requestFocus()
                    etInput.setSelection(etInput.text.length)
                }
            }
        }

        val cachedComments = ForumCacheHelper.getLocalComments(requireContext(), post.id)
        if (cachedComments.isNotEmpty()) {
            commentAdapter.updateData(cachedComments)
            tvCommentsHeaderTitle?.text = "Comments (${cachedComments.size})"
        }

        val listenerRegistration = commentsRef.addSnapshotListener { snapshot, error ->
            if (_binding == null || snapshot == null) return@addSnapshotListener
            if (!snapshot.isEmpty) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ForumComment::class.java)?.let { comment ->
                        if (comment.id.isBlank()) comment.copy(id = doc.id) else comment
                    }
                }
                val sortedList = list.sortedBy { it.timestamp?.toDate()?.time ?: 0L }
                commentAdapter.updateData(sortedList)
                tvCommentsHeaderTitle?.text = if (sortedList.isNotEmpty()) "Comments (${sortedList.size})" else "Comments"
            }
        }

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)

        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()

        dialog.setOnDismissListener {
            listenerRegistration.remove()
        }

        if (isGuest) {
            etInput.isFocusable = false
            etInput.isFocusableInTouchMode = false
            etInput.hint = "🔒 Sign in to comment"
            if (activeLang != "en") {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (isAdded) {
                        val trans = TranslationHelper.translateText(ctx, "Sign in to comment", activeLang)
                        etInput.hint = "🔒 $trans"
                    }
                }
            }
            val promptSignIn = View.OnClickListener {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("🔒 Sign In Required")
                    .setMessage("Guest users can read comments. Please sign in to write comments or replies.")
                    .setPositiveButton("Sign In") { _, _ ->
                        startActivity(Intent(ctx, SignInActivity::class.java))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            etInput.setOnClickListener(promptSignIn)
            btnSend.setOnClickListener(promptSignIn)
        } else {
            etInput.hint = "Comment your thoughts..."
            if (activeLang != "en") {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (isAdded) {
                        val trans = TranslationHelper.translateText(ctx, "Comment your thoughts...", activeLang)
                        etInput.hint = trans
                    }
                }
            }
            btnSend.setOnClickListener {
                val text = etInput.text.toString().trim()
                if (text.isBlank()) return@setOnClickListener

                submitCommentToFirestore(currentUser, post, text, activeReplyAuthor, commentsRef, etInput, commentAdapter)
                activeReplyAuthor = ""
                layoutReplyingBanner.visibility = View.GONE
                etInput.hint = "Comment your thoughts..."
            }
        }

        dialog.show()
    }

    private fun handleLikeForumComment(
        commentsRef: CollectionReference,
        comment: ForumComment,
        commentAdapter: ForumCommentAdapter
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            Toast.makeText(requireContext(), "Please sign in to like comments", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUid = user.uid
        val likedList = comment.likedBy.toMutableList()

        if (likedList.contains(currentUid)) {
            likedList.remove(currentUid)
            comment.likesCount = maxOf(0, comment.likesCount - 1)
            commentsRef.document(comment.id).set(
                mapOf(
                    "likesCount" to FieldValue.increment(-1),
                    "likedBy" to FieldValue.arrayRemove(currentUid)
                ),
                SetOptions.merge()
            )
        } else {
            likedList.add(currentUid)
            comment.likesCount += 1
            commentsRef.document(comment.id).set(
                mapOf(
                    "likesCount" to FieldValue.increment(1),
                    "likedBy" to FieldValue.arrayUnion(currentUid)
                ),
                SetOptions.merge()
            )
        }
        comment.likedBy = likedList
        commentAdapter.notifyDataSetChanged()
    }

    private fun submitCommentToFirestore(
        currentUser: FirebaseUser?,
        post: ForumPost,
        text: String,
        replyToAuthor: String,
        commentsRef: CollectionReference,
        etInput: EditText,
        commentAdapter: ForumCommentAdapter
    ) {
        val initialAuthorName = currentUser?.displayName?.ifBlank { "Farmer" } ?: "Farmer"
        val userId = currentUser?.uid.orEmpty()
        val initialPhotoUrl = currentUser?.photoUrl?.toString().orEmpty()

        fun executeCommentWrite(finalAuthorName: String, finalPhotoUrl: String) {
            if (_binding == null) return

            val commentId = "c_${System.currentTimeMillis()}"
            val newComment = ForumComment(
                id = commentId,
                userId = userId,
                authorName = finalAuthorName,
                authorPhotoUrl = finalPhotoUrl,
                text = text,
                replyToAuthor = replyToAuthor,
                timestamp = Timestamp.now()
            )
            etInput.setText("")
            ForumCacheHelper.saveLocalComment(requireContext(), post.id, newComment)
            commentAdapter.addComment(newComment)

            post.repliesCount = ForumCacheHelper.getLocalReplyCount(requireContext(), post.id, post.repliesCount)
            localCommentCounts[post.id] = post.repliesCount
            adapter?.notifyDataSetChanged()

            Toast.makeText(requireContext(), "💬 Comment posted!", Toast.LENGTH_SHORT).show()

            val commentMap = hashMapOf(
                "id" to commentId,
                "userId" to userId,
                "authorName" to finalAuthorName,
                "authorPhotoUrl" to finalPhotoUrl,
                "text" to text,
                "replyToAuthor" to replyToAuthor,
                "timestamp" to Timestamp.now(),
                "likesCount" to 0,
                "likedBy" to emptyList<String>()
            )

            val postDocRef = firestore.collection("forum_posts").document(post.id)
            postDocRef.set(mapOf("repliesCount" to FieldValue.increment(1)), SetOptions.merge()).addOnCompleteListener {
                commentsRef.document(commentId).set(commentMap, SetOptions.merge())
            }
        }

        if (initialPhotoUrl.isNotBlank()) {
            executeCommentWrite(initialAuthorName, initialPhotoUrl)
        } else if (userId.isNotBlank()) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val dbName = userDoc.getString("displayName").orEmpty().ifBlank { initialAuthorName }
                    val dbPhoto = userDoc.getString("photoUrl").orEmpty()
                    executeCommentWrite(dbName, dbPhoto)
                }
                .addOnFailureListener {
                    executeCommentWrite(initialAuthorName, "")
                }
        } else {
            executeCommentWrite(initialAuthorName, "")
        }
    }

    private fun confirmAndDeleteForumComment(
        post: ForumPost,
        comment: ForumComment,
        commentsRef: CollectionReference,
        commentAdapter: ForumCommentAdapter
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                if (comment.id.isNotBlank()) {
                    commentsRef.document(comment.id).delete()
                }
                commentAdapter.removeComment(comment)
                if (post.repliesCount > 0) {
                    post.repliesCount -= 1
                    localCommentCounts[post.id] = post.repliesCount
                    adapter?.notifyDataSetChanged()
                }
                firestore.collection("forum_posts").document(post.id)
                    .set(mapOf("repliesCount" to FieldValue.increment(-1)), SetOptions.merge())
                Toast.makeText(requireContext(), "Comment deleted by admin", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPostOptionsMenu(post: ForumPost, anchorView: View) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isAuthor = currentUser != null && currentUser.uid == post.userId
        val isAuthorOrAdmin = isUserAdmin || isAuthor

        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val popupWidth = (135 * density).toInt()

        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_compact_popup)
            val pVert = (3 * density).toInt()
            val pHoriz = (2 * density).toInt()
            setPadding(pHoriz, pVert, pHoriz, pVert)
        }

        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12 * density
            isOutsideTouchable = true
            isFocusable = true
        }

        fun createOptionRow(
            iconRes: Int,
            iconTint: Int,
            text: String,
            textColor: Int,
            onClick: () -> Unit
        ): View {
            val row = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                val padH = (12 * density).toInt()
                val padV = (8 * density).toInt()
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
            }

            val iv = ImageView(context).apply {
                val size = (16 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (10 * density).toInt()
                }
                setImageResource(iconRes)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(iconTint))
            }

            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                this.text = text
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
            }

            row.addView(iv)
            row.addView(tv)

            row.setOnClickListener {
                popupWindow.dismiss()
                onClick()
            }

            return row
        }

        fun addDivider() {
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * density).toInt()
                ).apply {
                    val mH = (8 * density).toInt()
                    val mV = (2 * density).toInt()
                    setMargins(mH, mV, mH, mV)
                }
                setBackgroundColor(Color.parseColor("#323236"))
            }
            popupView.addView(divider)
        }

        var addedCount = 0

        if (isAuthorOrAdmin) {
            val editRow = createOptionRow(
                iconRes = R.drawable.ic_edit,
                iconTint = Color.parseColor("#81C784"),
                text = "Edit Post",
                textColor = Color.WHITE
            ) {
                openCreatePostBottomSheet(initialDesc = post.description, initialCategory = post.category, editingPost = post)
            }
            popupView.addView(editRow)
            addedCount++

            if (addedCount > 0) addDivider()

            val deleteRow = createOptionRow(
                iconRes = R.drawable.ic_delete,
                iconTint = Color.parseColor("#FF453A"),
                text = "Delete Post",
                textColor = Color.parseColor("#FF453A")
            ) {
                confirmAndDeleteForumPost(post)
            }
            popupView.addView(deleteRow)
            addedCount++
        }

        if (!isAuthor) {
            if (addedCount > 0) addDivider()
            val reportRow = createOptionRow(
                iconRes = R.drawable.ic_info,
                iconTint = Color.parseColor("#FF9800"),
                text = "Report Post",
                textColor = Color.WHITE
            ) {
                showReportPostDialog(post.id, post.userId, post.description.take(50))
            }
            popupView.addView(reportRow)
            addedCount++
        }

        if (isUserAdmin) {
            if (addedCount > 0) addDivider()
            val blockRow = createOptionRow(
                iconRes = R.drawable.ic_close,
                iconTint = Color.parseColor("#E53935"),
                text = "Block User",
                textColor = Color.parseColor("#E53935")
            ) {
                confirmAndBlockUser(post.userId, post.authorName)
            }
            popupView.addView(blockRow)
            addedCount++
        }

        val anchorWidth = if (anchorView.width > 0) anchorView.width else (24 * density).toInt()
        val xOffset = -(popupWidth - anchorWidth)
        popupWindow.showAsDropDown(anchorView, xOffset, (4 * density).toInt())
    }

    private fun showReportPostDialog(postId: String, authorUid: String, postSnippet: String) {
        val reasons = arrayOf(
            "Inappropriate or Abusive Content",
            "Spam or Commercial Scam",
            "Harassment or Hate Speech",
            "False or Misleading Information",
            "Other Policy Violation"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🚩 Report Content")
            .setItems(reasons) { _, which ->
                val chosenReason = reasons[which]
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().ifBlank { "anonymous" }

                val reportData = hashMapOf(
                    "postId" to postId,
                    "authorUid" to authorUid,
                    "reporterUid" to currentUid,
                    "reason" to chosenReason,
                    "postSnippet" to postSnippet,
                    "timestamp" to Timestamp.now()
                )

                firestore.collection("forum_reports").add(reportData)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "🚩 Report submitted. Moderation team will review within 24 hours.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Report noted. Thank you.", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmAndBlockUser(authorUid: String, authorName: String) {
        if (authorUid.isBlank()) {
            Toast.makeText(requireContext(), "Unable to block anonymous user", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🚫 Block User")
            .setMessage("Are you sure you want to block $authorName? Their posts and comments will no longer be visible to you.")
            .setPositiveButton("Block") { _, _ ->
                ForumBlockHelper.blockUser(requireContext(), authorUid)
                Toast.makeText(requireContext(), "🚫 $authorName blocked. Posts hidden.", Toast.LENGTH_LONG).show()
                filterPostsByCategory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmAndDeleteForumPost(post: ForumPost) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                if (post.id.isNotBlank()) {
                    firestore.collection("forum_posts").document(post.id).delete()
                }
                allPostsList = allPostsList.filter { it.id != post.id }
                filterPostsByCategory()
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePostOnWhatsApp(post: ForumPost) {
        val appUrl = "https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"
        val ctx = requireContext()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        post.sharesCount += 1
        adapter?.notifyDataSetChanged()
        if (post.id.isNotBlank()) {
            firestore.collection("forum_posts").document(post.id)
                .set(mapOf("sharesCount" to FieldValue.increment(1)), SetOptions.merge())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val desc = if (activeLang != "en") TranslationHelper.translateText(ctx, post.description, activeLang) else post.description

            var mediaUri: Uri? = null
            var mimeType = "text/plain"

            if (post.photoUrl.isNotBlank()) {
                try {
                    val cachedFile = Glide.with(ctx.applicationContext)
                        .asFile()
                        .load(post.photoUrl)
                        .submit()
                        .get()

                    val jpegFile = File(ctx.cacheDir, "shared_forum_${System.currentTimeMillis()}.jpg")
                    cachedFile.copyTo(jpegFile, overwrite = true)

                    val authority = "${ctx.packageName}.fileprovider"
                    mediaUri = FileProvider.getUriForFile(ctx, authority, jpegFile)
                    mimeType = "image/jpeg"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var extraMediaText = ""
            if (post.videoUrl.isNotBlank() && mediaUri == null) {
                extraMediaText = "\n🎥 Watch Attached Video: ${post.videoUrl}"
            }

            val message = "👨‍🌾 *Kisan Forum Post*\n👤 ${post.authorName} (${post.authorLocation})\n\n$desc$extraMediaText\n\n📲 Join Discussion on Kisan Kendra App:\n$appUrl"

            withContext(Dispatchers.Main) {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        if (mediaUri != null) {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, mediaUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            type = "text/plain"
                        }
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share Forum Post via")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Could not share post", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showOriginalMediaDialog(url: String, isVideo: Boolean) {
        if (url.isBlank()) return

        if (isVideo) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_full_video_player, null)
            val videoView = dialogView.findViewById<VideoView>(R.id.vvFullVideo)
            val pbLoading = dialogView.findViewById<ProgressBar>(R.id.pbVideoLoading)
            val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseVideoPlayer)

            val mediaController = MediaController(requireContext())
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)
            videoView.setVideoURI(Uri.parse(url))

            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(dialogView)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            videoView.setOnPreparedListener { mp ->
                pbLoading.visibility = View.GONE
                mp.isLooping = true
                videoView.start()
            }

            videoView.setOnErrorListener { _, _, _ ->
                pbLoading.visibility = View.GONE
                Toast.makeText(requireContext(), "Unable to play video in-app", Toast.LENGTH_SHORT).show()
                true
            }

            btnClose.setOnClickListener {
                videoView.stopPlayback()
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                videoView.stopPlayback()
            }

            dialog.show()
        } else {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_full_media_viewer, null)
            val ivFull = dialogView.findViewById<ImageView>(R.id.ivFullImage)
            val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseFullMedia)

            Glide.with(requireContext())
                .load(url)
                .fitCenter()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(ivFull)

            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(dialogView)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            btnClose.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private val PREFS_DRAFT = "forum_draft_prefs"

    private fun getDraftKey(suffix: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return "${suffix}_$uid"
    }

    private fun savePostDraft(desc: String, category: String, mediaUri: Uri?) {
        val safeUri = if (mediaUri != null) MediaUtils.copyUriToAppCache(requireContext(), mediaUri) else null
        val prefs = requireContext().getSharedPreferences(PREFS_DRAFT, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(getDraftKey("draft_desc"), desc)
            putString(getDraftKey("draft_category"), category)
            putString(getDraftKey("draft_uri"), safeUri?.toString().orEmpty())
            apply()
        }
        Toast.makeText(requireContext(), "💾 Draft saved!", Toast.LENGTH_SHORT).show()
    }

    private fun getSavedDraft(): Triple<String, String, Uri?>? {
        val prefs = requireContext().getSharedPreferences(PREFS_DRAFT, Context.MODE_PRIVATE)
        val desc = prefs.getString(getDraftKey("draft_desc"), null) ?: return null
        if (desc.isBlank()) return null
        val category = prefs.getString(getDraftKey("draft_category"), "General Farming") ?: "General Farming"
        val uriStr = prefs.getString(getDraftKey("draft_uri"), null)
        var uri = if (!uriStr.isNullOrBlank()) Uri.parse(uriStr) else null
        if (uri != null && !MediaUtils.isUriAccessible(requireContext(), uri)) {
            uri = MediaUtils.copyUriToAppCache(requireContext(), uri)
            if (uri != null && !MediaUtils.isUriAccessible(requireContext(), uri)) {
                uri = null
            }
        }
        return Triple(desc, category, uri)
    }

    private fun clearSavedDraft() {
        val prefs = requireContext().getSharedPreferences(PREFS_DRAFT, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(getDraftKey("draft_desc"))
            remove(getDraftKey("draft_category"))
            remove(getDraftKey("draft_uri"))
            apply()
        }
    }

    private fun showCreatePostDialog(preAttachedUri: Uri? = null) {
        val savedDraft = if (preAttachedUri == null) getSavedDraft() else null
        if (savedDraft != null && preAttachedUri == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("💾 Saved Draft Found")
                .setMessage("You have an unsaved draft post. Would you like to resume editing your draft or start a new post?")
                .setPositiveButton("Resume Draft") { _, _ ->
                    openCreatePostBottomSheet(
                        initialDesc = savedDraft.first,
                        initialCategory = savedDraft.second,
                        preAttachedUri = savedDraft.third
                    )
                }
                .setNegativeButton("Start New") { _, _ ->
                    clearSavedDraft()
                    openCreatePostBottomSheet(preAttachedUri = null)
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            openCreatePostBottomSheet(preAttachedUri = preAttachedUri)
        }
    }

    private fun openCreatePostBottomSheet(
        initialDesc: String = "",
        initialCategory: String = "General Farming",
        preAttachedUri: Uri? = null,
        editingPost: ForumPost? = null
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_create_forum_post, null)
        val ivAvatar = dialogView.findViewById<ImageView>(R.id.ivPostDialogAvatar)
        val tvUserName = dialogView.findViewById<TextView>(R.id.tvPostDialogUserName)
        val chipGroupCategories = dialogView.findViewById<ChipGroup>(R.id.chipGroupDialogCategories)
        val actvCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.actvDialogCategory)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDialogQuestionDesc)
        val layoutMediaContainer = dialogView.findViewById<FrameLayout>(R.id.layoutMediaPreviewContainer)
        val ivMediaPreview = dialogView.findViewById<ImageView>(R.id.ivDialogMediaPreview)
        val btnRemoveMedia = dialogView.findViewById<ImageButton>(R.id.btnRemoveMedia)

        val btnCamera = dialogView.findViewById<MaterialButton>(R.id.btnDialogCamera)
        val btnVideo = dialogView.findViewById<MaterialButton>(R.id.btnDialogVideo)
        val btnGalleryPhoto = dialogView.findViewById<MaterialButton>(R.id.btnDialogGalleryPhoto)
        val btnGalleryVideo = dialogView.findViewById<MaterialButton>(R.id.btnDialogGalleryVideo)
        val btnSaveDraft = dialogView.findViewById<MaterialButton>(R.id.btnSaveDraft)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitQuestion)

        val isEditMode = editingPost != null
        var isExistingMediaRemoved = false

        val currentUser = FirebaseAuth.getInstance().currentUser
        val userName = currentUser?.displayName?.ifBlank { "Farmer" } ?: "Farmer"
        tvUserName.text = if (isEditMode) "$userName • Edit Post" else userName

        if (isEditMode) {
            btnSubmit.text = "Update Post"
            btnSaveDraft.visibility = View.GONE
        }

        val photoUrl = currentUser?.photoUrl?.toString().orEmpty()
        if (photoUrl.isNotBlank()) {
            Glide.with(requireContext()).load(photoUrl).circleCrop().into(ivAvatar)
        }
        ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        val descToSet = if (isEditMode) editingPost!!.description else initialDesc
        if (descToSet.isNotBlank()) {
            etDesc.setText(descToSet)
        }

        selectedMediaUri = preAttachedUri ?: if (!isEditMode) selectedMediaUri else null
        activeDialogMediaPreview = ivMediaPreview
        activeDialogMediaContainer = layoutMediaContainer

        val existingMediaUrl = editingPost?.photoUrl?.ifBlank { editingPost.videoUrl }.orEmpty()

        if (selectedMediaUri != null) {
            layoutMediaContainer.visibility = View.VISIBLE
            Glide.with(requireContext()).load(selectedMediaUri).centerCrop().into(ivMediaPreview)
        } else if (isEditMode && existingMediaUrl.isNotBlank()) {
            layoutMediaContainer.visibility = View.VISIBLE
            Glide.with(requireContext()).load(existingMediaUrl).centerCrop().into(ivMediaPreview)
        } else {
            layoutMediaContainer.visibility = View.GONE
        }

        btnRemoveMedia.setOnClickListener {
            selectedMediaUri = null
            isExistingMediaRemoved = true
            layoutMediaContainer.visibility = View.GONE
        }

        val ctx = requireContext()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.Main) {
            val tvSelectCategoryHeader = dialogView.findViewById<TextView>(R.id.tvSelectCategoryHeader)
            val tvMediaHeader = dialogView.findViewById<TextView>(R.id.tvMediaHeader)
            val chipGeneral = dialogView.findViewById<Chip>(R.id.chipDialogGeneral)
            val chipDiseases = dialogView.findViewById<Chip>(R.id.chipDialogDiseases)
            val chipFertilizers = dialogView.findViewById<Chip>(R.id.chipDialogFertilizers)
            val chipMandi = dialogView.findViewById<Chip>(R.id.chipDialogMandi)
            val chipSchemes = dialogView.findViewById<Chip>(R.id.chipDialogSchemes)
            val chipSeeds = dialogView.findViewById<Chip>(R.id.chipDialogSeeds)
            val chipOrganic = dialogView.findViewById<Chip>(R.id.chipDialogOrganic)
            val chipMachinery = dialogView.findViewById<Chip>(R.id.chipDialogMachinery)
            val chipWeather = dialogView.findViewById<Chip>(R.id.chipDialogWeather)
            val chipDairy = dialogView.findViewById<Chip>(R.id.chipDialogDairy)

            if (tvSelectCategoryHeader != null) {
                tvSelectCategoryHeader.text = "Select Category:"
            }
            if (tvMediaHeader != null) {
                tvMediaHeader.text = TranslationHelper.translateText(ctx, "Add Media to post", activeLang) + ":"
            }

            etDesc.hint = TranslationHelper.translateText(ctx, "Write your question or thoughts here", activeLang) + "..."

            btnCamera.text = "📷 " + TranslationHelper.translateText(ctx, "Live Photo", activeLang)
            btnVideo.text = "📹 " + TranslationHelper.translateText(ctx, "Record Video", activeLang)
            btnGalleryPhoto.text = "🖼️ " + TranslationHelper.translateText(ctx, "Gallery Photo", activeLang)
            btnGalleryVideo.text = "🎥 " + TranslationHelper.translateText(ctx, "Gallery Video", activeLang)
            btnSaveDraft.text = "💾 Save Draft"
            btnSubmit.text = TranslationHelper.translateText(ctx, if (isEditMode) "Update Post" else "Post", activeLang)

            if (chipGeneral != null) chipGeneral.text = TranslationHelper.translateText(ctx, "General Farming", activeLang)
            if (chipDiseases != null) chipDiseases.text = TranslationHelper.translateText(ctx, "Crop Diseases", activeLang)
            if (chipFertilizers != null) chipFertilizers.text = TranslationHelper.translateText(ctx, "Fertilizers", activeLang)
            if (chipMandi != null) chipMandi.text = TranslationHelper.translateText(ctx, "Mandi Advice", activeLang)
            if (chipSchemes != null) chipSchemes.text = TranslationHelper.translateText(ctx, "Govt Schemes", activeLang)
            if (chipSeeds != null) chipSeeds.text = TranslationHelper.translateText(ctx, "Seeds & Varieties", activeLang)
            if (chipOrganic != null) chipOrganic.text = TranslationHelper.translateText(ctx, "Organic Farming", activeLang)
            if (chipMachinery != null) chipMachinery.text = TranslationHelper.translateText(ctx, "Machinery & Tractors", activeLang)
            if (chipWeather != null) chipWeather.text = TranslationHelper.translateText(ctx, "Weather", activeLang)
            if (chipDairy != null) chipDairy.text = TranslationHelper.translateText(ctx, "Dairy Farming", activeLang)
        }

        var selectedCategoryName = if (isEditMode) editingPost!!.category else initialCategory
        val initialChipId = getDialogChipIdForCategoryName(selectedCategoryName)
        val initialChip = dialogView.findViewById<Chip>(initialChipId)
        if (initialChip != null) {
            initialChip.isChecked = true
        }
        selectedCategoryName = getCategoryNameForDialogChipId(initialChipId)

        // Map Category Chip Selection
        chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipDialogGeneral
            selectedCategoryName = getCategoryNameForDialogChipId(checkedId)
            actvCategory.setText(selectedCategoryName, false)
        }

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        dialog.setOnDismissListener {
            activeDialogMediaPreview = null
            activeDialogMediaContainer = null
        }

        btnCamera.setOnClickListener {
            val currentText = etDesc.text.toString().trim()
            if (currentText.isNotBlank() && !isEditMode) savePostDraft(currentText, selectedCategoryName, selectedMediaUri)
            dialog.dismiss()
            launchCameraCapture()
        }

        btnVideo.setOnClickListener {
            val currentText = etDesc.text.toString().trim()
            if (currentText.isNotBlank() && !isEditMode) savePostDraft(currentText, selectedCategoryName, selectedMediaUri)
            dialog.dismiss()
            launchVideoRecord()
        }

        btnGalleryPhoto.setOnClickListener {
            pickGalleryMediaLauncher.launch("image/*")
        }

        btnGalleryVideo.setOnClickListener {
            pickGalleryMediaLauncher.launch("video/*")
        }

        btnSaveDraft.setOnClickListener {
            val desc = etDesc.text.toString().trim()
            if (desc.isBlank() && selectedMediaUri == null) {
                Toast.makeText(requireContext(), "Nothing to save as draft", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePostDraft(desc, selectedCategoryName, selectedMediaUri)
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val desc = etDesc.text.toString().trim()
            val category = selectedCategoryName.ifBlank { "General Farming" }

            val hasMedia = selectedMediaUri != null ||
                    (isEditMode && !isExistingMediaRemoved && existingMediaUrl.isNotBlank())

            if (desc.isBlank() && !hasMedia) {
                Toast.makeText(requireContext(), "Please write what's on your mind or add a photo/video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isEditMode && editingPost != null) {
                uploadMediaAndUpdatePost(dialog, category, desc, selectedMediaUri, editingPost, isExistingMediaRemoved)
            } else {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    FirebaseAuth.getInstance().signInAnonymously()
                        .addOnSuccessListener {
                            uploadMediaAndSubmit(dialog, category, desc, selectedMediaUri)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Sign in error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    uploadMediaAndSubmit(dialog, category, desc, selectedMediaUri)
                }
            }
        }

        dialog.show()
    }

    private fun uploadMediaAndSubmit(dialog: Dialog, category: String, desc: String, mediaUri: Uri?) {
        val storageRef = FirebaseStorage.getInstance().reference

        if (mediaUri != null) {
            val safeUri = MediaUtils.copyUriToAppCache(requireContext(), mediaUri) ?: mediaUri

            val resolvedMime = try {
                if (safeUri.scheme == "content") {
                    requireContext().contentResolver.getType(safeUri)
                } else null
            } catch (_: Exception) {
                null
            }

            val isVideo = (resolvedMime != null && resolvedMime.startsWith("video/")) ||
                          safeUri.toString().contains("video") ||
                          safeUri.path?.endsWith(".mp4") == true ||
                          safeUri.path?.endsWith(".mkv") == true ||
                          safeUri == cameraVideoUri

            val finalMime = resolvedMime?.ifBlank { null }
                ?: if (isVideo) "video/mp4" else "image/jpeg"

            val folder = if (isVideo) "forum_videos" else "forum_media"
            val ext = if (isVideo) ".mp4" else ".jpg"

            val metadata = StorageMetadata.Builder()
                .setContentType(finalMime)
                .build()

            val ref = storageRef.child("$folder/post_${System.currentTimeMillis()}$ext")
            ref.putFile(safeUri, metadata)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        val pUrl = if (isVideo) "" else downloadUri.toString()
                        val vUrl = if (isVideo) downloadUri.toString() else ""
                        executePostSubmission(dialog, category, desc, pUrl, vUrl)
                    }.addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Media uploaded, but URL failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        executePostSubmission(dialog, category, desc, "", "")
                    }
                }
                .addOnFailureListener { e ->
                    val errMsg = if (e.message?.contains("403") == true || e.message?.contains("permission") == true) {
                        "Firebase Storage Permission Denied (403). Update Storage Rules in Firebase Console."
                    } else {
                        "Media upload failed: ${e.localizedMessage}"
                    }
                    Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show()
                    executePostSubmission(dialog, category, desc, "", "")
                }
        } else {
            executePostSubmission(dialog, category, desc, "", "")
        }
    }

    private fun uploadMediaAndUpdatePost(
        dialog: Dialog,
        category: String,
        desc: String,
        mediaUri: Uri?,
        editingPost: ForumPost,
        isExistingMediaRemoved: Boolean
    ) {
        val storageRef = FirebaseStorage.getInstance().reference

        if (mediaUri != null) {
            val safeUri = MediaUtils.copyUriToAppCache(requireContext(), mediaUri) ?: mediaUri

            val resolvedMime = try {
                if (safeUri.scheme == "content") {
                    requireContext().contentResolver.getType(safeUri)
                } else null
            } catch (_: Exception) {
                null
            }

            val isVideo = (resolvedMime != null && resolvedMime.startsWith("video/")) ||
                          safeUri.toString().contains("video") ||
                          safeUri.path?.endsWith(".mp4") == true ||
                          safeUri.path?.endsWith(".mkv") == true ||
                          safeUri == cameraVideoUri

            val finalMime = resolvedMime?.ifBlank { null }
                ?: if (isVideo) "video/mp4" else "image/jpeg"

            val folder = if (isVideo) "forum_videos" else "forum_media"
            val ext = if (isVideo) ".mp4" else ".jpg"

            val metadata = StorageMetadata.Builder()
                .setContentType(finalMime)
                .build()

            val ref = storageRef.child("$folder/post_${System.currentTimeMillis()}$ext")
            ref.putFile(safeUri, metadata)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        val pUrl = if (isVideo) "" else downloadUri.toString()
                        val vUrl = if (isVideo) downloadUri.toString() else ""
                        executeUpdatePostInFirestore(dialog, editingPost.id, category, desc, pUrl, vUrl)
                    }.addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Media uploaded, but URL failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        executeUpdatePostInFirestore(dialog, editingPost.id, category, desc, "", "")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Media upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    executeUpdatePostInFirestore(dialog, editingPost.id, category, desc, "", "")
                }
        } else if (isExistingMediaRemoved) {
            executeUpdatePostInFirestore(dialog, editingPost.id, category, desc, "", "")
        } else {
            executeUpdatePostInFirestore(dialog, editingPost.id, category, desc, editingPost.photoUrl, editingPost.videoUrl)
        }
    }

    private fun executeUpdatePostInFirestore(
        dialog: Dialog,
        postId: String,
        category: String,
        desc: String,
        photoUrl: String,
        videoUrl: String
    ) {
        val updates = hashMapOf<String, Any>(
            "category" to category,
            "description" to desc,
            "photoUrl" to photoUrl,
            "videoUrl" to videoUrl
        )

        firestore.collection("forum_posts").document(postId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "🎉 Post updated!", Toast.LENGTH_SHORT).show()
                selectedMediaUri = null
                dialog.dismiss()
                fetchForumPosts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update post: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun executePostSubmission(dialog: Dialog, category: String, desc: String, photoUrl: String, videoUrl: String = "") {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val initialAuthorName = currentUser?.displayName?.ifBlank { "Farmer" } ?: "Farmer"
        val savedDist = LocationHelper.getSavedDistrict(requireContext()) ?: "India"
        val userId = currentUser?.uid.orEmpty()
        val initialPhotoUrl = currentUser?.photoUrl?.toString().orEmpty()

        fun createPostWithAuthorData(finalAuthorName: String, finalPhotoUrl: String) {
            if (_binding == null) return
            val newPost = hashMapOf(
                "userId" to userId,
                "authorName" to finalAuthorName,
                "authorLocation" to savedDist,
                "authorPhotoUrl" to finalPhotoUrl,
                "category" to category,
                "description" to desc,
                "photoUrl" to photoUrl,
                "videoUrl" to videoUrl,
                "likesCount" to 1,
                "repliesCount" to 0,
                "timestamp" to Timestamp.now()
            )

            firestore.collection("forum_posts")
                .add(newPost)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "🎉 Posted!", Toast.LENGTH_LONG).show()
                    selectedMediaUri = null
                    clearSavedDraft()
                    dialog.dismiss()
                    fetchForumPosts()
                    updateCreationBarUserInfo()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to post: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        if (initialPhotoUrl.isNotBlank()) {
            createPostWithAuthorData(initialAuthorName, initialPhotoUrl)
        } else if (userId.isNotBlank()) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val dbName = userDoc.getString("displayName").orEmpty().ifBlank { initialAuthorName }
                    val dbPhoto = userDoc.getString("photoUrl").orEmpty()
                    createPostWithAuthorData(dbName, dbPhoto)
                }
                .addOnFailureListener {
                    createPostWithAuthorData(initialAuthorName, "")
                }
        } else {
            createPostWithAuthorData(initialAuthorName, "")
        }
    }

    private fun getCategoryNameForDialogChipId(checkedId: Int): String {
        return when (checkedId) {
            R.id.chipDialogDiseases -> "Crop Diseases"
            R.id.chipDialogFertilizers -> "Fertilizers"
            R.id.chipDialogMandi -> "Mandi Advice"
            R.id.chipDialogSchemes -> "Govt Schemes"
            R.id.chipDialogSeeds -> "Seeds & Varieties"
            R.id.chipDialogOrganic -> "Organic Farming"
            R.id.chipDialogMachinery -> "Machinery & Tractors"
            R.id.chipDialogWeather -> "Weather"
            R.id.chipDialogDairy -> "Dairy Farming"
            else -> "General Farming"
        }
    }

    private fun getDialogChipIdForCategoryName(categoryName: String): Int {
        val cat = categoryName.trim()
        return when {
            cat.contains("Disease", ignoreCase = true) || cat.contains("बीमारी", ignoreCase = true) || cat.contains("ਰੋਗ", ignoreCase = true) -> R.id.chipDialogDiseases
            cat.contains("Fertilizer", ignoreCase = true) || cat.contains("खाद", ignoreCase = true) || cat.contains("उर्वरक", ignoreCase = true) -> R.id.chipDialogFertilizers
            cat.contains("Mandi", ignoreCase = true) || cat.contains("मंडी", ignoreCase = true) -> R.id.chipDialogMandi
            cat.contains("Scheme", ignoreCase = true) || cat.contains("Yojana", ignoreCase = true) || cat.contains("योजना", ignoreCase = true) -> R.id.chipDialogSchemes
            cat.contains("Seed", ignoreCase = true) || cat.contains("बीज", ignoreCase = true) -> R.id.chipDialogSeeds
            cat.contains("Organic", ignoreCase = true) || cat.contains("जैविक", ignoreCase = true) -> R.id.chipDialogOrganic
            cat.contains("Machinery", ignoreCase = true) || cat.contains("Tractor", ignoreCase = true) || cat.contains("ट्रैक्टर", ignoreCase = true) || cat.contains("मशीनरी", ignoreCase = true) -> R.id.chipDialogMachinery
            cat.contains("Weather", ignoreCase = true) || cat.contains("मौसम", ignoreCase = true) -> R.id.chipDialogWeather
            cat.contains("Dairy", ignoreCase = true) || cat.contains("पशु", ignoreCase = true) -> R.id.chipDialogDairy
            else -> R.id.chipDialogGeneral
        }
    }

    override fun onDestroyView() {
        alertListenerRegistration?.remove()
        alertListenerRegistration = null
        super.onDestroyView()
        forumRunningBorderDrawable?.stop()
        forumRunningBorderDrawable = null
        activeDialogMediaPreview = null
        activeDialogMediaContainer = null
        _binding = null
    }
}
