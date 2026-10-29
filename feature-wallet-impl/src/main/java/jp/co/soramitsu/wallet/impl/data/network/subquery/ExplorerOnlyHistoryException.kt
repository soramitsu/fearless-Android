package jp.co.soramitsu.wallet.impl.data.network.subquery

/**
 * The network's optional history service has no data for this account (it is missing, serves another
 * cluster, or is down). The explorer has the full record: the history screen shows its link instead of an
 * empty list or an error, and cached operations are kept.
 */
class ExplorerOnlyHistoryException : Exception()
