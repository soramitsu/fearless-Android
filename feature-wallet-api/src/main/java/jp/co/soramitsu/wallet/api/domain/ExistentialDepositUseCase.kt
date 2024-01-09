package jp.co.soramitsu.wallet.api.domain

import jp.co.soramitsu.core.models.Asset
import java.math.BigInteger

interface ExistentialDepositUseCase {
    suspend operator fun invoke(chainAsset: Asset): BigInteger
}
