package jp.co.soramitsu.feature_account_api.presentation.exporting

import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

fun EncodableStruct<MetaAccountSecrets>?.buildExportSourceTypes(isEthereumBased: Boolean): Set<ExportSource> {
    val options = mutableSetOf<ExportSource>()

    when {
        this?.get(MetaAccountSecrets.Entropy) != null -> {
            options += ExportSource.Mnemonic
            if (!isEthereumBased) options += ExportSource.Seed
        }
        this?.get(MetaAccountSecrets.Seed) != null || this?.get(MetaAccountSecrets.EthereumKeypair) != null -> {
            options += ExportSource.Seed
        }
    }

    options += ExportSource.Json

    return options
}
