package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import android.os.Parcelable
import jp.co.soramitsu.common.model.ImportAccountType
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.parcelize.Parcelize

@Parcelize
class ConfirmMnemonicPayload(
    val mnemonic: List<String>,
    val metaId: Long?,
    val createExtras: CreateExtras?,
    val accountTypes: List<ImportAccountType>
) : Parcelable {
    @Parcelize
    open class CreateExtras(
        open val accountName: String,
        open val cryptoType: CryptoType,
        open val substrateDerivationPath: String,
        open val ethereumDerivationPath: String
    ) : Parcelable
}
