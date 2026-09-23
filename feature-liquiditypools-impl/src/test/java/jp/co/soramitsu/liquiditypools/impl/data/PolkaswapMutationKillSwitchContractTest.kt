package jp.co.soramitsu.liquiditypools.impl.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkaswapMutationKillSwitchContractTest {

    @Test
    fun `swap liquidity and Demeter writes reauthorize at the final submission boundary`() {
        val root = repositoryRoot()
        val swapRepository = File(
            root,
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/data/PolkaswapRepositoryImpl.kt"
        ).readText()
        val poolsRepository = File(
            root,
            "feature-liquiditypools-impl/src/main/java/jp/co/soramitsu/liquiditypools/impl/data/PoolsRepositoryImpl.kt"
        ).readText()
        val demeterRepository = File(
            root,
            "feature-liquiditypools-impl/src/main/java/jp/co/soramitsu/liquiditypools/impl/data/DemeterFarmingRepositoryImpl.kt"
        ).readText()

        assertSwapSubmissionBoundaryIsValidated(swapRepository)
        assertMutationBoundaries(
            source = poolsRepository,
            switch = "check(featureToggleStore.polkaswapMutationsEnabled)",
            validators = listOf("validateRemoveLiquidity", "validateAddLiquidity")
        )
        assertMutationBoundaries(
            source = demeterRepository,
            switch = "check(featureToggleStore.demeterMutationsEnabled)",
            validators = listOf("validateDeposit", "validateWithdraw", "validateClaim")
        )
    }

    @Test
    fun `SORA authority is contextual canonical signer backed and network fresh`() {
        val authorizer = File(
            repositoryRoot(),
            "feature-liquiditypools-impl/src/main/java/jp/co/soramitsu/liquiditypools/impl/data/SoraDeFiMutationAuthorizer.kt"
        ).readText()

        listOf(
            "chainId == soraMainChainId",
            "chainRegistry.getRuntimeOrNull(chainId)",
            "moduleOrNull(required.pallet)?.calls?.get(required.call)",
            "accountRepository.getSelectedMetaAccount()",
            "getChainAccountSecrets",
            "getSubstrateSecrets",
            "expectedIdentity == null || identity == expectedIdentity",
            "chain.assets.singleOrNull { it.currencyId == currencyId }",
            "canonical.precision == requested.precision",
            "walletRepository.getAccountSpendableBalance",
            "existingFeeAssetSpend + feeInPlanks"
        ).forEach { authorityRequirement ->
            assertTrue("Missing SORA authority check: $authorityRequirement", authorizer.contains(authorityRequirement))
        }
        assertTrue(
            "XOR fee identity must be pinned to the canonical SORA mainnet currency id",
            authorizer.contains("0x0200000000000000000000000000000000000000000000000000000000000000") &&
                authorizer.contains("const val XOR_PRECISION = 18")
        )
    }

    private fun assertSwapSubmissionBoundaryIsValidated(source: String) {
        val submit = source.indexOf("extrinsicService.submitExtrinsic")
        val validatorDefinition = source.indexOf("private suspend fun validateSwapSubmission")
        val gate = source.indexOf("check(featureToggleStore.polkaswapMutationsEnabled)", validatorDefinition)
        val validationCallsBeforeSubmit = source.substring(0, submit).indicesOf("validateSwapSubmission(")

        assertTrue("Swap must have one final extrinsic boundary", submit >= 0)
        assertTrue(
            "Swap validation helper must fail closed on the action switch",
            validatorDefinition >= 0 && gate > validatorDefinition
        )
        assertTrue(
            "Swap must validate before fee estimation and immediately before submission",
            validationCallsBeforeSubmit.size >= 2 &&
                validationCallsBeforeSubmit.last() > submit - MAX_GATE_DISTANCE
        )
    }

    private fun assertMutationBoundaries(source: String, switch: String, validators: List<String>) {
        val gatePositions = source.indicesOf(switch)
        val extrinsicPositions = source.indicesOf("extrinsicService.submitExtrinsic")

        assertTrue("Expected ${validators.size} guarded extrinsics", extrinsicPositions.size == validators.size)
        assertTrue("Each mutation must have a fail-closed final switch", gatePositions.size >= validators.size)

        validators.forEachIndexed { index, validator ->
            val extrinsic = extrinsicPositions[index]
            val validationCalls = source.indicesOf("$validator(").filter { it < extrinsic }
            val preliminaryValidation = validationCalls.getOrNull(validationCalls.lastIndex - 1) ?: -1
            val finalValidation = validationCalls.lastOrNull() ?: -1
            val fee = source.indexOf("extrinsicService.estimateFee", preliminaryValidation)
            val identityPin = source.lastIndexOf("preliminary.context.identity", extrinsic)
            val gate = gatePositions.last { it < extrinsic }

            assertTrue(
                "$validator must run once before fee estimation and again after it",
                preliminaryValidation >= 0 && fee in (preliminaryValidation + 1) until finalValidation &&
                    finalValidation < extrinsic
            )
            assertTrue(
                "$validator must pin the selected account across fee estimation",
                identityPin in finalValidation until extrinsic
            )
            assertTrue(
                "The switch for $validator must be the adjacent final boundary",
                gate in (finalValidation + 1) until extrinsic && gate > extrinsic - MAX_GATE_DISTANCE
            )

            val validatorDefinition = source.indexOf("private suspend fun $validator")
            val nextDefinition = source.indexOf("private suspend fun ", validatorDefinition + 1)
                .takeIf { it >= 0 } ?: source.length
            val validatorBody = source.substring(validatorDefinition, nextDefinition)
            val finalReauthorization = maxOf(
                validatorBody.lastIndexOf("reauthorizeLiquidityContext("),
                validatorBody.lastIndexOf("reauthorizeDemeterContext(")
            )
            assertTrue(
                "$validator must reauthorize after fresh network balances at the final boundary",
                finalReauthorization > validatorBody.lastIndexOf("requireFreshBalances(")
            )
        }
    }

    private fun String.indicesOf(needle: String): List<Int> = buildList {
        var start = 0
        while (true) {
            val index = indexOf(needle, start)
            if (index < 0) return@buildList
            add(index)
            start = index + needle.length
        }
    }

    private fun repositoryRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile }

    private companion object {
        const val MAX_GATE_DISTANCE = 2_000
    }
}
