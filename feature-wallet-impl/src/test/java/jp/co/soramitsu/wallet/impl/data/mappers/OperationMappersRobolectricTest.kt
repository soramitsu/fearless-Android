package jp.co.soramitsu.wallet.impl.data.mappers

import android.graphics.Picture
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.WalletTestModels.ALICE
import jp.co.soramitsu.wallet.impl.WalletTestModels.BOB
import jp.co.soramitsu.wallet.impl.WalletTestModels.ETH_A
import jp.co.soramitsu.wallet.impl.WalletTestModels.ETH_B
import jp.co.soramitsu.wallet.impl.WalletTestModels.TON_RAW
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse.Query.HistoryElements.Node
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItem
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItemParam
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OperationMappersRobolectricTest {

    private val dot = WalletTestModels.asset(precision = 10)
    private val xor = WalletTestModels.asset(id = "xor", chainId = "sora", precision = 18, currencyId = SORA_STAKING_CURRENCY_ID)
    private val valAsset = WalletTestModels.asset(id = "val", chainId = "sora", precision = 18, currencyId = SORA_REWARD_ASSET_ID, isUtility = false)
    private val sora = WalletTestModels.chain(id = "sora", assets = listOf(xor, valAsset), explorers = listOf(Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://sora.subscan.io")))
    private val polkadot = WalletTestModels.chain(assets = listOf(dot))

    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "res:${firstArg<Int>()}" }
        every { getDrawable(any()) } returns ColorDrawable(0)
    }
    private val icons = mockk<AddressIconGenerator> {
        coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { createEthereumAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { createWalletIcon(any(), any()) } returns PictureDrawable(Picture())
    }

    private val transfer = Operation.Type.Transfer(
        hash = "0xhash", myAddress = ALICE, amount = BigInteger.TEN.pow(10),
        sender = ALICE, receiver = BOB, status = Operation.Status.COMPLETED, fee = BigInteger.valueOf(1_000_000)
    )
    private val reward = Operation.Type.Reward(BigInteger.TEN.pow(9), isReward = true, era = 12, validator = BOB)
    private val extrinsic = Operation.Type.Extrinsic("0xext", "Balances", "transfer", BigInteger.valueOf(2_000_000), Operation.Status.FAILED)
    private val swap = Operation.Type.Swap(
        "0xswap", "liquidityProxy", BigInteger.TEN.pow(18), BigInteger.TEN.pow(15), "SMART", valAsset,
        BigInteger.TEN.pow(18).multiply(BigInteger.TWO), BigInteger.TEN.pow(16), Operation.Status.PENDING
    )

    @Test
    fun statusesMapBothWays() {
        Operation.Status.entries.forEach { status ->
            assertEquals(status.name, mapOperationStatusToOperationLocalStatus(status).name)
        }
        assertEquals(Operation.Status.COMPLETED, Operation.Status.fromSuccess(true))
    }

    @Test
    fun databaseMappingPreservesFieldsExceptTheLegacySwapModule() {
        listOf(transfer, reward, extrinsic, swap).forEach { type ->
            val asset = if (type is Operation.Type.Swap) xor else dot
            val chain = if (type is Operation.Type.Swap) sora else polkadot
            val operation = Operation("id-${type.javaClass.simpleName}", ALICE, type, 1_000L, asset)

            val local = mapOperationToOperationLocalDb(operation, OperationLocal.Source.APP)
            val restored = mapOperationLocalToOperation(local, asset, chain)

            assertEquals(OperationLocal.Source.APP, local.source)
            // Existing database mapping records module only for extrinsics, losing a swap's module.
            val expected = if (type is Operation.Type.Swap) operation.copy(type = type.copy(module = "")) else operation
            assertEquals(expected, restored)
        }
    }

    @Test
    fun localOperationOfAnUnknownAssetKeepsTheGivenAsset() {
        val local = OperationLocal(
            id = "x", address = ALICE, chainId = "polkadot", chainAssetId = "missing", time = 0,
            status = OperationLocal.Status.UNKNOWN, source = OperationLocal.Source.SUBQUERY,
            operationType = OperationLocal.Type.SWAP
        )

        val operation = mapOperationLocalToOperation(local, dot, polkadot)

        assertEquals(dot, operation.chainAsset)
        assertEquals(Operation.Status.UNKNOWN, (operation.type as Operation.Type.Swap).status)
        assertNull((operation.type as Operation.Type.Swap).targetAsset)
    }

    @Test
    fun soraHistoryItemsBecomeTransfersSwapsRewardsAndStaking() {
        val all = TransactionFilter.entries.toSet()
        val transferItem = item("transfer", "assets", params("assetId" to SORA_STAKING_CURRENCY_ID, "from" to ALICE, "to" to BOB, "amount" to "1.5"))
        val swapItem = item("swap", "liquidityProxy", params(
            "baseAssetId" to SORA_STAKING_CURRENCY_ID, "targetAssetId" to SORA_REWARD_ASSET_ID, "baseAssetAmount" to "2",
            "targetAssetAmount" to "3", "liquidityProviderFee" to "0.01", "selectedMarket" to "TBC"
        ))
        val rewardItem = item("rewarded", "staking", params("amount" to "4", "era" to "7"))
        val stakingItem = item("bond", "staking", emptyList(), fee = "nope")

        val transferOp = transferItem.toOperation(sora, xor, ALICE, all)!!
        val swapOp = swapItem.toOperation(sora, xor, ALICE, all)!!
        val rewardOp = rewardItem.toOperation(sora, valAsset, ALICE, all)!!
        val stakingOp = stakingItem.toOperation(sora, xor, ALICE, all)!!

        assertEquals(BigInteger("1500000000000000000"), (transferOp.type as Operation.Type.Transfer).amount)
        assertEquals(BOB, (transferOp.type as Operation.Type.Transfer).receiver)
        assertEquals(5_000L, transferOp.time)
        assertEquals(valAsset, (swapOp.type as Operation.Type.Swap).targetAsset)
        assertEquals("TBC", (swapOp.type as Operation.Type.Swap).selectedMarket)
        assertEquals(7, (rewardOp.type as Operation.Type.Reward).era)
        assertEquals(BigInteger.ZERO, (stakingOp.type as Operation.Type.Extrinsic).fee)
    }

    @Test
    fun historyItemsOutsideTheFiltersOrForOtherAssetsAreDropped() {
        val transferItem = item("transfer", "assets", params("assetId" to "other"))
        val swapItem = item("swap", "liquidityProxy", params("baseAssetId" to "a", "targetAssetId" to "b"))
        val swapWithoutBase = item("swap", "liquidityProxy", params("baseAssetId" to "a", "targetAssetId" to SORA_STAKING_CURRENCY_ID))

        assertNull(transferItem.toOperation(sora, xor, ALICE, TransactionFilter.entries.toSet()))
        assertNull(swapItem.toOperation(sora, xor, ALICE, TransactionFilter.entries.toSet()))
        assertNull(swapWithoutBase.toOperation(sora, xor, ALICE, TransactionFilter.entries.toSet()))
        assertNull(item("transfer", "assets", null).toOperation(sora, xor, ALICE, emptySet()))
        assertNull(item("unknown", "x", null).toOperation(sora, xor, ALICE, TransactionFilter.entries.toSet()))
    }

    @Test
    fun subqueryNodesBecomeOperations() {
        val rewardNode = Node("r", "10", ALICE, Node.Rewards(3, BigInteger.ONE, true, "", null), null, null)
        val extrinsicNode = Node("e", "11", ALICE, null, null, Node.Extrinsic("0x1", "staking", "bond", BigInteger.TWO, true, null))
        val transferNode = Node("t", "12", ALICE, null, Node.Transfer(BigInteger.TEN, BOB, ALICE, BigInteger.ONE, "1", false, null, null), null)

        assertNull((mapNodeToOperation(rewardNode, dot).type as Operation.Type.Reward).validator)
        assertEquals(Operation.Status.COMPLETED, (mapNodeToOperation(extrinsicNode, dot).type as Operation.Type.Extrinsic).status)
        assertEquals(12_000L, mapNodeToOperation(transferNode, dot).time)
        assertEquals(Operation.Status.FAILED, (mapNodeToOperation(transferNode, dot).type as Operation.Type.Transfer).status)
        assertThrows(IllegalStateException::class.java) { mapNodeToOperation(Node("n", "1", ALICE, null, null, null), dot) }
    }

    @Test
    fun operationModelsDescribeEachTypeForTheList(): Unit = runBlocking {
        val incoming = model(Operation.Type.Transfer(null, BOB, BigInteger.TEN.pow(10), BOB, ALICE, Operation.Status.PENDING, null), Ecosystem.Substrate)
        val outgoingEvm = model(transfer.copy(myAddress = ETH_A, sender = ETH_A, receiver = ETH_B), Ecosystem.Ethereum)
        val ton = model(transfer.copy(status = Operation.Status.FAILED), Ecosystem.Ton)
        val rewardModel = model(reward, Ecosystem.Substrate)
        val slashModel = model(reward.copy(isReward = false), Ecosystem.Substrate)
        val extrinsicModel = model(extrinsic, Ecosystem.Substrate)
        val crowdloanModel = model(extrinsic.copy(module = "Crowdloan", call = "contribute", status = Operation.Status.COMPLETED), Ecosystem.Substrate)
        val swapModel = mapOperationToOperationModel(Operation("s", ALICE, swap, 0, xor), resources, icons, Ecosystem.Substrate)
        val completedSwap = mapOperationToOperationModel(Operation("s", ALICE, swap.copy(status = Operation.Status.COMPLETED, targetAsset = null), 0, xor), resources, icons, Ecosystem.Substrate)

        assertEquals(OperationModel.Type.Transfer, incoming.type)
        assertTrue(incoming.amount.startsWith("+"))
        assertEquals("5GrwvaEF...oHGKutQY", incoming.header)
        assertEquals(OperationStatusAppearance.PENDING, incoming.statusAppearance)
        assertNotNull(outgoingEvm.operationIcon)
        assertEquals(OperationStatusAppearance.FAILED, ton.statusAppearance)
        assertEquals("res:${R.string.staking_reward}", rewardModel.header)
        assertEquals("res:${R.string.staking_slash}", slashModel.header)
        assertEquals("Transfer fee", extrinsicModel.header)
        assertEquals("Contribute fee", crowdloanModel.header)
        assertEquals(dot.iconUrl, extrinsicModel.assetIconUrl)
        assertTrue(swapModel.amount.contains("➝"))
        assertEquals("res:${R.string.transaction_status_pending}", swapModel.subHeader)
        assertEquals("res:${R.string.polkaswap_confirmation_swapped_stub}", completedSwap.subHeader)
    }

    @Test
    fun detailStatesListTheOperationFacts(): Unit = runBlocking {
        val substrateTransfer = details(transfer, polkadot) as TransferDetailsState
        val evmTransfer = details(transfer.copy(sender = ETH_A, receiver = ETH_B, myAddress = ETH_B), WalletTestModels.chain(ecosystem = Ecosystem.Ethereum)) as TransferDetailsState
        val tonTransfer = details(transfer.copy(status = Operation.Status.FAILED), WalletTestModels.chain(ecosystem = Ecosystem.Ton)) as TransferDetailsState
        val rewardDetails = details(reward, polkadot) as TransferDetailsState
        val noValidator = details(reward.copy(validator = null), polkadot) as TransferDetailsState
        val extrinsicDetails = details(extrinsic.copy(module = "", call = ""), polkadot) as TransferDetailsState
        val evmExtrinsic = details(extrinsic, WalletTestModels.chain(ecosystem = Ecosystem.EthereumBased), address = ETH_A) as TransferDetailsState
        val tonExtrinsic = details(extrinsic, WalletTestModels.chain(ecosystem = Ecosystem.Ton), address = TON_RAW) as TransferDetailsState
        val swapDetails = mapOperationToTransactionDetailsState(Operation("s", ALICE, swap, 0, xor), resources, icons, sora) as SwapDetailState
        val unknownSwap = mapOperationToTransactionDetailsState(
            Operation("s", ALICE, swap.copy(targetAsset = null, selectedMarket = null), 0, xor), resources, icons, polkadot
        ) as SwapDetailState

        assertEquals("0xhash", substrateTransfer.hash.text)
        assertEquals(BOB, substrateTransfer.secondAddress!!.input)
        assertEquals(3, substrateTransfer.items.size)
        assertEquals(ETH_A, evmTransfer.firstAddress!!.input)
        assertEquals(OperationStatusAppearance.FAILED, tonTransfer.status)
        assertEquals(BOB, rewardDetails.firstAddress!!.input)
        assertNull(noValidator.firstAddress)
        assertEquals(2, extrinsicDetails.items.size)
        assertEquals(4, evmExtrinsic.items.size)
        assertTrue(tonExtrinsic.firstAddress!!.input != TON_RAW)
        assertEquals("XOR", swapDetails.fromTokenName)
        assertEquals("VAL", swapDetails.toTokenName)
        assertTrue(swapDetails.isShowSubscanButtons)
        assertEquals("???", unknownSwap.toTokenName)
        assertEquals("???", unknownSwap.toTokenAmount)
        assertEquals(false, unknownSwap.isShowSubscanButtons)
    }

    @Test
    fun decimalParsingIsLenient() {
        assertEquals(java.math.BigDecimal("1.5"), "1.5".toBigDecimalOrNull())
        assertNull("abc".toBigDecimalOrNull())
    }

    private suspend fun model(type: Operation.Type, ecosystem: Ecosystem) =
        mapOperationToOperationModel(Operation("op", ALICE, type, 1_700_000_000_000, dot), resources, icons, ecosystem)

    private suspend fun details(type: Operation.Type, chain: Chain, address: String = ALICE) =
        mapOperationToTransactionDetailsState(Operation("op", address, type, 1_700_000_000_000, dot), resources, icons, chain)

    private fun params(vararg pairs: Pair<String, String>) = pairs.map { TxHistoryItemParam(it.first, it.second) }

    private fun item(method: String, module: String, data: List<TxHistoryItemParam>?, fee: String = "1000") =
        TxHistoryItem("id-$method", "block", module, method, "5", fee, true, data, emptyList())
}
