package jp.co.soramitsu.nft.impl.domain.models

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Type
import java.math.BigDecimal
import java.math.BigInteger

sealed interface NFTCall: EthCall.SmartContractCall {

    class Transfer(
        override val chainId: Long,
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val amount: BigDecimal,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ): NFTCall

    class AccountBalance(
        override val chainId: Long,
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val amount: BigDecimal,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ): NFTCall

    class TokenMint(
        override val chainId: Long,
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val amount: BigDecimal,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ): NFTCall

    class SetApproveForAll(
        override val chainId: Long,
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val amount: BigDecimal,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ): NFTCall

    class IsApprovedForAll(
        override val chainId: Long,
        override val nonce: BigInteger,
        override val sender: String,
        override val receiver: String,
        override val amount: BigDecimal,
        override val contractAddress: String,
        override val encodedFunction: String,
        override val outputTypeRefs: List<TypeReference<Type<*>>>
    ): NFTCall

}