package jp.co.soramitsu.liquiditypools.impl.data.network

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DemeterExtrinsicContractTest {

    @Test
    fun `native Demeter flow binds runtime deposit withdraw and reward calls`() {
        val root = repositoryRoot()
        val extrinsics = File(
            root,
            "feature-liquiditypools-impl/src/main/java/jp/co/soramitsu/liquiditypools/impl/data/network/DemeterExtrinsic.kt"
        ).readText()
        val repository = File(
            root,
            "feature-liquiditypools-impl/src/main/java/jp/co/soramitsu/liquiditypools/impl/data/DemeterFarmingRepositoryImpl.kt"
        ).readText()

        assertTrue(extrinsics.contains("DemeterFarmingPlatform"))
        listOf("\"deposit\"", "\"withdraw\"", "\"get_rewards\"").forEach {
            assertTrue(extrinsics.contains(it))
        }
        listOf("base_asset", "pool_asset", "reward_asset", "is_farm", "pooled_tokens").forEach {
            assertTrue(extrinsics.contains(it))
        }
        assertTrue(repository.contains("featureToggleStore.demeterMutationsEnabled"))
        assertTrue(
            "Farm deposits and withdrawals must use LP/pool-token precision",
            repository.contains("validated.target.exactPositivePlanks")
        )
        assertTrue(
            "Stored pooled-token balances must be decoded with LP/pool-token precision",
            repository.contains("mapBalance(demeter.amount, poolTokenMapped.token.configuration.precision)")
        )
    }

    private fun repositoryRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile }
}
