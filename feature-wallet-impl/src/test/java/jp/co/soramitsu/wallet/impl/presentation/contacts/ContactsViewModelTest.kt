package jp.co.soramitsu.wallet.impl.presentation.contacts

import android.graphics.Picture
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContactsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @Test
    fun contactsAreGroupedByInitialAndPickingOneReturnsItsAddress() {
        val router = mockk<WalletRouter>(relaxed = true)
        val shared = SendSharedState()
        val interactor = mockk<WalletInteractor>(relaxed = true) {
            every { observeAddressBook("polkadot") } returns flowOf(
                listOf(
                    AddressBookContact(WalletTestModels.ALICE, "alice", "polkadot"),
                    AddressBookContact("", null, "polkadot")
                )
            )
            coEvery { getChain("polkadot") } returns WalletTestModels.chain()
        }
        val icons = mockk<AddressIconGenerator> { coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture()) }
        val resources = mockk<ResourceManager>(relaxed = true) { every { getDrawable(any()) } returns ColorDrawable(0) }
        val viewModel = ContactsViewModel(SavedStateHandle(mapOf(ContactsFragment.KEY_PAYLOAD to "polkadot")), shared, interactor, resources, icons, router)

        val loaded = viewModel.state.awaitFirst { it is LoadingState.Loaded } as LoadingState.Loaded
        val groups = loaded.data.contactBookAddresses
        assertEquals(setOf("A", null), groups.keys)
        assertTrue(groups.getValue(null).single().image is ColorDrawable)
        assertTrue(groups.getValue("A").single().image is PictureDrawable)

        viewModel.onContactClick(groups.getValue("A").single())
        viewModel.onCreateContactClick(null, "5x")
        viewModel.onCreateContactClick("kusama", null)
        viewModel.onNavigationClick()

        verify { router.backWithResult(ContactsFragment.RESULT_CONTACT to WalletTestModels.ALICE) }
        verify { router.openCreateContact("polkadot", "5x") }
        verify { router.openCreateContact("kusama", null) }
        verify { router.back() }
    }
}
