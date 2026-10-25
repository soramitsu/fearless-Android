package jp.co.soramitsu.polkamarkt.impl.data

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal

/** Missing, ambiguous and unavailable holdings cannot prove either funding or a numeric zero. */
internal fun List<Asset>.marketAssetBalance(currencyId: String): BigDecimal? =
    filter { it.token.configuration.currencyId.equals(currencyId, ignoreCase = true) }
        .singleOrNull()?.transferable
