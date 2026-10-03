package shop.sabjiwala.kisankendra

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import shop.sabjiwala.kisankendra.databinding.ActivityAdminFeedbackBinding
import shop.sabjiwala.kisankendra.model.FeedbackItem
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.ShimmerHelper

class AdminFeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminFeedbackBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: AdminFeedbackAdapter
    private val feedbackList = mutableListOf<FeedbackItem>()

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        checkAdminAndLoadFeedbacks()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdminFeedback)
        binding.toolbarAdminFeedback.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminFeedbackAdapter(feedbackList) { feedbackItem ->
            showFeedbackActionDialog(feedbackItem)
        }
        binding.recyclerViewFeedback.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewFeedback.adapter = adapter

        ShimmerHelper.setupSwipeRefresh(binding.swipeRefreshFeedback)
        binding.swipeRefreshFeedback.setOnRefreshListener {
            loadFeedbacks()
        }
    }

    private fun checkAdminAndLoadFeedbacks() {
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
                    loadFeedbacks()
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
                    loadFeedbacks()
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

    private fun loadFeedbacks() {
        binding.swipeRefreshFeedback.isRefreshing = true
        ShimmerHelper.applyShimmer(binding.recyclerViewFeedback)
        firestore.collection("feedbacks")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                binding.swipeRefreshFeedback.isRefreshing = false
                ShimmerHelper.stopShimmer(binding.recyclerViewFeedback)
                feedbackList.clear()
                for (doc in result) {
                    val item = doc.toObject(FeedbackItem::class.java).copy(id = doc.id)
                    feedbackList.add(item)
                }
                adapter.updateData(feedbackList)

                if (feedbackList.isEmpty()) {
                    binding.layoutEmptyFeedback.visibility = View.VISIBLE
                    binding.recyclerViewFeedback.visibility = View.GONE
                } else {
                    binding.layoutEmptyFeedback.visibility = View.GONE
                    binding.recyclerViewFeedback.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                binding.swipeRefreshFeedback.isRefreshing = false
                ShimmerHelper.stopShimmer(binding.recyclerViewFeedback)
                Toast.makeText(this, "Failed to load feedbacks: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showFeedbackActionDialog(item: FeedbackItem) {
        val statuses = arrayOf("Pending", "In Progress", "Resolved", "Closed")
        val statusKeys = arrayOf("pending", "in_progress", "resolved", "closed")

        val details = """
            User: ${item.userName} (${item.userContact})
            Role: ${item.userRole}
            Category: ${item.category}
            Device: ${item.deviceModel} (Android ${item.androidVersion})
            App Version: v${item.appVersionName} (Code ${item.appVersionCode})
            
            Message:
            ${item.message}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Manage Feedback")
            .setMessage(details)
            .setItems(statuses) { _, which ->
                val selectedStatus = statusKeys[which]
                updateFeedbackStatus(item.id, selectedStatus)
            }
            .setNeutralButton("Delete") { _, _ ->
                confirmDeleteFeedback(item.id)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateFeedbackStatus(feedbackId: String, newStatus: String) {
        firestore.collection("feedbacks").document(feedbackId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                loadFeedbacks()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update status: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteFeedback(feedbackId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Feedback")
            .setMessage("Are you sure you want to delete this feedback entry?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("feedbacks").document(feedbackId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Feedback deleted", Toast.LENGTH_SHORT).show()
                        loadFeedbacks()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
