package br.com.joaoduartemariucio.rodaro.data.common.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import br.com.joaoduartemariucio.rodaro.domain.common.errors.AccountError
import br.com.joaoduartemariucio.rodaro.domain.common.services.Account
import br.com.joaoduartemariucio.rodaro.domain.common.services.AccountService
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAccountService(
    private val context: Context,
    private val activity: () -> Context?,
) : AccountService {
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    override suspend fun current(): Account? = auth.currentUser.toAccount()

    override suspend fun signIn(
        email: String,
        password: String,
    ) {
        authResult(auth.signInWithEmailAndPassword(email, password))
    }

    override suspend fun createAccount(
        email: String,
        password: String,
    ) {
        authResult(auth.createUserWithEmailAndPassword(email, password))
    }

    override suspend fun signInWithGoogle() {
        authResult(auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken(), null)))
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount() {
        val user = auth.currentUser ?: throw AccountError.NotSignedIn
        authResult(user.delete())
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw AccountError.NotSignedIn
        val email = user.email ?: throw AccountError.NotSignedIn
        authResult(user.reauthenticate(EmailAuthProvider.getCredential(email, password)))
    }

    override suspend fun reauthenticateWithGoogle() {
        signInWithGoogle()
    }

    private suspend fun googleIdToken(): String {
        val clientId = webClientId() ?: throw AccountError.GoogleNotConfigured
        val option =
            GetGoogleIdOption
                .Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val host = activity() ?: throw AccountError.Failed(IllegalStateException("no foreground activity"))
        val result =
            try {
                credentialManager.getCredential(host, request)
            } catch (error: GetCredentialCancellationException) {
                throw AccountError.Cancelled
            } catch (error: NoCredentialException) {
                throw AccountError.NoGoogleAccount
            } catch (error: GetCredentialException) {
                Log.w(TAG, "Google credential request failed", error)
                throw AccountError.Failed(error)
            }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AccountError.Failed(IllegalStateException("unexpected credential ${credential.type}"))
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private suspend fun webClientId(): String? {
        val id = defaultWebClientId()
        return if (id == 0) null else context.getString(id)
    }

    @SuppressLint("DiscouragedApi")
    private fun defaultWebClientId(): Int = context.resources.getIdentifier(WEB_CLIENT_ID, "string", context.packageName)

    private suspend fun <T> authResult(task: Task<T>) {
        try {
            task.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseAuthException) {
            throw accountError(error.errorCode, error)
        } catch (error: Exception) {
            throw AccountError.Failed(error)
        }
    }

    private fun FirebaseUser?.toAccount(): Account? {
        this ?: return null
        val isGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return Account(
            id = uid,
            email = email,
            displayName = displayName,
            provider = if (isGoogle) Account.Provider.GOOGLE else Account.Provider.PASSWORD,
        )
    }

    private companion object {
        const val WEB_CLIENT_ID = "default_web_client_id"
        const val TAG = "Rodaro.Account"
    }
}
