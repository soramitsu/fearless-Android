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
            )
        ),
        POLKADOT(
            "Polkadot",
            RuntimeConfiguration(
                addressByte = 0,
                genesisHash = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
            )
        ),
        WESTEND(
            "Westend",
            RuntimeConfiguration(
                addressByte = 42,
                genesisHash = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e",
            )
        ),

        ROCOCO(
            "Rococo",
            RuntimeConfiguration(
                addressByte = 43, // TODO wrong address type, actual is 42, but it will conflict with Westend
                genesisHash = "0x1ab7fbd1d7c3532386268ec23fe4ff69f5bb6b3e3697947df3a2ec2786424de3",
            )
        ),
        POLKATRAIN(
            "Polkatrain",
            RuntimeConfiguration(
                addressByte = 0,
                genesisHash = "92cf9522510aee390f71fc1a635840918cc78e813c4a5ff76d40b478eca94a81",
            )
        ),
        STATEMINE(
            "Statemine",
            RuntimeConfiguration(
                addressByte = 2,
                genesisHash = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a",
            )
        ),
        ACALA(
            "Acala",
            RuntimeConfiguration(
                addressByte = 10,
                genesisHash = "6e966844cfd406ac4d2e9ff66ae485d60677d8b514fe8cfefb54e19a7c12e6c3",
            )
        ),
        KARURA(
            "Karura",
            RuntimeConfiguration(
                addressByte = 8,
                genesisHash = "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b",
            )
        ),
        MOONRIVER(
            "Moonriver",
            RuntimeConfiguration(
                addressByte = 1285.toByte(),
                genesisHash = "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b",
            )
        ),
        SHIDEN(
            "Shiden",
            RuntimeConfiguration(
                addressByte = 5,
                genesisHash = "f1cf9022c7ebb34b162d5b5e34e705a5a740b2d0ecc1009fb89023e62a488108",
            )
        ),
        BIFROST(
            "Bifrost",
            RuntimeConfiguration(
                addressByte = 6,
                genesisHash = "9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed",
            )
        ),
        KHALA(
            "Khala",
            RuntimeConfiguration(
                addressByte = 30,
                genesisHash = "d43540ba6d3eb4897c28a77d48cb5b729fea37603cbbfc7a86a73b72adb3be8d",
            )
        ),
        SPIRITNET(
            "Spiritnet",
            RuntimeConfiguration(
                addressByte = 38,
                genesisHash = "411f057b9107718c9624d6aa4a3f23c1653898297f3d4d529d9bb6511a39dd21",
            )
        ),
        CALAMARI(
            "Calamari",
            RuntimeConfiguration(
                addressByte = 78,
                genesisHash = "4ac80c99289841dd946ef92765bf659a307d39189b3ce374a92b5f0415ee17a1",
            )
        ),
        QUARTZ(
            "Quartz",
            RuntimeConfiguration(
                addressByte = 255.toByte(),
                genesisHash = "cd4d732201ebe5d6b014edda071c4203e16867305332301dc8d092044b28e554",
            )
        ),
        UNDEFINED(
            "Undefined",
            RuntimeConfiguration(
                addressByte = -1,
                genesisHash = "",
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

val Node.NetworkType.chainId
    get() = runtimeConfiguration.genesisHash
