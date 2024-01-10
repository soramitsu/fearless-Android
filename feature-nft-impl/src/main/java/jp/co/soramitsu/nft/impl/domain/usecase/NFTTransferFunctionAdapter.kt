package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Array
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Convert
import javax.inject.Inject

class NFTTransferFunctionAdapter @Inject constructor() {

    operator fun invoke(params: NFTTransferParams): String {
        val function = when(params) {
            is NFTTransferParams.ERC721 -> Function(
                "safeTransferFrom",
                listOf(
                    Address(params.sender),
                    Address(params.receiver),
                    Uint256(params.tokenId),
                    DynamicBytes(params.data)
                ),
                listOf(TypeReference.create(Array::class.java))
            )
            is NFTTransferParams.ERC1155 -> Function(
                "safeTransferFrom",
                listOf(
                    Address(params.sender),
                    Address(params.receiver),
                    Uint256(params.tokenId),
                    Uint256(Convert.toWei(params.amount, Convert.Unit.ETHER).toBigInteger()),
                    DynamicBytes(params.data)
                ),
                listOf(TypeReference.create(Array::class.java))
            )
        }

        return FunctionEncoder.encode(function)
    }

}