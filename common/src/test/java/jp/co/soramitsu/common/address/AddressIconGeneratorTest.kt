package jp.co.soramitsu.common.address

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddressIconGeneratorTest {
    private val image = PictureDrawable(Picture())
    private val accountId = ByteArray(32) { it.toByte() }
    private val address = accountId.toAddress(42)
    private val delegate = mockk<AddressIconGenerator>().also {
        coEvery { it.createAddressIcon(any(), any(), any()) } returns image
        coEvery { it.createEthereumAddressIcon(any(), any(), any()) } returns image
        coEvery { it.createWalletIcon(any(), any()) } returns image
    }

    @Test
    fun shouldDecodeSubstrateAddressesAndPreserveNames(): Unit = runBlocking {
        val model = delegate.createAddressModel(address, 24, "Alice")
        assertEquals(address, model.address)
        assertEquals("Alice", model.nameOrAddress)
        assertSame(image, model.image)
        coVerify { delegate.createAddressIcon(match { it.contentEquals(accountId) }, 24, R.color.account_icon_light) }
        val unnamed = delegate.createAddressModel(false, address, 18)
        assertEquals(address, unnamed.nameOrAddress)
        assertSame(image, unnamed.image)
        assertSame(image, delegate.createAddressIcon(false, address, 32, R.color.white))
        coVerify { delegate.createAddressIcon(match { it.contentEquals(accountId) }, 32, R.color.white) }
    }

    @Test
    fun shouldFallBackToAStableBlankIdentityForInvalidSubstrateAddresses(): Unit = runBlocking {
        assertSame(image, delegate.createAddressIcon("invalid", 24))
        assertSame(image, delegate.createAddressIcon(false, "invalid", 18))
        coVerify { delegate.createAddressIcon(match { it.contentEquals(ByteArray(32)) }, 24, R.color.account_icon_light) }
        coVerify { delegate.createAddressIcon(match { it.contentEquals(ByteArray(32)) }, 18, R.color.account_icon_light) }
    }

    @Test
    fun shouldUseEthereumTextIdentityAndNormalizeOnlyTheIconPrefix(): Unit = runBlocking {
        val model = delegate.createEthereumAddressModel("aabb", 24, "EVM")
        assertEquals("aabb", model.address)
        assertEquals("EVM", model.nameOrAddress)
        assertSame(image, model.image)
        coVerify { delegate.createEthereumAddressIcon(match { it.contentEquals("0xaabb".toByteArray()) }, 24, R.color.account_icon_light) }
        assertSame(image, delegate.createAddressModel(true, "0x1234", 18).image)
        assertSame(image, delegate.createAddressIcon(true, "0x1234", 32))
        coVerify { delegate.createEthereumAddressIcon(match { it.contentEquals("0x1234".toByteArray()) }, 18, R.color.account_icon_light) }
        coVerify { delegate.createEthereumAddressIcon(match { it.contentEquals("0x1234".toByteArray()) }, 32, R.color.account_icon_light) }
    }

    @Test
    fun shouldChooseEcosystemIconsForSingleWalletsAndSortMultipleIdentities(): Unit = runBlocking {
        WalletEcosystem.entries.forEach { ecosystem ->
            val model = delegate.createAddressModel(mapOf(ecosystem to "wallet"), 24, "Name")
            assertEquals("wallet", model.address)
            assertSame(image, model.image)
            coVerify { delegate.createWalletIcon(ecosystem, 24) }
        }
        val map = WalletEcosystem.entries.reversed().associateWith { if (it == WalletEcosystem.Substrate) address else "other" }
        val selected = delegate.createAddressModel(map, 32)
        assertEquals(address, selected.address)
        assertSame(image, selected.image)
        val display = delegate.createAddressModel(map, "display address", 24, "Wallet")
        assertEquals("display address", display.address)
        assertEquals("Wallet", display.nameOrAddress)
        assertSame(image, delegate.createAddressIcon(mapOf(WalletEcosystem.Ton to "ton"), 18))
        coVerify { delegate.createWalletIcon(WalletEcosystem.Ton, 18) }
    }

    @Test
    fun shouldCacheByIdentitySizeAndBackground(): Unit = runBlocking {
        val cache = CachingAddressIconGenerator(delegate)
        assertSame(image, cache.createAddressIcon(accountId, 24, 1))
        assertSame(image, cache.createAddressIcon(accountId.copyOf(), 24, 1))
        assertSame(image, cache.createAddressIcon(accountId, 32, 1))
        assertSame(image, cache.createAddressIcon(accountId, 24, 2))
        coVerify(exactly = 1) { delegate.createAddressIcon(match { it.contentEquals(accountId) }, 24, 1) }
        assertEquals(3, cache.cache.size)
        assertSame(image, cache.createEthereumAddressIcon("0x1234".toByteArray(), 24, 1))
        assertSame(image, cache.createEthereumAddressIcon("0x1234".toByteArray(), 24, 1))
        coVerify(exactly = 1) { delegate.createEthereumAddressIcon(any(), 24, 1) }
        assertSame(image, cache.createWalletIcon(WalletEcosystem.Ton, 24))
        assertSame(image, cache.createWalletIcon(WalletEcosystem.Ton, 24))
        coVerify(exactly = 1) { delegate.createWalletIcon(WalletEcosystem.Ton, 24) }
        assertEquals(5, cache.cache.size)
    }

    @Test
    fun shouldConvertDpColorsAndUseStableEcosystemSeeds(): Unit = runBlocking {
        val resources = mockk<ResourceManager>()
        every { resources.measureInPx(24) } returns 48
        every { resources.getColor(R.color.white) } returns -1
        val engine = mockk<IconGenerator>()
        every { engine.getSvgImage(any(), any(), any(), any()) } returns image
        every { engine.generateEthereumAddressIcon(any(), any(), any()) } returns image
        val generator = StatelessAddressIconGenerator(engine, resources)
        assertSame(image, generator.createAddressIcon(accountId, 24, R.color.white))
        assertSame(image, generator.createEthereumAddressIcon(accountId, 24, R.color.white))
        verify { engine.getSvgImage(accountId, 48, false, -1) }
        verify { engine.generateEthereumAddressIcon(accountId, 48, -1) }
        WalletEcosystem.entries.forEach { assertSame(image, generator.createWalletIcon(it, 24)) }
        verify { engine.getSvgImage("fearless-wallet-ton".encodeToByteArray(), 48, true, any()) }
        verify { engine.generateEthereumAddressIcon("fearless-wallet-evm".encodeToByteArray(), 48, any()) }
        listOf("substrate", "bitcoin", "solana", "iroha").forEach { name ->
            verify { engine.getSvgImage("fearless-wallet-$name".encodeToByteArray(), 48, false, any()) }
        }
    }
}
