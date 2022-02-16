package jp.co.soramitsu.feature_account_api.presentation.exporting

import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

fun EncodableStruct<MetaAccountSecrets>?.buildExportSourceTypes(isEthereumBased: Boolean): Set<ExportSource> {
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
