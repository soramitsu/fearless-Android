package jp.co.soramitsu.tonconnect.impl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonConnectWalletBindingTest {

    @Test
    fun shouldBindThePickedWallet_whenItIsNotTheSelectedOne() {
        val wallets = listOf(SELECTED to KEY_A, PICKED to KEY_B)

        assertEquals(PICKED, TonConnectWalletBinding.boundWalletId(KEY_B.copyOf(), wallets, selectedWalletId = SELECTED))
    }

    @Test
    fun shouldBindTheSelectedWallet_whenItIsTheOneThatWasPicked() {
        val wallets = listOf(PICKED to KEY_B, SELECTED to KEY_A)

        assertEquals(SELECTED, TonConnectWalletBinding.boundWalletId(KEY_A.copyOf(), wallets, selectedWalletId = SELECTED))
    }

    @Test
    fun shouldPreferTheSelectedWallet_whenSeveralWalletsShareTheKey() {
        val wallets = listOf(PICKED to KEY_A, SELECTED to KEY_A)

        assertEquals(SELECTED, TonConnectWalletBinding.boundWalletId(KEY_A, wallets, selectedWalletId = SELECTED))
        assertEquals(PICKED, TonConnectWalletBinding.boundWalletId(KEY_A, wallets, selectedWalletId = OTHER))
    }

    @Test
    fun shouldBindNothing_whenNoWalletHoldsTheKey() {
        val wallets = listOf(SELECTED to KEY_A, PICKED to null)

        assertNull(TonConnectWalletBinding.boundWalletId(KEY_B, wallets, selectedWalletId = SELECTED))
        assertNull(TonConnectWalletBinding.boundWalletId(ByteArray(0), wallets, selectedWalletId = SELECTED))
    }

    private companion object {
        const val SELECTED = 1L
        const val PICKED = 2L
        const val OTHER = 3L
        val KEY_A = ByteArray(32) { 0x0A }
        val KEY_B = ByteArray(32) { 0x0B }
    }
}
