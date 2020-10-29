package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Parcelable
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import kotlinx.android.parcel.Parcelize

@Parcelize
class ConfirmMnemonicPayload(
    val mnemonic: List<String>,
    val createExtras: CreateExtras?
) : Parcelable {
    @Parcelize
    class CreateExtras(
        val accountName: String,
        val cryptoType: CryptoType,
        val networkType: Node.NetworkType,
        val derivationPath: String
    ) : Parcelable
}