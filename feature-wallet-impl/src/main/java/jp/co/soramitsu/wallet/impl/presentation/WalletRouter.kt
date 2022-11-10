package jp.co.soramitsu.wallet.impl.presentation

import android.graphics.drawable.Drawable
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.wallet.impl.domain.beacon.SignStatus
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import kotlinx.coroutines.flow.Flow

interface WalletRouter : SecureRouter {
    fun openAssetDetails(assetPayload: AssetPayload)

    fun back()

    fun openSend(assetPayload: AssetPayload, initialSendToAddress: String? = null)

    fun openSelectChain(assetId: String)

    fun openSelectAsset(selectedAssetId: String)

    fun openFilter()

    fun openSendSuccess(operationHash: String?, chainId: ChainId)

    fun openSendConfirm(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openTransferDetail(transaction: OperationParcelizeModel.Transfer, assetPayload: AssetPayload)

    fun openExtrinsicDetail(payload: ExtrinsicDetailsPayload)

    fun openRewardDetail(payload: RewardDetailsPayload)

    fun openAddAccount()

    fun openAccountDetails(metaAccountId: Long)

    fun openExportWallet(metaAccountId: Long)

    fun openImportAccountScreen(blockChainType: Int)

    fun openChangeAccountFromWallet()

    fun openReceive(assetPayload: AssetPayload)

    fun openSignBeaconTransaction(payload: SubstrateSignerPayload, dAppMetadata: DAppMetadataModel)

    val beaconSignStatus: Flow<SignStatus>

    fun setBeaconSignStatus(status: SignStatus)

    fun openNodes(chainId: ChainId)

    @PinRequired
    fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportSeed(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(metaId: Long, chainId: ChainId): DelayedNavigation

    fun openManageAssets()

    fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean)

    fun openEducationalStories(stories: StoryGroupModel)

    fun openSuccessFragment(avatar: Drawable)

    fun openTransactionRawData(rawData: String)

    fun openSelectWallet()

    fun openNetworkIssues()

    fun openOptionsAddAccount(payload: AddAccountBottomSheet.Payload)

    fun openNetworkUnavailable(chainName: String?)

    fun openSearchAssets(chainId: String?)

    fun openOptionsWallet(walletId: Long)

    fun setWalletSelectorPayload(payload: WalletSelectorPayload)

    fun openFrozenTokens(payload: FrozenAssetPayload)
}
