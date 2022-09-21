package jp.co.soramitsu.runtime.multiNetwork.chain

enum class ChainAssetType {
    Normal,
    OrmlChain,
    OrmlAsset,
    ForeignAsset,
    StableAssetPoolToken,
    LiquidCrowdloan,
    VToken,
    VSToken,
    Stable,
    Equilibrium;

    companion object {
        fun from(key: String?): ChainAssetType? {
            return when (key) {
                "normal" -> Normal
                "ormlChain" -> OrmlChain
                "ormlAsset" -> OrmlAsset
                "foreignAsset" -> ForeignAsset
                "stableAssetPoolToken" -> StableAssetPoolToken
                "liquidCrowdloan" -> LiquidCrowdloan
                "vToken" -> VToken
                "vsToken" -> VSToken
                "stable" -> Stable
                "equilibrium" -> Equilibrium
                else -> null
            }
        }
    }
}
