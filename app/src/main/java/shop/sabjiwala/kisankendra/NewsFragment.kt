package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// 🔹 Firebase Imports
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🔹 View Binding & Model Imports
import shop.sabjiwala.kisankendra.databinding.FragmentNewsBinding
import shop.sabjiwala.kisankendra.utils.ShimmerHelper
import shop.sabjiwala.kisankendra.model.ForumComment
import shop.sabjiwala.kisankendra.model.NewsItem
import shop.sabjiwala.kisankendra.utils.ForumCacheHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.ScreenAlertHelper
import com.google.firebase.firestore.ListenerRegistration
import shop.sabjiwala.kisankendra.utils.MarketTickerHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.NewsCacheHelper
import shop.sabjiwala.kisankendra.utils.RichTextHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var allNewsList: List<NewsItem> = emptyList()
    private var adapter: NewsAdapter? = null
    private var isUserAdmin = false
    private var alertListenerRegistration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertListenerRegistration = ScreenAlertHelper.setupScreenAlert(requireContext(), "news_screen", binding.alertBanner)

        binding.recyclerViewNews.layoutManager = LinearLayoutManager(requireContext())
        adapter = NewsAdapter(
            newsList = emptyList(),
            onLikeClick = { newsItem -> handleLikeNews(newsItem) },
            onCommentClick = { newsItem -> showNewsCommentsDialog(newsItem) },
            onShareClick = { newsItem -> shareOnWhatsApp(newsItem) },
            onItemClick = { newsItem -> openNewsDetail(newsItem) },
            isUserAdmin = isUserAdmin,
            onAdminEditClick = { newsItem -> editAdminNews(newsItem) },
            onAdminDeleteClick = { newsItem -> deleteAdminNews(newsItem) }
        )
        binding.recyclerViewNews.adapter = adapter

        binding.recyclerViewNews.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
            binding.recyclerViewNews.smoothScrollToPosition(0)
        }

        ShimmerHelper.setupSwipeRefresh(binding.swipeRefresh)
        binding.swipeRefresh.setOnRefreshListener { fetchNewsFromFirebase(forceRefresh = true) }

        binding.fabAddNews.setOnClickListener {
            startActivity(Intent(requireContext(), AdminCreateNewsActivity::class.java))
        }

        setupMarketTicker()
        setupCategoryChips()
        fetchNewsFromFirebase()
        checkAdminRole()
    }

    private fun setupMarketTicker() {
        if (_binding == null) return
        val ctx = context ?: return

        // 1. Immediately set up cached market items (<1ms)
        val cachedItems = MarketTickerHelper.getCachedMarketItems(ctx)
        MarketTickerHelper.setupTickerTextView(
            binding.tvMarketTicker,
            MarketTickerHelper.buildTickerHtml(cachedItems)
        )

        // Pulse live red badge dot
        MediaUtils.startCompactGreenPulseAnimation(binding.viewLiveDot)

        // 2. Fetch fresh live market values in background
        lifecycleScope.launch(Dispatchers.IO) {
            val safeCtx = context ?: return@launch
            val liveItems = MarketTickerHelper.fetchLiveMarketTicker(safeCtx)
            val html = MarketTickerHelper.buildTickerHtml(liveItems)
            withContext(Dispatchers.Main) {
                if (_binding != null && isAdded) {
                    MarketTickerHelper.setupTickerTextView(binding.tvMarketTicker, html)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchNewsFromFirebase()
        checkAdminRole()
    }

    private fun checkAdminRole() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (_binding == null || currentUser == null) {
            _binding?.fabAddNews?.visibility = View.GONE
            isUserAdmin = false
            adapter?.setAdminState(false)
            return
        }

        val uid = currentUser.uid

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener
                val role = userDoc.getString("role")
                val isAdmin = userDoc.getBoolean("isAdmin") ?: false

                if (role.equals("admin", ignoreCase = true) || isAdmin) {
                    isUserAdmin = true
                    binding.fabAddNews.visibility = View.VISIBLE
                    adapter?.setAdminState(true)
                } else {
                    checkAdminCollectionFallback(uid)
                }
            }
            .addOnFailureListener {
                checkAdminCollectionFallback(uid)
            }
    }

    private fun checkAdminCollectionFallback(uid: String) {
        firestore.collection("admin").document(uid).get()
            .addOnSuccessListener { adminDoc ->
                if (_binding == null) return@addOnSuccessListener
                if (adminDoc.exists()) {
                    isUserAdmin = true
                    binding.fabAddNews.visibility = View.VISIBLE
                    adapter?.setAdminState(true)
                } else {
                    isUserAdmin = false
                    binding.fabAddNews.visibility = View.GONE
                    adapter?.setAdminState(false)
                }
            }
            .addOnFailureListener {
                isUserAdmin = false
                _binding?.fabAddNews?.visibility = View.GONE
                adapter?.setAdminState(false)
            }
    }

    private fun editAdminNews(newsItem: NewsItem) {
        val intent = Intent(requireContext(), AdminCreateNewsActivity::class.java).apply {
            putExtra(AdminCreateNewsActivity.EXTRA_EDIT_NEWS_ID, newsItem.id)
            putExtra(AdminCreateNewsActivity.EXTRA_TITLE, newsItem.title)
            putExtra(AdminCreateNewsActivity.EXTRA_CATEGORY, newsItem.category)
            putExtra(AdminCreateNewsActivity.EXTRA_DESCRIPTION, newsItem.description)
            putExtra(AdminCreateNewsActivity.EXTRA_IMAGE_URL, newsItem.imageUrl)
            putExtra(AdminCreateNewsActivity.EXTRA_IS_PINNED, newsItem.isPinned)
        }
        startActivity(intent)
    }

    private fun deleteAdminNews(newsItem: NewsItem) {
        if (newsItem.id.isBlank()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete News Article?")
            .setMessage("Are you sure you want to delete \"${newsItem.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("news").document(newsItem.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Article deleted", Toast.LENGTH_SHORT).show()
                        NewsCacheHelper.clearCache(requireContext())
                        fetchNewsFromFirebase(forceRefresh = true)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchNewsFromFirebase(forceRefresh: Boolean = false) {
        if (_binding == null) return
        val ctx = context ?: return

        if (!forceRefresh) {
            // 1. ⚡ INSTANT LOCAL CACHE LOAD (<10ms)
            val cachedNews = NewsCacheHelper.getLocalNewsList(ctx)
            if (cachedNews.isNotEmpty()) {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                allNewsList = cachedNews.map { item ->
                    if (currentUid.isNotBlank()) {
                        val isLiked = ForumCacheHelper.isUserLikedStatus(ctx, item.id, currentUid, item.likedBy.contains(currentUid))
                        val likedList = item.likedBy.toMutableList()
                        if (isLiked && !likedList.contains(currentUid)) {
                            likedList.add(currentUid)
                        } else if (!isLiked && likedList.contains(currentUid)) {
                            likedList.remove(currentUid)
                        }
                        item.likedBy = likedList
                    }
                    item.likesCount = ForumCacheHelper.getLocalLikeCount(ctx, item.id, item.likesCount)
                    item
                }
                filterNewsByCategory()
                binding.progressBarNews.visibility = View.GONE
            } else {
                binding.progressBarNews.visibility = View.VISIBLE
                ShimmerHelper.applyShimmer(binding.recyclerViewNews)
                allNewsList = emptyList()
            }

            // 2. ⚡ LIGHTWEIGHT DELTA CHECK (1 Read Cost)
            firestore.collection("news")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { topSnapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    binding.swipeRefresh.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.recyclerViewNews)

                    val topDoc = topSnapshot.documents.firstOrNull()
                    val topNewsId = topDoc?.id.orEmpty()
                    val localTopNewsId = allNewsList.firstOrNull()?.id.orEmpty()

                    // If local cache is up to date with server top news -> STOP (0 full list reads!)
                    if (topNewsId.isNotBlank() && topNewsId == localTopNewsId && allNewsList.isNotEmpty()) {
                        binding.progressBarNews.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    // Server has newer articles or cache is empty -> Fetch latest 30 articles
                    fetchFullNewsListFromFirestore(ctx)
                }
                .addOnFailureListener { exception ->
                    if (_binding == null) return@addOnFailureListener
                    binding.progressBarNews.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.recyclerViewNews)
                }
        } else {
            binding.swipeRefresh.isRefreshing = true
            ShimmerHelper.applyShimmer(binding.recyclerViewNews)
            fetchFullNewsListFromFirestore(ctx)
        }
    }

    private fun fetchFullNewsListFromFirestore(ctx: Context) {
        if (_binding == null) return
        firestore.collection("news")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBarNews.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                ShimmerHelper.stopShimmer(binding.recyclerViewNews)

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val fetchedList = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(NewsItem::class.java)?.let { rawItem ->
                        val item = if (rawItem.id.isBlank()) rawItem.copy(id = doc.id) else rawItem
                        if (currentUid.isNotBlank()) {
                            val isLiked = ForumCacheHelper.isUserLikedStatus(requireContext(), item.id, currentUid, item.likedBy.contains(currentUid))
                            val likedList = item.likedBy.toMutableList()
                            if (isLiked && !likedList.contains(currentUid)) {
                                likedList.add(currentUid)
                            } else if (!isLiked && likedList.contains(currentUid)) {
                                likedList.remove(currentUid)
                            }
                            item.likedBy = likedList
                        }
                        item.likesCount = ForumCacheHelper.getLocalLikeCount(requireContext(), item.id, item.likesCount)
                        item
                    }
                }

                if (fetchedList.isNotEmpty()) {
                    allNewsList = fetchedList
                    NewsCacheHelper.saveNewsListLocally(ctx, fetchedList)
                    filterNewsByCategory()
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBarNews.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                ShimmerHelper.stopShimmer(binding.recyclerViewNews)
            }
    }

    private fun saveNewsSessionState(checkedChipId: Int) {
        val ctx = context ?: return
        ctx.getSharedPreferences("news_session_prefs", Context.MODE_PRIVATE).edit()
            .putInt("news_active_chip_id", checkedChipId)
            .apply()
    }

    private fun restoreNewsSessionState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("news_session_prefs", Context.MODE_PRIVATE)
        val savedChipId = prefs.getInt("news_active_chip_id", 0)
        if (savedChipId != 0 && _binding != null) {
            binding.chipGroupCategories.check(savedChipId)
        }
    }

    private fun setupCategoryChips() {
        val ctx = context
        if (ctx != null) {
            val activeLang = LocaleHelper.getPersistedLanguage(ctx)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    TranslationHelper.syncMasterDictionaryFromFirestore(ctx, activeLang)
                    withContext(Dispatchers.Main) {
                        if (_binding != null && isAdded) {
                            translateNewsUiElements()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        translateNewsUiElements()
        restoreNewsSessionState()
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            saveNewsSessionState(checkedId)
            filterNewsByCategory(checkedId)
        }
    }

    private fun translateNewsUiElements() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (_binding == null || !isAdded) return@launch
            val ctx = context ?: return@launch
            val activeLang = LocaleHelper.getPersistedLanguage(ctx)

            val transAll = TranslationHelper.getDirectTranslation("All", activeLang, ctx) 
                ?: TranslationHelper.getDirectTranslation("All News", activeLang, ctx)
                ?: TranslationHelper.translateText(ctx, "All", activeLang)

            val transNews = TranslationHelper.getDirectTranslation("News", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "News", activeLang)

            val transGovtScheme = TranslationHelper.getDirectTranslation("Govt Scheme", activeLang, ctx) 
                ?: TranslationHelper.getDirectTranslation("Govt Schemes", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Govt Scheme", activeLang)

            val transAgriMarket = TranslationHelper.getDirectTranslation("Agri Market", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Agri Market", activeLang)

            val transAgriGyan = TranslationHelper.getDirectTranslation("Agri Gyan", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Agri Gyan", activeLang)

            val transCropProtection = TranslationHelper.getDirectTranslation("Crop Protection", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Crop Protection", activeLang)

            val transTechnology = TranslationHelper.getDirectTranslation("Technology", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Technology", activeLang)

            val transSuccessStories = TranslationHelper.getDirectTranslation("Success Stories", activeLang, ctx) 
                ?: TranslationHelper.translateText(ctx, "Success Stories", activeLang)

            if (_binding != null && isAdded) {
                binding.chipAll.text = transAll
                binding.chipNews.text = transNews
                binding.chipGovtScheme.text = transGovtScheme
                binding.chipAgriMarket.text = transAgriMarket
                binding.chipAgriGyan.text = transAgriGyan
                binding.chipCropProtection.text = transCropProtection
                binding.chipTechnology.text = transTechnology
                binding.chipSuccessStories.text = transSuccessStories
            }
        }
    }

    private fun filterNewsByCategory(checkedId: Int? = binding.chipGroupCategories.checkedChipId) {
        val selectedCategory = when (checkedId) {
            R.id.chipNews -> "News"
            R.id.chipGovtScheme -> "Govt Scheme"
            R.id.chipAgriMarket -> "Agri Market"
            R.id.chipAgriGyan -> "Agri Gyan"
            R.id.chipCropProtection -> "Crop Protection"
            R.id.chipTechnology -> "Technology"
            R.id.chipSuccessStories -> "Success Stories"
            else -> "All"
        }

        val filtered = if (selectedCategory == "All") {
            allNewsList
        } else {
            allNewsList.filter { item ->
                val cat = item.category.trim()
                cat.equals(selectedCategory, ignoreCase = true) ||
                (selectedCategory == "News" && (cat.contains("News", ignoreCase = true) || cat.contains("Samachar", ignoreCase = true) || cat.contains("Khabar", ignoreCase = true))) ||
                (selectedCategory == "Govt Scheme" && (cat.contains("Yojana", ignoreCase = true) || cat.contains("Scheme", ignoreCase = true))) ||
                (selectedCategory == "Agri Market" && (cat.contains("Market", ignoreCase = true) || cat.contains("Updates", ignoreCase = true))) ||
                (selectedCategory == "Agri Gyan" && (cat.contains("Gyan", ignoreCase = true) || cat.contains("Krishi", ignoreCase = true)))
            }
        }

        adapter?.updateData(filtered)
    }

    private fun handleLikeNews(item: NewsItem) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🔒 Sign In Required")
                .setMessage("Please sign in to like news posts. (लाइक करने के लिए साइन इन करें)")
                .setPositiveButton("Sign In") { _, _ ->
                    startActivity(Intent(requireContext(), SignInActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val currentUid = user.uid
        val likedList = item.likedBy.toMutableList()
        val isNowLiked: Boolean

        if (likedList.contains(currentUid)) {
            likedList.remove(currentUid)
            item.likesCount = maxOf(0, item.likesCount - 1)
            isNowLiked = false
        } else {
            likedList.add(currentUid)
            item.likesCount += 1
            isNowLiked = true
        }
        item.likedBy = likedList

        ForumCacheHelper.saveUserLikedStatus(requireContext(), item.id, currentUid, isNowLiked)
        ForumCacheHelper.saveLocalLikeCount(requireContext(), item.id, item.likesCount)

        adapter?.notifyItemLiked(item.id)

        if (item.id.isNotBlank()) {
            val docRef = firestore.collection("news").document(item.id)
            val updates = mapOf(
                "likesCount" to FieldValue.increment(if (isNowLiked) 1 else -1),
                "likedBy" to if (isNowLiked) FieldValue.arrayUnion(currentUid) else FieldValue.arrayRemove(currentUid)
            )
            docRef.update(updates).addOnFailureListener {
                docRef.set(updates, SetOptions.merge())
            }
        }
    }

    private fun showNewsCommentsDialog(item: NewsItem) {
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

        val commentsRef = firestore.collection("news").document(item.id).collection("comments")

        lateinit var commentAdapter: ForumCommentAdapter
        commentAdapter = ForumCommentAdapter(
            comments = emptyList(),
            isAdmin = isUserAdmin,
            currentUserId = currentUid,
            onDeleteClick = { commentToDelete ->
                confirmAndDeleteNewsComment(item, commentToDelete, commentsRef, commentAdapter)
            },
            onLikeClick = { commentToLike ->
                handleLikeNewsComment(commentsRef, commentToLike, commentAdapter)
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

        val listenerRegistration = commentsRef.addSnapshotListener { snapshot, _ ->
            if (_binding == null || snapshot == null) return@addSnapshotListener
            if (!snapshot.isEmpty) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ForumComment::class.java)?.let { comment ->
                        if (comment.id.isBlank()) comment.copy(id = doc.id) else comment
                    }
                }
                val sorted = list.sortedBy { it.timestamp?.toDate()?.time ?: 0L }
                commentAdapter.updateData(sorted)
                tvCommentsHeaderTitle?.text = if (sorted.isNotEmpty()) "Comments (${sorted.size})" else "Comments"
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

                submitNewsComment(currentUser, item, text, activeReplyAuthor, commentsRef, etInput, commentAdapter)
                activeReplyAuthor = ""
                layoutReplyingBanner.visibility = View.GONE
                etInput.hint = "Comment your thoughts..."
            }
        }

        dialog.show()
    }

    private fun handleLikeNewsComment(
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

    private fun submitNewsComment(
        currentUser: FirebaseUser?,
        item: NewsItem,
        text: String,
        replyToAuthor: String,
        commentsRef: CollectionReference,
        etInput: EditText,
        commentAdapter: ForumCommentAdapter
    ) {
        val initialAuthorName = currentUser?.displayName?.ifBlank { "Farmer" } ?: "Farmer"
        val userId = currentUser?.uid.orEmpty()
        val initialPhotoUrl = currentUser?.photoUrl?.toString().orEmpty()

        val commentId = "c_${System.currentTimeMillis()}"
        val newComment = ForumComment(
            id = commentId,
            userId = userId,
            authorName = initialAuthorName,
            authorPhotoUrl = initialPhotoUrl,
            text = text,
            replyToAuthor = replyToAuthor,
            timestamp = Timestamp.now()
        )
        etInput.setText("")
        commentAdapter.addComment(newComment)

        item.commentsCount += 1
        adapter?.notifyDataSetChanged()

        Toast.makeText(requireContext(), "💬 Comment posted!", Toast.LENGTH_SHORT).show()

        val commentMap = hashMapOf(
            "id" to commentId,
            "userId" to userId,
            "authorName" to initialAuthorName,
            "authorPhotoUrl" to initialPhotoUrl,
            "text" to text,
            "replyToAuthor" to replyToAuthor,
            "timestamp" to Timestamp.now(),
            "likesCount" to 0,
            "likedBy" to emptyList<String>()
        )

        val newsDocRef = firestore.collection("news").document(item.id)
        newsDocRef.set(mapOf("commentsCount" to FieldValue.increment(1)), SetOptions.merge()).addOnCompleteListener {
            commentsRef.document(commentId).set(commentMap, SetOptions.merge())
        }
    }

    private fun confirmAndDeleteNewsComment(
        item: NewsItem,
        comment: ForumComment,
        commentsRef: CollectionReference,
        commentAdapter: ForumCommentAdapter?
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Comment / कमेंट हटाएं")
            .setMessage("Are you sure you want to delete this comment? (क्या आप इस कमेंट को हटाना चाहते हैं?)")
            .setPositiveButton("Delete / हटाएं") { _, _ ->
                if (comment.id.isNotBlank()) {
                    commentsRef.document(comment.id).delete()
                }
                commentAdapter?.removeComment(comment)
                if (item.commentsCount > 0) {
                    item.commentsCount -= 1
                    adapter?.notifyDataSetChanged()
                }
                firestore.collection("news").document(item.id)
                    .set(mapOf("commentsCount" to FieldValue.increment(-1)), SetOptions.merge())
                Toast.makeText(requireContext(), "Comment deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareOnWhatsApp(item: NewsItem) {
        val ctx = requireContext()
        val activeLang = LocaleHelper.getPersistedLanguage(ctx)

        lifecycleScope.launch(Dispatchers.IO) {
            val title = if (activeLang != "en") TranslationHelper.translateText(ctx, item.title, activeLang) else item.title
            val desc = if (activeLang != "en") TranslationHelper.translateText(ctx, item.description, activeLang) else item.description

            val message = RichTextHelper.buildTruncatedNewsShareText(title, desc)

            var imageUri: Uri? = null
            if (item.imageUrl.isNotBlank()) {
                try {
                    val cachedFile = Glide.with(ctx.applicationContext)
                        .asFile()
                        .load(item.imageUrl)
                        .submit()
                        .get()

                    // Copy Glide cached file to explicit .jpg extension file for WhatsApp image recognition
                    val jpegFile = File(ctx.cacheDir, "shared_news_${System.currentTimeMillis()}.jpg")
                    cachedFile.copyTo(jpegFile, overwrite = true)

                    val authority = "${ctx.packageName}.fileprovider"
                    imageUri = FileProvider.getUriForFile(ctx, authority, jpegFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                item.sharesCount += 1
                adapter?.notifyDataSetChanged()
                if (item.id.isNotBlank()) {
                    firestore.collection("news").document(item.id)
                        .set(mapOf("sharesCount" to FieldValue.increment(1)), SetOptions.merge())
                }

                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        if (imageUri != null) {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            type = "text/plain"
                        }
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share News Article via")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Could not share news article", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openNewsDetail(item: NewsItem) {
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val formattedDate = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""

        NewsDetailActivity.start(
            context = requireContext(),
            title = item.title,
            desc = item.description,
            category = item.category.ifBlank { "Agriculture" },
            imageUrl = item.imageUrl,
            date = formattedDate,
            id = item.id,
            isPinned = item.isPinned
        )
    }

    override fun onDestroyView() {
        alertListenerRegistration?.remove()
        alertListenerRegistration = null
        super.onDestroyView()
        _binding = null
    }
}
