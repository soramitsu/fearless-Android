package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.ExposurePage
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.Payout
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindCurrentEra
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindEraRewardPoints
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExposurePage
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindValidatorPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.math.BigInteger

/**
 * On-chain discovery includes former nominations; it does not depend on a relay-chain indexer.
 *
 * The current targets are not a history (a validator's `kick` removes a target and keeps `submitted_in`), so every
 * era is searched. Only unclaimed pages can pay, and within days of an era nearly all its pages are claimed, so a
 * page is read only when its validator has not claimed it: on Polkadot Asset Hub (2026-10-30) a full history is
 * about 50 000 pages (70 MB), of which a few hundred are unclaimed.
 */
internal class AssetHubPayoutRepository(
    private val registry: ChainRegistry
) {

    suspend fun payouts(state: StakingState.Stash): List<Payout> = withTimeout(STAKING_READ_TIMEOUT_MILLIS) {
        val snapshot = AssetHubStorageSnapshot.capture(registry, state.chain.id)
        val runtime = snapshot.runtime
        val staking = runtime.metadata.staking()
        val eraKeys = listOf(staking.storage("CurrentEra").storageKey(), staking.storage("ActiveEra").storageKey())
        val eraValues = snapshot.query(eraKeys)
        val currentEra = bindCurrentEra(requireNotNull(eraValues[eraKeys[0]]), runtime)
        val activeEra = bindActiveEra(requireNotNull(eraValues[eraKeys[1]]), runtime)
        val firstEra = (currentEra - staking.numberConstant("HistoryDepth", runtime)).max(BigInteger.ZERO)
        // Eras are independent reads at one block. One at a time, a full history took 130-140 s of the timeout from
        // a desktop connection (2026-10-30); a few in flight keep it well inside on a phone.
        val keys = inParallel(eraRange(firstEra, activeEra)) { era -> unclaimedPageKeys(snapshot, era) }.flatten()
        // Bound memory and RPC payloads while retaining every historical page.
        val relevant = inParallel(keys.chunked(PAGE_BATCH)) { chunk -> stashPages(snapshot, state, chunk) }.flatten()
        calculate(snapshot, state, relevant) + ownStakeOnlyPayouts(snapshot, state, firstEra, activeEra)
    }

    /** The exposure pages among [keys] that pay the stash: as the page's validator or as one of its nominators. */
    private suspend fun stashPages(
        snapshot: AssetHubStorageSnapshot,
        state: StakingState.Stash,
        keys: List<String>
    ): List<Pair<AssetHubExposureKey, ExposurePage>> {
        val runtime = snapshot.runtime
        val paged = runtime.metadata.staking().storage("ErasStakersPaged")
        val pages = snapshot.query(keys)
        return keys.mapNotNull { key ->
            val raw = pages[key] ?: error("Exposure disappeared from finalized staking state")
            val page = requireNotNull(bindExposurePage(raw, runtime)) { "Malformed staking exposure page" }
            val identity = assetHubExposureKey(key)
            require(paged.storageKey(runtime, identity.era, identity.validator, identity.page) == key)
            val ownValidator = identity.validator.contentEquals(state.stashId)
            if (ownValidator || page.others.any { it.who.contentEquals(state.stashId) }) identity to page else null
        }
    }

    /** At most [PARALLEL_READS] items in flight; results keep the items' order and the first failure fails all. */
    private suspend fun <T, R> inParallel(items: List<T>, block: suspend (T) -> R): List<R> = coroutineScope {
        val permits = Semaphore(PARALLEL_READS)
        items.map { item -> async { permits.withPermit { block(item) } } }.awaitAll()
    }

    /** The era's exposure pages that their validator has not claimed: claimed pages pay nothing more. */
    private suspend fun unclaimedPageKeys(snapshot: AssetHubStorageSnapshot, era: BigInteger): List<String> {
        val runtime = snapshot.runtime
        val staking = runtime.metadata.staking()
        val pages = snapshot.discoverKeys(staking.storage("ErasStakersPaged").storageKey(runtime, era))
            .associateWith(::assetHubExposureKey)
        require(pages.values.all { it.era == era }) { "Exposure page outside its era" }
        val claimed = staking.storage("ClaimedRewards")
        val claimers = snapshot.discoverKeys(claimed.storageKey(runtime, era)).map { key ->
            val bytes = key.fromHex()
            require(bytes.size == CLAIMED_KEY_BYTES) { "Unsupported claimed rewards key layout" }
            val validator = bytes.copyOfRange(VALIDATOR_OFFSET, VALIDATOR_END)
            require(claimed.storageKey(runtime, era, validator) == key)
            validator.toHexString()
        }.toSet()
        val pagesByValidator = pages.values.groupBy { it.validator.toHexString() }
        // A validator with one page and a claim has claimed that page; only several pages need the claimed list.
        val partly = pagesByValidator.filter { (validator, own) -> validator in claimers && own.size > 1 }.keys.toList()
        val claimedKeys = partly.map { claimed.storageKey(runtime, era, it.fromHex()) }
        val claimedValues = snapshot.query(claimedKeys)
        val claimedPages = partly.zip(claimedKeys).associate { (validator, key) ->
            validator to claimedValues[key]?.let {
                claimed.returnType().fromHexOrNull(runtime, it) as? List<*> ?: error("Malformed claimed reward pages")
            }.orEmpty()
        }
        return pages.filter { (_, identity) ->
            val validator = identity.validator.toHexString()
            when {
                validator !in claimers -> true
                pagesByValidator.getValue(validator).size == 1 -> false
                else -> identity.page !in claimedPages.getValue(validator)
            }
        }.keys.toList()
    }

    private fun eraRange(from: BigInteger, until: BigInteger): List<BigInteger> =
        generateSequence(from) { it + BigInteger.ONE }.takeWhile { it < until }.toList()

    private suspend fun ownStakeOnlyPayouts(
        snapshot: AssetHubStorageSnapshot,
        state: StakingState.Stash,
        firstEra: BigInteger,
        activeEra: BigInteger
    ): List<Payout> {
        val runtime = snapshot.runtime
        val overview = runtime.metadata.staking().storage("ErasStakersOverview")
        val eras = generateSequence(firstEra) { it + BigInteger.ONE }.takeWhile { it < activeEra }.toList()
        val keys = eras.map { overview.storageKey(runtime, it, state.stashId) }
        val values = snapshot.query(keys)
        val pages = eras.zip(keys).mapNotNull { (era, key) ->
            val exposure = values[key]?.let { requireNotNull(bindExposure(it, runtime)) } ?: return@mapNotNull null
            if (exposure.pageCount == BigInteger.ZERO) {
                AssetHubExposureKey(era, state.stashId, BigInteger.ZERO) to ExposurePage(BigInteger.ZERO, emptyList())
            } else {
                null
            }
        }
        return calculate(snapshot, state, pages)
    }

    private suspend fun calculate(
        snapshot: AssetHubStorageSnapshot,
        state: StakingState.Stash,
        pages: List<Pair<AssetHubExposureKey, ExposurePage>>
    ): List<Payout> {
        if (pages.isEmpty()) return emptyList()
        val runtime = snapshot.runtime
        val staking = runtime.metadata.staking()
        val keysByPage = pages.associate { (identity, _) ->
            identity to listOf(
                staking.storage("ClaimedRewards").storageKey(runtime, identity.era, identity.validator),
                staking.storage("ErasStakersOverview").storageKey(runtime, identity.era, identity.validator),
                staking.storage("ErasValidatorPrefs").storageKey(runtime, identity.era, identity.validator),
                staking.storage("ErasValidatorReward").storageKey(runtime, identity.era),
                staking.storage("ErasRewardPoints").storageKey(runtime, identity.era)
            )
        }
        val values = snapshot.query(keysByPage.values.flatten())
        return pages.mapNotNull { (identity, page) ->
            val keys = keysByPage.getValue(identity)
            val claimed = values[keys[0]]?.let {
                staking.storage("ClaimedRewards").returnType().fromHexOrNull(runtime, it) as? List<*>
                    ?: error("Malformed claimed reward pages")
            }.orEmpty()
            if (identity.page in claimed) return@mapNotNull null
            // Active eras have exposure, but no completed reward budget yet.
            val budgetScale = values[keys[REWARD_BUDGET_INDEX]] ?: return@mapNotNull null
            val overview = requireNotNull(values[keys[1]]?.let { bindExposure(it, runtime) })
            val prefs = bindValidatorPrefs(requireNotNull(values[keys[2]]), runtime, staking.storage("ErasValidatorPrefs").returnType())
            val budget = staking.storage("ErasValidatorReward").returnType().fromHexOrNull(runtime, budgetScale) as? BigInteger
                ?: error("Malformed era reward budget")
            val points = bindEraRewardPoints(values[keys[REWARD_POINTS_INDEX]], runtime, staking.storage("ErasRewardPoints").returnType())
            val validatorPoints = points.individual.singleOrNull { it.accountId.contentEquals(identity.validator) }?.rewardPoints
                ?: BigInteger.ZERO
            val validatorBudget = assetHubPerbillShare(budget, validatorPoints, points.totalPoints)
            val commission = validatorBudget.toBigDecimal().multiply(prefs.commission).toBigInteger()
            val ownReward = assetHubPerbillShare(validatorBudget - commission, overview.own, overview.total)
            val ownValidator = identity.validator.contentEquals(state.stashId)
            val amount = if (ownValidator) {
                val pageStake = page.pageTotal + if (identity.page == BigInteger.ZERO) overview.own else BigInteger.ZERO
                assetHubPerbillShare(commission + ownReward, pageStake, overview.total) +
                    validatorIncentive(snapshot, identity, validatorPoints, pageStake, overview.total)
            } else {
                val stake = page.others.single { it.who.contentEquals(state.stashId) }.value
                assetHubPerbillShare(validatorBudget - commission - ownReward, stake, overview.total - overview.own)
            }
            if (amount.signum() <= 0) {
                null
            } else {
                Payout(
                identity.validator.toAddress(state.chain.addressPrefix.toShort()), identity.era, amount, identity.page
            )
            }
        }
    }

    private suspend fun validatorIncentive(
        snapshot: AssetHubStorageSnapshot,
        identity: AssetHubExposureKey,
        points: BigInteger,
        pageStake: BigInteger,
        totalStake: BigInteger
    ): BigInteger {
        if (points.signum() == 0) return BigInteger.ZERO
        val runtime = snapshot.runtime
        val staking = runtime.metadata.staking()
        val names = listOf("ErasValidatorIncentiveBudget", "ErasValidatorIncentiveWeight", "ErasSumValidatorIncentiveWeight")
        val keys = names.map { name ->
            if (name == "ErasValidatorIncentiveWeight") {
                staking.storage(name).storageKey(runtime, identity.era, identity.validator)
            } else {
                staking.storage(name).storageKey(runtime, identity.era)
            }
        }
        val values = snapshot.query(keys)
        val numbers = names.zip(keys).map { (name, key) ->
            values[key]?.let {
                staking.storage(name).returnType().fromHexOrNull(runtime, it) as? BigInteger
                ?: error("Malformed validator incentive")
            } ?: BigInteger.ZERO
        }
        val (budget, weight, totalWeight) = numbers
        if (budget.signum() == 0 || weight.signum() == 0) return BigInteger.ZERO
        val cutoffStorage = staking.storageOrNull("WeightedPointsFormulaStartEra")
        val cutoffKey = cutoffStorage?.storageKey()
        val cutoff = cutoffKey?.let { snapshot.query(listOf(it))[it] }?.let {
            requireNotNull(cutoffStorage).returnType().fromHexOrNull(runtime, it) as? BigInteger ?: error("Malformed incentive cutoff")
        }
        val share = if (cutoffStorage != null && (cutoff == null || identity.era >= cutoff)) {
            val sum = staking.storage("ErasSumWeightedPoints")
            val sumKey = sum.storageKey(runtime, identity.era)
            val raw = requireNotNull(snapshot.query(listOf(sumKey))[sumKey])
            val denominator = sum.returnType().fromHexOrNull(runtime, raw) as? BigInteger ?: error("Malformed weighted points")
            assetHubPerbillShare(budget, weight * points, denominator)
        } else {
            assetHubPerbillShare(budget, weight, totalWeight)
        }
        return assetHubPerbillShare(share, pageStake, totalStake)
    }

    private companion object {
        const val STAKING_READ_TIMEOUT_MILLIS = 180_000L
        const val PAGE_BATCH = 128
        const val PARALLEL_READS = 8
        const val REWARD_BUDGET_INDEX = 3
        const val REWARD_POINTS_INDEX = 4
    }
}

private const val EXPOSURE_KEY_BYTES = 96
private const val CLAIMED_KEY_BYTES = 84
private const val ERA_OFFSET = 40
private const val ERA_END = 44
private const val VALIDATOR_OFFSET = 52
private const val VALIDATOR_END = 84
private const val PAGE_OFFSET = 92
private val perbillAccuracy = BigInteger.valueOf(1_000_000_000L)

internal data class AssetHubExposureKey(val era: BigInteger, val validator: ByteArray, val page: BigInteger)

internal fun assetHubExposureKey(key: String): AssetHubExposureKey {
    val bytes = key.fromHex()
    require(bytes.size == EXPOSURE_KEY_BYTES) { "Unsupported paged staking key layout" }
    return AssetHubExposureKey(
        BigInteger(1, bytes.copyOfRange(ERA_OFFSET, ERA_END).reversedArray()),
        bytes.copyOfRange(VALIDATOR_OFFSET, VALIDATOR_END),
        BigInteger(1, bytes.copyOfRange(PAGE_OFFSET, EXPOSURE_KEY_BYTES).reversedArray())
    )
}

internal fun assetHubPerbillShare(
    amount: BigInteger,
    numerator: BigInteger,
    denominator: BigInteger
): BigInteger {
    require(amount.signum() >= 0 && numerator.signum() >= 0 && denominator.signum() >= 0 && numerator <= denominator)
    if (numerator == BigInteger.ZERO || denominator == BigInteger.ZERO) return BigInteger.ZERO
    return numerator * perbillAccuracy / denominator * amount / perbillAccuracy
}
