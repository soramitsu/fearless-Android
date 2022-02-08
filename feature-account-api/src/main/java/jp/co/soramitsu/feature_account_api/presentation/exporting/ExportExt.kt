package jp.co.soramitsu.feature_account_api.presentation.exporting

import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

fun EncodableStruct<MetaAccountSecrets>?.buildExportSourceTypes(isEthereumBased: Boolean): List<ExportSource> {
    val options = mutableListOf<ExportSource>()

    when {
        this?.get(MetaAccountSecrets.Entropy) != null -> {
            options += ExportSource.Mnemonic
            if (!isEthereumBased) options += ExportSource.Seed
        }
        this?.get(MetaAccountSecrets.Seed) != null -> {
            if (!isEthereumBased) options += ExportSource.Seed
        }
    }

    if (!isEthereumBased) options += ExportSource.Json

    return options
}
