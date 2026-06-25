package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

data class ChainRemote(
    val chainId: String,
    val paraId: String?,
    val rank: Int?,
    val name: String,
    val minSupportedVersion: String?,
    val assets: List<ChainAssetRemote>?,
    val nodes: List<ChainNodeRemote>?,
    val externalApi: ChainExternalApiRemote?,
    val icon: String?,
    val addressPrefix: Int,
    val options: List<String>?,
    val parentId: String?,
    val disabled: Boolean = false,
    val identityChain: String? = null,
    val ecosystem: String,
    val androidMinAppVersion: String? = null,
    val tonBridgeUrl: String? = null,
    val xcm: ChainXcmRemote? = null
)

data class ChainXcmRemote(
    val chainId: String?,
    val xcmVersion: String?,
    val availableAssets: List<ChainXcmAssetRemote>?,
    val availableDestinations: List<ChainXcmDestinationRemote>?
)

data class ChainXcmAssetRemote(
    val id: String?,
    val symbol: String?,
    val minAmount: String?
)

data class ChainXcmDestinationRemote(
    val chainId: String?,
    val assets: List<ChainXcmAssetRemote>?,
    val bridgeParachainId: String?,
    val execution: ChainXcmExecutionRemote?
)

data class ChainXcmExecutionRemote(
    val palletName: String?,
    val callName: String?,
    val transferType: String?,
    val destinationLocation: ChainXcmMultiLocationRemote?,
    val assetLocation: ChainXcmMultiLocationRemote?,
    val beneficiaryLocation: ChainXcmMultiLocationRemote?,
    val feeAssetLocation: ChainXcmMultiLocationRemote?,
    val feeAssetItem: Int?,
    val weightLimit: ChainXcmWeightLimitRemote?,
    val destinationFee: ChainXcmDestinationFeeRemote?,
    val bridge: ChainXcmBridgeRemote?
)

data class ChainXcmMultiLocationRemote(
    val parents: Int?,
    val interior: String?
)

data class ChainXcmWeightLimitRemote(
    val type: String?,
    val refTime: String?,
    val proofSize: String?
)

data class ChainXcmDestinationFeeRemote(
    val mode: String?,
    val assetSymbol: String?,
    val amount: String?
)

data class ChainXcmBridgeRemote(
    val parachainId: String?,
    val feeAssetLocation: ChainXcmMultiLocationRemote?,
    val feeAssetItem: Int?
)
