package jp.co.soramitsu.wallet.impl.presentation

import android.graphics.drawable.Drawable
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import java.math.BigDecimal
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.beacon.SignStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

interface WalletRouter : SecureRouter, WalletRouterApi {

    fun trackReturnToAssetDetailsFromChainSelector(): Flow<Unit>?

    fun openAssetDetails(assetPayload: AssetPayload)

    fun openAssetDetailsAndPopUpToBalancesList(assetPayload: AssetPayload)

    fun openAssetIntermediateDetails(assetId: String)

    fun openAssetIntermediateDetailsSort()

    fun openSend(assetPayload: AssetPayload?, initialSendToAddress: String? = null, currencyId: String? = null, amount: BigDecimal? = null)
    fun openLockedAmountSend(assetPayload: AssetPayload?, initialSendToAddress: String? = null, currencyId: String? = null, amount: BigDecimal?)
    fun openCBDCSend(cbdcQrInfo: QrContentCBDC)

    fun openCrossChainSend(assetPayload: AssetPayload?)

    fun openSwapTokensScreen(chainId: String?, assetIdFrom: String?, assetIdTo: String?)

    fun openSelectChain(
        assetId: String,
        chainId: ChainId? = null,
        chooserMode: Boolean = true,
        isSelectAsset: Boolean = true,
        showAllChains: Boolean = false
    )

    fun openSelectChain(
        selectedChainId: ChainId? = null,
        filterChainIds: List<ChainId>? = null,
        chooserMode: Boolean = true,
        currencyId: String? = null,
        showAllChains: Boolean = true,
        isSelectAsset: Boolean = true,
        isFilteringEnabled: Boolean = false
    )

    fun openSelectChainForXcm(
        selectedChainId: ChainId?,
        xcmChainType: XcmChainType,
        selectedOriginChainId: String? = null,
        xcmAssetSymbol: String? = null
    )

    fun openSelectAsset(selectedAssetId: String)

    fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, excludeAssetId: String?)

    fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, isFilterXcmAssets: Boolean)

    fun openFilter(filtersToShowOrAll: Set<TransactionFilter> = TransactionFilter.entries.toSet())

    fun openOperationSuccess(operationHash: String?, chainId: ChainId?)
    fun openOperationSuccess(operationHash: String?, chainId: ChainId?, customMessage: String?, customTitle: String?)

    fun openSendConfirm(transferDraft: TransferDraft, phishingType: PhishingType?, overrides: Map<String, Any?> = emptyMap(), transferComment: String? = null, skipEdValidation: Boolean = false)

    fun openCrossChainSendConfirm(transferDraft: CrossChainTransferDraft, phishingType: PhishingType?)

    fun finishSendFlow()

    fun openTransferDetail(transaction: OperationParcelizeModel.Transfer, assetPayload: AssetPayload, chainExplorerType: Chain.Explorer.Type?)

    fun openSwapDetail(operation: OperationParcelizeModel.Swap)

    fun openExtrinsicDetail(payload: ExtrinsicDetailsPayload)

    fun openRewardDetail(payload: RewardDetailsPayload)

    fun openCreateAccountFromWallet()

    fun openAccountDetails(metaAccountId: Long)

    fun openBackupWalletScreen(metaAccountId: Long)

    fun openRenameWallet(metaAccountId: Long, name: String?)

    fun openImportAccountScreenFromWallet(blockChainType: Int)

    fun openManageControllerAccount(chainId: ChainId)

    fun openReceive(assetPayload: AssetPayload)

    fun openSignBeaconTransaction(payload: SubstrateSignerPayload, dAppMetadata: DAppMetadataModel)

    val beaconSignStatus: Flow<SignStatus>

    fun setBeaconSignStatus(status: SignStatus)

    fun openNodes(chainId: ChainId)
    fun openClaimRewards(chainId: ChainId)

    @PinRequired
    fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportSeed(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(metaId: Long, chainId: ChainId): DelayedNavigation

    fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean)

    fun openSuccessFragment(avatar: Drawable)

    fun openTransactionRawData(rawData: String)

    fun openSelectWallet()

    fun openGetSoraCard()
    fun openSoraCardDetails()

    fun openOptionsAddAccount(metaId: Long, type: WalletEcosystem)

    fun openOptionsSwitchNode(
        metaId: Long,
        chainId: ChainId,
        chainName: String
    )

    fun openAlert(payload: AlertViewState)

    fun openAlert(payload: AlertViewState, resultKey: String)

    fun listenAlertResultFlowFromNetworkIssuesScreen(key: String): Flow<Result<Unit>>

    fun openSearchAssets()

    fun openOptionsWallet(walletId: Long, allowDetails: Boolean = true)

    fun setWalletSelectorPayload(payload: WalletSelectorPayload)

    fun openFrozenTokens(payload: FrozenAssetPayload)

    fun openAddressHistory(chainId: ChainId)

    fun openWalletSelectorForResult(
        selectedWalletId: Long?,
        walletSelectionMode: WalletSelectionMode
    ): Flow<Long>

    fun openAddressHistoryWithResult(chainId: ChainId): Flow<String>

    fun openCreateContact(chainId: ChainId?, address: String?)

    val chainSelectorPayloadFlow: Flow<ChainId?>

    fun openImportRemoteWalletDialog()
    fun openConnectionDetails(topic: String)

    fun listenPolkaswapDisclaimerResultFlowFromMainScreen(): Flow<Boolean>

    fun openContactsWithResult(chainId: ChainId): Flow<String>

    fun openNftCollection(selectedAssetId: ChainId, contractAddress: String, collectionName: String)

    fun openNFTFilter()

    fun openManageAssets()

    fun openServiceScreen()

    fun openScoreDetailsScreen(metaId: Long)

    fun openCreateSubstrateOrEvmAccountScreen()
    fun openCreateTonAccountScreen()

    fun openDappScreen(dapp: DappModel)

    suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject
}
