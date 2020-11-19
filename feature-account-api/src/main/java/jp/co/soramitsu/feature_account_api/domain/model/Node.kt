package jp.co.soramitsu.feature_account_api.domain.model

import java.math.BigDecimal

data class Node(
    val id: Int,
    val name: String,
    val networkType: NetworkType,
    val link: String,
    val isDefault: Boolean
) {
    enum class NetworkType(
        val readableName: String,
        val genesisHash: String,
        val existentialDeposit: BigDecimal
    ) {
        KUSAMA(
            "Kusama",
            "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
            BigDecimal("0.001666666666")
        ),
        POLKADOT(
            "Polkadot",
            "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
            BigDecimal("1")
        ),
        WESTEND(
            "Westend",
            "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e",
            BigDecimal("0.01")
        )
    }
}