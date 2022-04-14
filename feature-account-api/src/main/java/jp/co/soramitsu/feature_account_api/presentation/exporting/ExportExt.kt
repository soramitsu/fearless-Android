package jp.co.soramitsu.feature_account_api.presentation.exporting

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema

inline fun <reified T : Schema<T>> EncodableStruct<T>?.buildExportSourceTypes(isEthereumBased: Boolean): Set<ExportSource> = when (T::class.java) {
    MetaAccountSecrets::class.java -> buildMainAccountOptions(isEthereumBased)
    ChainAccountSecrets::class.java -> buildChainAccountOptions(isEthereumBased)
    else -> mutableSetOf()
}

fun <T : Schema<T>> EncodableStruct<T>?.buildMainAccountOptions(
    isEthereumBased: Boolean
): MutableSet<ExportSource> {
    val options = mutableSetOf<ExportSource>()

    this?.run {
        get(MetaAccountSecrets.Entropy)?.run {
            options += ExportSource.Mnemonic
            options += ExportSource.Seed
        }

        when (isEthereumBased) {
            true -> get(MetaAccountSecrets.EthereumKeypair)?.run { options += ExportSource.Seed }
            else -> get(MetaAccountSecrets.Seed)?.run { options += ExportSource.Seed }
        }

        options += ExportSource.Json
    }

    return options
}

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

    return options
}
