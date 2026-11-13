package jp.co.soramitsu.staking.impl.domain

import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import java.math.BigInteger

/** The network's minimum stake that earns rewards cannot be determined yet. */
class MinimumStakeUnavailableException : IllegalStateException("The minimum stake cannot be read yet")

/**
 * The lowest stake that earns rewards: `MinimumActiveStake` where it can be read, else the lowest nomination in the
 * active exposures. Null when neither is known, for example after a failed read while the exposures are not cached
 * yet or their nominator pages are missing: the minimum is then unavailable, never zero and never an exception.
 */
fun lowestActiveStake(minimumActiveStake: BigInteger?, exposures: Collection<LegacyExposure>): BigInteger? =
    minimumActiveStake ?: exposures.flatMap { it.others }.minOfOrNull { it.value }
