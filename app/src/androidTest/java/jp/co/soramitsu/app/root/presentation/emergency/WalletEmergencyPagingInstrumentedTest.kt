package jp.co.soramitsu.app.root.presentation.emergency

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryAccessResult
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryField
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyKind
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryState
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoverySession
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Activity methods with real PIN, AndroidKeyStore-backed encryption and
 * real preference storage on an exclusively owned fresh synthetic profile.
 * The injected classes are constructed over a unique retained namespace; the
 * delegate only counts reads or supplies an explicitly named fault/scheduling seam.
 * This does not qualify Hilt binding, migrations, a funded wallet or signing.
 */
@RunWith(AndroidJUnit4::class)
class WalletEmergencyPagingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun authenticatedPagesRevealOnlyTheChosenCurrentKeyAndCopyIsSensitive() {
        val fixture = Fixture("pages", 66)
        fixture.launch().use { scenario ->
            scenario.onActivity { activity ->
                fixture.assertLocked(activity)
                fixture.enterPin(activity, WRONG_PIN)
            }
            fixture.await(scenario) { it.findViewById<View>(R.id.emergencyPinError).isShown }
            assertTrue(fixture.observer.secretReads().isEmpty())
            fixture.assertStoredSecretsExact(pinFailures = 1)
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys.take(64))
            assertTrue(fixture.observer.secretReads().isEmpty())
            scenario.onActivity { fixture.click(it, CommonR.string.wallet_emergency_next_page) }
            fixture.awaitPage(scenario, fixture.keys.drop(64))
            assertTrue(fixture.observer.secretReads().isEmpty())
            val chosen = fixture.keys.last()
            val replacement = "public current exact recovery bytes: second revision \n  Unicode 日本語"
            fixture.replace(chosen, replacement)
            scenario.onActivity { fixture.choose(it, chosen) }
            fixture.await(scenario) { fixture.texts(it).contains(replacement) }
            assertEquals(listOf(chosen), fixture.observer.secretReads())
            scenario.onActivity { activity ->
                assertTrue(fixture.texts(activity).contains(fixture.identifier(activity, chosen)))
                val secret = fixture.entries(activity).filterIsInstance<TextView>().single { it.text.toString() == replacement }
                assertFalse("Framework selection must not bypass the sensitive Copy action", secret.isTextSelectable)
                fixture.click(activity, CommonR.string.common_copy)
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                val clip = checkNotNull(clipboard.primaryClip)
                assertEquals(1, clip.itemCount)
                assertEquals(replacement, clip.getItemAt(0).text.toString())
                assertTrue(checkNotNull(clip.description.extras).getBoolean("android.content.extra.IS_SENSITIVE"))
                fixture.assertSecure(activity)
                fixture.click(activity, CommonR.string.wallet_emergency_back_to_list)
            }
            fixture.awaitPage(scenario, fixture.keys.drop(64))
            assertEquals(listOf(chosen), fixture.observer.secretReads())
            scenario.onActivity { fixture.click(it, CommonR.string.wallet_emergency_first_page) }
            fixture.awaitPage(scenario, fixture.keys.take(64))
            fixture.assertStoredSecretsExact()
            fixture.proof("complete")
        }
    }

    @Test
    fun staleCursorRequiresRefreshAndDeletedSelectionNeverSubstitutesAnotherKey() {
        val fixture = Fixture("cursor", 66)
        fixture.launch().use { scenario ->
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys.take(64))
            fixture.remove(fixture.keys[63])
            scenario.onActivity { fixture.click(it, CommonR.string.wallet_emergency_next_page) }
            fixture.await(scenario) { it.findViewById<TextView>(R.id.emergencyMessage).text == it.getString(CommonR.string.wallet_emergency_inventory_changed) }
            assertTrue(fixture.observer.secretReads().isEmpty())
            scenario.onActivity { activity ->
                assertEquals(listOf(activity.getString(CommonR.string.wallet_emergency_first_page)), fixture.entries(activity).filterIsInstance<Button>().map { it.text.toString() })
                fixture.click(activity, CommonR.string.wallet_emergency_first_page)
            }
            fixture.awaitPage(scenario, fixture.keys.filterNot { it == fixture.keys[63] }.take(64))
            val removed = fixture.keys.first()
            fixture.remove(removed)
            scenario.onActivity { fixture.choose(it, removed) }
            fixture.await(scenario) { fixture.texts(it).contains(it.getString(CommonR.string.wallet_emergency_unreadable)) }
            scenario.onActivity { activity ->
                assertTrue(fixture.texts(activity).contains(fixture.identifier(activity, removed)))
                assertTrue(fixture.entries(activity).filterIsInstance<Button>().none { it.text == activity.getString(CommonR.string.common_copy) })
            }
            assertEquals(listOf(removed), fixture.observer.secretReads())
            fixture.assertStoredSecretsExact()
            fixture.proof("complete")
        }
    }

    @Test
    fun stopAndRecreationRelockAndNeverRestoreTypedPinOrRevealedBytes() {
        val fixture = Fixture("lifecycle", 2)
        fixture.launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.emergencyPin).setText(PUBLIC_PIN)
                assertFalse(activity.findViewById<EditText>(R.id.emergencyPin).isSaveEnabled)
            }
            scenario.recreate()
            scenario.onActivity { fixture.install(it); fixture.assertLocked(it) }
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys)
            scenario.onActivity { fixture.choose(it, fixture.keys.first()) }
            fixture.await(scenario) { fixture.texts(it).contains(fixture.values.getValue(fixture.keys.first())) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { fixture.assertLocked(it) }
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys)
            scenario.onActivity { fixture.choose(it, fixture.keys.first()) }
            fixture.await(scenario) { fixture.texts(it).contains(fixture.values.getValue(fixture.keys.first())) }
            scenario.recreate()
            scenario.onActivity { fixture.install(it); fixture.assertLocked(it) }
            assertEquals(listOf(fixture.keys.first(), fixture.keys.first()), fixture.observer.secretReads())
            fixture.assertStoredSecretsExact()
            fixture.proof("complete")
        }
    }

    @Test
    fun missingPinDistinguishesEmptyStorageFromStoredKeysWithoutDecryptingThem() {
        for (count in listOf(0, 2)) {
            val fixture = Fixture("missing-pin-$count", count, savePin = false)
            fixture.launch().use { scenario ->
                scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
                val expected = if (count == 0) CommonR.string.wallet_emergency_empty else CommonR.string.wallet_emergency_unavailable
                fixture.await(scenario) { it.findViewById<TextView>(R.id.emergencyMessage).text == it.getString(expected) }
                scenario.onActivity { assertTrue(fixture.entries(it).isEmpty()) }
                assertTrue(fixture.observer.secretReads().isEmpty())
                fixture.assertStoredSecretsExact()
                fixture.proof("complete")
            }
        }
    }

    @Test
    fun inventoryFailureReturnsToPinAndDoesNotRenderAnEmptySuccess() {
        val fixture = Fixture("inventory-error", 1)
        fixture.observer.failInventory = true
        fixture.launch().use { scenario ->
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.await(scenario) { it.findViewById<TextView>(R.id.emergencyPinError).text == it.getString(CommonR.string.wallet_emergency_read_failed) }
            scenario.onActivity { fixture.assertLocked(it) }
            assertTrue(fixture.observer.secretReads().isEmpty())
            fixture.observer.failInventory = false
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys)
            fixture.assertStoredSecretsExact()
            fixture.proof("complete")
        }
    }

    @Test
    fun stoppingDuringAnExplicitReadPreventsLateSecretRendering() {
        val fixture = Fixture("late-read", 1)
        fixture.launch().use { scenario ->
            scenario.onActivity { fixture.enterPin(it, PUBLIC_PIN) }
            fixture.awaitPage(scenario, fixture.keys)
            fixture.observer.blockSelectedRead = true
            scenario.onActivity { fixture.choose(it, fixture.keys.single()) }
            val pending = AtomicReference<Job>()
            try {
                assertTrue(fixture.observer.readStarted.await(10, TimeUnit.SECONDS))
                scenario.onActivity { activity ->
                    val field = WalletEmergencyExportActivity::class.java.getDeclaredField("revealJob")
                    field.isAccessible = true
                    pending.set(field.get(activity) as Job)
                }
                scenario.moveToState(Lifecycle.State.CREATED)
            } finally {
                fixture.observer.readMayFinish.countDown()
            }
            assertTrue(fixture.observer.readFinished.await(10, TimeUnit.SECONDS))
            runBlocking { withTimeout(10_000) { pending.get().join() } }
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { fixture.assertLocked(it) }
            assertEquals(listOf(fixture.keys.single()), fixture.observer.secretReads())
            fixture.assertStoredSecretsExact()
            fixture.proof("complete")
        }
    }

    @Test
    fun realEncryptedPublicVectorsRetainAll991FieldsAndCiphertexts() = runBlocking {
        val bytes = instrumentation.context.assets.open("emergency-exact-fields-v1.json").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("3b97b4ad58d597f64cdeb3928ac82df22f2bc57dfcf4ee284f3cba577dcf6db5", digest)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(259, root.getInt("count"))
        val array = root.getJSONArray("vectors")
        assertEquals(259, array.length())
        val vectors = (0 until array.length()).map(array::getJSONObject)
        val stored = vectors.associate { it.getString("storageKey") to it.getString("stored") }
        assertEquals(259, stored.size)
        val fixture = Fixture("vectors", 259, initialValues = stored)
        val session = WalletEmergencyRecoverySession(fixture.authenticator, fixture.reader)
        assertTrue(session.unlock(PUBLIC_PIN) is EmergencyRecoveryAccessResult.Page)
        assertTrue(fixture.observer.secretReads().isEmpty())
        var fields = 0
        vectors.forEach { vector ->
            val key = vector.getString("storageKey")
            val expectedArray = vector.getJSONArray("fields")
            val expected = (0 until expectedArray.length()).map { index ->
                val field = expectedArray.getJSONObject(index)
                EmergencyRecoveryField(
                    EmergencyRecoveryFieldType.valueOf(field.getString("type")),
                    field.getString("value")
                )
            }
            val entry = session.readKey(key)
            val label = vector.getString("case")
            assertEquals(label, key, entry.storageKey)
            assertEquals(label, vector.getLong("walletId"), entry.walletId)
            assertEquals(label, EmergencyRecoveryKeyKind.valueOf(vector.getString("kind")), entry.kind)
            assertEquals(label, EmergencyRecoveryState.ACTIVE, entry.state)
            assertTrue(label, entry.readable)
            assertEquals(label, expected, entry.fields)
            fields += expected.size
        }
        assertEquals(991, fields)
        assertEquals(vectors.map { it.getString("storageKey") }, fixture.observer.secretReads())
        fixture.assertStoredSecretsExact()
        fixture.proof("complete-259-vectors-991-fields")
        session.revoke()
    }

    private inner class Fixture(
        label: String,
        count: Int,
        savePin: Boolean = true,
        initialValues: Map<String, String>? = null
    ) {
        val nonce: String
        val source: String
        val namespace: String
        val scope: FixtureContext
        val raw: SharedPreferences
        val encrypted: EncryptedPreferencesImpl
        val observer: ObservedEncryptedPreferences
        val reader: WalletEmergencyRecoveryReader
        val authenticator: WalletEmergencyAuthenticator
        val keys = initialValues?.keys?.toList()
            ?: (0 until count).map { "42:${it.toString(16).padStart(8, '0')}:ACCESS_SECRETS" }
        val values = initialValues?.toMutableMap()
            ?: keys.associateWith { "public exact undecodable stored bytes for $it" }.toMutableMap()
        private val expectedCiphertexts = mutableMapOf<String, Any?>()
        private lateinit var expectedPreferences: Map<String, Map<String, Any?>>

        init {
            val args = InstrumentationRegistry.getArguments()
            check(args.getString("fearlessEmergencyPagingOwnedUid") == "exclusively-owned-fresh-synthetic-app-uid")
            nonce = checkNotNull(args.getString("fearlessEmergencyPagingNonce"))
            source = checkNotNull(args.getString("fearlessEmergencyPagingSource"))
            check(nonce.matches(Regex("[0-9a-f]{32}")) && source.matches(Regex("[0-9a-f]{40}")))
            check(args.getString("fearlessEmergencyPagingSerial")?.matches(Regex("emulator-[0-9]+")) == true)
            check(args.getString("fearlessEmergencyPagingAvd") == "FearlessEmergencyPagingApi${Build.VERSION.SDK_INT}_$nonce")
            check(Build.VERSION.SDK_INT in setOf(26, 34, 36) && Build.HARDWARE in setOf("ranchu", "goldfish"))
            check(Process.myUid() == context.applicationInfo.uid)
            namespace = "emergency-paging-$nonce-$label-${UUID.randomUUID()}"
            scope = FixtureContext(context, namespace)
            raw = scope.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            assertTrue(raw.all.isEmpty())
            val encryption = EncryptionUtil(scope)
            encryption.getPrerenceAesKey()
            encrypted = EncryptedPreferencesImpl(PreferencesImpl(raw), encryption)
            if (savePin) encrypted.savePinCodeDurably(PUBLIC_PIN)
            values.forEach { (key, value) -> encrypted.putEncryptedString(key, value) }
            expectedCiphertexts.putAll(secretImage())
            expectedPreferences = scope.snapshot()
            observer = ObservedEncryptedPreferences(encrypted)
            reader = WalletEmergencyRecoveryReader(observer)
            authenticator = WalletEmergencyAuthenticator(PreferencesImpl(raw), observer)
            assertFalse(scope.getDatabasePath("app.db").exists())
            proof("prepared")
        }

        fun launch(): ActivityScenario<WalletEmergencyExportActivity> =
            ActivityScenario.launch<WalletEmergencyExportActivity>(WalletEmergencyExportActivity.intent(context)).also { scenario ->
                scenario.onActivity { install(it); assertLocked(it) }
            }

        fun install(activity: WalletEmergencyExportActivity) {
            // Before its lazy session is first accessed; real classes, only the preference namespace differs.
            activity.authenticator = authenticator
            activity.reader = reader
        }

        fun enterPin(activity: WalletEmergencyExportActivity, pin: String) {
            activity.findViewById<EditText>(R.id.emergencyPin).setText(pin)
            assertTrue(activity.findViewById<View>(R.id.emergencyUnlock).performClick())
        }

        fun entries(activity: WalletEmergencyExportActivity): List<View> {
            val group = activity.findViewById<LinearLayout>(R.id.emergencyEntries)
            return (0 until group.childCount).map(group::getChildAt)
        }

        fun texts(activity: WalletEmergencyExportActivity) = entries(activity).filterIsInstance<TextView>().map { it.text.toString() }

        fun identifier(activity: WalletEmergencyExportActivity, key: String) = activity.getString(CommonR.string.wallet_emergency_key_identifier, key)

        fun choose(activity: WalletEmergencyExportActivity, key: String) {
            val description = activity.getString(CommonR.string.wallet_emergency_view_key) + " · " + identifier(activity, key)
            val button = entries(activity).filterIsInstance<Button>().single { it.contentDescription == description }
            assertTrue(button.isShown && button.isEnabled)
            assertTrue(button.performClick())
        }

        fun click(activity: WalletEmergencyExportActivity, label: Int) {
            val button = entries(activity).filterIsInstance<Button>().single { it.text == activity.getString(label) }
            assertTrue(button.isShown && button.isEnabled)
            assertTrue(button.performClick())
        }

        fun awaitPage(scenario: ActivityScenario<WalletEmergencyExportActivity>, expected: List<String>) {
            await(scenario) { activity ->
                entries(activity).filterIsInstance<Button>().count { it.text == activity.getString(CommonR.string.wallet_emergency_view_key) } == expected.size &&
                    expected.all { texts(activity).contains(identifier(activity, it)) }
            }
            scenario.onActivity { activity ->
                val descriptions = entries(activity).filterIsInstance<Button>()
                    .filter { it.text == activity.getString(CommonR.string.wallet_emergency_view_key) }
                    .map { it.contentDescription.toString() }
                assertEquals(expected.map { activity.getString(CommonR.string.wallet_emergency_view_key) + " · " + identifier(activity, it) }, descriptions)
                assertEquals(expected.size, descriptions.distinct().size)
                assertTrue(expected.size <= 64)
                assertFalse(activity.findViewById<View>(R.id.emergencyPinGroup).isShown)
                assertFalse(activity.findViewById<View>(R.id.emergencyWarning).isShown)
                values.values.forEach { assertFalse(texts(activity).contains(it)) }
                assertSecure(activity)
            }
        }

        fun await(scenario: ActivityScenario<WalletEmergencyExportActivity>, condition: (WalletEmergencyExportActivity) -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            do {
                var ready = false
                scenario.onActivity { ready = condition(it) }
                if (ready) return
                Thread.sleep(20)
            } while (System.nanoTime() < deadline)
            throw AssertionError("Activity state did not reach the expected fixture condition")
        }

        fun assertSecure(activity: WalletEmergencyExportActivity) {
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(activity.findViewById<EditText>(R.id.emergencyPin).isSaveEnabled)
        }

        fun assertLocked(activity: WalletEmergencyExportActivity) {
            assertSecure(activity)
            assertTrue(activity.findViewById<View>(R.id.emergencyPinGroup).isShown)
            assertEquals("", activity.findViewById<EditText>(R.id.emergencyPin).text.toString())
            assertTrue(entries(activity).isEmpty())
            assertFalse(activity.findViewById<View>(R.id.emergencyWarning).isShown)
        }

        fun replace(key: String, value: String) {
            encrypted.putEncryptedString(key, value)
            values[key] = value
            expectedCiphertexts[key] = raw.all.getValue(key)
            expectedPreferences = expectedPreferences + (SHARED_PREFERENCES_FILE to
                (expectedPreferences.getValue(SHARED_PREFERENCES_FILE) + (key to raw.all.getValue(key))))
            proof("explicit-fixture-replacement")
        }

        fun remove(key: String) {
            assertTrue(raw.edit().remove(key).commit())
            values.remove(key)
            expectedCiphertexts.remove(key)
            expectedPreferences = expectedPreferences + (SHARED_PREFERENCES_FILE to
                (expectedPreferences.getValue(SHARED_PREFERENCES_FILE) - key))
            proof("explicit-fixture-removal")
        }

        private fun secretImage(): Map<String, Any?> = raw.all.filterKeys { it in keys || it == "pin_code" }

        fun assertStoredSecretsExact(pinFailures: Int? = null) {
            assertEquals(expectedCiphertexts, secretImage())
            val expected = if (pinFailures == null) expectedPreferences else expectedPreferences +
                (SHARED_PREFERENCES_FILE to (expectedPreferences.getValue(SHARED_PREFERENCES_FILE) +
                    ("prefs_pin_failed_attempts" to pinFailures)))
            assertEquals("Every scoped preference cell except the explicit fixture/PIN-limiter change", expected, scope.snapshot())
            assertFalse(scope.getDatabasePath("app.db").exists())
        }

        fun proof(phase: String) {
            val record = JSONObject().put("phase", phase).put("namespace", namespace).put("nonce", nonce)
                .put("source", source).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
                .put("keys", JSONArray(keys)).put("currentKeys", JSONArray(values.keys.toList()))
                .put("databaseAbsent", !scope.getDatabasePath("app.db").exists())
                .put("secretReads", JSONArray(observer.secretReads()))
                .put("preferenceFingerprints", JSONObject().apply {
                    scope.snapshot().toSortedMap().forEach { (name, cells) ->
                        put(name, JSONObject().apply {
                            cells.toSortedMap().forEach { (key, value) ->
                                val typed = (value?.javaClass?.name ?: "null") + ":" + value.toString()
                                put(key, MessageDigest.getInstance("SHA-256").digest(typed.toByteArray()).joinToString("") { "%02x".format(it) })
                            }
                        })
                    }
                })
            File(scope.directory, "$phase-${System.nanoTime()}.json").writeText(record.toString(2))
        }
    }

    private class ObservedEncryptedPreferences(private val delegate: EncryptedPreferences) : EncryptedPreferences by delegate {
        private val reads = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var failInventory = false
        @Volatile var blockSelectedRead = false
        val readStarted = CountDownLatch(1)
        val readMayFinish = CountDownLatch(1)
        val readFinished = CountDownLatch(1)

        override fun getDecryptedString(field: String): String? {
            reads.add(field)
            if (blockSelectedRead && field != "pin_code") {
                readStarted.countDown()
                check(readMayFinish.await(10, TimeUnit.SECONDS))
                return try { delegate.getDecryptedString(field) } finally { readFinished.countDown() }
            }
            return delegate.getDecryptedString(field)
        }

        override fun forEachKeyWithPrefixes(prefixes: Set<String>, maxKeyBytes: Int, action: (String) -> Unit) {
            if (failInventory) throw IOException("Public synthetic inventory failure")
            delegate.forEachKeyWithPrefixes(prefixes, maxKeyBytes, action)
        }

        fun secretReads(): List<String> = synchronized(reads) { reads.filterNot { it == "pin_code" } }
    }

    private class FixtureContext(base: Context, namespace: String) : ContextWrapper(base) {
        val directory = File(base.cacheDir, namespace).apply { check(mkdir()) }
        private val prefix = namespace
        private val opened = Collections.synchronizedMap(mutableMapOf<String, SharedPreferences>())
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = File(directory, "app.db").also { check(name == "app.db") }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${prefix}_$name", mode).also { opened[name] = it }

        fun snapshot(): Map<String, Map<String, Any?>> = synchronized(opened) {
            opened.mapValues { (_, preferences) -> preferences.all.mapValues { (_, value) ->
                if (value is Set<*>) value.toSet() else value
            } }
        }
    }

    private companion object {
        const val PUBLIC_PIN = "123456"
        const val WRONG_PIN = "654321"
    }
}
