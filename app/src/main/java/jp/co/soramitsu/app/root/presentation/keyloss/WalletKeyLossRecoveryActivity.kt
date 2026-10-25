package jp.co.soramitsu.app.root.presentation.keyloss

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGate
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.app.root.domain.keyloss.WalletPublicSnapshotReader
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.app.root.presentation.emergency.WalletEmergencyExportActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletKeyLossConsent
import jp.co.soramitsu.common.data.storage.encrypt.WalletKeyLossRetry
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deliberate recovery of lost device storage. This screen never grants wallet signing access. */
@AndroidEntryPoint
class WalletKeyLossRecoveryActivity : AppCompatActivity() {
    @Inject lateinit var encryption: EncryptionUtil
    @Inject lateinit var startupGate: WalletDatabaseStartupGate
    @Inject lateinit var publicReader: WalletPublicSnapshotReader
    private lateinit var content: LinearLayout
    private var job: Job? = null
    private var consent: WalletKeyLossConsent? = null
    private var credentialPurpose: CredentialPurpose? = null
    private var activeDialog: AlertDialog? = null
    private val credentials = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val purpose = credentialPurpose
        credentialPurpose = null
        if (result.resultCode == RESULT_OK) when (purpose) {
            CredentialPurpose.VIEW -> showPublicSnapshot()
            CredentialPurpose.START -> activate()
            null -> showHome()
        } else showHome()
    }

    override fun attachBaseContext(base: Context) {
        val manager = ContextManager.getInstanceOrInit(base.applicationContext, LanguagesHolder())
        applyOverrideConfiguration(manager.setLocale(base).resources.configuration)
        super.attachBaseContext(manager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.rgb(16, 17, 25))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        showHome()
    }

    override fun onStop() {
        super.onStop()
        activeDialog?.dismiss()
        activeDialog = null
        if (credentialPurpose == null) {
            job?.cancel()
            consent = null
            showHome()
        } else content.removeAllViews()
    }

    private fun showHome(message: Int? = null) {
        content.removeAllViews()
        text(getString(R.string.wallet_keyloss_title), 24f)
        text(getString(R.string.wallet_keyloss_intro))
        message?.let { text(getString(it)) }
        button(R.string.wallet_keyloss_retry) { retry() }
        if (consent != null) button(R.string.wallet_keyloss_start) { confirmStart() }
        if (getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            button(R.string.wallet_keyloss_view) { authenticate(CredentialPurpose.VIEW) }
        }
        button(R.string.wallet_emergency_open) { startActivity(WalletEmergencyExportActivity.intent(this)) }
        button(R.string.common_close) { finish() }
    }

    private fun retry() = runWork {
        consent = null
        when (val result = withContext(Dispatchers.IO) { encryption.retryPreviousSecureStorage() }) {
            WalletKeyLossRetry.Recovered -> returnToStartup()
            WalletKeyLossRetry.RestartDevice -> showHome(R.string.wallet_keyloss_restart_device)
            WalletKeyLossRetry.RetryLater -> showHome(R.string.wallet_keyloss_retry_later)
            is WalletKeyLossRetry.CanStart -> { consent = result.consent; showHome(R.string.wallet_keyloss_can_start) }
        }
    }

    private fun confirmStart() {
        val dialogContext = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
        val confirmation = CheckBox(dialogContext).apply {
            text = getString(R.string.wallet_keyloss_backup_confirmation)
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val body = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(dialogContext).apply {
                setText(R.string.wallet_keyloss_consent)
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(24), dp(12), dp(24), dp(8))
            })
            addView(confirmation)
        }
        val scroll = ScrollView(dialogContext).apply { addView(body) }
        val boundedBody = FrameLayout(dialogContext).apply {
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5f).toInt()))
        }
        activeDialog = AlertDialog.Builder(dialogContext)
            .setTitle(R.string.wallet_keyloss_start)
            .setView(boundedBody)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_continue, null)
            .create().also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.show()
                val proceed = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                proceed.setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(Color.WHITE, Color.LTGRAY)))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
                // OnShow is delivered later. Gate the actual button immediately, and
                // recheck the consent control in the handler before starting recovery.
                proceed.isEnabled = false
                confirmation.setOnCheckedChangeListener { _, checked -> proceed.isEnabled = checked }
                proceed.setOnClickListener {
                    if (confirmation.isChecked) {
                        dialog.dismiss()
                        if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticate(CredentialPurpose.START) else activate()
                    }
                }
            }
    }

    @Suppress("DEPRECATION") // Credential-only prompt works on API 26–29 as well as current Android.
    private fun authenticate(purpose: CredentialPurpose) {
        val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(
            getString(R.string.wallet_keyloss_title), getString(R.string.wallet_keyloss_authenticate)
        ) ?: return showHome(R.string.wallet_keyloss_retry_later)
        credentialPurpose = purpose
        credentials.launch(intent)
    }

    private fun activate() = runWork {
        val approved = consent ?: return@runWork showHome(R.string.wallet_keyloss_retry_later)
        withContext(Dispatchers.IO) { encryption.beginFreshKeySlot(approved, hasRecoveryBackups = true) }
        consent = null
        returnToStartup()
    }

    private suspend fun returnToStartup() {
        // Re-run the complete migration/journal gate; never mark the session Ready here.
        WalletStartupSession.recheckAfterKeyRecovery { startupGate.open() }.await()
        startActivity(Intent(this, WalletStartupActivity::class.java))
        finish()
    }

    private fun showPublicSnapshot() = runWork {
        val snapshot = withContext(Dispatchers.IO) { publicReader.read() }
        content.removeAllViews()
        text(getString(R.string.wallet_keyloss_view), 24f)
        text(getString(R.string.wallet_keyloss_cached_warning))
        if (snapshot.incomplete) text(getString(R.string.wallet_keyloss_incomplete))
        if (snapshot.wallets.isEmpty()) text(getString(R.string.wallet_keyloss_no_cached))
        snapshot.wallets.forEach { wallet ->
            text(wallet.name, 20f)
            wallet.identities.forEach { (field, value) ->
                val label = when (field) {
                    "substrateAccountId" -> R.string.wallet_keyloss_substrate_id
                    "ethereumAddress" -> R.string.wallet_keyloss_evm_address
                    "tonPublicKey" -> R.string.wallet_keyloss_ton_public
                    "address" -> R.string.wallet_keyloss_address
                    else -> R.string.wallet_keyloss_public
                }
                text(getString(label, if (field == "ethereumAddress" && value.matches(Regex("[0-9a-fA-F]{40}"))) "0x$value" else value))
            }
            wallet.balances.forEach { text(getString(R.string.wallet_keyloss_balance, it.network, it.asset, it.amount)) }
        }
        button(R.string.wallet_keyloss_retry) { retry() }
        button(R.string.wallet_keyloss_back) { showHome() }
    }

    private fun runWork(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        content.removeAllViews()
        text(getString(R.string.wallet_keyloss_working))
        job = lifecycleScope.launch {
            try { block() } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                consent = null
                showHome(if (failure is WalletSecureStorageUnavailableException &&
                    failure.kind == WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
                ) R.string.wallet_startup_restart_required_message else R.string.wallet_keyloss_retry_later)
            }
        }
    }

    private fun text(value: String, size: Float = 16f) {
        content.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    private fun button(label: Int, action: () -> Unit) {
        content.addView(Button(this).apply { setText(label); isAllCaps = false; setOnClickListener { action() } })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private enum class CredentialPurpose { VIEW, START }

    companion object {
        fun intent(context: Context): Intent = Intent(context, WalletKeyLossRecoveryActivity::class.java)
    }
}
