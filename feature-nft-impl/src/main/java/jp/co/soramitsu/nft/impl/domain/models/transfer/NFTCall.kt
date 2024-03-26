package jp.co.soramitsu.nft.impl.domain.models.transfer

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Type
import java.math.BigInteger

sealed interface NFTCall : EthCall.SmartContractCall {

    class OwnerOf(
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ) : NFTCall

    class AccountBalance(
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ) : NFTCall

    class Transfer(
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ) : NFTCall
}
