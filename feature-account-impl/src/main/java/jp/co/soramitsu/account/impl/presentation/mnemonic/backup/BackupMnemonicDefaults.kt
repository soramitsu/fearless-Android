package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic

internal object BackupMnemonicDefaults {
    @Suppress("UNUSED_PARAMETER")
    fun mnemonicLengthForNewWallet(accountTypes: Collection<WalletEcosystem>): Mnemonic.Length {
        return Mnemonic.Length.TWENTY_FOUR
    }
}
