package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.resources.ResourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedWalletRecoveryViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val recovery = mockk<ArchivedWalletRecovery>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private lateinit var model: ArchivedWalletRecoveryViewModel
    private val record = ArchivedWalletRecovery.Record("wallet_secret_archive:v1:7:ETHEREUM_SECRETS",
        listOf(ArchivedWalletRecovery.Address(WalletSecretScope.Ethereum, "0xpublic")), 0, true,
        EncryptedPreferenceSnapshot.unencrypted("public fixture data"))

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { recovery.read() } returns listOf(record)
        val resources = mockk<ResourceManager> { every { getString(any()) } returns "Localized fixture" }
        model = ArchivedWalletRecoveryViewModel(recovery, router, resources)
    }
    @After fun teardown() {
        model.viewModelScope.cancel()
        model.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test fun permanentDeletionNeedsTwoDistinctConfirmationsAndCanBeCancelled() = runTest(dispatcher) {
        runCurrent()
        model.purge(record)
        assertEquals(1, model.state.value.confirmation?.purgeStep)
        model.confirm()
        runCurrent()
        assertEquals(2, model.state.value.confirmation?.purgeStep)
        coVerify(exactly = 0) { recovery.purge(any()) }
        model.cancelConfirmation()
        assertNull(model.state.value.confirmation)
        model.confirm()
        runCurrent()
        coVerify(exactly = 0) { recovery.purge(any()) }
        model.purge(record)
        model.confirm()
        model.confirm()
        runCurrent()
        coVerify(exactly = 1) { recovery.purge(record) }
        assertFalse(model.state.value.busy)
    }

    @Test fun restoreShowsAnAddressConfirmationThenCallsOnlyTheSelectedScope() = runTest(dispatcher) {
        runCurrent()
        model.restore(record, WalletSecretScope.Ethereum)
        coVerify(exactly = 0) { recovery.restore(any(), any(), any()) }
        assertEquals(WalletSecretScope.Ethereum, model.state.value.confirmation?.scope)
        model.confirm()
        runCurrent()
        coVerify(exactly = 1) { recovery.restore(record, WalletSecretScope.Ethereum, "Localized fixture") }
    }

    @Test fun backgroundClearsAllMaterialAndRequiresAuthenticationBeforeReadingAgain() = runTest(dispatcher) {
        runCurrent()
        every { recovery.exactExport(record) } returns "exact private fixture"
        model.export(record)
        assertEquals("exact private fixture", model.state.value.export)
        model.closeExport()
        assertNull(model.state.value.export)
        model.export(record)
        model.purge(record)
        model.stopped()
        assertTrue(model.state.value.locked)
        assertTrue(model.state.value.records.isEmpty())
        assertNull(model.state.value.confirmation)
        assertNull(model.state.value.export)
        model.reload()
        runCurrent()
        coVerify(exactly = 1) { recovery.read() }
        model.unlock()
        model.back()
        verify(exactly = 1) { router.openArchivedWalletRecovery() }
        verify(exactly = 1) { router.back() }
    }

    @Test fun failureKeepsRetryAvailableWithoutClaimingNoDataExists() = runTest(dispatcher) {
        coEvery { recovery.read() } throws IllegalStateException()
        model.reload()
        runCurrent()
        assertTrue(model.state.value.failed)
        coEvery { recovery.read() } returns listOf(record)
        model.reload()
        runCurrent()
        assertFalse(model.state.value.failed)
        assertEquals(listOf(record), model.state.value.records)
    }
}
