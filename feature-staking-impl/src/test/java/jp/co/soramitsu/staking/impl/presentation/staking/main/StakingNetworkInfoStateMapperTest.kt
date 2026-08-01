package jp.co.soramitsu.staking.impl.presentation.staking.main

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class StakingNetworkInfoStateMapperTest {

    @Test
    fun `loaded pool state updates the pool default`() {
        val model = StakingNetworkInfoModel.Pool(
            minToJoin = "1 DOT",
            minToJoinFiat = "\$7",
            minToCreate = "10 DOT",
            minToCreateFiat = "\$70",
            existingPools = "42",
            possiblePools = "128",
            maxMembersInPool = "unused",
            maxPoolsMembers = "512"
        )

        val result = mapStakingNetworkInfoState(
            StakingType.POOL,
            LoadingState.Loaded(model),
            defaults
        ) as StakingAssetInfoViewState.StakingPool

        assertEquals("1 DOT", result.minToJoin.value)
        assertEquals("\$7", result.minToJoin.additionalValue)
        assertEquals("10 DOT", result.minToCreate.value)
        assertEquals("42", result.existingPools.value)
        assertEquals("128", result.possiblePools.value)
        assertEquals("512", result.maxPoolsMembers.value)
    }

    @Test
    fun `loaded relay chain state updates the relay default`() {
        val model = StakingNetworkInfoModel.RelayChain(
            lockupPeriod = "28 days",
            minimumStake = "250 DOT",
            minimumStakeFiat = "\$1750",
            totalStake = "1M DOT",
            totalStakeFiat = "\$7M",
            nominatorsCount = "1000"
        )

        val result = mapStakingNetworkInfoState(
            StakingType.RELAYCHAIN,
            LoadingState.Loaded(model),
            defaults
        ) as StakingAssetInfoViewState.RelayChain

        assertEquals("250 DOT", result.minStake.value)
        assertEquals("\$1750", result.minStake.additionalValue)
        assertEquals("28 days", result.unstakingPeriod.value)
        assertEquals("1M DOT", result.totalStaked.value)
        assertEquals("1000", result.activeNominators.value)
    }

    @Test
    fun `loaded parachain state updates the parachain default`() {
        val model = StakingNetworkInfoModel.Parachain(
            lockupPeriod = "7 days",
            minimumStake = "5 GLMR",
            minimumStakeFiat = "\$2"
        )

        val result = mapStakingNetworkInfoState(
            StakingType.PARACHAIN,
            LoadingState.Loaded(model),
            defaults
        ) as StakingAssetInfoViewState.Parachain

        assertEquals("5 GLMR", result.minStake.value)
        assertEquals("\$2", result.minStake.additionalValue)
        assertEquals("7 days", result.unstakingPeriod.value)
    }

    @Test
    fun `loading state returns the selected scenario default`() {
        StakingType.entries.forEach { type ->
            val result = mapStakingNetworkInfoState(
                type,
                LoadingState.Loading(),
                defaults
            )

            assertSame(defaults.getValue(type), result)
        }
    }

    @Test
    fun `stale models cannot leak across any scenario boundary`() {
        val models = mapOf(
            StakingType.POOL to StakingNetworkInfoModel.Pool(
                minToJoin = "attacker pool value",
                minToJoinFiat = null,
                minToCreate = "attacker pool value",
                minToCreateFiat = null,
                existingPools = "attacker pool value",
                possiblePools = null,
                maxMembersInPool = "attacker pool value",
                maxPoolsMembers = null
            ),
            StakingType.RELAYCHAIN to StakingNetworkInfoModel.RelayChain(
                lockupPeriod = "attacker relay value",
                minimumStake = "attacker relay value",
                minimumStakeFiat = null,
                totalStake = "attacker relay value",
                totalStakeFiat = null,
                nominatorsCount = "attacker relay value"
            ),
            StakingType.PARACHAIN to StakingNetworkInfoModel.Parachain(
                lockupPeriod = "attacker parachain value",
                minimumStake = "attacker parachain value",
                minimumStakeFiat = null
            )
        )

        StakingType.entries.forEach { selectedType ->
            models.filterKeys { modelType -> modelType != selectedType }.values.forEach { staleModel ->
                val result = mapStakingNetworkInfoState(
                    selectedType,
                    LoadingState.Loaded(staleModel),
                    defaults
                )

                assertSame(defaults.getValue(selectedType), result)
            }
        }
    }

    @Test
    fun `nullable network values remain recoverable without contaminating labels`() {
        val model = StakingNetworkInfoModel.Pool(
            minToJoin = "1 DOT",
            minToJoinFiat = null,
            minToCreate = "10 DOT",
            minToCreateFiat = null,
            existingPools = "42",
            possiblePools = null,
            maxMembersInPool = "unused",
            maxPoolsMembers = null
        )

        val result = mapStakingNetworkInfoState(
            StakingType.POOL,
            LoadingState.Loaded(model),
            defaults
        ) as StakingAssetInfoViewState.StakingPool

        assertEquals("Minimum to join", result.minToJoin.title)
        assertEquals("1 DOT", result.minToJoin.value)
        assertEquals(null, result.minToJoin.additionalValue)
        assertEquals(null, result.possiblePools.value)
        assertEquals(null, result.maxPoolsMembers.value)
    }

    @Test
    fun `missing or mistyped defaults fail with a deterministic invariant error`() {
        StakingType.entries.forEach { type ->
            val missing = assertThrows(IllegalArgumentException::class.java) {
                mapStakingNetworkInfoState(
                    type,
                    LoadingState.Loading(),
                    defaults - type
                )
            }
            assertEquals("Missing default network info state for $type", missing.message)

            val otherType = StakingType.entries.first { it != type }
            val mistyped = assertThrows(IllegalArgumentException::class.java) {
                mapStakingNetworkInfoState(
                    type,
                    LoadingState.Loading(),
                    defaults + (type to defaults.getValue(otherType))
                )
            }
            assertEquals("Invalid default network info state for $type", mistyped.message)
        }
    }

    @Test
    fun `staking body state accepts only the selected scenario family`() {
        val estimated = EstimatedEarningsViewState(
            monthlyChange = null,
            yearlyChange = null,
            amountInputViewState = AmountInputViewState("DOT", "", "0 DOT", null, BigDecimal.ZERO, null)
        )
        val states = mapOf(
            StakingType.POOL to StakingViewState.Pool.Welcome(estimated),
            StakingType.RELAYCHAIN to StakingViewState.RelayChain.Welcome(estimated),
            StakingType.PARACHAIN to StakingViewState.Parachain.Welcome(estimated)
        )

        StakingType.entries.forEach { selectedType ->
            states.forEach { (stateType, state) ->
                val result = mapStakingBodyState(selectedType, state)

                if (selectedType == stateType) {
                    assertSame(state, result)
                } else {
                    assertEquals(null, result)
                }
            }
        }
        assertEquals(null, mapStakingBodyState(StakingType.POOL, null))
    }

    private val poolDefault = StakingAssetInfoViewState.StakingPool(
        title = "Pools",
        guide = "Guide",
        minToJoin = TitleValueViewState("Minimum to join"),
        minToCreate = TitleValueViewState("Minimum to create"),
        existingPools = TitleValueViewState("Existing pools"),
        possiblePools = TitleValueViewState("Possible pools"),
        maxPoolsMembers = TitleValueViewState("Maximum members")
    )
    private val relayDefault = StakingAssetInfoViewState.RelayChain(
        title = "Relay chain",
        stories = "",
        totalStaked = TitleValueViewState("Total staked"),
        minStake = TitleValueViewState("Minimum stake"),
        activeNominators = TitleValueViewState("Active nominators"),
        unstakingPeriod = TitleValueViewState("Unstaking period")
    )
    private val parachainDefault = StakingAssetInfoViewState.Parachain(
        title = "Parachain",
        stories = "",
        minStake = TitleValueViewState("Minimum stake"),
        unstakingPeriod = TitleValueViewState("Unstaking period")
    )
    private val defaults = mapOf(
        StakingType.POOL to poolDefault,
        StakingType.RELAYCHAIN to relayDefault,
        StakingType.PARACHAIN to parachainDefault
    )
}
