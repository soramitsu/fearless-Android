package jp.co.soramitsu.account.api.presentation.exporting

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema


fun <T : Schema<T>> EncodableStruct<T>?.buildChainAccountOptions(
    isEthereumBased: Boolean
): MutableSet<ExportSource> {
    val options = mutableSetOf<ExportSource>()

    this?.run {
        get(ChainAccountSecrets.Entropy)?.run {
            options += ExportSource.Mnemonic
            options += ExportSource.Seed
        }

        if (isEthereumBased) options += ExportSource.Seed
        get(ChainAccountSecrets.Seed)?.run { options += ExportSource.Seed }

        options += ExportSource.Json
    }

    return options.toSortedSet(compareBy { it.sort })
}
