package shop.sabjiwala.kisankendra

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import shop.sabjiwala.kisankendra.databinding.DialogFeedbackBottomSheetBinding

class FeedbackBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogFeedbackBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFeedbackBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSubmitFeedback.setOnClickListener {
            validateAndSubmitFeedback()
        }
    }

    private fun validateAndSubmitFeedback() {
        val user = auth.currentUser
        val isGuest = user == null || user.isAnonymous || SignInActivity.isGuestUser(requireContext())

        // 🔒 Auth Check: Only logged in users or admins can fill and submit feedback
        if (isGuest) {
            dismiss()
            showSignInPrompt()
            return
        }

        val message = binding.etFeedbackMessage.text.toString().trim()
        if (message.isEmpty()) {
            binding.tilFeedbackMessage.error = getString(R.string.feedback_hint)
            return
        } else {
            binding.tilFeedbackMessage.error = null
        }

        val category = when (binding.chipGroupFeedbackCategory.checkedChipId) {
            R.id.chipFeatureRequest -> "Feature Request"
            R.id.chipReportBug -> "Report Bug"
            R.id.chipGeneral -> "General"
            else -> "Feature Request"
        }

        showLoading(true)

        // 🔍 Determine user role (Admin vs Standard User)
        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role").orEmpty()
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                val isUserAdmin = role.equals("admin", ignoreCase = true) || isAdmin

                saveFeedbackToFirestore(
                    user = user,
                    category = category,
                    message = message,
                    userRole = if (isUserAdmin) "Admin" else "User"
                )
            }
            .addOnFailureListener {
                // Default fallback if role fetch fails
                saveFeedbackToFirestore(
                    user = user,
                    category = category,
                    message = message,
                    userRole = "User"
                )
            }
    }

    private fun saveFeedbackToFirestore(
        user: FirebaseUser,
        category: String,
        message: String,
        userRole: String
    ) {
        // 🚀 Auto-capture technical & context metadata behind the scenes (not shown in form)
        val feedbackData = hashMapOf(
            "category" to category,
            "message" to message,
            "userId" to user.uid,
            "userName" to (user.displayName.orEmpty().ifBlank { "Farmer" }),
            "userContact" to (user.email.orEmpty().ifBlank { user.phoneNumber.orEmpty() }),
            "userRole" to userRole,
            "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "androidVersion" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "timestamp" to Timestamp.now(),
            "status" to "pending"
        )

        firestore.collection("feedbacks")
            .add(feedbackData)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(requireContext(), R.string.feedback_success, Toast.LENGTH_LONG).show()
                dismiss()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(
                    requireContext(),
                    "Failed to submit feedback: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.pbSubmitFeedback.visibility = View.VISIBLE
            binding.btnSubmitFeedback.visibility = View.INVISIBLE
            binding.btnSubmitFeedback.isEnabled = false
        } else {
            binding.pbSubmitFeedback.visibility = View.GONE
            binding.btnSubmitFeedback.visibility = View.VISIBLE
            binding.btnSubmitFeedback.isEnabled = true
        }
    }

    private fun showSignInPrompt() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.feedback_login_required)
            .setMessage("Please sign in with your phone or Google account to send feedback to the Kisan Kendra team.")
            .setPositiveButton("Sign In") { _, _ ->
                startActivity(Intent(requireContext(), SignInActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FeedbackBottomSheetDialog {
            return FeedbackBottomSheetDialog()
        }
    }
}
