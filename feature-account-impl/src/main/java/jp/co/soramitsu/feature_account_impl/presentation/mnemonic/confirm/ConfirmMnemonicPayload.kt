package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ConfirmMnemonicPayload(
    val mnemonic: List<String>,
    val createExtras: CreateExtras?
) : Parcelable {
    @Parcelize
    class CreateExtras(
        val accountName: String,
        val cryptoType: jp.co.soramitsu.domain.model.CryptoType,
        val networkType: jp.co.soramitsu.domain.model.Node.NetworkType,
        val derivationPath: String
    ) : Parcelable
}