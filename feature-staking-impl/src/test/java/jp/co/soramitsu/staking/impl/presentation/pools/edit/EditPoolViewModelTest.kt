package jp.co.soramitsu.staking.impl.presentation.pools.edit

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditPoolViewModelTest : LegacySelectionFixture() {
    private val provider = StakingPoolSharedStateProvider()
    private val selection = MutableStateFlow<WalletSelectorPayload?>(null)
    private val initialId = ByteArray(32) { 1 }
    private val selectedId = ByteArray(32) { 2 }

    private fun model(): EditPoolViewModel {
        provider.mainState.set(StakingPoolState(chain = StakingLaneFixtures.chain()))
        provider.editPoolState.set(EditPoolFlowState(BigInteger.ONE, "Original pool", initialId, initialId, null, null))
        every { router.walletSelectorPayloadFlow } returns selection
        val wallet = mockk<MetaAccount>()
        every { wallet.chainAccounts } returns emptyMap()
        every { wallet.substrateAccountId } returns selectedId
        coEvery { interactor.getMetaAccount(2) } returns wallet
        return track(EditPoolViewModel(router, provider, interactor))
    }

    @Test
    fun editingRequiresANonemptyChangedNameAndStoresOnlyTheNameChange(): Unit = runBlocking {
        val vm = model()
        assertFalse(vm.state.value.continueAvailable)
        assertEquals(initialId.toAddress(0), vm.state.value.root)
        assertEquals(initialId.toAddress(0), vm.state.value.depositor)
        assertEquals("", vm.state.value.nominator)
        vm.onClearNameClick()
        assertFalse(vm.state.await { it.poolName.isEmpty() }.continueAvailable)
        vm.onNameInput("Original pool")
        assertFalse(vm.state.await { it.poolName == "Original pool" }.continueAvailable)
        vm.onNameInput("Renamed pool")
        assertTrue(vm.state.await { it.poolName == "Renamed pool" }.continueAvailable)
        vm.onNextClick()
        assertEquals("Renamed pool", provider.requireEditPoolState.newPoolName)
        assertNull(provider.requireEditPoolState.newRoot)
        assertNull(provider.requireEditPoolState.newNominator)
        assertNull(provider.requireEditPoolState.newStateToggler)
        verify { router.openEditPoolConfirm() }
    }

    @Test
    fun walletSelectorTagsUpdateTheirCorrespondingRoleAndConfirmationDraft(): Unit = runBlocking {
        val vm = model()
        vm.onRootClick()
        vm.onNominatorClick()
        vm.onStateTogglerClick()
        verify { router.openWalletSelector("root") }
        verify { router.openWalletSelector("nominator") }
        verify { router.openWalletSelector("state_toggler") }
        val address = selectedId.toAddress(0)
        selection.value = WalletSelectorPayload("root", 2)
        assertTrue(vm.state.await { it.root == address }.continueAvailable)
        selection.value = WalletSelectorPayload("nominator", 2)
        assertEquals(address, vm.state.await { it.nominator == address }.nominator)
        selection.value = WalletSelectorPayload("state_toggler", 2)
        assertEquals(address, vm.state.await { it.stateToggler == address }.stateToggler)
        vm.onNextClick()
        assertArrayEquals(selectedId, provider.requireEditPoolState.newRoot)
        assertArrayEquals(selectedId, provider.requireEditPoolState.newNominator)
        assertArrayEquals(selectedId, provider.requireEditPoolState.newStateToggler)
        assertNull(provider.requireEditPoolState.newPoolName)
        vm.onCloseClick()
        verify { router.back() }
    }
}
