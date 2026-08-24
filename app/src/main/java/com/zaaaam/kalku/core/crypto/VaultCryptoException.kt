package com.zaaaam.kalku.core.crypto

/** Thrown when vault cryptography fails (wrong key, corrupt data, bad format). */
class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
