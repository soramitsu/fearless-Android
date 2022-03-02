package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Parcelable
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ConfirmMnemonicPayload(
    val mnemonic: List<String>,
    val createExtras: CreateExtras?
) : Parcelable {
    @Parcelize
    open class CreateExtras(
        open val accountName: String,
        open val cryptoType: CryptoType,
        open val substrateDerivationPath: String,
        open val ethereumDerivationPath: String,
    ) : Parcelable

    @Parcelize
    class CreateChainExtras(
        override val accountName: String,
        override val cryptoType: CryptoType,
        override val substrateDerivationPath: String,
        override val ethereumDerivationPath: String,
        val chainId: ChainId,
        val metaId: Long
    ) : CreateExtras(accountName, cryptoType, substrateDerivationPath, ethereumDerivationPath)
}
