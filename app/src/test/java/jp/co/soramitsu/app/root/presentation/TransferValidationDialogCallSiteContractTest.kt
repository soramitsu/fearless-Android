package jp.co.soramitsu.app.root.presentation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferValidationDialogCallSiteContractTest {

    @Test
    fun productionCallSitesUseCentralContractForBindingAndEnqueue() {
        callSites().forEach { callSite ->
            val source = File(repositoryRoot(), callSite.path).readText()
            val contract =
                "TransferValidationDialogContract.${callSite.contract}"
            val escapedContract = Regex.escape(contract)

            assertEquals(
                "${callSite.path} must bind its production contract once",
                1,
                Regex(
                    """bindTransferValidationDialog\(\s*$escapedContract,"""
                ).findAll(source).count()
            )
            assertEquals(
                "${callSite.path} must enqueue the same contract once",
                1,
                Regex(
                    """validationDialogCoordinator\.enqueue\(\s*$escapedContract,"""
                ).findAll(source).count()
            )
            callSite.requiredCallbacks.forEach { callback ->
                assertTrue(
                    "${callSite.path} lost $callback",
                    source.contains(callback)
                )
            }
            assertFalse(
                "${callSite.path} reintroduced a parallel request-key literal",
                source.contains("VALIDATION_DIALOG_RESULT_KEY")
            )
        }
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(System.getProperty("user.dir"))) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile &&
                File(it, "feature-wallet-impl").isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private fun callSites() = listOf(
        CallSite(
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/send/setup/SendSetupFragment.kt",
            "SEND_SETUP",
            listOf(
                "onPositive = viewModel::warningConfirmed",
                "onSecondPositive = viewModel::warningConfirmedSecond",
                "onNegative = viewModel::warningCancelled"
            )
        ),
        CallSite(
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/send/confirm/ConfirmSendFragment.kt",
            "CONFIRM_SEND"
        ),
        CallSite(
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/send/setupcbdc/CBDCSendSetupFragment.kt",
            "CBDC_SEND_SETUP"
        ),
        CallSite(
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/cross_chain/confirm/CrossChainConfirmFragment.kt",
            "CROSS_CHAIN_CONFIRM"
        ),
        CallSite(
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/cross_chain/setup/CrossChainSetupFragment.kt",
            "CROSS_CHAIN_SETUP"
        )
    )

    private data class CallSite(
        val path: String,
        val contract: String,
        val requiredCallbacks: List<String> =
            listOf("onPositive = viewModel::warningConfirmed")
    )
}
