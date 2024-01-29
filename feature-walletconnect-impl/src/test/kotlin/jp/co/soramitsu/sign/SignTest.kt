package jp.co.soramitsu.sign

import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder

class SignTest {

    @Test
    fun `should sign eth_signTypedData_v4 message`() {
        val message =
            "{\"types\":{\"EIP712Domain\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"chainId\",\"type\":\"uint256\"},{\"name\":\"verifyingContract\",\"type\":\"address\"}],\"Person\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"wallet\",\"type\":\"address\"}],\"Mail\":[{\"name\":\"from\",\"type\":\"Person\"},{\"name\":\"to\",\"type\":\"Person\"},{\"name\":\"contents\",\"type\":\"string\"}]},\"primaryType\":\"Mail\",\"domain\":{\"name\":\"Ether Mail\",\"version\":\"1\",\"chainId\":1,\"verifyingContract\":\"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC\"},\"message\":{\"from\":{\"name\":\"Cow\",\"wallet\":\"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826\"},\"to\":{\"name\":\"Bob\",\"wallet\":\"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB\"},\"contents\":\"Hello, Bob!\"}}"
        val privateKey = "0x69451a2eec3f524742c50b6af126f1e96dcb9fe82ccde6dd085b43dacf8fde0f"

        val cred = Credentials.create(privateKey)

        val messageStructHash = StructuredDataEncoder(message).hashStructuredData()
        val signatureData = Sign.signMessage(messageStructHash, cred.ecKeyPair, false)
        val signatureWrapper = SignatureWrapper.Ecdsa(signatureData.v, signatureData.r, signatureData.s)

        val expectedSignature = "0x70b5e2325179b14a011737ccbce436b3022d343a1c9d2d72f6328e0d60da6e3b4b0ceda6d5d0bcdcdf8356f00ad45191f3fd53bfbba16cde7c9225fec255e6e81b"

        assertEquals(expectedSignature, signatureWrapper.signature.toHexString(true))
    }
}
