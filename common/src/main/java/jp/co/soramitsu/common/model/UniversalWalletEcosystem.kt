package jp.co.soramitsu.common.model

enum class UniversalWalletEcosystem(val id: String) {
    Substrate("substrate"),
    Evm("evm"),
    Ton("ton"),
    Bitcoin("bitcoin"),
    Solana("solana"),
    Iroha("iroha");

    companion object {
        fun fromId(id: String): UniversalWalletEcosystem? = values().firstOrNull { it.id == id }
    }
}
