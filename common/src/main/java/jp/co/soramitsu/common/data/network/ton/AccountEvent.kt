package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

data class AccountEvents(
    @SerializedName("events")
    val events: List<AccountEvent>
)

data class AccountEvent (

    @SerializedName("event_id")
    val eventId: kotlin.String,

    @SerializedName("account")
    val account: AccountAddress,

    @SerializedName("timestamp")
    val timestamp: kotlin.Long,

    @SerializedName("actions")
    val actions: kotlin.collections.List<AccountEventAction>,

    /* scam */
    @SerializedName("is_scam")
    val isScam: kotlin.Boolean,

    @SerializedName("lt")
    val lt: kotlin.Long,

    /* Event is not finished yet. Transactions still happening */
    @SerializedName("in_progress")
    val inProgress: kotlin.Boolean,

    /* TODO */
    @SerializedName("extra")
    val extra: kotlin.Long

)

data class AccountEventAction (

    @SerializedName("type")
    val type: AccountEventAction.Type,

    @SerializedName("status")
    val status: AccountEventAction.Status,

    @SerializedName("simple_preview")
    val simplePreview: ActionSimplePreview,

    @SerializedName("base_transactions")
    val baseTransactions: kotlin.collections.List<kotlin.String>,

    @SerializedName("TonTransfer")
    val tonTransfer: TonTransferAction? = null,
//
//    @SerializedName("ContractDeploy")
//    val contractDeploy: ContractDeployAction? = null,
//
    @SerializedName("JettonTransfer")
    val jettonTransfer: JettonTransferAction? = null,
//
//    @SerializedName("JettonBurn")
//    val jettonBurn: JettonBurnAction? = null,
//
//    @SerializedName("JettonMint")
//    val jettonMint: JettonMintAction? = null,
//
//    @SerializedName("NftItemTransfer")
//    val nftItemTransfer: NftItemTransferAction? = null,
//
//    @SerializedName("Subscribe")
//    val subscribe: SubscriptionAction? = null,
//
//    @SerializedName("UnSubscribe")
//    val unSubscribe: UnSubscriptionAction? = null,
//
//    @SerializedName("AuctionBid")
//    val auctionBid: AuctionBidAction? = null,
//
//    @SerializedName("NftPurchase")
//    val nftPurchase: NftPurchaseAction? = null,
//
//    @SerializedName("DepositStake")
//    val depositStake: DepositStakeAction? = null,
//
//    @SerializedName("WithdrawStake")
//    val withdrawStake: WithdrawStakeAction? = null,
//
//    @SerializedName("WithdrawStakeRequest")
//    val withdrawStakeRequest: WithdrawStakeRequestAction? = null,
//
//    @SerializedName("ElectionsDepositStake")
//    val electionsDepositStake: ElectionsDepositStakeAction? = null,
//
//    @SerializedName("ElectionsRecoverStake")
//    val electionsRecoverStake: ElectionsRecoverStakeAction? = null,
//
//    @SerializedName("JettonSwap")
//    val jettonSwap: JettonSwapAction? = null,
//
//    @SerializedName("SmartContractExec")
//    val smartContractExec: SmartContractAction? = null,
//
//    @SerializedName("DomainRenew")
//    val domainRenew: DomainRenewAction? = null,
//
//    @SerializedName("InscriptionTransfer")
//    val inscriptionTransfer: InscriptionTransferAction? = null,
//
//    @SerializedName("InscriptionMint")
//    val inscriptionMint: InscriptionMintAction? = null

) {

    fun isJetton(): Boolean {
        return type in listOf(Type.jettonBurn, Type.jettonTransfer, Type.jettonMint, Type.jettonSwap)
    }

    enum class Type(val value: kotlin.String) {
        @SerializedName("TonTransfer") tonTransfer("TonTransfer"),
        @SerializedName("JettonTransfer") jettonTransfer("JettonTransfer"),
        @SerializedName("JettonBurn") jettonBurn("JettonBurn"),
        @SerializedName("JettonMint") jettonMint("JettonMint"),
        @SerializedName("NftItemTransfer") nftItemTransfer("NftItemTransfer"),
        @SerializedName("ContractDeploy") contractDeploy("ContractDeploy"),
        @SerializedName("Subscribe") subscribe("Subscribe"),
        @SerializedName("UnSubscribe") unSubscribe("UnSubscribe"),
        @SerializedName("AuctionBid") auctionBid("AuctionBid"),
        @SerializedName("NftPurchase") nftPurchase("NftPurchase"),
        @SerializedName("DepositStake") depositStake("DepositStake"),
        @SerializedName("WithdrawStake") withdrawStake("WithdrawStake"),
        @SerializedName("WithdrawStakeRequest") withdrawStakeRequest("WithdrawStakeRequest"),
        @SerializedName("JettonSwap") jettonSwap("JettonSwap"),
        @SerializedName("SmartContractExec") smartContractExec("SmartContractExec"),
        @SerializedName("ElectionsRecoverStake") electionsRecoverStake("ElectionsRecoverStake"),
        @SerializedName("ElectionsDepositStake") electionsDepositStake("ElectionsDepositStake"),
        @SerializedName("DomainRenew") domainRenew("DomainRenew"),
        @SerializedName("InscriptionTransfer") inscriptionTransfer("InscriptionTransfer"),
        @SerializedName("InscriptionMint") inscriptionMint("InscriptionMint"),
        @SerializedName("Unknown") unknown("Unknown");
    }

    enum class Status(val value: kotlin.String) {
        @SerializedName("ok") ok("ok"),
        @SerializedName("failed") failed("failed");
    }
}
data class ActionSimplePreview (

    @SerializedName("name")
    val name: kotlin.String,

    @SerializedName("description")
    val description: kotlin.String,

    @SerializedName("accounts")
    val accounts: kotlin.collections.List<AccountAddress>,

    /* a link to an image for this particular action. */
    @SerializedName("action_image")
    val actionImage: kotlin.String? = null,

    @SerializedName("value")
    val `value`: kotlin.String? = null,

    /* a link to an image that depicts this action's asset. */
    @SerializedName("value_image")
    val valueImage: kotlin.String? = null

)
data class TonTransferAction (

    @SerializedName("sender")
    val sender: AccountAddress,

    @SerializedName("recipient")
    val recipient: AccountAddress,

    /* amount in nanotons */
    @SerializedName("amount")
    val amount: kotlin.Long,

    @SerializedName("comment")
    val comment: kotlin.String? = null,

    @SerializedName("encrypted_comment")
    val encryptedComment: EncryptedComment? = null,

    @SerializedName("refund")
    val refund: Refund? = null

)

data class EncryptedComment (

    @SerializedName("encryption_type")
    val encryptionType: kotlin.String,

    @SerializedName("cipher_text")
    val cipherText: kotlin.String
)

data class Refund (

    @SerializedName("type")
    val type: String,

    @SerializedName("origin")
    val origin: kotlin.String
)

data class JettonTransferAction (

    @SerializedName("senders_wallet")
    val sendersWallet: kotlin.String,

    @SerializedName("recipients_wallet")
    val recipientsWallet: kotlin.String,

    /* amount in quanta of tokens */
    @SerializedName("amount")
    val amount: kotlin.String,

    @SerializedName("jetton")
    val jetton: JettonPreview,

    @SerializedName("sender")
    val sender: AccountAddress? = null,

    @SerializedName("recipient")
    val recipient: AccountAddress? = null,

    @SerializedName("comment")
    val comment: kotlin.String? = null,

    @SerializedName("encrypted_comment")
    val encryptedComment: EncryptedComment? = null,

    @SerializedName("refund")
    val refund: Refund? = null

)
