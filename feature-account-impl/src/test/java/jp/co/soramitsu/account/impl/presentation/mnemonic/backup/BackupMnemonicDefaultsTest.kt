package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMnemonicDefaultsTest {

    @Test
    fun `new Substrate and EVM wallet uses twenty four word mnemonic`() {
        assertEquals(
            Mnemonic.Length.TWENTY_FOUR,
            BackupMnemonicDefaults.mnemonicLengthForNewWallet(
                listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
            )
        )
    }

    @Test
    fun `new TON wallet uses twenty four word mnemonic`() {
        assertEquals(
            Mnemonic.Length.TWENTY_FOUR,
            BackupMnemonicDefaults.mnemonicLengthForNewWallet(listOf(WalletEcosystem.Ton))
        )
    }
}
