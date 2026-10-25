package jp.co.soramitsu.app.root.presentation.emergency

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.app.databinding.ActivityWalletEmergencyExportBinding
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryAccessResult
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryEntry
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryPage
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryPageChangedException
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoverySession
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthentication
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emergency key access (addendum U0.4). Reachable from every start-up
 * failure that keeps the wallet closed and from the wallet's recovery banner:
 * after the wallet PIN, it shows every recovery phrase and key this device
 * stores, exactly as stored, including keys set aside or archived by an
 * upgrade. It never opens the wallet database and never changes anything.
 */
@AndroidEntryPoint
class WalletEmergencyExportActivity : AppCompatActivity() {

    @Inject
    lateinit var authenticator: WalletEmergencyAuthenticator

    @Inject
    lateinit var reader: WalletEmergencyRecoveryReader

    private lateinit var binding: ActivityWalletEmergencyExportBinding
    private var revealJob: Job? = null
    private val access by lazy { WalletEmergencyRecoverySession(authenticator, reader) }

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(
            base.applicationContext,
            LanguagesHolder()
        )
        applyOverrideConfiguration(
            contextManager.setLocale(base).resources.configuration
        )
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Revealed keys and typed digits are never restored from saved state.
        super.onCreate(null)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityWalletEmergencyExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.emergencyUnlock.setOnClickListener { unlock() }
        binding.emergencyClose.setOnClickListener { finish() }
    }

    override fun onStop() {
        super.onStop()
        // Stop and recreation both revoke this screen instance's authorization.
        revealJob?.cancel()
        access.revoke()
        showPinStep(error = null)
    }

    private fun unlock() {
        if (revealJob?.isActive == true) return
        val code = binding.emergencyPin.text?.toString().orEmpty()
        binding.emergencyPin.text?.clear()
        binding.emergencyEntries.removeAllViews()
        binding.emergencyWarning.isVisible = false
        revealJob = lifecycleScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { access.unlock(code) }) {
                    is EmergencyRecoveryAccessResult.Page -> showPage(result.page, null)
                    is EmergencyRecoveryAccessResult.Refused -> showRefusal(result.result)
                    EmergencyRecoveryAccessResult.EmptyWithoutAuthentication -> {
                        binding.emergencyPinGroup.isVisible = false
                        binding.emergencyWarning.isVisible = false
                        binding.emergencyEntries.removeAllViews()
                        binding.emergencyMessage.setText(R.string.wallet_emergency_empty)
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                showPinStep(getString(R.string.wallet_emergency_read_failed))
            }
        }
    }

    private fun loadPage(afterStorageKey: String?) {
        if (revealJob?.isActive == true) return
        binding.emergencyEntries.removeAllViews()
        binding.emergencyWarning.isVisible = false
        revealJob = lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { access.listPage(afterStorageKey) }
                showPage(page, afterStorageKey)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: EmergencyRecoveryPageChangedException) {
                binding.emergencyMessage.setText(R.string.wallet_emergency_inventory_changed)
                addActionButton(getString(R.string.wallet_emergency_first_page)) { loadPage(null) }
            } catch (_: Exception) {
                showPinStep(getString(R.string.wallet_emergency_read_failed))
            }
        }
    }

    private fun revealKey(storageKey: String, pageCursor: String?) {
        if (revealJob?.isActive == true) return
        binding.emergencyEntries.removeAllViews()
        revealJob = lifecycleScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { access.readKey(storageKey) }
                binding.emergencyMessage.setText(R.string.wallet_emergency_title)
                binding.emergencyWarning.isVisible = true
                addWalletHeading(entry.walletId)
                addEntry(entry)
                addActionButton(getString(R.string.wallet_emergency_back_to_list)) { loadPage(pageCursor) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                showPinStep(getString(R.string.wallet_emergency_read_failed))
            }
        }
    }

    private fun showRefusal(result: WalletEmergencyAuthentication) {
        when (result) {
            WalletEmergencyAuthentication.WrongPin ->
                showPinStep(getString(R.string.wallet_emergency_wrong_pin))

            is WalletEmergencyAuthentication.LockedUntil -> {
                val remainingMillis = result.epochMillis - System.currentTimeMillis()
                val seconds = ((remainingMillis + MILLIS_IN_SECOND - 1) / MILLIS_IN_SECOND).coerceAtLeast(1)
                showPinStep(getString(R.string.wallet_emergency_locked, seconds.toInt()))
            }

            WalletEmergencyAuthentication.Unavailable -> {
                binding.emergencyPinGroup.isVisible = false
                binding.emergencyMessage.setText(R.string.wallet_emergency_unavailable)
            }

            WalletEmergencyAuthentication.Authenticated -> Unit
        }
    }

    private fun showPinStep(error: String?) {
        access.revoke()
        binding.emergencyPin.text?.clear()
        binding.emergencyEntries.removeAllViews()
        binding.emergencyWarning.isVisible = false
        binding.emergencyMessage.setText(R.string.wallet_emergency_intro)
        binding.emergencyPinGroup.isVisible = true
        binding.emergencyPinError.isVisible = error != null
        binding.emergencyPinError.text = error
    }

    private fun showPage(page: EmergencyRecoveryPage, pageCursor: String?) {
        binding.emergencyPinGroup.isVisible = false
        binding.emergencyPinError.isVisible = false
        binding.emergencyEntries.removeAllViews()
        binding.emergencyWarning.isVisible = false
        binding.emergencyMessage.setText(
            if (page.keys.isNotEmpty()) R.string.wallet_emergency_select_key
            else if (pageCursor == null) R.string.wallet_emergency_empty
            else R.string.wallet_emergency_no_more_keys
        )
        var lastWalletId: Long? = UNSET_WALLET_ID
        page.keys.forEach { key ->
            if (key.walletId != lastWalletId) {
                lastWalletId = key.walletId
                addWalletHeading(key.walletId)
            }
            addText(
                getString(EmergencyRecoveryLabels.kind(key.kind)) + " · " +
                    getString(EmergencyRecoveryLabels.state(key.state)),
                R.style.TextAppearance_Soramitsu_Body1,
                R.color.white_64,
                ITEM_MARGIN_DP
            )
            val identifier = addStoredKeyIdentifier(key.storageKey)
            addActionButton(getString(R.string.wallet_emergency_view_key)) {
                revealKey(key.storageKey, pageCursor)
            }.contentDescription = getString(R.string.wallet_emergency_view_key) + " · " + identifier
        }
        page.nextStorageKey?.let { next ->
            addActionButton(getString(R.string.wallet_emergency_next_page)) { loadPage(next) }
        }
        addActionButton(getString(R.string.wallet_emergency_first_page)) { loadPage(null) }
    }

    private fun addWalletHeading(walletId: Long?) {
        addText(
            text = walletId?.let { getString(R.string.wallet_emergency_wallet, it) }
                ?: getString(R.string.wallet_emergency_wallet_unknown),
            style = R.style.TextAppearance_Soramitsu_Header4,
            color = R.color.white,
            topMargin = SECTION_MARGIN_DP
        )
    }

    private fun addStoredKeyIdentifier(storageKey: String): String {
        val identifier = getString(R.string.wallet_emergency_key_identifier, storageKey)
        addText(identifier, R.style.TextAppearance_Soramitsu_Body1, R.color.white_64, FIELD_MARGIN_DP)
        return identifier
    }

    private fun addEntry(entry: EmergencyRecoveryEntry) {
        addStoredKeyIdentifier(entry.storageKey)
        addText(
            text = getString(EmergencyRecoveryLabels.kind(entry.kind)) + " · " +
                getString(EmergencyRecoveryLabels.state(entry.state)),
            style = R.style.TextAppearance_Soramitsu_Body1,
            color = R.color.white_64,
            topMargin = ITEM_MARGIN_DP
        )
        if (!entry.readable) {
            addText(
                text = getString(R.string.wallet_emergency_unreadable),
                style = R.style.TextAppearance_Soramitsu_Body1,
                color = R.color.white_64,
                topMargin = FIELD_MARGIN_DP
            )
        }
        entry.fields.forEach { field ->
            val label = getString(EmergencyRecoveryLabels.field(field.type))
            addText(label, R.style.TextAppearance_Soramitsu_Body1, R.color.white_64, FIELD_MARGIN_DP)
            addText(field.value, R.style.TextAppearance_Soramitsu_Body1, R.color.white, 0).apply {
                typeface = Typeface.MONOSPACE
                // Copy is available only through the action that marks the clip sensitive.
                setTextIsSelectable(false)
            }
            addCopyButton(label, field.value)
        }
    }

    private fun addText(text: String, style: Int, color: Int, topMargin: Int): TextView {
        val view = TextView(this).apply {
            setTextAppearance(style)
            setTextColor(ContextCompat.getColor(context, color))
            this.text = text
        }
        binding.emergencyEntries.addView(view, linearParams(topMargin))
        return view
    }

    private fun addActionButton(label: String, action: () -> Unit): Button {
        val button = Button(this, null, 0, R.style.Widget_Fearless_Button_Text).apply {
            text = label
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setOnClickListener { action() }
        }
        binding.emergencyEntries.addView(button, linearParams(0))
        return button
    }

    private fun addCopyButton(label: String, value: String) {
        val button = Button(this, null, 0, R.style.Widget_Fearless_Button_Text).apply {
            setText(R.string.common_copy)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setOnClickListener { copy(label, value) }
        }
        binding.emergencyEntries.addView(button, linearParams(0))
    }

    private fun copy(label: String, value: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        // Keeps the value out of the keyboard's clipboard preview on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        } else {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(LEGACY_SENSITIVE_EXTRA, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.common_copied, Toast.LENGTH_SHORT).show()
    }

    private fun linearParams(topMarginDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = (topMarginDp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val MILLIS_IN_SECOND = 1_000L
        private const val SECTION_MARGIN_DP = 24
        private const val ITEM_MARGIN_DP = 12
        private const val FIELD_MARGIN_DP = 8
        private const val UNSET_WALLET_ID = Long.MIN_VALUE
        private const val LEGACY_SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

        fun intent(context: Context): Intent = Intent(context, WalletEmergencyExportActivity::class.java)
    }
}
