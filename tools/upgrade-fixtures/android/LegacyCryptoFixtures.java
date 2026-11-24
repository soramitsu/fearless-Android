import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records what the crypto libraries Fearless Android 4.3.1 and earlier shipped computed, so that the BouncyCastle
 * and in-repo code that replaced them in 4.3.2 can be held to the same bytes after the libraries left the build.
 *
 * <ul>
 *   <li>EdDSA-Java 0.3.0: Ed25519 public keys, signatures and the verify verdicts of the 4.3.1 Signer.</li>
 *   <li>SpongyCastle 1.58: the BIP39 PBKDF2 of the 4.3.1 SeedCreator, and Base64 of the JSON backups.</li>
 *   <li>polkaj-scale-0.2.3.jar: the transcript of PolkajScaleExercise (runtime-utils tests) run on the jar.</li>
 * </ul>
 *
 * <p>Inputs are synthetic and deterministic. Run from the repository root with JDK 21:
 * <pre>
 * git show c9d18c435:runtime-utils/libs/polkaj-scale-0.2.3.jar &gt; /tmp/polkaj-scale-0.2.3.jar
 * javac -d /tmp/legacy-out runtime-utils/src/test/java/jp/co/soramitsu/fearless_utils/scale/polkaj/PolkajScaleExercise.java \
 *     -cp /tmp/polkaj-scale-0.2.3.jar
 * java -cp eddsa-0.3.0.jar:core-1.58.0.0.jar \
 *     tools/upgrade-fixtures/android/LegacyCryptoFixtures.java \
 *     /tmp/polkaj-scale-0.2.3.jar /tmp/legacy-out runtime-utils/src/test/resources/legacy-crypto
 * </pre>
 * The jars are the Maven Central artifacts net.i2p.crypto:eddsa:0.3.0 and com.madgag.spongycastle:core:1.58.0.0.
 * Their SHA-256 (also in gradle/verification-metadata.xml) is checked before anything runs.
 */
public final class LegacyCryptoFixtures {

    private static final Map<String, String> PINNED_JARS = Map.of(
        "eddsa-0.3.0.jar", "4dda1120db856640dbec04140ed23242215a075fe127bdefa0dcfa29fb31267d",
        "core-1.58.0.0.jar", "199617dd5698c5a9312b898c0a4cec7ce9dd8649d07f65d91629f58229d72728",
        "polkaj-scale-0.2.3.jar", "bea0f3904fef83497b9e5802f36408663a4aba462bf63ded38b89a5f22befa4d"
    );

    private static final BigInteger ED25519_ORDER =
        new BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16);

    static final long POLKAJ_SEED = 20261107L;
    static final int POLKAJ_ROUNDS = 400;
    static final int POLKAJ_CHUNK = 1000;

    private LegacyCryptoFixtures() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <polkaj-scale-0.2.3.jar> <exercise classes dir> <output dir>");
        }
        List<Path> pinned = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            pinned.add(Paths.get(entry));
        }
        pinned.add(Paths.get(args[0]));
        requirePinnedJars(pinned);

        Path out = Paths.get(args[2]);
        Files.createDirectories(out);
        Files.writeString(out.resolve("ed25519-eddsa-java-0.3.0.json"), ed25519());
        Files.writeString(out.resolve("bip39-seed-spongycastle-1.58.json"), bip39Seeds());
        Files.writeString(out.resolve("base64-spongycastle-1.58.json"), base64());
        Files.writeString(out.resolve("polkaj-scale-0.2.3-jar-transcript.json"), polkaj(Paths.get(args[0]), Paths.get(args[1])));
    }

    // Ed25519 exactly as the 4.3.1 Ed25519SubstrateKeypairFactory and Signer called EdDSA-Java.
    private static String ed25519() throws Exception {
        Security.addProvider(new net.i2p.crypto.eddsa.EdDSASecurityProvider());
        net.i2p.crypto.eddsa.spec.EdDSAParameterSpec spec =
            net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName(net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.ED_25519);
        KeyFactory keyFactory = KeyFactory.getInstance(net.i2p.crypto.eddsa.EdDSAKey.KEY_ALGORITHM, "EdDSA");
        int[] lengths = {0, 1, 32, 33, 255, 1000};
        StringBuilder json = new StringBuilder();
        json.append("{\n \"library\": \"net.i2p.crypto:eddsa:0.3.0 via the 4.3.1 Ed25519SubstrateKeypairFactory and Signer\",\n");
        json.append(" \"messages\": \"message(seedIndex, length) = first length bytes of SHA-256('fearless-ed25519-message/' + seedIndex + '/' + length + '/' + counter) for counter = 0, 1, ...\",\n");
        json.append(" \"cases\": [\n");
        List<byte[]> publicKeys = new ArrayList<>();
        List<byte[]> seeds = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            byte[] seed = stream("fearless-ed25519-seed/" + index, 32);
            net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec privateSpec = new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(seed, spec);
            byte[] privateEncoded = keyFactory.generatePrivate(privateSpec).getEncoded();
            byte[] publicEncoded = keyFactory.generatePublic(
                new net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec(privateSpec.getA(), spec)).getEncoded();
            byte[] privateKey = java.util.Arrays.copyOfRange(privateEncoded, 16, privateEncoded.length);
            byte[] publicKey = java.util.Arrays.copyOfRange(publicEncoded, 12, publicEncoded.length);
            if (!java.util.Arrays.equals(privateKey, seed)) {
                throw new IllegalStateException("EdDSA-Java private key is not the seed");
            }
            seeds.add(seed);
            publicKeys.add(publicKey);
        }
        for (int index = 0; index < seeds.size(); index++) {
            byte[] seed = seeds.get(index);
            byte[] publicKey = publicKeys.get(index);
            byte[] otherKey = publicKeys.get((index + 1) % publicKeys.size());
            json.append("  {\"seed\": \"").append(hex(seed)).append("\", \"publicKey\": \"").append(hex(publicKey)).append("\", \"signatures\": [\n");
            for (int l = 0; l < lengths.length; l++) {
                byte[] message = stream("fearless-ed25519-message/" + index + "/" + lengths[l], lengths[l]);
                byte[] signature = sign(spec, seed, message);
                Map<String, String> verdicts = new LinkedHashMap<>();
                verdicts.put("valid", verify(spec, message, signature, publicKey));
                verdicts.put("sPlusL", verify(spec, message, withScalar(signature, scalar(signature).add(ED25519_ORDER)), publicKey));
                verdicts.put("sEqualsL", verify(spec, message, withScalar(signature, ED25519_ORDER), publicKey));
                verdicts.put("flippedR", verify(spec, message, flip(signature, 0), publicKey));
                verdicts.put("flippedS", verify(spec, message, flip(signature, 32), publicKey));
                verdicts.put("otherMessage", verify(spec, message.length == 0 ? new byte[]{0} : flip(message, message.length - 1), signature, publicKey));
                verdicts.put("otherKey", verify(spec, message, signature, otherKey));
                json.append("   {\"length\": ").append(lengths[l]).append(", \"signature\": \"").append(hex(signature)).append("\", \"verify\": {");
                int count = 0;
                for (Map.Entry<String, String> verdict : verdicts.entrySet()) {
                    json.append(count++ == 0 ? "" : ", ").append('"').append(verdict.getKey()).append("\": \"").append(verdict.getValue()).append('"');
                }
                json.append("}}").append(l + 1 < lengths.length ? ",\n" : "\n");
            }
            json.append("  ]}").append(index + 1 < seeds.size() ? ",\n" : "\n");
        }
        return json.append(" ]\n}\n").toString();
    }

    private static byte[] sign(net.i2p.crypto.eddsa.spec.EdDSAParameterSpec spec, byte[] seed, byte[] message) throws Exception {
        Signature signature = Signature.getInstance(net.i2p.crypto.eddsa.EdDSAEngine.SIGNATURE_ALGORITHM,
            net.i2p.crypto.eddsa.EdDSASecurityProvider.PROVIDER_NAME);
        signature.initSign(new net.i2p.crypto.eddsa.EdDSAPrivateKey(new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(seed, spec)));
        signature.update(message);
        return signature.sign();
    }

    // The 4.3.1 Signer.verifyEd25519: length checks, then S < L, then EdDSA-Java.
    private static String verify(net.i2p.crypto.eddsa.spec.EdDSAParameterSpec spec, byte[] message, byte[] signature, byte[] publicKey) {
        try {
            if (signature.length != 64 || publicKey.length != 32) {
                return "false";
            }
            if (scalar(signature).compareTo(ED25519_ORDER) >= 0) {
                return "false";
            }
            Signature verifier = Signature.getInstance(net.i2p.crypto.eddsa.EdDSAEngine.SIGNATURE_ALGORITHM,
                net.i2p.crypto.eddsa.EdDSASecurityProvider.PROVIDER_NAME);
            verifier.initVerify(new net.i2p.crypto.eddsa.EdDSAPublicKey(new net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec(publicKey, spec)));
            verifier.update(message);
            return Boolean.toString(verifier.verify(signature));
        } catch (Exception error) {
            return "error:" + error.getClass().getName();
        }
    }

    // The 4.3.1 SeedCreator.deriveSeed: PBKDF2-HMAC-SHA512, 2048 rounds, salt NFKD("mnemonic" + passphrase) in UTF-8.
    private static String bip39Seeds() throws Exception {
        String[] passphrases = {
            null, "", "TREZOR", "Substrate", "pw", "Café", "Café", "Ångström", "Ångström",
            "パスワード", "ﾊﾟｽﾜｰﾄﾞ", "㍍ガバヴァぱばぐゞちぢ十人十色",
            "🔑 key", "  spaced  out  ", "tab\tand\nnewline", "ﬁne Ⅳ",
        };
        String[] mnemonics = {
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "bottom drive obey lake curtain smoke basket hold race lonely fit walk",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote",
        };
        StringBuilder json = new StringBuilder();
        json.append("{\n \"library\": \"com.madgag.spongycastle:core:1.58.0.0 PKCS5S2ParametersGenerator(SHA512Digest) as the 4.3.1 SeedCreator called it\",\n");
        json.append(" \"cases\": [\n");
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            byte[] entropy = stream("fearless-bip39-entropy/" + index, 16 + 4 * (index % 5));
            String passphrase = passphrases[index % passphrases.length];
            rows.add(seedRow("entropy", hex(entropy), entropy, passphrase));
        }
        for (String passphrase : passphrases) {
            for (String mnemonic : mnemonics) {
                rows.add(seedRow("words", mnemonic, mnemonic.getBytes(StandardCharsets.UTF_8), passphrase));
            }
        }
        json.append(String.join(",\n", rows)).append("\n ]\n}\n");
        return json.toString();
    }

    private static String seedRow(String kind, String input, byte[] password, String passphrase) {
        org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator generator =
            new org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator(new org.spongycastle.crypto.digests.SHA512Digest());
        String salt = Normalizer.normalize("mnemonic" + (passphrase == null ? "" : passphrase), Normalizer.Form.NFKD);
        generator.init(password, salt.getBytes(StandardCharsets.UTF_8), 2048);
        byte[] seed = ((org.spongycastle.crypto.params.KeyParameter) generator.generateDerivedMacParameters(64 * 8)).getKey();
        return "  {\"kind\": \"" + kind + "\", \"input\": \"" + input + "\", \"passphrase\": "
            + (passphrase == null ? "null" : "\"" + jsonEscape(passphrase) + "\"")
            + ", \"seed\": \"" + hex(java.util.Arrays.copyOfRange(seed, 0, 64)) + "\"}";
    }

    // Base64 as JsonSeedEncoder wrote and JsonSeedDecoder read the "encoded" field.
    private static String base64() throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\n \"library\": \"com.madgag.spongycastle:core:1.58.0.0 org.spongycastle.util.encoders.Base64\",\n");
        json.append(" \"encode\": [\n");
        List<String> rows = new ArrayList<>();
        for (int length = 0; length <= 40; length++) {
            byte[] data = stream("fearless-base64/" + length, length);
            rows.add("  {\"bytes\": \"" + hex(data) + "\", \"base64\": \"" + org.spongycastle.util.encoders.Base64.toBase64String(data) + "\"}");
        }
        byte[] large = stream("fearless-base64/large", 1000);
        rows.add("  {\"bytes\": \"" + hex(large) + "\", \"base64\": \"" + org.spongycastle.util.encoders.Base64.toBase64String(large) + "\"}");
        json.append(String.join(",\n", rows)).append("\n ],\n \"decode\": [\n");
        String[] inputs = {
            "", "A", "AB", "ABC", "ABCD", "ABC=", "AB==", "A===", "====", "AB=C", "QUJD", "QUJDRA==", "QUJDRA=", "QUJDRA",
            " QUJD ", "QU\nJD", "QU\r\nJD\n", "QU JD", "\tQUJD\t", "QUJ*", "QUJ-", "QUJ_", "QUJé", "QUJDé===",
            "////", "++++", "-_-_", "Zm9vYmFy", "Zm9vYmE=", "Zm9vYg==", "Zm9vYg", "Zm9vYg=", "Zm9=vYg==", "=Zm9v",
            "Z", "Zm", "Zm9", "Zm9v\n", "\nZm9v", "Zm9v====", "Zm9vYmFyYmF6", "AAAA", "////////", "/w==", "/w=",
        };
        rows.clear();
        for (String input : inputs) {
            String result;
            try {
                result = hex(org.spongycastle.util.encoders.Base64.decode(input));
            } catch (Exception error) {
                result = "error:" + error.getClass().getName();
            }
            rows.add("  {\"input\": \"" + jsonEscape(input) + "\", \"result\": \"" + result + "\"}");
        }
        return json.append(String.join(",\n", rows)).append("\n ]\n}\n").toString();
    }

    @SuppressWarnings("unchecked")
    private static String polkaj(Path jar, Path exerciseClasses) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL(), exerciseClasses.toUri().toURL()},
            ClassLoader.getPlatformClassLoader())) {
            Class<?> reader = loader.loadClass("io.emeraldpay.polkaj.scale.ScaleCodecReader");
            if (!reader.getProtectionDomain().getCodeSource().getLocation().equals(jar.toUri().toURL())) {
                throw new IllegalStateException("polkaj classes did not load from the jar");
            }
            Method run = loader.loadClass("jp.co.soramitsu.fearless_utils.scale.polkaj.PolkajScaleExercise")
                .getMethod("run", long.class, int.class);
            List<String> lines = (List<String>) run.invoke(null, POLKAJ_SEED, POLKAJ_ROUNDS);
            StringBuilder json = new StringBuilder();
            json.append("{\n \"library\": \"runtime-utils/libs/polkaj-scale-0.2.3.jar (sha256 ").append(PINNED_JARS.get("polkaj-scale-0.2.3.jar"))
                .append("), PolkajScaleExercise.run(seed, rounds)\",\n");
            json.append(" \"seed\": ").append(POLKAJ_SEED).append(",\n \"rounds\": ").append(POLKAJ_ROUNDS).append(",\n");
            json.append(" \"lines\": ").append(lines.size()).append(",\n \"chunkLines\": ").append(POLKAJ_CHUNK).append(",\n");
            json.append(" \"sha256\": \"").append(transcriptDigest(lines, 0, lines.size())).append("\",\n \"chunks\": [\n");
            List<String> chunks = new ArrayList<>();
            for (int start = 0; start < lines.size(); start += POLKAJ_CHUNK) {
                chunks.add("  \"" + transcriptDigest(lines, start, Math.min(lines.size(), start + POLKAJ_CHUNK)) + "\"");
            }
            return json.append(String.join(",\n", chunks)).append("\n ]\n}\n").toString();
        }
    }

    // SHA-256 over the UTF-8 lines, each followed by '\n'.
    static String transcriptDigest(List<String> lines, int from, int to) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = from; i < to; i++) {
            digest.update(lines.get(i).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return hex(digest.digest());
    }

    private static void requirePinnedJars(List<Path> classpath) throws IOException, Exception {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Path entry : classpath) {
            String name = entry.getFileName().toString();
            if (PINNED_JARS.containsKey(name)) {
                String actual = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(entry)));
                if (!actual.equals(PINNED_JARS.get(name))) {
                    throw new IllegalStateException(name + " has SHA-256 " + actual);
                }
                seen.put(name, true);
            }
        }
        if (seen.size() != PINNED_JARS.size()) {
            throw new IllegalStateException("Expected the pinned jars " + PINNED_JARS.keySet() + ", found " + seen.keySet());
        }
    }

    // Deterministic synthetic bytes: SHA-256(label + '/' + counter) blocks.
    static byte[] stream(String label, int length) throws Exception {
        byte[] out = new byte[length];
        int offset = 0;
        for (int counter = 0; offset < length; counter++) {
            byte[] block = MessageDigest.getInstance("SHA-256").digest((label + "/" + counter).getBytes(StandardCharsets.UTF_8));
            int take = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, out, offset, take);
            offset += take;
        }
        return out;
    }

    private static BigInteger scalar(byte[] signature) {
        byte[] s = new byte[32];
        for (int i = 0; i < 32; i++) {
            s[i] = signature[63 - i];
        }
        return new BigInteger(1, s);
    }

    private static byte[] withScalar(byte[] signature, BigInteger scalar) {
        byte[] result = java.util.Arrays.copyOf(signature, 64);
        byte[] bigEndian = scalar.toByteArray();
        for (int i = 0; i < 32; i++) {
            int source = bigEndian.length - 1 - i;
            result[32 + i] = source >= 0 ? bigEndian[source] : 0;
        }
        return result;
    }

    private static byte[] flip(byte[] value, int index) {
        byte[] copy = value.clone();
        copy[index] ^= 1;
        return copy;
    }

    private static String jsonEscape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                escaped.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    static String hex(byte[] value) {
        StringBuilder text = new StringBuilder();
        for (byte b : value) {
            text.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return text.toString();
    }
}
