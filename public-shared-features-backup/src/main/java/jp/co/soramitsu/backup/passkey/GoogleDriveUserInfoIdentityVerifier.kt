package jp.co.soramitsu.backup.passkey

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.OkHttpClient
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Identity obtained from Google's UserInfo endpoint with the same bearer used for Drive. */
class GoogleDriveVerifiedIdentity(subject: String, email: String, emailVerified: Boolean) {
    val subject = requireSubject(subject)
    val email = GoogleDrivePasskeyBackup.requireAccountName(email)

    init {
        require(emailVerified) { "Google Drive account email is not verified" }
    }

    override fun toString(): String = "GoogleDriveVerifiedIdentity([REDACTED])"

    companion object {
        private val SUBJECT = Regex("^[A-Za-z0-9_-]{1,255}$")

        fun requireSubject(subject: String): String = subject.also {
            require(SUBJECT.matches(it)) { "Google Drive account subject is invalid" }
        }
    }
}

fun interface GoogleDriveIdentityVerifier {
    suspend fun verify(accessToken: String): GoogleDriveVerifiedIdentity
}

/** Fixed Google HTTPS origin, bounded body, no redirects/cache/interceptors or automatic retry. */
class GoogleDriveUserInfoIdentityVerifier(
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    )
) : GoogleDriveIdentityVerifier {
    override suspend fun verify(accessToken: String): GoogleDriveVerifiedIdentity {
        val token = GoogleDriveAccountAccess.requireAccessToken(accessToken)
        val response = transport.execute(
            GoogleDriveHttpRequest(
                method = "GET",
                url = USER_INFO_URL,
                headers = mapOf("Authorization" to "Bearer $token", "Cache-Control" to "no-store"),
                maxResponseBytes = MAX_IDENTITY_RESPONSE_BYTES
            )
        )
        require(response.code == HTTP_OK) { "Google Drive account identity could not be verified" }
        return parseIdentity(response.body)
    }

    private fun parseIdentity(body: ByteArray): GoogleDriveVerifiedIdentity {
        require(body.size in 1..MAX_IDENTITY_RESPONSE_BYTES) { "Invalid Google Drive identity response length" }
        return runCatching {
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body)).toString()
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                var subject: String? = null
                var email: String? = null
                var emailVerified = false
                val seen = mutableSetOf<String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(seen.add(name) && seen.size <= MAX_IDENTITY_FIELDS)
                    when (name) {
                        "sub" -> subject = reader.requiredString()
                        "email" -> email = reader.requiredString()
                        "email_verified" -> {
                            require(reader.peek() == JsonToken.BOOLEAN)
                            emailVerified = reader.nextBoolean()
                        }
                        else -> {
                            require(reader.peek() in OPTIONAL_SCALAR_TYPES)
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT)
                GoogleDriveVerifiedIdentity(requireNotNull(subject), requireNotNull(email), emailVerified)
            }
        }.getOrElse { throw IllegalArgumentException("Malformed Google Drive identity response") }
    }

    private fun JsonReader.requiredString(): String {
        require(peek() == JsonToken.STRING)
        return nextString()
    }

    companion object {
        const val USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
        internal const val MAX_IDENTITY_RESPONSE_BYTES = 8192
        private const val MAX_IDENTITY_FIELDS = 32
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val HTTP_OK = 200
        private val OPTIONAL_SCALAR_TYPES = setOf(JsonToken.STRING, JsonToken.BOOLEAN, JsonToken.NUMBER, JsonToken.NULL)
    }
}
