package jp.co.soramitsu.fearless_utils.encrypt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static check over every module's production sources (src/main) for crypto entry points the app must not call.
 *
 * - web3j's Keys.createEcKeyPair(), WalletUtils, Bip44WalletUtils and Wallet.create reach SecureRandomUtils, whose
 *   class initializer installs LinuxSecureRandom as the process-wide SecureRandom provider on Android.
 * - Key material stays out of ton-kotlin and curve25519-kotlin: TON phrases, keys and signatures go through
 *   jp.co.soramitsu.fearless_utils.encrypt.ton (BouncyCastle). The app does not package curve25519-kotlin, so
 *   every ton-kotlin path into it is listed here: its three org.ton.crypto Ed25519 classes, the org.ton.api key
 *   types that wrap them, ADNL and the lite client, and the ton-kotlin wallet contracts.
 * - SpongyCastle and EdDSA-Java left the build in 4.3.2.
 */
class CryptoLibraryUsageGuardTest {

    private val forbidden = listOf(
        Regex("""\bcreateEcKeyPair\b""") to "web3j Keys.createEcKeyPair replaces the process RNG",
        Regex("""\bcreateSecp256k1KeyPair\b""") to "web3j Keys.createSecp256k1KeyPair replaces the process RNG",
        Regex("""\b(Bip44)?WalletUtils\b""") to "web3j WalletUtils initializes SecureRandomUtils",
        Regex("""\bSecureRandomUtils\b""") to "web3j SecureRandomUtils installs LinuxSecureRandom",
        Regex("""\bLinuxSecureRandom\b""") to "web3j LinuxSecureRandom replaces the process RNG",
        Regex("""\borg\.web3j\.crypto\.Wallet\b""") to "web3j Wallet.create reaches SecureRandomUtils",
        Regex("""\borg\.ton\.mnemonic\b""") to "TON phrases go through TonMnemonic",
        Regex("""\borg\.ton\.api\b""") to "TON keys go through TonEd25519; ton-kotlin's key types need curve25519-kotlin",
        Regex("""\borg\.ton\.(adnl|lite\.client)\b""") to "ADNL and the lite client need curve25519-kotlin",
        Regex("""\borg\.ton\.crypto\.(?:(?:Ed25519|EncryptorEd25519|DecryptorEd25519|SecureRandom)\b|\*)""") to
            "TON keys go through TonEd25519",
        Regex("""\borg\.ton\.contract\.wallet\.(?:(?:WalletContract|WalletContractKt|WalletV[0-9]R[0-9]Contract|transfer)\b|\*)""") to
            "ton-kotlin wallet contracts sign with ton-kotlin keys",
        Regex("""\bio\.github\.andreypfau\.curve25519\b""") to "curve25519-kotlin is not packaged in the app",
        Regex("""\borg\.spongycastle\b""") to "SpongyCastle was removed",
        Regex("""\bnet\.i2p\.crypto\b""") to "EdDSA-Java was removed"
    )

    @Test
    fun `production sources call none of the retired or process-wide crypto entry points`() {
        val root = repositoryRoot()
        val sources = root.listFiles().orEmpty()
            .filter { module -> File(module, "build.gradle").isFile || File(module, "build.gradle.kts").isFile }
            .flatMap { module -> File(module, "src").listFiles().orEmpty().filter { it.name == "main" || it.name.endsWith("Main") } }
            .flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        assertTrue("found only ${sources.size} production sources under $root", sources.size > 1000)

        val violations = sources.flatMap { file ->
            val text = file.readText()
            forbidden.mapNotNull { (pattern, reason) ->
                pattern.find(text)?.let { "${file.relativeTo(root)}: ${it.value} ($reason)" }
            }
        }
        assertEquals("", violations.joinToString("\n"))
    }

    @Test
    fun `the guard recognizes each forbidden call`() {
        val samples = listOf(
            "Keys.createEcKeyPair()", "WalletUtils.generateNewWalletFile(p, d)", "Bip44WalletUtils.generateBip44Wallet(p, d)",
            "import org.web3j.crypto.Wallet", "org.ton.mnemonic.Mnemonic.toSeed(words)", "import org.ton.api.pk.PrivateKeyEd25519",
            "import org.ton.api.pub.PublicKey", "import org.ton.api.adnl.AdnlNode", "import org.ton.lite.client.LiteClient",
            "import org.ton.adnl.connection.AdnlClient", "org.ton.crypto.Ed25519.sign(k, m)", "import org.ton.crypto.*",
            "import org.ton.contract.wallet.WalletV4R2Contract", "import org.ton.contract.wallet.WalletContract",
            "import org.ton.contract.wallet.transfer", "import org.ton.contract.wallet.*",
            "import io.github.andreypfau.curve25519.ed25519.Ed25519",
            "import org.spongycastle.util.encoders.Hex", "import net.i2p.crypto.eddsa.EdDSAEngine"
        )
        samples.forEach { sample -> assertTrue(sample, forbidden.any { (pattern, _) -> pattern.containsMatchIn(sample) }) }
        listOf(
            "import org.web3j.crypto.WalletFile", "Keys.toChecksumAddress(a)", "import org.ton.cell.Cell", "import org.ton.crypto.hex",
            "import org.ton.contract.wallet.WalletTransferBuilder", "import org.ton.contract.wallet.MessageData",
            "import org.ton.block.AddrStd"
        ).forEach { allowed ->
            assertTrue(allowed, forbidden.none { (pattern, _) -> pattern.containsMatchIn(allowed) })
        }
    }

    private fun repositoryRoot(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null && !File(directory, "settings.gradle").isFile) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Run from inside the repository" }
    }
}
