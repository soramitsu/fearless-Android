package jp.co.soramitsu.core.runtime.mappers

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.remote.PriceProvider

fun PriceProvider.mapRemoteToModel(): Asset.PriceProvider {
    val providerType = Asset.PriceProviderType.entries.firstOrNull {
        it.name.equals(type, ignoreCase = true)
    } ?: Asset.PriceProviderType.Unknown

    return Asset.PriceProvider(
        id = id,
        type = providerType,
        precision = precision
    )
}
