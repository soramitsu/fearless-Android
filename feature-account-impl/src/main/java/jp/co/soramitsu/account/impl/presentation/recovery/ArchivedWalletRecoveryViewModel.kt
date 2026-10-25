package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ArchivedWalletRecoveryViewModel @Inject constructor(
    private val recovery: ArchivedWalletRecovery,
    private val router: AccountRouter,
    private val resources: ResourceManager
) : BaseViewModel() {
    data class State(
        val records: List<ArchivedWalletRecovery.Record> = emptyList(),
        val busy: Boolean = true,
        val locked: Boolean = false,
        val failed: Boolean = false,
        val confirmation: Confirmation? = null,
        val export: String? = null
    ) {
        override fun toString(): String = "ArchiveRecoveryState(busy=$busy, locked=$locked, failed=$failed)"
    }
    class Confirmation(val record: ArchivedWalletRecovery.Record, val scope: WalletSecretScope? = null, val purgeStep: Int = 0)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun reload() {
        if (mutable.value.locked) return
        job?.cancel()
        mutable.value = State()
        job = viewModelScope.launch {
            try { mutable.value = State(records = recovery.read(), busy = false) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = State(busy = false, failed = true) }
        }
    }

    fun restore(record: ArchivedWalletRecovery.Record, scope: WalletSecretScope) {
        if (mutable.value.busy || mutable.value.locked) return
        mutable.value = mutable.value.copy(confirmation = Confirmation(record, scope))
    }

    fun purge(record: ArchivedWalletRecovery.Record) {
        if (mutable.value.busy || mutable.value.locked || !record.canPurge) return
        mutable.value = mutable.value.copy(confirmation = Confirmation(record, purgeStep = 1))
    }

    fun confirm() {
        val confirmation = mutable.value.confirmation ?: return
        if (confirmation.purgeStep == 1) {
            mutable.value = mutable.value.copy(confirmation = Confirmation(confirmation.record, purgeStep = 2))
            return
        }
        mutable.value = mutable.value.copy(confirmation = null, busy = true, failed = false)
        job = viewModelScope.launch {
            try {
                if (confirmation.purgeStep == 2) recovery.purge(confirmation.record)
                else recovery.restore(confirmation.record, checkNotNull(confirmation.scope), resources.getString(R.string.wallet_archive_restored_name))
                showMessage(resources.getString(if (confirmation.purgeStep == 2) R.string.wallet_archive_purged else R.string.wallet_recovery_restored))
                mutable.value = State(records = recovery.read(), busy = false)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, failed = true) }
        }
    }

    fun cancelConfirmation() { mutable.value = mutable.value.copy(confirmation = null) }
    fun export(record: ArchivedWalletRecovery.Record) {
        if (!mutable.value.busy && !mutable.value.locked && record.readable) {
            mutable.value = mutable.value.copy(export = recovery.exactExport(record))
        }
    }
    fun closeExport() { mutable.value = mutable.value.copy(export = null) }
    fun stopped() {
        job?.cancel()
        mutable.value = State(busy = false, locked = true)
    }
    fun unlock() = router.openArchivedWalletRecovery()
    fun back() = router.back()
}
