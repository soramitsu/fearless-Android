package jp.co.soramitsu.feature_wallet_impl.presentation

import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignStatus
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface WalletRouter : SecureRouter {
    fun openAssetDetails(assetPayload: AssetPayload)

    fun back()

    fun openChooseRecipient(assetPayload: AssetPayload)

    fun openFilter()

    fun openChooseAmount(recipientAddress: String, assetPayload: AssetPayload)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String, assetPayload: AssetPayload)

    fun openTransferDetail(transaction: OperationParcelizeModel.Transfer, assetPayload: AssetPayload)

    fun openExtrinsicDetail(payload: ExtrinsicDetailsPayload)

    fun openRewardDetail(payload: RewardDetailsPayload)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive(assetPayload: AssetPayload)

    fun openSignBeaconTransaction(payload: SubstrateSignerPayload)

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
}
