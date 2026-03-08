package com.antigravity.parentalcontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.parentalcontrol.auth.GoogleAuthHelper
import com.antigravity.parentalcontrol.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already signed in, skip to MainActivity
        if (GoogleAuthHelper.isSignedIn()) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.btnGoogleSignIn.setOnClickListener {
            startSignIn()
        }
    }

    private fun startSignIn() {
        binding.btnGoogleSignIn.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        GoogleAuthHelper.signIn(this)
    }

    @Deprecated("Use Activity Result APIs")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleAuthHelper.RC_SIGN_IN) {
            GoogleAuthHelper.handleSignInResult(
                data,
                onSuccess = { account ->
                    // Save display name from Google profile
                    val displayName = account.displayName ?: account.email ?: "User"
                    AppModeManager.setUsername(this, displayName)
                    navigateToMain()
                },
                onFailure = { errorMessage ->
                    binding.btnGoogleSignIn.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = errorMessage
                    binding.tvError.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
