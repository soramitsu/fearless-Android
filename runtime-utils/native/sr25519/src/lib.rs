//! JNI bindings for sr25519 (schnorrkel), the signature scheme of Polkadot,
//! Kusama and SORA accounts in Fearless Wallet.
//!
//! The ABI is unchanged from the library shipped up to Android 4.3.1: every
//! export below exists under both `jp.co.soramitsu.fearless_utils.encrypt.Sr25519`
//! and `jp.co.soramitsu.shared_utils.encrypt.Sr25519`, takes and returns the same
//! byte-array layouts, and throws `java.lang.Exception` with the same messages
//! (see `keys.rs`). Changes against the legacy library:
//! - a refused keypair no longer puts the keypair bytes into the exception text;
//! - a chain code that is not 32 bytes throws instead of aborting the process;
//! - an Ed25519-form secret whose scalar is not canonical returns `null` from
//!   `fromEd25519Bytes` instead of an unusable secret;
//! - secret inputs and outputs are wiped from native memory before returning.

mod keys;

use std::any::Any;
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::errors::{Error as JniError, ErrorPolicy};
use jni::objects::{JByteArray, JString};
use jni::strings::{JNIStr, JNIString};
use jni::sys::{jboolean, jclass};
use jni::{Env, EnvUnowned, jni_str};
use zeroize::Zeroizing;

use keys::Rejection;

/// Why a native call failed.
#[derive(Debug)]
enum NativeError {
    /// A JNI call failed, for example on a `null` argument.
    Jni(JniError),
    /// The input was refused by the sr25519 layer.
    Rejected(Rejection),
}

impl From<JniError> for NativeError {
    fn from(error: JniError) -> Self {
        NativeError::Jni(error)
    }
}

impl From<Rejection> for NativeError {
    fn from(rejection: Rejection) -> Self {
        NativeError::Rejected(rejection)
    }
}

impl NativeError {
    fn message(&self) -> JNIString {
        match self {
            NativeError::Rejected(rejection) => JNIString::from(rejection.message()),
            // The legacy library reported a null argument as "null pointer".
            NativeError::Jni(JniError::NullPtr(_)) => JNIString::from("null pointer"),
            NativeError::Jni(_) => JNIString::from("JNI error"),
        }
    }
}

type NativeResult<T> = Result<T, NativeError>;

/// Throws `java.lang.Exception` unless an exception is already pending.
fn throw_exception(env: &mut Env, message: impl AsRef<JNIStr>) {
    if !env.exception_check() {
        // throw_new reports the thrown exception as Err(JavaException).
        let _ = env.throw_new(jni_str!("java/lang/Exception"), message.as_ref());
    }
}

/// Maps errors and panics to `java.lang.Exception`, returning `null`/`false`.
/// Panic payloads are never formatted: they could describe secret input.
struct ThrowException;

impl<T: Default> ErrorPolicy<T, NativeError> for ThrowException {
    type Captures<'unowned_env_local: 'native_method, 'native_method> = ();

    fn on_error<'unowned_env_local: 'native_method, 'native_method>(
        env: &mut Env<'unowned_env_local>,
        _captures: &mut Self::Captures<'unowned_env_local, 'native_method>,
        error: NativeError,
    ) -> jni::errors::Result<T> {
        throw_exception(env, error.message());
        Ok(T::default())
    }

    fn on_panic<'unowned_env_local: 'native_method, 'native_method>(
        env: &mut Env<'unowned_env_local>,
        _captures: &mut Self::Captures<'unowned_env_local, 'native_method>,
        payload: Box<dyn Any + Send + 'static>,
    ) -> jni::errors::Result<T> {
        if let Err(drop_panic) = catch_unwind(AssertUnwindSafe(|| drop(payload))) {
            std::mem::forget(drop_panic);
        }
        throw_exception(env, jni_str!("sr25519 native failure"));
        Ok(T::default())
    }
}

/// Copies a Java byte array into a buffer that is wiped when dropped.
fn read_bytes(env: &Env, array: &JByteArray) -> NativeResult<Zeroizing<Vec<u8>>> {
    let length = array.len(env)?;
    let mut bytes = Zeroizing::new(vec![0u8; length]);
    // SAFETY: i8 and u8 have the same size, alignment and validity, so the
    // buffer can be viewed as the jbyte slice that GetByteArrayRegion fills.
    let region = unsafe { &mut *(bytes.as_mut_slice() as *mut [u8] as *mut [i8]) };
    array.get_region(env, 0, region)?;
    Ok(bytes)
}

fn new_byte_array<'local>(env: &mut Env<'local>, bytes: &[u8]) -> NativeResult<JByteArray<'local>> {
    Ok(env.byte_array_from_slice(bytes)?)
}

fn greeting<'local>(mut env: EnvUnowned<'local>, name: JString<'local>) -> JString<'local> {
    env.with_env(|env| -> NativeResult<JString<'local>> {
        let name = name.try_to_string(env)?;
        Ok(env.new_string(keys::greeting(&name))?)
    })
    .resolve::<ThrowException>()
}

fn verify<'local>(
    mut env: EnvUnowned<'local>,
    signature: JByteArray<'local>,
    message: JByteArray<'local>,
    public_key: JByteArray<'local>,
) -> jboolean {
    env.with_env(|env| -> NativeResult<jboolean> {
        let public_key = read_bytes(env, &public_key)?;
        let message = read_bytes(env, &message)?;
        let signature = read_bytes(env, &signature)?;
        Ok(keys::verify(&signature, &message, &public_key)?)
    })
    .resolve::<ThrowException>()
}

fn sign<'local>(
    mut env: EnvUnowned<'local>,
    public_key: JByteArray<'local>,
    secret: JByteArray<'local>,
    message: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let public_key = read_bytes(env, &public_key)?;
        let secret = read_bytes(env, &secret)?;
        let message = read_bytes(env, &message)?;
        let signature = keys::sign(&public_key, &secret, &message)?;
        new_byte_array(env, &signature)
    })
    .resolve::<ThrowException>()
}

fn derive_keypair_hard<'local>(
    mut env: EnvUnowned<'local>,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let keypair = read_bytes(env, &keypair)?;
        let chain_code = read_bytes(env, &chain_code)?;
        let child = keys::derive_keypair_hard(&keypair, &chain_code)?;
        new_byte_array(env, &child[..])
    })
    .resolve::<ThrowException>()
}

fn derive_keypair_soft<'local>(
    mut env: EnvUnowned<'local>,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let keypair = read_bytes(env, &keypair)?;
        let chain_code = read_bytes(env, &chain_code)?;
        let child = keys::derive_keypair_soft(&keypair, &chain_code)?;
        new_byte_array(env, &child[..])
    })
    .resolve::<ThrowException>()
}

fn derive_public_soft<'local>(
    mut env: EnvUnowned<'local>,
    public_key: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let public_key = read_bytes(env, &public_key)?;
        let chain_code = read_bytes(env, &chain_code)?;
        let child = keys::derive_public_soft(&public_key, &chain_code)?;
        new_byte_array(env, &child)
    })
    .resolve::<ThrowException>()
}

fn keypair_from_seed<'local>(
    mut env: EnvUnowned<'local>,
    seed: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let seed = read_bytes(env, &seed)?;
        let keypair = keys::keypair_from_seed(&seed)?;
        new_byte_array(env, &keypair[..])
    })
    .resolve::<ThrowException>()
}

fn to_ed25519_bytes<'local>(
    mut env: EnvUnowned<'local>,
    secret: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let secret = read_bytes(env, &secret)?;
        let expanded = keys::to_ed25519_bytes(&secret)?;
        new_byte_array(env, &expanded[..])
    })
    .resolve::<ThrowException>()
}

fn from_ed25519_bytes<'local>(
    mut env: EnvUnowned<'local>,
    expanded: JByteArray<'local>,
) -> JByteArray<'local> {
    env.with_env(|env| -> NativeResult<JByteArray<'local>> {
        let expanded = read_bytes(env, &expanded)?;
        // A refused key returns null without an exception, as before.
        match keys::from_ed25519_bytes(&expanded) {
            Some(secret) => new_byte_array(env, &secret[..]),
            None => Ok(JByteArray::default()),
        }
    })
    .resolve::<ThrowException>()
}

// Exports for jp.co.soramitsu.fearless_utils.encrypt.Sr25519.

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_test<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    hello_what: JString<'local>,
) -> JString<'local> {
    greeting(env, hello_what)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_verify<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    signature: JByteArray<'local>,
    message: JByteArray<'local>,
    public_key: JByteArray<'local>,
) -> jboolean {
    verify(env, signature, message, public_key)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_sign<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    public_key: JByteArray<'local>,
    secret: JByteArray<'local>,
    message: JByteArray<'local>,
) -> JByteArray<'local> {
    sign(env, public_key, secret, message)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairHard<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_keypair_hard(env, keypair, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_deriveKeypairSoft<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_keypair_soft(env, keypair, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_derivePublicSoft<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    public_key: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_public_soft(env, public_key, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_keypairFromSeed<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    seed: JByteArray<'local>,
) -> JByteArray<'local> {
    keypair_from_seed(env, seed)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_toEd25519Bytes<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    secret: JByteArray<'local>,
) -> JByteArray<'local> {
    to_ed25519_bytes(env, secret)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_fromEd25519Bytes<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    expanded_ed_secret: JByteArray<'local>,
) -> JByteArray<'local> {
    from_ed25519_bytes(env, expanded_ed_secret)
}

// Exports for jp.co.soramitsu.shared_utils.encrypt.Sr25519, kept for apps that
// load this library under the shared_utils package name.

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_test<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    hello_what: JString<'local>,
) -> JString<'local> {
    greeting(env, hello_what)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_verify<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    signature: JByteArray<'local>,
    message: JByteArray<'local>,
    public_key: JByteArray<'local>,
) -> jboolean {
    verify(env, signature, message, public_key)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_sign<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    public_key: JByteArray<'local>,
    secret: JByteArray<'local>,
    message: JByteArray<'local>,
) -> JByteArray<'local> {
    sign(env, public_key, secret, message)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_deriveKeypairHard<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_keypair_hard(env, keypair, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_deriveKeypairSoft<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    keypair: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_keypair_soft(env, keypair, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_derivePublicSoft<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    public_key: JByteArray<'local>,
    chain_code: JByteArray<'local>,
) -> JByteArray<'local> {
    derive_public_soft(env, public_key, chain_code)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_keypairFromSeed<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    seed: JByteArray<'local>,
) -> JByteArray<'local> {
    keypair_from_seed(env, seed)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_toEd25519Bytes<'local>(
    env: EnvUnowned<'local>,
    _class: jclass,
    secret: JByteArray<'local>,
) -> JByteArray<'local> {
    to_ed25519_bytes(env, secret)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_fromEd25519Bytes<
    'local,
>(
    env: EnvUnowned<'local>,
    _class: jclass,
    expanded_ed_secret: JByteArray<'local>,
) -> JByteArray<'local> {
    from_ed25519_bytes(env, expanded_ed_secret)
}
