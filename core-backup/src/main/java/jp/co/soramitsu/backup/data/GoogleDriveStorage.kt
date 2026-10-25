// GoogleSignIn is deprecated, but it holds the sign-in 4.2.0 users already have. Moving to
// Identity.getAuthorizationClient belongs to a later release.
@file:Suppress("DEPRECATION")

package jp.co.soramitsu.backup.data

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import jp.co.soramitsu.backup.domain.Storage
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.FileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google sign-in for the Drive app data scope, as in shared_features; the requests live in [GoogleDriveFiles]. */
internal class GoogleDriveStorage(
    context: Context,
    tokenId: String
) : Storage {

    private val context = context.applicationContext

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdTokenWhenConfigured(tokenId)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

    @Volatile
    private var driveFiles: GoogleDriveFiles? = null

    override suspend fun saveFile(
        name: String,
        description: String,
        content: String
    ): String {
        return withContext(Dispatchers.IO) {
            silentSignInAndCheckAuthorization()

            getDriveFiles().createFile(name, description, content)
        }
    }

    override suspend fun signIn(activityResultLauncher: ActivityResultLauncher<Intent>): Boolean {
        return suspendCancellableCoroutine { continuation ->
            if (isAuthorized()) {
                continuation.resume(true)
            } else {
                val client = GoogleSignIn.getClient(context, signInOptions)

                // A canceled task completes too, so one listener covers every outcome.
                client.silentSignIn().addOnCompleteListener {
                    if (it.isSuccessful) {
                        continuation.resume(true)
                    } else {
                        launchSignIn(activityResultLauncher, client)
                        continuation.resume(false)
                    }
                }
            }
        }
    }

    override suspend fun getFileContent(fileId: String): String {
        return withContext(Dispatchers.IO) {
            silentSignInAndCheckAuthorization()

            getDriveFiles().getFileContent(fileId)
        }
    }

    override suspend fun getListOfFileHeads(): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            silentSignInAndCheckAuthorization()

            getDriveFiles().listAppFiles()
        }
    }

    override suspend fun getWebListOfFileHeads(): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            silentSignInAndCheckAuthorization()

            getDriveFiles().listWebFiles()
        }
    }

    override suspend fun deleteFile(fileId: String) {
        withContext(Dispatchers.IO) {
            silentSignInAndCheckAuthorization()

            getDriveFiles().deleteFile(fileId)
        }
    }

    override suspend fun logout() {
        return suspendCancellableCoroutine { continuation ->
            GoogleSignIn.getClient(context, signInOptions).signOut().addOnCompleteListener {
                driveFiles = null
                continuation.resume(Unit)
            }
        }
    }

    private fun launchSignIn(activityResultLauncher: ActivityResultLauncher<Intent>, client: GoogleSignInClient) {
        // The launcher may be unregistered because its screen is gone; the caller gets false either way.
        runCatching { activityResultLauncher.launch(client.signInIntent) }
    }

    private fun getDriveFiles(): GoogleDriveFiles {
        return driveFiles ?: synchronized(this) {
            driveFiles ?: GoogleDriveFiles(buildGoogleDrive()).also {
                driveFiles = it
            }
        }
    }

    private fun buildGoogleDrive(): Drive {
        val credential: GoogleAccountCredential =
            GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(DriveScopes.DRIVE_APPDATA)
            )
        credential.selectedAccount = GoogleSignIn.getLastSignedInAccount(context)?.account
        return Drive.Builder(
            NetHttpTransport.Builder().build(),
            GsonFactory(),
            credential
        ).build()
    }

    private fun isAuthorized(): Boolean {
        val lastSigned = GoogleSignIn.getLastSignedInAccount(context)
        return lastSigned != null && !lastSigned.isExpired
    }

    private suspend fun silentSignInAndCheckAuthorization() {
        suspendCancellableCoroutine { continuation ->
            if (isAuthorized()) {
                continuation.resume(Unit)
            } else {
                GoogleSignIn.getClient(context, signInOptions).silentSignIn().addOnCompleteListener {
                    if (it.isSuccessful) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(UnauthorizedException(it.exception))
                    }
                }
            }
        }
    }
}

/**
 * Drive access is granted to the app's Android OAuth client; the ID token is never used. Requesting
 * one with a blank client id throws while the singleton is created, so it is requested only when
 * the build has a web client id.
 */
internal fun GoogleSignInOptions.Builder.requestIdTokenWhenConfigured(tokenId: String): GoogleSignInOptions.Builder {
    return if (tokenId.isNotBlank()) requestIdToken(tokenId) else this
}
