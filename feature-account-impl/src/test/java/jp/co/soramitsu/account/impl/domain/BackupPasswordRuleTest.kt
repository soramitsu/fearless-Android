package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.domain.BackupPasswordRule.Problem
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPasswordRuleTest {

    @Test
    fun `twelve characters need three character classes`() {
        assertNull(BackupPasswordRule.check("abcdefgh12AB", walletName = null, addresses = emptyList()))
        assertNull(BackupPasswordRule.check("abcdefghij1!", walletName = null, addresses = emptyList()))
        assertEquals(Problem.TOO_SHORT_OR_SIMPLE, BackupPasswordRule.check("abcdefghij12", null, emptyList()))
        assertEquals(Problem.TOO_SHORT_OR_SIMPLE, BackupPasswordRule.check("Abc12!xyZ", null, emptyList()))
    }

    @Test
    fun `sixteen characters are enough on their own`() {
        assertNull(BackupPasswordRule.check("correcthorsebatt", null, emptyList()))
        assertEquals(Problem.TOO_SHORT_OR_SIMPLE, BackupPasswordRule.check("correcthorsebat", null, emptyList()))
    }

    @Test
    fun `length counts characters, not UTF-16 units`() {
        val emoji = "🔑"
        assertEquals(Problem.TOO_SHORT_OR_SIMPLE, BackupPasswordRule.check(emoji.repeat(8), null, emptyList()))
        assertNull(BackupPasswordRule.check(emoji.repeat(16), null, emptyList()))
    }

    @Test
    fun `character classes include caseless scripts and symbols as other`() {
        assertEquals(4, BackupPasswordRule.characterClasses("aA1!"))
        assertEquals(2, BackupPasswordRule.characterClasses("パスワード1"))
        assertEquals(3, BackupPasswordRule.characterClasses("пароль ПАРОЛЬ"))
        assertEquals(0, BackupPasswordRule.characterClasses(""))
    }

    @Test
    fun `a password in PIN form is refused before anything else`() {
        assertEquals(Problem.PIN, BackupPasswordRule.check("123456", null, emptyList()))
        assertFalse(BackupPasswordRule.isStrong("123456"))
    }

    @Test
    fun `the wallet name and its addresses are refused in any case or prefix form`() {
        val name = "My Savings Wallet 2026"
        val ethereum = "0x52908400098527886E0F7030069857D2E4169EE7"

        assertEquals(Problem.WALLET_NAME, BackupPasswordRule.check("  my savings wallet 2026 ", name, emptyList()))
        assertEquals(Problem.ADDRESS, BackupPasswordRule.check(ethereum.lowercase(), name, listOf(ethereum)))
        assertEquals(Problem.ADDRESS, BackupPasswordRule.check(ethereum.removePrefix("0x"), name, listOf(ethereum)))
        assertNull(BackupPasswordRule.check("My Savings Wallet 2026!", name, listOf(ethereum)))
        // A blank name never matches anything.
        assertNull(BackupPasswordRule.check("correct horse battery", " ", listOf("")))
    }

    @Test
    fun `wallet addresses cover each ecosystem and the common Substrate forms`() {
        val publicKey = ByteArray(32) { 1 }
        val wallet = MetaAccount(
            id = 1,
            chainAccounts = emptyMap(),
            favoriteChains = emptyMap(),
            substratePublicKey = publicKey,
            substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = publicKey,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Wallet",
            initialized = true
        )

        val addresses = BackupPasswordRule.walletAddresses(wallet)

        assertTrue(addresses.contains(publicKey.toAddress(0.toShort())))
        assertTrue(addresses.contains(publicKey.toAddress(2.toShort())))
        assertTrue(addresses.contains(publicKey.toAddress(42.toShort())))
        assertEquals(Problem.ADDRESS, BackupPasswordRule.check(publicKey.toAddress(42.toShort()), "Wallet", addresses))
    }
}
