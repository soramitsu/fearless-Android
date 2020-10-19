package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.common.utils.requirePrefix
import jp.co.soramitsu.feature_account_api.domain.model.Node

class AppLinksProvider(
    val termsUrl: String,
    val privacyUrl: String,
    private val externalAnalyzerTemplates: Map<ExternalAnalyzer, String>
) {

    fun getExternalTransactionUrl(
        analyzer: ExternalAnalyzer,
        hash: String,
        networkType: Node.NetworkType
    ): String {
        val template = externalAnalyzerTemplates[analyzer] ?: error("No template for $analyzer")

        return template.format(networkPathSegment(networkType), hashWithPrefix(hash))
    }
}

enum class ExternalAnalyzer(val supportedNetworks: List<Node.NetworkType>) {
    SUBSCAN(
        supportedNetworks = listOf(Node.NetworkType.KUSAMA, Node.NetworkType.WESTEND, Node.NetworkType.POLKADOT)
    ),

    POLKASCAN(
        supportedNetworks = listOf(Node.NetworkType.KUSAMA, Node.NetworkType.POLKADOT)
    );

    fun isNetworkSupported(networkType: Node.NetworkType) = networkType in supportedNetworks
}

private fun networkPathSegment(networkType: Node.NetworkType) = networkType.readableName.toLowerCase()

private fun hashWithPrefix(hash: String) = hash.requirePrefix("0x")