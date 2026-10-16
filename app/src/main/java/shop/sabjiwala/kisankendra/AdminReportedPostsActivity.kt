package shop.sabjiwala.kisankendra

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import shop.sabjiwala.kisankendra.databinding.ActivityAdminReportedPostsBinding
import shop.sabjiwala.kisankendra.model.ForumPost
import shop.sabjiwala.kisankendra.model.ForumReport
import shop.sabjiwala.kisankendra.model.ReportedPostItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.ShimmerHelper

class AdminReportedPostsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminReportedPostsBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: AdminReportedPostsAdapter
    private val reportedPostItems = mutableListOf<ReportedPostItem>()

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminReportedPostsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        checkAdminAndLoadReportedPosts()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdminReportedPosts)
        binding.toolbarAdminReportedPosts.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminReportedPostsAdapter(
            reportedPostItems,
            onDeletePostClick = { item -> confirmAndDeletePost(item) },
            onDismissReportClick = { item -> confirmAndDismissReport(item) }
        )
        binding.recyclerViewReportedPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewReportedPosts.adapter = adapter

        ShimmerHelper.setupSwipeRefresh(binding.swipeRefreshReportedPosts)
        binding.swipeRefreshReportedPosts.setOnRefreshListener {
            loadReportedPosts()
        }
    }

    private fun checkAdminAndLoadReportedPosts() {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role").orEmpty()
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                if (role.equals("admin", ignoreCase = true) || isAdmin) {
                    loadReportedPosts()
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
                if (adminDoc.exists()) {
                    loadReportedPosts()
                } else {
                    Toast.makeText(this, "Access Denied: Admin privileges required", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Admin verification failed", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadReportedPosts() {
        binding.swipeRefreshReportedPosts.isRefreshing = true
        ShimmerHelper.applyShimmer(binding.recyclerViewReportedPosts)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reportsSnapshot = firestore.collection("forum_reports")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val items = mutableListOf<ReportedPostItem>()

                for (doc in reportsSnapshot.documents) {
                    val report = doc.toObject(ForumReport::class.java)?.copy(id = doc.id) ?: continue

                    // Fetch associated ForumPost if postId is present
                    var forumPost: ForumPost? = null
                    if (report.postId.isNotBlank()) {
                        try {
                            val postDoc = firestore.collection("forum_posts")
                                .document(report.postId)
                                .get()
                                .await()

                            if (postDoc.exists()) {
                                forumPost = postDoc.toObject(ForumPost::class.java)?.copy(id = postDoc.id)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    items.add(ReportedPostItem(report = report, post = forumPost))
                }

                withContext(Dispatchers.Main) {
                    binding.swipeRefreshReportedPosts.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.recyclerViewReportedPosts)

                    reportedPostItems.clear()
                    reportedPostItems.addAll(items)
                    adapter.updateData(reportedPostItems)

                    if (reportedPostItems.isEmpty()) {
                        binding.layoutEmptyReportedPosts.visibility = View.VISIBLE
                        binding.recyclerViewReportedPosts.visibility = View.GONE
                    } else {
                        binding.layoutEmptyReportedPosts.visibility = View.GONE
                        binding.recyclerViewReportedPosts.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshReportedPosts.isRefreshing = false
                    ShimmerHelper.stopShimmer(binding.recyclerViewReportedPosts)
                    Toast.makeText(this@AdminReportedPostsActivity, "Failed to load reports: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmAndDeletePost(item: ReportedPostItem) {
        val postId = item.report.postId
        if (postId.isBlank()) {
            Toast.makeText(this, "Invalid post ID", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Delete Forum Post")
            .setMessage("Are you sure you want to delete this forum post? This action will permanently remove the post and clear all associated report requests.")
            .setPositiveButton("Delete Post") { _, _ ->
                deletePostAndReports(postId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePostAndReports(postId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete post document
                firestore.collection("forum_posts").document(postId).delete().await()

                // 2. Query and delete all report documents for this postId
                val reportsSnapshot = firestore.collection("forum_reports")
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()

                val batch = firestore.batch()
                for (doc in reportsSnapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminReportedPostsActivity, "Post deleted and report resolved", Toast.LENGTH_SHORT).show()
                    loadReportedPosts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminReportedPostsActivity, "Failed to delete post: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmAndDismissReport(item: ReportedPostItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Dismiss Report Request")
            .setMessage("Are you sure this report is not genuine? Dismissing it will delete the report request while keeping the forum post intact.")
            .setPositiveButton("Dismiss Report") { _, _ ->
                dismissReport(item.report.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dismissReport(reportDocId: String) {
        if (reportDocId.isBlank()) return

        firestore.collection("forum_reports").document(reportDocId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Report request dismissed", Toast.LENGTH_SHORT).show()
                loadReportedPosts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to dismiss report: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
