#![cfg(target_os = "android")]
#![allow(non_snake_case)]

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject};
use jni::sys::{jbyteArray, jboolean, jsize};
use jni::errors::{Result as JniResult, Error as JniError, ErrorKind};
use schnorrkel::{
    derive::{CHAIN_CODE_LENGTH, ChainCode, Derivation}, ExpansionMode, Keypair, MiniSecretKey, PublicKey,
    SecretKey, Signature};
use std::ptr;

const SIGNING_CTX: &'static [u8] = b"substrate";

/// Size of input SEED for derivation, bytes
pub const SR25519_SEED_SIZE: jsize = 32;

/// Size of CHAINCODE, bytes
pub const SR25519_CHAINCODE_SIZE: jsize = 32;

/// Size of SR25519 PUBLIC KEY, bytes
pub const SR25519_PUBLIC_SIZE: jsize = 32;

/// Size of SR25519 PRIVATE (SECRET) KEY, which consists of [32 bytes key | 32 bytes nonce]
pub const SR25519_SECRET_SIZE: jsize = 64;

/// Size of SR25519 SIGNATURE, bytes
pub const SR25519_SIGNATURE_SIZE: jsize = 64;

/// Size of SR25519 KEYPAIR. [32 bytes key | 32 bytes nonce | 32 bytes public]
pub const SR25519_KEYPAIR_SIZE: jsize = 96;

macro_rules! r#try_or_throw {
    ($jni_env: ident, $expr:expr, $ret: expr) => {
        match $expr {
            JniResult::Ok(val) => val,
            JniResult::Err(err) => {
                $jni_env.throw_new("java/lang/Exception", err.description()).unwrap();
                return $ret;
            }
        }
    };
    ($expr:expr,) => {
        $crate::r#try!($expr)
    };
}

macro_rules! r#try_or_throw_null {
    ($jni_env: ident, $expr:expr) => {
        try_or_throw!($jni_env, $expr, ptr::null_mut());
    }
}

/// ChainCode construction helper
fn create_cc(data: &[u8]) -> ChainCode {
    let mut cc = [0u8; CHAIN_CODE_LENGTH];

    cc.copy_from_slice(&data);

    ChainCode(cc)
}

/// Keypair helper function.
fn create_from_seed(seed: &[u8]) -> JniResult<Keypair> {
    match MiniSecretKey::from_bytes(seed) {
        Ok(mini) => return JniResult::Ok(mini.expand_to_keypair(ExpansionMode::Ed25519)),
        Err(_) => return JniResult::Err(
            JniError::from_kind(ErrorKind::Msg("Provided seed is invalid".to_string()))),
    }
}

/// Keypair helper function.
fn create_from_pair(pair: &[u8]) -> JniResult<Keypair> {
    match Keypair::from_bytes(pair) {
        Ok(pair) => return JniResult::Ok(pair),
        Err(_) => return JniResult::Err(
            JniError::from_kind(ErrorKind::Msg(format!("Provided pair is invalid: {:?}", pair)))),
    }
}

/// PublicKey helper
fn create_public(public: &[u8]) -> JniResult<PublicKey> {
    match PublicKey::from_bytes(public) {
        Ok(public) => return JniResult::Ok(public),
        Err(_) => return JniResult::Err(
            JniError::from_kind(ErrorKind::Msg("Provided public key is invalid.".to_string()))),
    }
}

/// SecretKey helper
fn create_secret(secret: &[u8]) -> JniResult<SecretKey> {
    match SecretKey::from_bytes(secret) {
        Ok(secret) => return JniResult::Ok(secret),
        Err(_) => return JniResult::Err(
            JniError::from_kind(ErrorKind::Msg("Provided private key is invalid.".to_string()))),
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_test<'a>(
    jni_env: JNIEnv<'a>,
    _: JClass,
    hello_what: JString) -> JString<'a> {
    let hello_what_str = try_or_throw!(jni_env, jni_env.get_string(hello_what), JObject::null().into());
    let s = format!("Hello, {}!", hello_what_str.to_str().unwrap_or("<invalid string>"));
    println!("{}", s);
    try_or_throw!(jni_env, jni_env.new_string(s), JObject::null().into())
}

/**
 * Verify a message and its corresponding against a public key;
 *
 * @param signature:  verify this signature
 * @param message:    arbitrary message
 * @param public_key: verify with this public key
 * @return true if signature is valid, false otherwise
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_verify(
    jni_env: JNIEnv,
    _: JClass,
    signature_bytes: jbyteArray, message: jbyteArray, public_key: jbyteArray) -> jboolean {

    let public_key_vec = try_or_throw!(jni_env, jni_env.convert_byte_array(public_key), 0);
    let message_vec = try_or_throw!(jni_env, jni_env.convert_byte_array(message), 0);
    let signature_vec = try_or_throw!(jni_env, jni_env.convert_byte_array(signature_bytes), 0);
    let signature = match Signature::from_bytes(signature_vec.as_slice()) {
        Ok(signature) => signature,
        Err(_) => return 0,
    };
    let public = try_or_throw!(jni_env, create_public(public_key_vec.as_slice()), 0);
    let res = public.verify_simple(SIGNING_CTX, message_vec.as_slice(), &signature);
    if res.is_ok() { 1 } else { 0 }
}

/**
 * Sign a message
 * The combination of both public and private key must be provided.
 * This is effectively equivalent to a keypair.
 *
 * @param public_key: public key
 * @param secret:     private key (secret)
 * @param message:    Arbitrary message
 * @return the signature
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_sign(
    jni_env: JNIEnv,
    _: JClass,
    public_key: jbyteArray, secret: jbyteArray, message: jbyteArray) -> jbyteArray {

    let public_key_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(public_key));
    let secret_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(secret));
    let message_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(message));

    let secret = try_or_throw_null!(jni_env, create_secret(secret_vec.as_slice()));
    let public = try_or_throw_null!(jni_env, create_public(public_key_vec.as_slice()));
    let signature = secret.sign_simple(SIGNING_CTX, message_vec.as_slice(),
                     &public);

    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(signature.to_bytes().as_ref()))
}

/**
 * Perform a derivation on a secret
 *
 * @param pair: existing keypair - input buffer of SR25519_KEYPAIR_SIZE bytes
 * @param cc:   chaincode - input buffer of SR25519_CHAINCODE_SIZE bytes
 * @return pre-allocated output buffer of SR25519_KEYPAIR_SIZE bytes
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairHard(
    jni_env: JNIEnv,
    _: JClass,
   pair: jbyteArray, cc: jbyteArray,
) -> jbyteArray {
    let pair_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(pair));
    let cc_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(cc));
    let kp = try_or_throw_null!(jni_env, create_from_pair(pair_vec.as_slice()));
    let mini_secret_key = kp.secret.hard_derive_mini_secret_key(
        Some(create_cc(cc_vec.as_slice())), &[]).0;
    let res_kp = mini_secret_key.expand_to_keypair(ExpansionMode::Ed25519);
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(res_kp.to_bytes().as_ref()))
}

/**
 * Perform a derivation on a secret
 *
 * @param pair: existing keypair - input buffer of SR25519_KEYPAIR_SIZE bytes
 * @param cc:   chaincode - input buffer of SR25519_CHAINCODE_SIZE bytes
 * @return keypair: pre-allocated output buffer of SR25519_KEYPAIR_SIZE bytes
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairSoft(
    jni_env: JNIEnv,
    _: JClass,
    pair: jbyteArray,
    cc: jbyteArray,
) -> jbyteArray {
    let pair_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(pair));
    let cc_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(cc));
    let kp = try_or_throw_null!(jni_env, create_from_pair(pair_vec.as_slice()));
    let mini_secret_key = kp.derived_key_simple(
        create_cc(cc_vec.as_slice()), &[]).0;
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(mini_secret_key.to_bytes().as_ref()))
}

/**
 * Perform a derivation on a publicKey
 *
 * @param public_key: public key - input buffer of SR25519_PUBLIC_SIZE bytes
 * @param cc:         chaincode - input buffer of SR25519_CHAINCODE_SIZE bytes
 * @return pre-allocated output buffer of SR25519_PUBLIC_SIZE bytes
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_derivePublicSoft(
    jni_env: JNIEnv,
    _: JClass,
    pair: jbyteArray,
    cc: jbyteArray,
) -> jbyteArray {
    let pair_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(pair));
    let cc_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(cc));
    let public_key = try_or_throw_null!(jni_env, create_public(pair_vec.as_slice()));
    let derived_key = public_key.derived_key_simple(create_cc(cc_vec.as_slice()), &[]).0;
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(derived_key.to_bytes().as_ref()))
}

/**
 * Generate a key pair.
 *
 * @param seed: generation seed - input buffer of SR25519_SEED_SIZE bytes
 * @return keypair [32b key | 32b nonce | 32b public], pre-allocated output buffer of SR25519_KEYPAIR_SIZE bytes
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_keypairFromSeed(
    jni_env: JNIEnv,
    _: JClass,
    seed: jbyteArray) -> jbyteArray {
    let seed_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(seed));
    let kp = try_or_throw_null!(jni_env, create_from_seed(&seed_vec));
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(kp.to_bytes().as_ref()))
}

/**
 * Converts a secret key, provided as an array of 64 bytes,
 * to a corresponding ed25519 expanded secret key
 * @return an array of 64 bytes, with the first 32 bytes being the secret scalar shifted ed25519 style,
 * and the last 32 bytes being the seed for nonces
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_toEd25519Bytes(
    jni_env: JNIEnv,
    _: JClass,
    secret: jbyteArray
) -> jbyteArray {
    let secret_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(secret));
    let secret = try_or_throw_null!(jni_env, create_secret(secret_vec.as_slice()));
    let secret_ed_bytes = secret.to_ed25519_bytes();
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(&secret_ed_bytes))
}

/**
 * Converts an ed25519 expanded secret key to a corresponding sr25519 secret key.
 * @return an array of 64 bytes, with the first 32 bytes being the secret scalar
 * represented canonically, and the last 32 bytes being the seed for nonces
 */
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_fromEd25519Bytes(
    jni_env: JNIEnv,
    _: JClass,
    expanded_ed_secret: jbyteArray
) -> jbyteArray {
    let ed_secret_vec = try_or_throw_null!(jni_env, jni_env.convert_byte_array(expanded_ed_secret));
    let secret = match SecretKey::from_ed25519_bytes(ed_secret_vec.as_slice()) {
        Ok(s) => s,
        Err(_) => return ptr::null_mut()
    };
    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(secret.to_bytes().as_ref()))
}

// Backward-compatible JNI exports for shared_utils package names used by downstream apps.
#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_test<'a>(
    jni_env: JNIEnv<'a>,
    clazz: JClass,
    hello_what: JString,
) -> JString<'a> {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_test(jni_env, clazz, hello_what)
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_verify(
    jni_env: JNIEnv,
    clazz: JClass,
    signature_bytes: jbyteArray,
    message: jbyteArray,
    public_key: jbyteArray,
) -> jboolean {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_verify(
        jni_env,
        clazz,
        signature_bytes,
        message,
        public_key,
    )
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_sign(
    jni_env: JNIEnv,
    clazz: JClass,
    public_key: jbyteArray,
    secret: jbyteArray,
    message: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_sign(
        jni_env,
        clazz,
        public_key,
        secret,
        message,
    )
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_deriveKeypairHard(
    jni_env: JNIEnv,
    clazz: JClass,
    pair: jbyteArray,
    cc: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairHard(
        jni_env, clazz, pair, cc,
    )
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_deriveKeypairSoft(
    jni_env: JNIEnv,
    clazz: JClass,
    pair: jbyteArray,
    cc: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairSoft(
        jni_env, clazz, pair, cc,
    )
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_derivePublicSoft(
    jni_env: JNIEnv,
    clazz: JClass,
    pair: jbyteArray,
    cc: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_derivePublicSoft(
        jni_env, clazz, pair, cc,
    )
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_keypairFromSeed(
    jni_env: JNIEnv,
    clazz: JClass,
    seed: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_keypairFromSeed(jni_env, clazz, seed)
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_toEd25519Bytes(
    jni_env: JNIEnv,
    clazz: JClass,
    secret: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_toEd25519Bytes(jni_env, clazz, secret)
}

#[no_mangle]
pub unsafe extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_fromEd25519Bytes(
    jni_env: JNIEnv,
    clazz: JClass,
    expanded_ed_secret: jbyteArray,
) -> jbyteArray {
    Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_fromEd25519Bytes(
        jni_env,
        clazz,
        expanded_ed_secret,
    )
}
