package jp.co.soramitsu.common.data.network.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class MutationAuthorizationTest {
    private val vectors = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/mutation-authorization-v1-vectors.json"))
        .bufferedReader().use { it.readText() }).asJsonObject
    private val keyId = vectors.get("keyId").asString
    private val context = MutationAuthorizationContext(
        "jp.co.soramitsu.fearless", 230, "1".repeat(64), "2".repeat(64),
        mapOf(keyId to hex(vectors.get("publicKeyHex").asString)), MutationCapability.entries.toSet()
    )
    private val now = 1_800_000_010L
    private val verifier = MutationAuthorizationVerifier()

    @Test
    fun `shared Android iOS signed vectors have identical decisions`() {
        vectors.getAsJsonArray("vectors").forEach { element ->
            val row = element.asJsonObject
            assertEquals(row.get("name").asString, row.get("expectedValid").asBoolean,
                verifier.verify(row.get("token").asString, context, now) != null)
        }
    }

    @Test
    fun `version bounds are inclusive and trust cannot be supplied by the token`() {
        val token = token("valid_xcm_enabled")
        assertNotNull(verifier.verify(token, context, now))
        assertNull(verifier.verify(token, context.copy(appVersion = 229), now))
        assertNull(verifier.verify(token, context.copy(appVersion = 231), now))
        assertNull(verifier.verify(token, context.copy(trustedKeys = emptyMap()), now))
        assertNull(verifier.verify(token, null, now))
        assertNull(verifier.verify(token, context.copy(policySha256 = "a".repeat(64)), now))
        assertTrue(verifier.verify(token, context.copy(compiledApprovals = emptySet()), now)!!.capabilities.isEmpty())
    }

    @Test
    fun `valid signatures cannot make noncanonical or ambiguous payloads acceptable`() {
        val original = payload("valid_xcm_enabled").toString()
        val invalid = listOf(
            original.replace("\"schema\":\"1\"", "\"schema\":\"1\",\"schema\":\"1\""),
            original.replace("\"schema\":\"1\"", "\"schema\":1"),
            original.replace("\"revision\":\"42\"", "\"revision\":\"042\""),
            original.replace("\"revision\":\"42\"", "\"revision\":\"9223372036854775808\""),
            original.replace("\"environment\":\"production\"", "\"environment\":\"staging\""),
            original.replace("\"xcm\":true", "\"xcm\":\"true\""),
            original.replace("\"xcm\":true", "\"xcm\":true,\"unknown\":true"),
            original.replace("{\"schema\"", "{ \"schema\""),
            original.replace("jp.co", "jp\\u002eco")
        )
        invalid.forEach { assertNull(verifier.verify(sign(it), context, now)) }
        val future = payload("valid_xcm_enabled").apply {
            addProperty("issuedAt", (now + 61).toString()); addProperty("expiresAt", (now + 900).toString())
        }
        assertNull(verifier.verify(sign(future.toString()), context, now))
        assertNull(verifier.verify(token("valid_xcm_enabled") + "=", context, now))
        assertNull(verifier.verify(" "+token("valid_xcm_enabled"), context, now))
        assertNull(verifier.verify("a".repeat(8193), context, now))
    }

    @Test
    fun `startup never revives cached grants and durable revision blocks replay`() {
        val disk = Disk()
        val clock = Clock(now)
        val state = state(disk, clock)
        assertFalse(state.permits(MutationCapability.XCM))
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        assertTrue(state.permits(MutationCapability.XCM))
        assertFalse(disk.value!!.contains("FWMA1"))
        val restarted = state(disk, clock)
        assertFalse(restarted.permits(MutationCapability.XCM))
        assertTrue(restarted.acceptFresh(token("valid_xcm_enabled")))
        assertFalse(restarted.acceptFresh(token("valid_all_denied"))) // same revision, different bytes
        assertFalse(restarted.permits(MutationCapability.XCM))
        assertTrue(restarted.acceptFresh(token("valid_next_revision_revoked")))
        assertFalse(restarted.acceptFresh(token("valid_xcm_enabled"))) // lower revision
        assertFalse(restarted.permits(MutationCapability.XCM))
    }

    @Test
    fun `durable write failure and corrupted high water always deny`() {
        val disk = Disk().apply { failWrite = true }
        val state = state(disk, Clock(now))
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        assertFalse(state.permits(MutationCapability.XCM))
        assertNull(disk.value)
        assertFalse(state(Disk().apply { value = "{}" }, Clock(now)).acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `ambiguous durable commit poisons the process until restart`() {
        val disk = Disk()
        val clock = Clock(now)
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        disk.failWrite = true
        disk.commitBeforeFailure = true
        assertFalse(state.acceptFresh(token("valid_next_revision_revoked")))
        disk.failWrite = false
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        assertFalse(state.acceptFresh(token("valid_next_revision_revoked")))
        val restarted = state(disk, clock)
        assertFalse(restarted.acceptFresh(token("valid_xcm_enabled")))
        assertTrue(restarted.acceptFresh(token("valid_next_revision_revoked")))
    }

    @Test
    fun `repeated fetch cannot extend monotonic lifetime with a frozen wall clock`() {
        val clock = Clock(now)
        val state = state(Disk(), clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        clock.elapsed = 400_000
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        clock.elapsed = 890_000
        assertFalse(state.permits(MutationCapability.XCM))
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `expiry clock rollback and authorization changes invalidate existing leases`() {
        val clock = Clock(now)
        val state = state(Disk(), clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        val lease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        assertEquals("signed", lease.runIfAuthorized("a".repeat(64)) { "signed" })
        state.invalidate()
        assertThrows(IllegalStateException::class.java) { lease.check("a".repeat(64)) }
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        assertThrows(IllegalStateException::class.java) { lease.check("a".repeat(64)) }
        val newLease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        clock.wall -= 61
        assertThrows(IllegalStateException::class.java) { newLease.check("a".repeat(64)) }
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        clock.wall = now + 900
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        assertThrows(IllegalArgumentException::class.java) { state.acquire(MutationCapability.XCM, "bad") }
    }

    @Test
    fun `disabled capability and failed refresh do not reset another capability or lifetime`() {
        val clock = Clock(now)
        val state = state(Disk(), clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        assertFalse(state.permits(MutationCapability.POLKAMARKT))
        assertTrue(state.permits(MutationCapability.XCM))
        val lease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        var executed = false
        assertThrows(IllegalStateException::class.java) {
            lease.runIfAuthorized("b".repeat(64)) { executed = true }
        }
        assertFalse(executed)
        clock.elapsed = 400_000
        state.invalidate()
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        assertThrows(IllegalStateException::class.java) { lease.check("a".repeat(64)) }
        clock.elapsed = 890_000
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `observed wall progress is durable and persistence failure poisons active authority`() {
        val disk = Disk()
        val clock = Clock(now)
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        clock.wall += 100
        assertTrue(state.permits(MutationCapability.XCM))
        clock.wall -= 61
        assertFalse(state(disk, clock).acceptFresh(token("valid_xcm_enabled")))
        clock.wall = now + 101
        disk.failWrite = true
        assertFalse(state.permits(MutationCapability.XCM))
        disk.failWrite = false
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `expired fresh fetch records wall progress before denying and survives restart`() {
        val disk = Disk()
        val clock = Clock(now)
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        clock.wall = now + 900
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        clock.wall = now
        assertFalse(state(disk, clock).acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `denied lookup after failed refresh still records clock progress`() {
        val disk = Disk()
        val clock = Clock(now)
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        state.invalidate()
        clock.wall = now + 900
        assertFalse(state.permits(MutationCapability.POLKAMARKT))
        clock.wall = now
        assertFalse(state(disk, clock).acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `bundled composition binds actual package version policy and exact route assets`() {
        val assets = fixtureAssets()
        val loaded = loadMutationAuthorizationContext(context.audience, 230) { assets.getValue(it) }
        assertNotNull(loaded)
        assertEquals(setOf(MutationCapability.XCM), loaded!!.compiledApprovals)
        assertNull(loadMutationAuthorizationContext("other.wallet", 230) { assets.getValue(it) })
        assertNull(loadMutationAuthorizationContext(context.audience, 231) { assets.getValue(it) })
        assets["local_chains.json"] = "changed discovery and execution".toByteArray()
        assertNull(loadMutationAuthorizationContext(context.audience, 230) { assets.getValue(it) })
    }

    @Test
    fun `production bundle contains no synthetic authority and keeps approvals disabled`() {
        val trust = JsonParser.parseString(File("src/main/assets/mutation_authorization_trust.json").readText()).asJsonObject
        assertTrue(trust.getAsJsonObject("keys").keySet().isEmpty())
        assertTrue(trust.get("policySha256").isJsonNull)
        val policy = JsonParser.parseString(File("src/main/assets/mutation_authorization_policy.json").readText()).asJsonObject
        assertTrue(policy.getAsJsonObject("capabilities").entrySet().all { !it.value.asBoolean })
        assertNull(loadMutationAuthorizationContext(context.audience, 230) { File("src/main/assets/$it").readBytes() })
    }

    @Test
    fun `revocation waits for committed operation then permanently rejects that lease`() {
        val state = state(Disk(), Clock(now))
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        val lease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        val entered = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val revoking = java.util.concurrent.CountDownLatch(1)
        val revoked = java.util.concurrent.CountDownLatch(1)
        val sender = kotlin.concurrent.thread {
            lease.runIfAuthorized("a".repeat(64)) {
                entered.countDown()
                check(finish.await(3, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val revoker = kotlin.concurrent.thread { revoking.countDown(); state.invalidate(); revoked.countDown() }
        assertTrue(revoking.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(revoked.await(50, java.util.concurrent.TimeUnit.MILLISECONDS))
        finish.countDown()
        assertTrue(revoked.await(3, java.util.concurrent.TimeUnit.SECONDS))
        sender.join(3_000); revoker.join(3_000)
        assertThrows(IllegalStateException::class.java) { lease.check("a".repeat(64)) }
    }

    @Test
    fun `durable save latency cannot authorize an expired physical action`() {
        val clock = Clock(now)
        val disk = Disk()
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        val lease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        clock.wall++
        disk.onWrite = { clock.wall = now + 900; clock.elapsed = 900_000 }
        var ran = false
        assertThrows(IllegalStateException::class.java) { lease.runIfAuthorized("a".repeat(64)) { ran = true } }
        assertFalse(ran)
        disk.onWrite = null
        clock.wall = now
        assertFalse(state(disk, clock).acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `initial revision save that crosses elapsed expiry cannot issue a grant`() {
        val clock = Clock(now)
        val disk = Disk().apply { onWrite = { clock.elapsed = 900_000 } }
        val state = state(disk, clock)
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
        assertFalse(state.permits(MutationCapability.XCM))
    }

    @Test
    fun `expiry observed during save cannot revive through a small wall rollback`() {
        val clock = Clock(now)
        val disk = Disk()
        val state = state(disk, clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        clock.wall = now + 890 // exact signed expiry
        disk.onWrite = { clock.wall -= 1 }
        assertFalse(state.permits(MutationCapability.XCM))
        disk.onWrite = null
        assertFalse(state.acceptFresh(token("valid_xcm_enabled")))
    }

    @Test
    fun `waiting for authority lock resamples time before physical action`() {
        val clock = Clock(now)
        val state = state(Disk(), clock)
        assertTrue(state.acceptFresh(token("valid_xcm_enabled")))
        val lease = state.acquire(MutationCapability.XCM, "a".repeat(64))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val queued = java.util.concurrent.CountDownLatch(1)
        val denied = java.util.concurrent.CountDownLatch(1)
        val unsafe = java.util.concurrent.atomic.AtomicBoolean(false)
        val first = kotlin.concurrent.thread { lease.runIfAuthorized("a".repeat(64)) {
            entered.countDown(); check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
        } }
        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val second = kotlin.concurrent.thread {
            queued.countDown()
            try { lease.runIfAuthorized("a".repeat(64)) { unsafe.set(true) } }
            catch (expected: IllegalStateException) { denied.countDown() }
        }
        assertTrue(queued.await(3, java.util.concurrent.TimeUnit.SECONDS))
        clock.elapsed = 900_000
        release.countDown()
        assertTrue(denied.await(3, java.util.concurrent.TimeUnit.SECONDS))
        first.join(3_000); second.join(3_000)
        assertFalse(unsafe.get())
    }

    private fun fixtureAssets(): MutableMap<String, ByteArray> {
        val source = mutableMapOf("approved_xcm_routes.tsv" to "reviewed routes".toByteArray(), "local_chains.json" to "reviewed chains".toByteArray())
        val routes = JsonObject().apply {
            addProperty("schema", "1")
            add("files", JsonObject().apply { source.forEach { (name, bytes) -> addProperty(name, mutationSha256(bytes)) } })
        }.toString().toByteArray()
        val policy = JsonObject().apply {
            addProperty("schema", "1"); addProperty("audience", context.audience); addProperty("environment", "production")
            addProperty("appVersion", "230")
            add("capabilities", JsonObject().apply { MutationCapability.entries.forEach { addProperty(it.wireName, it == MutationCapability.XCM) } })
        }.toString().toByteArray()
        source["mutation_route_manifest.json"] = routes
        source["mutation_authorization_policy.json"] = policy
        source["mutation_authorization_trust.json"] = JsonObject().apply {
            addProperty("schema", "1"); addProperty("policySha256", mutationSha256(policy)); addProperty("routeManifestSha256", mutationSha256(routes))
            add("keys", JsonObject().apply { addProperty(keyId, vectors.get("publicKeyHex").asString) })
        }.toString().toByteArray()
        return source
    }

    private fun token(name: String) = vector(name).get("token").asString
    private fun payload(name: String) = vector(name).getAsJsonObject("payload").deepCopy()
    private fun vector(name: String) = vectors.getAsJsonArray("vectors").first { it.asJsonObject.get("name").asString == name }.asJsonObject
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sign(payload: String): String {
        // Public RFC8032 TEST1 seed, test-only. It is never installed in production trust.
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val message = "FearlessWallet-MutationAuthorization-v1\n$keyId\n".toByteArray() + bytes
        signer.update(message, 0, message.size)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "FWMA1.$keyId.${encoder.encodeToString(bytes)}.${encoder.encodeToString(signer.generateSignature())}"
    }
    private fun state(disk: Disk, clock: Clock) = MutationAuthorizationState(context, disk, clock)
    private class Disk : MutationAuthorizationPersistence {
        var onWrite: (() -> Unit)? = null
        var value: String? = null
        var failWrite = false
        var commitBeforeFailure = false
        override fun read() = value
        override fun write(value: String): Boolean {
            onWrite?.invoke()
            if (failWrite) {
                if (commitBeforeFailure) this.value = value
                return false
            }
            this.value = value
            return true
        }
    }
    private class Clock(var wall: Long) : MutationAuthorizationClock {
        var elapsed = 0L
        override fun wallSeconds() = wall
        override fun elapsedMillis() = elapsed
    }
}
