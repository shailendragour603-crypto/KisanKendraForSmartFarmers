package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import shop.sabjiwala.kisankendra.databinding.ActivitySettingsBinding
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var googleSignInClient: GoogleSignInClient? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    hideGoogleLoading()
                    Toast.makeText(this, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                hideGoogleLoading()
                Toast.makeText(this, "Google Sign-In Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                hideGoogleLoading()
                Toast.makeText(this, "Google Sign-In Error: ${e.localizedMessage ?: "Service Unavailable"}", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickProfileImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val safeUri = MediaUtils.copyUriToAppCache(this, uri) ?: uri
                uploadProfileImageToFirebase(safeUri)
            }
        }

    companion object {
        private const val PREFS_SETTINGS = "settings_prefs"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateResources(newBase, "en"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else 0
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.cardBottomNavSettings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = v.layoutParams as ViewGroup.MarginLayoutParams
            val baseMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams.bottomMargin = baseMargin + systemBars.bottom
            v.layoutParams = layoutParams
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationSettings) { v, insets ->
            val density = resources.displayMetrics.density
            v.updatePadding(bottom = (6 * density).toInt())
            insets
        }

        setupGoogleSignIn()
        setupToolbar()
        setupAccountSection()
        setupDarkModeSection()
        setupSupportSection()
        setupAdminSection()
        setupBottomNav()
    }

    private fun setupAdminSection() {
        val user = auth.currentUser
        if (user == null || user.isAnonymous || SignInActivity.isGuestUser(this)) {
            binding.cardAdminSection.visibility = View.GONE
            return
        }

        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role").orEmpty()
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                if (role.equals("admin", ignoreCase = true) || isAdmin) {
                    binding.cardAdminSection.visibility = View.VISIBLE
                    binding.cardAdminSection.setOnClickListener {
                        startActivity(Intent(this, AdminDashboardActivity::class.java))
                    }
                    binding.tvAdminViewFeedback.setOnClickListener {
                        startActivity(Intent(this, AdminDashboardActivity::class.java))
                    }
                } else {
                    checkAdminCollectionFallbackForSettings(uid)
                }
            }
            .addOnFailureListener {
                checkAdminCollectionFallbackForSettings(uid)
            }
    }

    private fun checkAdminCollectionFallbackForSettings(uid: String) {
        firestore.collection("admin").document(uid).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc.exists()) {
                    binding.cardAdminSection.visibility = View.VISIBLE
                    binding.cardAdminSection.setOnClickListener {
                        startActivity(Intent(this, AdminDashboardActivity::class.java))
                    }
                    binding.tvAdminViewFeedback.setOnClickListener {
                        startActivity(Intent(this, AdminDashboardActivity::class.java))
                    }
                } else {
                    binding.cardAdminSection.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                binding.cardAdminSection.visibility = View.GONE
            }
    }

    private fun setupGoogleSignIn() {
        try {
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
            if (availability != ConnectionResult.SUCCESS) {
                googleSignInClient = null
                return
            }

            val webClientId = try {
                getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                "425108111333-4aokaf8qrmhhei03li3gt49n1f1oe1t4.apps.googleusercontent.com"
            }

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (_: Exception) {
            googleSignInClient = null
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                hideGoogleLoading()
                val user = result.user
                if (user != null) {
                    saveUserToFirestore(user.uid, user.phoneNumber.orEmpty(), user.email.orEmpty(), user.displayName.orEmpty())
                } else {
                    navigateToMain()
                }
            }
            .addOnFailureListener { e ->
                hideGoogleLoading()
                Toast.makeText(this, "Google Authentication Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveUserToFirestore(uid: String, phone: String, email: String, name: String) {
        val finalName = name.ifBlank { "Farmer" }

        val userMap = hashMapOf(
            "uid" to uid,
            "phone" to phone,
            "email" to email,
            "displayName" to finalName,
            "lastLogin" to Timestamp.now()
        )

        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(finalName)
                .build()
        )

        firestore.collection("users").document(uid)
            .set(userMap, SetOptions.merge())
            .addOnCompleteListener {
                SignInActivity.setGuestUser(this, false)
                KhataHelper.migrateGuestDataToCurrentUser(this, KhataRepository()) {
                    navigateToMain()
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showGoogleLoading() {
        binding.pbGoogle.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
    }

    private fun hideGoogleLoading() {
        binding.pbGoogle.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
    }

    private fun setupBottomNav() {
        binding.bottomNavigationSettings.selectedItemId = R.id.nav_home
        binding.bottomNavigationSettings.setOnItemSelectedListener { item ->
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("select_tab_id", item.itemId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
            true
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarSettings)
        binding.toolbarSettings.setNavigationOnClickListener { finish() }
    }

    private fun setupAccountSection() {
        val user = auth.currentUser
        val isGuest = user == null || user.isAnonymous || SignInActivity.isGuestUser(this)

        if (isGuest) {
            binding.layoutGuestSection.visibility = View.VISIBLE
            binding.layoutProfileSection.visibility = View.GONE
            binding.pbGoogle.visibility = View.GONE

            binding.btnGuestSignIn.setOnClickListener {
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
            }

            binding.btnGoogleSignIn.setOnClickListener {
                showGoogleLoading()
                val signInIntent = googleSignInClient?.signInIntent
                if (signInIntent != null) {
                    googleSignInLauncher.launch(signInIntent)
                } else {
                    hideGoogleLoading()
                    Toast.makeText(this, "Google Sign-In unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.layoutGuestSection.visibility = View.GONE
            binding.layoutProfileSection.visibility = View.VISIBLE
            binding.layoutNameEdit.visibility = View.GONE
            binding.layoutNameDisplay.visibility = View.VISIBLE

            val identifier = when {
                !user.phoneNumber.isNullOrBlank() -> user.phoneNumber
                !user.email.isNullOrBlank() -> user.email
                else -> "Authenticated User"
            }
            binding.tvUserIdentifier.text = identifier

            loadUserProfileData(user.uid)

            binding.fabEditProfilePhoto.setOnClickListener {
                pickProfileImageLauncher.launch("image/*")
            }
            binding.ivProfilePicture.setOnClickListener {
                pickProfileImageLauncher.launch("image/*")
            }

            binding.btnEditName.setOnClickListener {
                binding.layoutNameDisplay.visibility = View.GONE
                binding.layoutNameEdit.visibility = View.VISIBLE
                binding.etDisplayName.requestFocus()
            }

            binding.etDisplayName.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollViewSettings.postDelayed({
                        val rect = Rect(0, 0, view.width, view.height + 120)
                        view.requestRectangleOnScreen(rect, true)
                    }, 200)
                }
            }

            binding.btnCancelEditName.setOnClickListener {
                binding.layoutNameEdit.visibility = View.GONE
                binding.layoutNameDisplay.visibility = View.VISIBLE
                binding.etDisplayName.setText(binding.tvDisplayName.text)
            }

            binding.btnSaveProfile.setOnClickListener {
                saveDisplayName(user.uid)
            }

            binding.btnLogout.setOnClickListener {
                auth.signOut()
                SignInActivity.setGuestUser(this, false)
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
            }

            binding.layoutDeleteAccount.setOnClickListener {
                showDeleteAccountWarningDialog()
            }
        }
    }

    private fun loadUserProfileData(uid: String) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("displayName").orEmpty()
                    .ifBlank { auth.currentUser?.displayName.orEmpty() }
                    .ifBlank { "Farmer" }
                val photoUrl = doc.getString("photoUrl").orEmpty()
                    .ifBlank { auth.currentUser?.photoUrl?.toString().orEmpty() }

                binding.etDisplayName.setText(name)
                binding.tvDisplayName.text = name

                if (photoUrl.isNotBlank()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(binding.ivProfilePicture)
                } else {
                    Glide.with(this)
                        .load(R.drawable.ic_default_avatar)
                        .circleCrop()
                        .into(binding.ivProfilePicture)
                }
            }
    }

    private fun uploadProfileImageToFirebase(imageUri: Uri) {
        val user = auth.currentUser ?: return
        binding.pbProfileUpload.visibility = View.VISIBLE
        binding.fabEditProfilePhoto.isEnabled = false

        val storageRef = FirebaseStorage.getInstance().reference.child("profile_pictures/${user.uid}.jpg")
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    updateProfilePhotoInFirestoreAndAuth(user.uid, downloadUrl)
                }.addOnFailureListener { e ->
                    binding.pbProfileUpload.visibility = View.GONE
                    binding.fabEditProfilePhoto.isEnabled = true
                    Toast.makeText(this, "Failed to get photo URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.pbProfileUpload.visibility = View.GONE
                binding.fabEditProfilePhoto.isEnabled = true
                val errMsg = if (e.message?.contains("403") == true || e.message?.contains("permission") == true) {
                    "Firebase Storage Permission Denied (403). Update Storage Rules in Firebase Console."
                } else {
                    "Photo upload failed: ${e.localizedMessage}"
                }
                Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show()
            }
    }

    private fun updateProfilePhotoInFirestoreAndAuth(uid: String, photoUri: Uri) {
        val photoUrlString = photoUri.toString()
        val userMap = hashMapOf("photoUrl" to photoUrlString)

        firestore.collection("users").document(uid)
            .set(userMap, SetOptions.merge())
            .addOnSuccessListener {
                auth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setPhotoUri(photoUri)
                        .build()
                )

                binding.pbProfileUpload.visibility = View.GONE
                binding.fabEditProfilePhoto.isEnabled = true

                Glide.with(this)
                    .load(photoUrlString)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(binding.ivProfilePicture)

                Toast.makeText(this, "Profile picture updated successfully! 🎉", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                binding.pbProfileUpload.visibility = View.GONE
                binding.fabEditProfilePhoto.isEnabled = true
                Toast.makeText(this, "Failed to save profile picture: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDisplayName(uid: String) {
        val newName = binding.etDisplayName.text.toString().trim()
        if (newName.isBlank()) {
            Toast.makeText(this, "Please enter a valid name", Toast.LENGTH_SHORT).show()
            return
        }

        val userMap = hashMapOf("displayName" to newName)

        firestore.collection("users").document(uid)
            .set(userMap, SetOptions.merge())
            .addOnSuccessListener {
                auth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()
                )
                binding.tvDisplayName.text = newName
                binding.layoutNameEdit.visibility = View.GONE
                binding.layoutNameDisplay.visibility = View.VISIBLE
                Toast.makeText(this, "Name updated successfully! 👍", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update name: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteAccountWarningDialog() {
        val user = auth.currentUser ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Delete Account Warning")
            .setMessage("Are you sure you want to delete your account? This will permanently remove all your saved profile data and forum posts. This action cannot be undone.")
            .setPositiveButton("Delete Account") { _, _ ->
                val uid = user.uid
                firestore.collection("users").document(uid).delete()
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        auth.signOut()
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, SignInActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to delete account: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDarkModeSection() {
        val prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_DARK_MODE, isChecked) }
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupSupportSection() {
        binding.tvFeedback.setOnClickListener {
            val user = auth.currentUser
            val isGuest = user == null || user.isAnonymous || SignInActivity.isGuestUser(this)

            if (isGuest) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.feedback_login_required)
                    .setMessage("Please sign in with your account to send feedback or report an issue.")
                    .setPositiveButton("Sign In") { _, _ ->
                        startActivity(Intent(this, SignInActivity::class.java))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                FeedbackBottomSheetDialog.newInstance().show(supportFragmentManager, "FeedbackBottomSheetDialog")
            }
        }

        binding.tvAboutUs.setOnClickListener {
            NativeInfoActivity.start(this, NativeInfoActivity.TYPE_ABOUT)
        }

        binding.tvContactUs.setOnClickListener {
            NativeInfoActivity.start(this, NativeInfoActivity.TYPE_CONTACT)
        }

        binding.tvPrivacyPolicy.setOnClickListener {
            NativeInfoActivity.start(this, NativeInfoActivity.TYPE_PRIVACY)
        }

        binding.tvShareApp.setOnClickListener {
            val appUrl = "https://play.google.com/store/apps/details?id=shop.sabjiwala.kisankendra"
            val shareText = "🌾 Check out Kisan Kendra App on Google Play Store for Daily Mandi Rates, Agriculture News & Farmer Community:\n$appUrl"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share Kisan Kendra via"))
        }
    }
}