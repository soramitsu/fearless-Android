package jp.co.soramitsu.nft.impl.domain.models

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Type
import java.math.BigInteger

sealed interface EthCall {

    val nonce: BigInteger

    val sender: String

    val receiver: String

    interface SmartContractCall : EthCall {

        val contractAddress: String

        val encodedFunction: String

        val outputTypeRefs: List<TypeReference<Type<*>>>
    }
}
