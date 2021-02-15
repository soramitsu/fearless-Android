package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.fearless_utils.extensions.requirePrefix

class AppLinksProvider(
    val termsUrl: String,
    val privacyUrl: String,

    private val externalAnalyzerTemplates: Map<ExternalAnalyzer, ExternalAnalyzerLinks>,

    val roadMapUrl: String,
    val devStatusUrl: String
) {

    fun getExternalTransactionUrl(
        analyzer: ExternalAnalyzer,
        hash: String,
        networkType: jp.co.soramitsu.domain.model.Node.NetworkType
    ) = getExternalUrl(analyzer, hashWithPrefix(hash), networkType, ExternalAnalyzerLinks::transaction)

    fun getExternalAddressUrl(
        analyzer: ExternalAnalyzer,
        address: String,
        networkType: jp.co.soramitsu.domain.model.Node.NetworkType
    ) = getExternalUrl(analyzer, address, networkType, ExternalAnalyzerLinks::account)

    private fun getExternalUrl(
        analyzer: ExternalAnalyzer,
        value: String,
        networkType: jp.co.soramitsu.domain.model.Node.NetworkType,
        extractor: (ExternalAnalyzerLinks) -> String
    ): String {
        val template = externalAnalyzerTemplates[analyzer] ?: error("No template for $analyzer")

        return extractor(template).format(networkPathSegment(networkType), value)
    }
}

class ExternalAnalyzerLinks(val transaction: String, val account: String)

enum class ExternalAnalyzer(val supportedNetworks: List<jp.co.soramitsu.domain.model.Node.NetworkType>) {
    SUBSCAN(
        supportedNetworks = listOf(jp.co.soramitsu.domain.model.Node.NetworkType.KUSAMA, jp.co.soramitsu.domain.model.Node.NetworkType.WESTEND, jp.co.soramitsu.domain.model.Node.NetworkType.POLKADOT)
    ),

    POLKASCAN(
        supportedNetworks = listOf(jp.co.soramitsu.domain.model.Node.NetworkType.KUSAMA, jp.co.soramitsu.domain.model.Node.NetworkType.POLKADOT)
    );

    fun isNetworkSupported(networkType: jp.co.soramitsu.domain.model.Node.NetworkType) = networkType in supportedNetworks
}

private fun networkPathSegment(networkType: jp.co.soramitsu.domain.model.Node.NetworkType) = networkType.readableName.toLowerCase()

private fun hashWithPrefix(hash: String) = hash.requirePrefix("0x")