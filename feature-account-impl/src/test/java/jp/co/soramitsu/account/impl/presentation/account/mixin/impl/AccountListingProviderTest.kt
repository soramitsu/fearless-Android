package jp.co.soramitsu.account.impl.presentation.account.mixin.impl

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountListingProviderTest {
    private val interactor = mockk<AccountInteractor>()
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val substrateOnly = VmTestFixtures.meta(id = 3, name = "Sub", ethereumAddress = null, ethereumPublicKey = null)

    @Test
    fun `a single wallet maps to its id, name, selection and a wallet icon of the requested size`(): Unit = runBlocking {
        coEvery { interactor.getLightMetaAccount(3) } returns substrateOnly
        val ui = AccountListingProvider(interactor, icons).getAccount(3, 40)

        assertEquals(3, ui.id)
        assertEquals("Sub", ui.name)
        assertTrue(ui.isSelected)
        assertNotNull(ui.picture.value)
        coVerify { icons.createWalletIcon(WalletEcosystem.Substrate, 40) }
    }

    @Test
    fun `the wallet list maps every wallet with the medium icon by default`(): Unit = runBlocking {
        val ton = VmTestFixtures.meta(
            id = 4, name = "Ton", substratePublicKey = null, substrateAccountId = null, ethereumAddress = null,
            ethereumPublicKey = null, tonPublicKey = ByteArray(32) { 1 }
        ).copy(isSelected = false)
        every { interactor.lightMetaAccountsFlow() } returns flowOf(listOf(substrateOnly, ton))

        val list = AccountListingProvider(interactor, icons).accountsFlow(null).first()

        assertEquals(listOf("Sub", "Ton"), list.map { it.name })
        assertFalse(list[1].isSelected)
        coVerify { icons.createWalletIcon(WalletEcosystem.Ton, AddressIconGenerator.SIZE_MEDIUM) }
    }
}
