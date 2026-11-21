"""Ed25519 (RFC 8032, PureEdDSA) for the remote-config signing tool, using only the Python standard library.

This follows the reference implementation in RFC 8032, section 6. It is used offline, on the release owner's machine,
to sign the remote-config manifest; the app verifies with BouncyCastle. Python integers are not constant-time, so the
key must only be used where no other user can observe the signing process.
"""
import hashlib

_P = 2 ** 255 - 19
_L = 2 ** 252 + 27742317777372353535851937790883648493
_D = -121665 * pow(121666, _P - 2, _P) % _P
_SQRT_M1 = pow(2, (_P - 1) // 4, _P)

SEED_SIZE = 32
PUBLIC_KEY_SIZE = 32
SIGNATURE_SIZE = 64


def _inverse(x):
    return pow(x, _P - 2, _P)


def _add(p1, p2):
    a = (p1[1] - p1[0]) * (p2[1] - p2[0]) % _P
    b = (p1[1] + p1[0]) * (p2[1] + p2[0]) % _P
    c = 2 * p1[3] * p2[3] * _D % _P
    d = 2 * p1[2] * p2[2] % _P
    e, f, g, h = b - a, d - c, d + c, b + a
    return e * f % _P, g * h % _P, f * g % _P, e * h % _P


def _multiply(scalar, point):
    result = (0, 1, 1, 0)
    while scalar > 0:
        if scalar & 1:
            result = _add(result, point)
        point = _add(point, point)
        scalar >>= 1
    return result


def _equal(p1, p2):
    if (p1[0] * p2[2] - p2[0] * p1[2]) % _P != 0:
        return False
    return (p1[1] * p2[2] - p2[1] * p1[2]) % _P == 0


def _recover_x(y, sign):
    if y >= _P:
        return None
    x2 = (y * y - 1) * _inverse(_D * y * y + 1)
    if x2 == 0:
        return None if sign else 0
    x = pow(x2, (_P + 3) // 8, _P)
    if (x * x - x2) % _P != 0:
        x = x * _SQRT_M1 % _P
    if (x * x - x2) % _P != 0:
        return None
    if (x & 1) != sign:
        x = _P - x
    return x


_G_Y = 4 * _inverse(5) % _P
_G_X = _recover_x(_G_Y, 0)
_G = (_G_X, _G_Y, 1, _G_X * _G_Y % _P)


def _compress(point):
    z_inverse = _inverse(point[2])
    x = point[0] * z_inverse % _P
    y = point[1] * z_inverse % _P
    return int.to_bytes(y | ((x & 1) << 255), 32, 'little')


def _decompress(data):
    if len(data) != 32:
        return None
    y = int.from_bytes(data, 'little')
    sign = y >> 255
    y &= (1 << 255) - 1
    x = _recover_x(y, sign)
    if x is None:
        return None
    return x, y, 1, x * y % _P


def _expand(seed):
    if len(seed) != SEED_SIZE:
        raise ValueError('An Ed25519 seed is 32 bytes')
    digest = hashlib.sha512(seed).digest()
    scalar = int.from_bytes(digest[:32], 'little')
    scalar &= (1 << 254) - 8
    scalar |= 1 << 254
    return scalar, digest[32:]


def _hash_mod_l(data):
    return int.from_bytes(hashlib.sha512(data).digest(), 'little') % _L


def public_key(seed):
    scalar, _ = _expand(seed)
    return _compress(_multiply(scalar, _G))


def sign(seed, message):
    scalar, prefix = _expand(seed)
    public = _compress(_multiply(scalar, _G))
    r = _hash_mod_l(prefix + message)
    encoded_r = _compress(_multiply(r, _G))
    h = _hash_mod_l(encoded_r + public + message)
    s = (r + h * scalar) % _L
    return encoded_r + int.to_bytes(s, 32, 'little')


def verify(public, message, signature):
    if len(public) != PUBLIC_KEY_SIZE or len(signature) != SIGNATURE_SIZE:
        return False
    point_a = _decompress(public)
    point_r = _decompress(signature[:32])
    if point_a is None or point_r is None:
        return False
    s = int.from_bytes(signature[32:], 'little')
    if s >= _L:
        return False
    h = _hash_mod_l(signature[:32] + public + message)
    return _equal(_multiply(s, _G), _add(point_r, _multiply(h, point_a)))
