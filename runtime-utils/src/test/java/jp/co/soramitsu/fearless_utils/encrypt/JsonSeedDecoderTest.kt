package jp.co.soramitsu.fearless_utils.encrypt

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.common.TestAddressBytes
import jp.co.soramitsu.fearless_utils.common.TestGeneses
import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.InvalidJsonException
import jp.co.soramitsu.fearless_utils.encrypt.model.NetworkTypeIdentifier
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

private const val VALID_JSON_SR25519 =
    "{\"address\":\"F2dMuaCik4Ackmo9hoMMV79ETtVNvKSZMVK5sue9q1syPrW\",\"encoded\":\"DjQJTO2m1HlbCuaF0A9B9XJPHQlz1+0dOVURUSSS3VsAgAAAAQAAAAgAAAC9nLArVYH4ip7+fN03vcLOy727cNE6PWMCVXtpPKoAktb4YTIaf/Oe8oPZOUa1KnMCPtTRJPUsZbCMp41rdaT82b6wvOI/CL3kmmPlVIX8GmW6GZHaPVvvu2fGiC7EchpV5V1SR11GhTbqE94JhfSKSJALkd1Nxy4Ilfizyx7UZAw/pzavw7PR7td0MXEjctUhiWg1TDApXXGiUX3+\",\"encoding\":{\"content\":[\"pkcs8\",\"sr25519\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"genesisHash\":\"0xb0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe\",\"name\":\"test\",\"tags\":[],\"whenCreated\":1600410143263}}"
private const val VALID_JSON_EDWARDS =
    "{\"address\":\"GnSs2bd76KkrcvN7prrFfkbu939mkZHyBzVKnjS8ikg3CZ8\",\"encoded\":\"fPeWwZNEDSNKM+k+Vjv7HFGr/r7LSev6DHmX+5TwvqYAgAAAAQAAAAgAAAAzuSqaguZBu/q23iphXVSsn3GP70l4Pn/bTw6TdYltaMZDJRADWz+T7FUfZ460FkI70yczt9O1HSAEEj5QLMJEvxa4+wNQWA+hXeQoVxKf+1iUIr+RW9wL9JGpZw61HWhpr0t4b+TEBQwbf7hDT/P0dFmYLDa3RfxVg07GTrzYe2uBQt9MCYLUcctBNk7W7r8CI3FuqbAA/oP07GB/\",\"encoding\":{\"content\":[\"pkcs8\",\"ed25519\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"genesisHash\":\"0xb0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe\",\"name\":\"test\",\"tags\":[],\"whenCreated\":1600432656361,\"whenEdited\":1600433019129}}"
private const val VALID_JSON_ECDSA =
    "{\"address\":\"0x02f3d42516c317757748b073f5221455e31035286ea7417b827d8eb8ad1a6c49d6\",\"encoded\":\"VsYSlEzIMvgk2lpGigHs8fR6kqyTZAgnz+QkoAwxy/0AgAAAAQAAAAgAAADtvqAkZhcSD94AaYzzDFLgMDgz0U3+ZD6p0eaWqlWXgPJZrTLe6Go6bXgiT0nklIHMipQ4CxDsnJwIO98NY7RJwWDqnH9+72huUq7VODaN7LUyChBLT58AwN0xXvx1cEksMlBZoCZ9W0qU0o98kfhPuLA+goplCb/XLp5PEdE=\",\"encoding\":{\"content\":[\"pkcs8\",\"ecdsa\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"genesisHash\":\"0xb0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe\",\"name\":\"test\",\"tags\":[],\"whenCreated\":1600433095690}}"
private const val VALID_JSON_ETHEREUM =
    "{\"encoded\":\"uqlovHpPdx2cKc/opcB5a78YJCOiTz32ovTFsqEXvugAgAAAAQAAAAgAAAAeRik/93BCVP63EtTkiiww+UWYPEIGOdTkzhhd5kxrPC4GeTry9wE7mxooZesRwfNUOi92YGzETPiHk9PTXKV+XkpGXrBF7480dH0qbXSbIYSi0UQVLuX+4fBmLY8Y4YZCItNvHo454SiSimlBhMFX64psdIVGumzu4P3I7UU=\",\"encoding\":{\"content\":[\"pkcs8\",\"ethereum\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"address\":\"0x036966ab114f00c72a1eb218e890ce940eed68a1288fe52a0665f5413aa9d19290\",\"meta\":{\"genesisHash\":\"0x91bc6e169807aaa54802737e1c504b2577d4fafedd5a02c10293b1cd60e39527\",\"isHardware\":false,\"name\":\"1\",\"tags\":[],\"whenCreated\":1634656032513}}"

private const val JSON_NO_GENESIS =
    "{\"address\":\"GnSs2bd76KkrcvN7prrFfkbu939mkZHyBzVKnjS8ikg3CZ8\",\"encoded\":\"fPeWwZNEDSNKM+k+Vjv7HFGr/r7LSev6DHmX+5TwvqYAgAAAAQAAAAgAAAAzuSqaguZBu/q23iphXVSsn3GP70l4Pn/bTw6TdYltaMZDJRADWz+T7FUfZ460FkI70yczt9O1HSAEEj5QLMJEvxa4+wNQWA+hXeQoVxKf+1iUIr+RW9wL9JGpZw61HWhpr0t4b+TEBQwbf7hDT/P0dFmYLDa3RfxVg07GTrzYe2uBQt9MCYLUcctBNk7W7r8CI3FuqbAA/oP07GB/\",\"encoding\":{\"content\":[\"pkcs8\",\"ed25519\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"name\":\"test\",\"tags\":[],\"whenCreated\":1600432656361,\"whenEdited\":1600433019129}}"
private const val JSON_NO_NETWORK_INFO =
    "{\"address\":\"0x02f3d42516c317757748b073f5221455e31035286ea7417b827d8eb8ad1a6c49d6\",\"encoded\":\"VsYSlEzIMvgk2lpGigHs8fR6kqyTZAgnz+QkoAwxy/0AgAAAAQAAAAgAAADtvqAkZhcSD94AaYzzDFLgMDgz0U3+ZD6p0eaWqlWXgPJZrTLe6Go6bXgiT0nklIHMipQ4CxDsnJwIO98NY7RJwWDqnH9+72huUq7VODaN7LUyChBLT58AwN0xXvx1cEksMlBZoCZ9W0qU0o98kfhPuLA+goplCb/XLp5PEdE=\",\"encoding\":{\"content\":[\"pkcs8\",\"ecdsa\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"name\":\"test\",\"tags\":[],\"whenCreated\":1600433095690}}"

// TODO not possible to use sr25519 library in unit tests for now
val JSONS = listOf(VALID_JSON_EDWARDS, VALID_JSON_ECDSA)

private const val VALID_PASSWORD = "12345"
private const val INVALID_PASSWORD = "123456"

private const val ETHEREUM_PASSWORD = "1"

private const val VALID_NAME = "test"

private val VALID_ADDRESS_BYTE = TestAddressBytes.KUSAMA
private val VALID_GENESIS = TestGeneses.KUSAMA

private const val INVALID_JSON = "{\"some_field\": 123}"
private const val NOT_JSON = "not json"

@RunWith(MockitoJUnitRunner::class)
class JsonSeedDecoderTest {
    private val gson = Gson()

    private val decoder = JsonSeedDecoder(gson)

    @Test
    fun `should decode valid json with correct password`() {
        JSONS.forEachIndexed { index, json ->
            val result = decoder.decode(json, VALID_PASSWORD)
            val networkTypeIdentifier = result.networkTypeIdentifier

            assertEquals(VALID_NAME, result.username)
            assertInstance<NetworkTypeIdentifier.Genesis>(networkTypeIdentifier)
            assertEquals(VALID_GENESIS, networkTypeIdentifier.genesis)
        }
    }

    @Test
    fun `should decode ethereum`() {
        val result = decoder.decode(VALID_JSON_ETHEREUM, ETHEREUM_PASSWORD)

        assertEquals("036966ab114f00c72a1eb218e890ce940eed68a1288fe52a0665f5413aa9d19290", result.keypair.publicKey.toHexString())
    }

    @Test
    fun `should handle valid json with incorrect password`() {
        JSONS.forEach {
            assertThrows<IncorrectPasswordException> {
                decoder.decode(it, INVALID_PASSWORD)
            }
        }
    }

    @Test(expected = InvalidJsonException::class)
    fun `should handle invalid json with valid password`() {
        decoder.decode(INVALID_JSON, VALID_PASSWORD)
    }

    @Test(expected = InvalidJsonException::class)
    fun `should handle not json with valid password`() {
        decoder.decode(NOT_JSON, VALID_PASSWORD)
    }

    @Test(expected = InvalidJsonException::class)
    fun `should handle invalid json with incorrect password`() {
        decoder.decode(INVALID_JSON, INVALID_PASSWORD)
    }

    @Test
    fun `should extract meta from ethereum json`() {
        val data = decoder.extractImportMetaData(VALID_JSON_ETHEREUM)

        assertEquals(data.encryption.encryptionType, EncryptionType.ECDSA)
    }

    @Test
    fun `should extract meta from valid json`() {
        val data = decoder.extractImportMetaData(VALID_JSON_SR25519)
        val networkTypeIdentifier = data.networkTypeIdentifier

        assertEquals(EncryptionType.SR25519, data.encryption.encryptionType)
        assertInstance<NetworkTypeIdentifier.Genesis>(networkTypeIdentifier)
        assertEquals(VALID_GENESIS, networkTypeIdentifier.genesis)
        assertEquals(VALID_NAME, data.name)
    }

    @Test
    fun `should handle meta from invalid json`() {
        assertThrows<InvalidJsonException> {
            decoder.extractImportMetaData(INVALID_JSON)
        }
    }

    @Test
    fun `should handle json with no genesis`() {
        val result = decoder.decode(JSON_NO_GENESIS, VALID_PASSWORD)
        val networkTypeIdentifier = result.networkTypeIdentifier

        assertInstance<NetworkTypeIdentifier.AddressByte>(networkTypeIdentifier)
        assertEquals(VALID_ADDRESS_BYTE, networkTypeIdentifier.addressByte)
    }

    @Test
    fun `should handle json with no network info`() {
        val result = decoder.decode(JSON_NO_NETWORK_INFO, VALID_PASSWORD)

        assertInstance<NetworkTypeIdentifier.Undefined>(result.networkTypeIdentifier)
    }

    @Test
    fun `should extract seed from non-sr25519 crypto`() {
        JSONS.forEach {
            val result = decoder.decode(it, VALID_PASSWORD)

            assertNotNull(result.seed)
        }
    }

    @Test
    @Ignore("sr25519 is not supported in unit tests")
    fun `should not extract seed from sr25519 crypto`() {
        val result = decoder.decode(VALID_JSON_SR25519, VALID_PASSWORD)

        assertNull(result.seed)
    }

    @Test
    fun `should decode ecdsa json`() {
        val json =
            "{\"address\":\"0x020cddfb851af41912813cc47cb5f57b170beb8dfce1fe605ab4555143d2771cfc\",\"encoded\":\"YJYD6Gl+UXnrXuEFHka671J3b0LDQFcNraUjzC9fBdwAgAAAAQAAAAgAAAAiGaLl7nU8rDPrYuY8yYnOGr8SHqm9W6jDUmneYEIc5wRIJfsPPmTSnEZ5Z5DPJznZr4mVjdPrcwl17VeXsIg7MARhfRyC9vLZaZPyt4W1vSSsLijUDEyhV1KUzL3hQsevwshTGjDOIRzGWFerRqwl/0JrxtcXzgqulEqt3ZQ=\",\"encoding\":{\"content\":[\"pkcs8\",\"ecdsa\"],\"type\":[\"scrypt\",\"xsalsa20-poly1305\"],\"version\":\"3\"},\"meta\":{\"genesisHash\":\"0xb0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe\",\"name\":\"njnjd\",\"tags\":[],\"whenCreated\":1600428913336}}"

        val result = decoder.decode(json, "1")
        val expectedPublicKey = "020cddfb851af41912813cc47cb5f57b170beb8dfce1fe605ab4555143d2771cfc"
        val expectedPublicKeyBytes = Hex.decode(expectedPublicKey)

        assertArrayEquals(result.keypair.publicKey, expectedPublicKeyBytes)
    }
}