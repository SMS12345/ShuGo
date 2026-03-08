package com.antigravity.parentalcontrol.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

object GoogleAuthHelper {
    private const val TAG = "GoogleAuth"

    // Web client ID from google-services.json (client_type: 3)
    private const val WEB_CLIENT_ID = "435998586068-qd38icqors4l9ljvjn0p5ie43dn7omck.apps.googleusercontent.com"

    const val RC_SIGN_IN = 9001

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private fun getGoogleSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    /**
     * Launches the Google Sign-In intent
     */
    fun signIn(activity: Activity) {
        val client = getGoogleSignInClient(activity)
        activity.startActivityForResult(client.signInIntent, RC_SIGN_IN)
    }

    /**
     * Handles the result from Google Sign-In intent
     */
    fun handleSignInResult(
        data: Intent?,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account, onSuccess, onFailure)
            } else {
                onFailure("Google Sign-In returned null account")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In failed with code: ${e.statusCode}", e)
            onFailure("Google Sign-In failed: ${e.localizedMessage}")
        }
    }

    /**
     * Exchanges Google credential for Firebase Auth credential
     */
    private fun firebaseAuthWithGoogle(
        account: GoogleSignInAccount,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase Auth success: ${firebaseAuth.currentUser?.displayName}")
                    onSuccess(account)
                } else {
                    Log.e(TAG, "Firebase Auth failed", task.exception)
                    onFailure(task.exception?.localizedMessage ?: "Firebase Auth failed")
                }
            }
    }

    /**
     * Signs out of both Google and Firebase
     */
    fun signOut(activity: Activity, onComplete: () -> Unit = {}) {
        firebaseAuth.signOut()
        getGoogleSignInClient(activity).signOut().addOnCompleteListener {
            Log.d(TAG, "Signed out successfully")
            onComplete()
        }
    }

    /**
     * Returns the currently signed-in Firebase user, or null
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Quick check if user is signed in
     */
    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    /**
     * Returns the display name from the current Firebase user
     */
    fun getDisplayName(): String {
        return firebaseAuth.currentUser?.displayName ?: ""
    }

    /**
     * Returns the email from the current Firebase user
     */
    fun getEmail(): String {
        return firebaseAuth.currentUser?.email ?: ""
    }

    /**
     * Returns the profile photo URL from the current Firebase user
     */
    fun getPhotoUrl(): String? {
        return firebaseAuth.currentUser?.photoUrl?.toString()
    }
}
