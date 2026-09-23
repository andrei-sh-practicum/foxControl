package com.andrew.foxcontrol.core.security

/**
 * Encrypts secrets stored in the DB (the SMTP app password, bugs_plan.md B-15).
 * Encrypted values carry a prefix so legacy plain-text values can be recognized and migrated.
 */
interface SecretCipher {
    fun isEncrypted(stored: String): Boolean
    fun encrypt(plain: String): String
    /** @throws Exception if the value can't be decrypted (e.g. the key is gone). */
    fun decrypt(stored: String): String
}
