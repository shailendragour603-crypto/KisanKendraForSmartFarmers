package shop.sabjiwala.kisankendra

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import shop.sabjiwala.kisankendra.databinding.ActivitySignInBinding
import shop.sabjiwala.kisankendra.repository.KhataRepository
import shop.sabjiwala.kisankendra.utils.KhataHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var googleSignInClient: GoogleSignInClient? = null
    private var isSignupMode = false

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_IS_GUEST = "is_guest"

        fun isGuestUser(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_GUEST, false) && FirebaseAuth.getInstance().currentUser == null
        }

        fun setGuestUser(context: Context, isGuest: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit { putBoolean(KEY_IS_GUEST, isGuest) }
        }
    }

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
                when (e.statusCode) {
                    12501 -> {
                        // User cancelled sign-in prompt - no error Toast needed
                    }
                    10 -> {
                        Toast.makeText(this, "Google Sign-In Error (10): Missing SHA-1 fingerprint in Firebase Console.", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this, "Google Sign-In Error (${e.statusCode}): ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                hideGoogleLoading()
                Toast.makeText(this, "Google Sign-In Error: ${e.localizedMessage ?: "Service Unavailable"}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setupGoogleSignIn()
        setupListeners()
        setupEmailAuthListeners()
        setupFocusListeners()
    }

    private fun setupFocusListeners() {
        val inputFields = listOf(
            binding.etSignupName,
            binding.etEmail,
            binding.etPassword,
            binding.etConfirmPassword
        )
        for (field in inputFields) {
            field.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollView.postDelayed({
                        val rect = Rect(0, 0, view.width, view.height + 120)
                        view.requestRectangleOnScreen(rect, true)
                    }, 200)
                }
            }
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

    private fun setupListeners() {
        binding.toggleAuthMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isSignupMode = (checkedId == R.id.btnModeSignup)
                if (isSignupMode) {
                    binding.tilSignupName.visibility = View.VISIBLE
                    binding.tilConfirmPassword.visibility = View.VISIBLE
                    binding.tvEmailCardTitle.text = "Create Account"
                    binding.btnEmailAuth.text = "Create Account & Sign Up"
                } else {
                    binding.tilSignupName.visibility = View.GONE
                    binding.tilConfirmPassword.visibility = View.GONE
                    binding.tvEmailCardTitle.text = "Email & Password"
                    binding.btnEmailAuth.text = "Login with Email"
                }
            }
        }

        binding.btnSkip.setOnClickListener {
            setGuestUser(this, true)
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("select_tab_id", R.id.nav_home)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("select_tab_id", R.id.nav_home)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
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
        val enteredName = if (isSignupMode) binding.etSignupName.text.toString().trim() else ""
        val finalName = enteredName.ifBlank { name.ifBlank { "Farmer" } }

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
                setGuestUser(this, false)
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

    private fun setupEmailAuthListeners() {
        binding.btnEmailAuth.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Please enter a valid email address"
                return@setOnClickListener
            } else {
                binding.tilEmail.error = null
            }

            if (password.length < 6) {
                binding.tilPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            } else {
                binding.tilPassword.error = null
            }

            if (isSignupMode) {
                if (password != confirmPassword) {
                    binding.tilConfirmPassword.error = "Passwords do not match"
                    return@setOnClickListener
                } else {
                    binding.tilConfirmPassword.error = null
                }
                signUpWithEmail(email, password)
            } else {
                loginWithEmail(email, password)
            }
        }
    }

    private fun signUpWithEmail(email: String, pass: String) {
        showEmailLoading()
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                hideEmailLoading()
                val user = result.user
                if (user != null) {
                    saveUserToFirestore(user.uid, user.phoneNumber.orEmpty(), email, user.displayName.orEmpty())
                } else {
                    navigateToMain()
                }
            }
            .addOnFailureListener { e ->
                hideEmailLoading()
                Toast.makeText(this, "Sign Up Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loginWithEmail(email: String, pass: String) {
        showEmailLoading()
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                hideEmailLoading()
                val user = result.user
                if (user != null) {
                    saveUserToFirestore(user.uid, user.phoneNumber.orEmpty(), email, user.displayName.orEmpty())
                } else {
                    navigateToMain()
                }
            }
            .addOnFailureListener { e ->
                hideEmailLoading()
                Toast.makeText(this, "Login Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showEmailLoading() {
        binding.pbEmailAuth.visibility = View.VISIBLE
        binding.btnEmailAuth.isEnabled = false
    }

    private fun hideEmailLoading() {
        binding.pbEmailAuth.visibility = View.GONE
        binding.btnEmailAuth.isEnabled = true
    }
}