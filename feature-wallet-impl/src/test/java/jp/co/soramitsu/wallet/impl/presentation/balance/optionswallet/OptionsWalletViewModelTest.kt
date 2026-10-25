package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.core.models.CryptoType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsWalletViewModelTest {
    @Test
    fun `adding TON to universal wallets retains account details and Nexus address access`() {
        for ((substrate, ethereum) in listOf(true to true, true to false, false to true)) {
            assertTrue(walletDetailsAvailable(wallet(substrate, ethereum, ton = false), allowWalletDetails = true))
            assertTrue(walletDetailsAvailable(wallet(substrate, ethereum, ton = true), allowWalletDetails = true))
        }
    }

    @Test
    fun `native TON only wallets retain their existing hidden details behavior`() {
        val nativeTon = wallet(substrate = false, ethereum = false, ton = true)
        assertFalse(walletDetailsAvailable(nativeTon, allowWalletDetails = true))
    }

    @Test
    fun `explicitly disabled details stay hidden for every wallet root combination`() {
        for (roots in 0..7) {
            val account = wallet(
                substrate = roots and 1 != 0,
                ethereum = roots and 2 != 0,
                ton = roots and 4 != 0
            )
            assertFalse(walletDetailsAvailable(account, allowWalletDetails = false))
        }
    }

    private fun wallet(
        substrate: Boolean,
        ethereum: Boolean,
        ton: Boolean
    ) = LightMetaAccount(
        id = 1L,
        substratePublicKey = if (substrate) ByteArray(32) { 1 } else null,
        substrateCryptoType = if (substrate) CryptoType.SR25519 else null,
        substrateAccountId = if (substrate) ByteArray(32) { 1 } else null,
        ethereumAddress = if (ethereum) ByteArray(20) { 2 } else null,
        ethereumPublicKey = if (ethereum) ByteArray(33) { 3 } else null,
        tonPublicKey = if (ton) ByteArray(32) { 4 } else null,
        isSelected = true,
        name = "Restored wallet",
        isBackedUp = true,
        initialized = true
    )
}
