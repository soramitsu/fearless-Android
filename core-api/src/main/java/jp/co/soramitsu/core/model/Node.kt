@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package jp.co.soramitsu.core.model

data class Node(
    val id: Int,
    val name: String,
    val networkType: NetworkType,
    val link: String,
    val isActive: Boolean,
    val isDefault: Boolean,
) {
    enum class NetworkType(
        val readableName: String,
        val runtimeConfiguration: RuntimeConfiguration,
    ) {
        KUSAMA(
            "Kusama",
            RuntimeConfiguration(
                addressByte = 2,
                genesisHash = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
                erasPerDay = 4
            )
        ),
        POLKADOT(
            "Polkadot",
            RuntimeConfiguration(
                addressByte = 0,
                genesisHash = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
                erasPerDay = 1,
            )
        ),
        WESTEND(
            "Westend",
            RuntimeConfiguration(
                addressByte = 42,
                genesisHash = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e",
                erasPerDay = 4
            )
        ),

        ROCOCO(
            "Rococo",
            RuntimeConfiguration(
                addressByte = 43, // TODO wrong address type, actual is 42, but it will conflict with Westend
                genesisHash = "0x1ab7fbd1d7c3532386268ec23fe4ff69f5bb6b3e3697947df3a2ec2786424de3",
                erasPerDay = 4
            )
        );

        companion object {
            fun <T> find(value: T, extractor: (NetworkType) -> T): NetworkType? {
                return values().find { extractor(it) == value }
            }

            fun findByAddressByte(addressByte: Byte) = find(addressByte) { it.runtimeConfiguration.addressByte }

            fun findByGenesis(genesis: String) = find(genesis.removePrefix("0x")) { it.runtimeConfiguration.genesisHash }
        }
    }
}
