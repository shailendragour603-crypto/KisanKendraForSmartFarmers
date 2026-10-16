package shop.sabjiwala.kisankendra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import shop.sabjiwala.kisankendra.databinding.ActivityAdminNotificationBinding
import shop.sabjiwala.kisankendra.model.PushNotification
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class AdminNotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminNotificationBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var adapter: AdminNotificationAdapter

    private val topicList = listOf(
        "all_users" to "All App Users (सभी किसान)",
        "mandi_updates" to "Mandi Price Subscribers (मंडी भाव)",
        "news_updates" to "News & Media Subscribers (कृषि समाचार)",
        "advisory_updates" to "Crop Advisory Subscribers (फसल सलाह)"
    )

    private val screenList = listOf(
        "mandi" to "Mandi Rates Screen (मंडी भाव)",
        "news" to "News & Media Screen (कृषि समाचार)",
        "crop_care" to "Weather & Crop Advisory (मौसम व सलाह)",
        "home" to "Main Home Screen (मुख्य पेज)",
        "forum" to "Farmer Discussion Forum (किसान चर्चा)"
    )

    private var selectedTopic: String = "all_users"
    private var selectedScreen: String = "mandi"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateResources(newBase, "en"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setSupportActionBar(binding.toolbarAdminNotification)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarAdminNotification.setNavigationOnClickListener { finish() }

        setupDropdowns()
        setupTextWatchers()
        setupTemplateChips()
        setupHistoryRecyclerView()
        setupFocusListeners()

        binding.btnSendNotification.setOnClickListener {
            confirmAndSendNotification()
        }

        loadHistoryFromFirestore()
    }

    private fun setupFocusListeners() {
        val fields = listOf(
            binding.etNotificationTitle,
            binding.etNotificationBody
        )
        for (field in fields) {
            field.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollViewAdminNotification.postDelayed({
                        val rect = Rect(0, 0, view.width, view.height + 120)
                        view.requestRectangleOnScreen(rect, true)
                    }, 200)
                }
            }
        }
    }

    private fun setupDropdowns() {
        // Topic Dropdown
        val topicLabels = topicList.map { it.second }
        val topicAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, topicLabels)
        binding.autoCompleteTopicSelect.setAdapter(topicAdapter)
        binding.autoCompleteTopicSelect.setText(topicLabels[0], false)
        selectedTopic = topicList[0].first

        binding.autoCompleteTopicSelect.setOnItemClickListener { _, _, position, _ ->
            selectedTopic = topicList[position].first
        }

        // Target Screen Dropdown
        val screenLabels = screenList.map { it.second }
        val screenAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, screenLabels)
        binding.autoCompleteScreenSelect.setAdapter(screenAdapter)
        binding.autoCompleteScreenSelect.setText(screenLabels[0], false)
        selectedScreen = screenList[0].first

        binding.autoCompleteScreenSelect.setOnItemClickListener { _, _, position, _ ->
            selectedScreen = screenList[position].first
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLivePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etNotificationTitle.addTextChangedListener(textWatcher)
        binding.etNotificationBody.addTextChangedListener(textWatcher)

        updateLivePreview()
    }

    private fun updateLivePreview() {
        val title = binding.etNotificationTitle.text?.toString()?.trim().orEmpty()
        val body = binding.etNotificationBody.text?.toString()?.trim().orEmpty()

        binding.tvPreviewTitle.text = if (title.isNotEmpty()) title else "🌾 आज के मंडी भाव अपडेट"
        binding.tvPreviewBody.text = if (body.isNotEmpty()) body else "आज के ताज़ा भाव देखें।"
    }

    private fun setupTemplateChips() {
        binding.chipTemplateMandi.setOnClickListener {
            binding.etNotificationTitle.setText("🌾 आज के ताज़ा मंडी भाव (4:00 PM)")
            binding.etNotificationBody.setText("सोयाबीन, मक्का और चना के ताज़ा भाव अपडेट हो चुके हैं। अभी चेक करें अपनी फसल और मंडी के भाव!")
            setTopicAndScreen("all_users", "mandi")
        }

        binding.chipTemplateNews.setOnClickListener {
            binding.etNotificationTitle.setText("📢 किसानों के लिए ज़रूरी सूचना")
            binding.etNotificationBody.setText("मौसम व PM किसान सम्मान निधि योजना में बड़ी अपडेट आ चुकी है। तुरंत जानकारी प्राप्त करें!")
            setTopicAndScreen("all_users", "news")
        }

        binding.chipTemplateAdvisory.setOnClickListener {
            binding.etNotificationTitle.setText("⚠️ फसल सलाह: मौसम चेतावनी")
            binding.etNotificationBody.setText("अगले 24 घंटे में बारिश की संभावना, क्या आपकी फसल सुरक्षित है? बचाव के उपाय देखें।")
            setTopicAndScreen("all_users", "crop_care")
        }
    }

    private fun setTopicAndScreen(topicKey: String, screenKey: String) {
        val topicIdx = topicList.indexOfFirst { it.first == topicKey }
        if (topicIdx >= 0) {
            selectedTopic = topicList[topicIdx].first
            binding.autoCompleteTopicSelect.setText(topicList[topicIdx].second, false)
        }

        val screenIdx = screenList.indexOfFirst { it.first == screenKey }
        if (screenIdx >= 0) {
            selectedScreen = screenList[screenIdx].first
            binding.autoCompleteScreenSelect.setText(screenList[screenIdx].second, false)
        }
    }

    private fun confirmAndSendNotification() {
        val title = binding.etNotificationTitle.text?.toString()?.trim().orEmpty()
        val body = binding.etNotificationBody.text?.toString()?.trim().orEmpty()

        if (title.isEmpty()) {
            binding.tilNotificationTitle.error = "Title cannot be empty"
            return
        } else {
            binding.tilNotificationTitle.error = null
        }

        if (body.isEmpty()) {
            binding.tilNotificationBody.error = "Message body cannot be empty"
            return
        } else {
            binding.tilNotificationBody.error = null
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Broadcast Notification")
            .setMessage("Are you sure you want to broadcast this notification to topic '$selectedTopic'?\n\nTitle: $title\nMessage: $body")
            .setPositiveButton("Send Now") { _, _ ->
                sendNotificationToFirestore(title, body)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendNotificationToFirestore(title: String, body: String) {
        val docRef = firestore.collection("push_notifications").document()
        val currentUserId = auth.currentUser?.uid.orEmpty()

        val notification = PushNotification(
            id = docRef.id,
            title = title,
            body = body,
            topic = selectedTopic,
            targetScreen = selectedScreen,
            timestamp = System.currentTimeMillis(),
            sentBy = currentUserId,
            status = "SENT"
        )

        docRef.set(notification)
            .addOnSuccessListener {
                Toast.makeText(this, "🚀 Notification broadcasted successfully!", Toast.LENGTH_LONG).show()
                triggerLocalNotificationPreview(title, body, selectedScreen)
                loadHistoryFromFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send notification: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun triggerLocalNotificationPreview(title: String, body: String, targetScreen: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "mandi_news_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Mandi & News Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for Mandi prices, agricultural news, and advisories"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("target_screen", targetScreen)
            }

            val requestCode = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            notificationManager.notify(requestCode, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupHistoryRecyclerView() {
        adapter = AdminNotificationAdapter(
            items = emptyList(),
            onReuseClick = { notification ->
                binding.etNotificationTitle.setText(notification.title)
                binding.etNotificationBody.setText(notification.body)
                setTopicAndScreen(notification.topic, notification.targetScreen)
                binding.scrollViewAdminNotification.smoothScrollTo(0, 0)
                Toast.makeText(this, "Template loaded into composer!", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { notification ->
                confirmDeleteNotification(notification)
            }
        )

        binding.rvNotificationHistory.layoutManager = LinearLayoutManager(this)
        binding.rvNotificationHistory.adapter = adapter
    }

    private fun loadHistoryFromFirestore() {
        firestore.collection("push_notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    val list = snapshot.toObjects(PushNotification::class.java)
                    adapter.updateData(list)
                }
            }
            .addOnFailureListener {
                // Ignore failure silently or show toast if needed
            }
    }

    private fun confirmDeleteNotification(notification: PushNotification) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Notification History")
            .setMessage("Are you sure you want to delete this notification record?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("push_notifications")
                    .document(notification.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Notification deleted", Toast.LENGTH_SHORT).show()
                        loadHistoryFromFirestore()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
