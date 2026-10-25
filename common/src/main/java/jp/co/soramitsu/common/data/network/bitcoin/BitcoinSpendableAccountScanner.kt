package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import java.lang.Math.addExact

/** Uses the same receive/change discovery for display and spending, with paths bound to the wallet. */
class BitcoinSpendableAccountScanner(private val client: BitcoinIndexerClient) {
    /** Requires the root to match before scanning, then reads every funded address rather than index zero. */
    suspend fun scan(
        mnemonic: String,
        expectedFirstReceiveAddress: String,
        network: BitcoinKeyDerivation.Network,
        baseUrl: String? = null,
        gapLimit: Int? = null
    ): BitcoinSpendableAccount {
        val root = BitcoinKeyDerivation.deriveAccount(mnemonic, network = network)
        require(root.firstReceiveAddress == expectedFirstReceiveAddress.lowercase()) { "Bitcoin root does not match wallet" }
        val discovery = BitcoinReceiveDiscovery(client).discoverAccount(mnemonic, network = network, baseUrl = baseUrl, gapLimit = gapLimit)
        val total = discovery.addresses.fold(0L) { sum, address -> addExact(sum, address.totalSats) }
        require(total <= BitcoinUtxoSelector.MAX_SATOSHI)
        val sources = discovery.addresses.filter { it.totalSats > 0 || it.confirmedSats > 0 }
            .map { BitcoinUtxoSource(it.address, it.path) }
        if (sources.size > 100) throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.TOO_MANY_SOURCES)
        val indexerNetwork = if (network == BitcoinKeyDerivation.Network.Mainnet) BitcoinIndexerRoutes.Network.Mainnet else BitcoinIndexerRoutes.Network.Testnet
        val selector = BitcoinUtxoSelector()
        val utxos = sources.flatMap { source ->
            selector.spendableUtxos(source, client.utxos(source.address, indexerNetwork, baseUrl), indexerNetwork, includeUnconfirmed = false)
        }
        require(utxos.map { "${it.txid}:${it.vout}" }.distinct().size == utxos.size) { "Duplicate account UTXO" }
        val spendable = utxos.fold(0L) { sum, coin -> addExact(sum, coin.valueSats) }
        require(spendable <= total) { "Inconsistent Bitcoin balance snapshot" }
        val change = discovery.change
        return BitcoinSpendableAccount(total, spendable, total - spendable, sources, utxos,
            change.nextReceiveAddress, BitcoinKeyDerivation.getReceivePath(network, change.nextReceiveIndex.toLong(), 1), discovery)
    }
}
