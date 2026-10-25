package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain

/** The registry snapshot the shared files were reviewed against (root `config/portfolio/registry/`). */
internal const val REVIEWED_REGISTRY_RESOURCE = "portfolio/chains-v13-20261009.json"

internal const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
internal const val POLKADOT_ASSET_HUB_CHAIN_ID = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
internal const val MOONBEAM_CHAIN_ID = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"
internal const val DOT_ASSET_ID = "887a17c7-1370-4de0-97dd-5422e294fa75"
internal const val ETHEREUM_CHAIN_ID = "1"
internal const val ARBITRUM_CHAIN_ID = "42161"
internal const val OPTIMISM_CHAIN_ID = "10"
internal const val POLYGON_ZKEVM_CHAIN_ID = "1101"
internal const val ETHEREUM_ETH_ASSET_ID = "c2a6c062-d511-4bde-9ce6-ea775d2a302c"
internal const val ARBITRUM_ETH_ASSET_ID = "37b6375a-0708-4728-bec9-bf15b8680aff"
internal const val OPTIMISM_ETH_ASSET_ID = "08bb34b8-8027-4fea-948d-5243f5cbd6ba"
internal const val POLYGON_ZKEVM_ETH_ASSET_ID = "8f85b561-18f5-4cf0-9077-305ebc84d015"
internal const val ARBITRUM_USDC_ASSET_ID = "0xaf88d065e77c8cc2239327c5edb3a432268e5831"
internal const val CAGA_ANKARA_CHAIN_ID = "72778"
internal const val KUSAMA_ASSET_HUB_CHAIN_ID = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"

/** Kusama AssetHub Assets id 11: a third-party token the registry names "tether usd" and prices as Tether. */
internal const val KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID = "27768227-8a9a-4443-a57d-6013c24f85e9"
internal const val WESTEND_CHAIN_ID = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"

internal fun bundledAsset(name: String): ByteArray {
    return listOf(File("src/main/assets/$name"), File("runtime/src/main/assets/$name"))
        .first(File::isFile)
        .readBytes()
}

internal fun testResource(name: String): ByteArray {
    return requireNotNull(SharedPortfolioConfigPinTest::class.java.classLoader?.getResource(name)) {
        "Missing test resource $name"
    }.readBytes()
}

internal fun bundledConfigSource(): SharedPortfolioConfigSource {
    return SharedPortfolioConfigSource(
        readAsset = ::bundledAsset,
        onLoadFailure = { name, error -> throw AssertionError("Bundled $name failed to load", error) }
    )
}

internal fun bundledConfig(): SharedPortfolioConfig = bundledConfigSource().config

internal fun bundledCatalog(): TokenGroupCatalog = requireNotNull(bundledConfig().tokenGroups)

internal fun bundledCatalogJson(): JsonObject {
    return JsonParser.parseString(bundledAsset(SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET).decodeToString()).asJsonObject
}

internal fun JsonObject.toBytes(): ByteArray = toString().toByteArray()

internal fun JsonObject.group(id: String): JsonObject {
    return getAsJsonArray("groups").map { it.asJsonObject }.single { it["id"].asString == id }
}

/** Enabled chains of a registry file mapped exactly as the app maps the remote registry. */
internal fun registryChains(bytes: ByteArray): List<Chain> {
    val type = object : TypeToken<List<ChainRemote>>() {}.type
    return Gson().fromJson<List<ChainRemote>>(bytes.decodeToString(), type)
        .filterNot(ChainRemote::disabled)
        .map(ChainRemote::toChain)
}

internal fun reviewedRegistryChains(): List<Chain> = registryChains(testResource(REVIEWED_REGISTRY_RESOURCE))

internal fun bundledRegistryChains(): List<Chain> = registryChains(bundledAsset("local_chains.json"))

internal fun List<Chain>.chain(chainId: String): Chain = single { it.id == chainId }

internal fun List<Chain>.replaceChain(chainId: String, transform: (Chain) -> Chain): List<Chain> {
    return map { if (it.id == chainId) transform(it) else it }
}

internal fun List<Chain>.replaceAsset(
    chainId: String,
    assetId: String,
    transform: (Asset) -> Asset
): List<Chain> = replaceChain(chainId) { chain ->
    require(chain.assets.any { it.id == assetId }) { "No asset $assetId on $chainId" }
    chain.copy(assets = chain.assets.map { if (it.id == assetId) transform(it) else it })
}

internal fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
