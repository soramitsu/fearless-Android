package jp.co.soramitsu.common.data.network.bitcoin

/** A gap-complete receive/change inventory and confirmed UTXOs still unspent in the mempool view. */
data class BitcoinSpendableAccount(
    val totalSats: Long,
    val spendableSats: Long,
    val pendingSats: Long,
    val sources: List<BitcoinUtxoSource>,
    val utxos: List<BitcoinSpendableUtxo>,
    val nextChangeAddress: String,
    val nextChangePath: String,
    val discovery: BitcoinAccountDiscoveryResult
)
