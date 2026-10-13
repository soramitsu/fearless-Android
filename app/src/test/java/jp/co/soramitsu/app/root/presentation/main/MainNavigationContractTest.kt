package jp.co.soramitsu.app.root.presentation.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationContractTest {

    @Test
    fun siblingTabLeafRoutesHaveConcreteOwnersAndUseStrictOwnerSelection() {
        val root = repositoryRoot()
        val graph = File(root, "app/src/main/res/navigation/bottom_nav_graph.xml").readText()
        val portfolio = graph.substringAfter("android:id=\"@+id/portfolioGraph\"")
            .substringBefore("android:id=\"@+id/defiGraph\"")
        val defi = graph.substringAfter("android:id=\"@+id/defiGraph\"")
            .substringBefore("android:id=\"@+id/polkaswapGraph\"")
        val settings = graph.substringAfter("android:id=\"@+id/settingsGraph\"")
            .substringBefore("<!-- Reusable selectors")

        assertTrue(portfolio.contains("android:id=\"@+id/manageAssetsFragment\""))
        assertTrue(defi.contains("android:id=\"@+id/poolsFlowFragment\""))
        assertTrue(settings.contains("android:id=\"@+id/requestPreviewFragment\""))

        val navigator = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/navigation/Navigator.kt"
        ).readText()
        val strictRoute = navigator.substringAfter("private fun navigateInMainTabOrRoot")
            .substringBefore("private fun activeAuthenticatedController")
        assertTrue(strictRoute.contains("navigateToMainTabDestination"))
        assertFalse(strictRoute.contains("runCatching"))
        assertFalse(strictRoute.contains("Log.w"))
        listOf(
            "openManageAssets" to "R.id.manageAssetsFragment",
            "openPools" to "R.id.poolsFlowFragment",
            "openRequestPreview" to "R.id.requestPreviewFragment"
        ).forEach { (entryPoint, destination) ->
            val routeBody = navigator.substringAfter("override fun $entryPoint")
                .substringBefore("override fun")
            assertTrue("$entryPoint must route to $destination", routeBody.contains(destination))
            assertTrue(routeBody.contains("navigateInMainTabOrRoot"))
        }

        val tabNavigation = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/main/MainTabNavigation.kt"
        ).readText()
        val ownerNavigation = tabNavigation.substringAfter("internal fun navigateToMainTabDestination")
            .substringBefore("internal fun rootDestinationForMainTab")
        assertTrue(ownerNavigation.contains("requireDirectChildOwner"))
        assertTrue(ownerNavigation.contains("selectMainTabOrThrow"))
        assertFalse(ownerNavigation.contains("runCatching"))
    }

    @Test
    fun walletOptionsAreGlobalSoOpeningThemFromASelectorDoesNotSwitchTabs() {
        val graph = File(repositoryRoot(), "app/src/main/res/navigation/bottom_nav_graph.xml")
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(graph)
        val dialogs = document.getElementsByTagName("dialog")
        for (id in listOf("optionsWalletFragment", "accountDetailsDialog", "backupWalletDialog", "renameAccountDialog")) {
            val options = (0 until dialogs.length).map { dialogs.item(it) as org.w3c.dom.Element }
                .single { it.getAttribute("android:id") == "@+id/$id" }
            assertTrue("$id must remain in the same shell as the invoking reusable selector",
                options.parentNode === document.documentElement)
        }
    }

    @Test
    fun fiveDestinationMenuUsesDefiHubAndContainsNoCrowdloan() {
        val root = repositoryRoot()
        val menu = File(root, "common/src/main/res/menu/bottom_navigations_fab.xml").readText()
        val graph = File(root, "app/src/main/res/navigation/bottom_nav_graph.xml").readText()

        listOf(
            "portfolioGraph",
            "defiGraph",
            "polkaswapGraph",
            "crossChainGraph",
            "settingsGraph"
        ).forEach { destination ->
            assertTrue("Missing main destination $destination", menu.contains(destination))
        }
        assertTrue(graph.contains("defiHubFragment"))
        assertTrue(graph.contains("stakingFragment"))
        assertTrue(graph.contains("app:startDestination=\"@id/portfolioGraph\""))
        assertTrue(graph.contains("android:id=\"@+id/polkaswapGraph\""))
        assertTrue(graph.contains("android:id=\"@+id/crossChainGraph\""))
        assertTrue(graph.contains("android:id=\"@+id/settingsGraph\""))
        assertTrue(
            graph.contains(
                "android:id=\"@+id/crossChainGraph\"\n        app:startDestination=\"@id/crossChainFragment\""
            )
        )
        assertFalse(graph.contains("crossChainHubFragment"))
        assertFalse(menu.contains("crowdloan", ignoreCase = true))
        assertTrue(graph.contains("legacyCrowdloanFragment"))
        assertFalse(graph.contains("android:id=\"@+id/crowdloanFragment\""))
        assertFalse(graph.contains("presentation.main.CrowdloanFragment"))

        listOf(
            "swapTokensFragment",
            "polkaswapDisclaimerFragment",
            "transactionSettingsFragment",
            "swapPreviewFragment",
            "selectMarketFragment",
            "crossChainFragment",
            "confirmCrossChainSendFragment"
        ).forEach { destination ->
            assertTrue(
                "$destination must be a normal tab-stack fragment",
                graph.contains("<fragment\n            android:id=\"@+id/$destination\"")
            )
            assertFalse(
                "$destination must not be a modal navigation destination",
                graph.contains("<dialog\n            android:id=\"@+id/$destination\"")
            )
        }

        val mainFragment = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/main/MainFragment.kt"
        ).readText()
        assertTrue(mainFragment.contains("setOnItemReselectedListener"))
        assertTrue(mainFragment.contains("R.id.defiHubFragment"))
        assertTrue(mainFragment.contains("R.id.crossChainFragment"))
        assertFalse(mainFragment.contains("R.id.crossChainHubFragment"))
        assertTrue(mainFragment.contains("TAB_ROOT_DESTINATIONS"))
        assertTrue(mainFragment.contains("navigator.attachMainTabs"))

        val tabNavigation = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/main/MainTabNavigation.kt"
        ).readText()
        assertTrue(tabNavigation.contains("R.id.crossChainGraph -> R.id.crossChainFragment"))
        assertFalse(tabNavigation.contains("R.id.crossChainGraph -> R.id.crossChainHubFragment"))

        val navigator = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/navigation/Navigator.kt"
        ).readText()
        assertTrue(navigator.contains("navigateInMainTabOrRoot"))
        assertTrue(navigator.contains("attachMainTabs"))
        assertTrue(navigator.contains("authenticatedDestinationTarget"))
        assertFalse(navigator.contains("if (!navigatedInTab)"))
        assertTrue(graph.contains("scoreDetailsFragment"))
        assertFalse(navigator.contains("navController?.navigate(R.id.scoreDetailsFragment"))
        listOf(
            "confirmSendFragment",
            "successSheetFragment",
            "transferDetailFragment",
            "rewardDetailFragment",
            "extrinsicDetailFragment",
            "swapDetailFragment",
            "chainAccountsDialog",
            "poolFullUnstakeDepositorAlertFragment"
        ).forEach { child ->
            assertTrue("Missing authenticated child $child", graph.contains(child))
        }
    }

    @Test
    fun crossChainRootDoesNotDereferenceOptionalAssetPayload() {
        val source = File(
            repositoryRoot(),
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/" +
                "presentation/cross_chain/setup/CrossChainSetupViewModel.kt"
        ).readText()

        assertFalse(source.contains("payload!!"))
        assertTrue(source.contains("fun onOriginChainClick()"))
        assertTrue(source.contains("XcmChainType.Origin"))
    }

    @Test
    fun polkaswapAndCrossChainChildrenUsePersistentFragmentHosts() {
        val root = repositoryRoot()
        listOf(
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/presentation/swap_tokens/SwapTokensFragment.kt",
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/presentation/disclaimer/PolkaswapDisclaimerFragment.kt",
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/presentation/select_market/SelectMarketFragment.kt",
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/presentation/swap_preview/SwapPreviewFragment.kt",
            "feature-polkaswap-impl/src/main/kotlin/jp/co/soramitsu/polkaswap/impl/presentation/transaction_settings/TransactionSettingsFragment.kt",
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/presentation/cross_chain/setup/CrossChainSetupFragment.kt",
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/presentation/cross_chain/confirm/CrossChainConfirmFragment.kt"
        ).forEach { relativePath ->
            val source = File(root, relativePath).readText()
            assertTrue("$relativePath must use a persistent Fragment host", source.contains("BaseComposeFragment"))
            assertFalse(
                "$relativePath must not use a bottom-sheet dialog host",
                source.contains("BaseComposeBottomSheetDialogFragment")
            )
        }
    }

    @Test
    fun crowdloanIsUnreachableFromNormalDiscoveryWhileLegacyAccountingRemains() {
        val root = repositoryRoot()
        val normalDiscoverySources = listOf(
            "app/src/main/res/navigation/main_nav_graph.xml",
            "common/src/main/res/menu/bottom_navigations_fab.xml",
            "app/src/main/java/jp/co/soramitsu/app/di/app/NavigationModule.kt",
            "feature-account-impl/src/main/java/jp/co/soramitsu/account/impl/presentation/profile/ProfileScreen.kt"
        )

        normalDiscoverySources.forEach { relativePath ->
            val source = File(root, relativePath).readText()
            assertFalse("Crowdloan is globally reachable through $relativePath", source.contains("crowdloan", true))
        }

        val authenticatedGraph = File(root, "app/src/main/res/navigation/bottom_nav_graph.xml").readText()
        assertTrue(authenticatedGraph.contains("android:id=\"@+id/legacyCrowdloanFragment\""))
        assertTrue(
            authenticatedGraph.contains(
                "jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy.LegacyCrowdloanFragment"
            )
        )
        assertFalse(authenticatedGraph.contains("android:id=\"@+id/crowdloanFragment\""))

        val navigator = File(
            root,
            "app/src/main/java/jp/co/soramitsu/app/root/navigation/Navigator.kt"
        ).readText()
        assertTrue(navigator.contains("override fun openLegacyCrowdloan"))
        assertTrue(navigator.contains("navigateInMainTabOrRoot(\n            R.id.legacyCrowdloanFragment"))
        assertFalse(navigator.contains("R.id.crowdloanFragment"))

        val balanceDetail = File(
            root,
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/presentation/balance/detail/BalanceDetailViewModel.kt"
        ).readText()
        assertTrue(balanceDetail.contains("shouldShowLegacyCrowdloan"))
        assertTrue(balanceDetail.contains("router.openLegacyCrowdloan(assetPayload.value)"))

        val retainedBalanceBinding = File(
            root,
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/SubstrateBalanceLoader.kt"
        ).readText()
        assertTrue(retainedBalanceBinding.contains("ChainAssetType.LiquidCrowdloan"))
        assertTrue(File(root, "feature-crowdloan-impl").isDirectory)
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull {
                File(it, "settings.gradle").isFile && File(it, "feature-wallet-impl").isDirectory
            } ?: error("Cannot locate the Fearless Android repository root")
    }
}
