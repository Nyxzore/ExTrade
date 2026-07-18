package com.example.exotrade.utils

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.KeyPair
import com.goterl.lazysodium.utils.Key

class EncryptionManager {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    data class EncryptionResult(val ciphertext: String, val nonce: String)

    fun generateIdentityKeys(): Pair<String, ByteArray> {
        val keyPair = sodium.cryptoBoxKeypair()
        val publicKeyBase64 = Base64.encodeToString(keyPair.publicKey.asBytes, Base64.NO_WRAP)
        return Pair(publicKeyBase64, keyPair.secretKey.asBytes)
    }

    fun generateSalt(): ByteArray {
        return sodium.randomBytesBuf(PwHash.ARGON2ID_SALTBYTES)
    }

    fun generateNonce(): ByteArray {
        return sodium.randomBytesBuf(SecretBox.NONCEBYTES) // Same for Box.NONCEBYTES (24 bytes)
    }

    fun deriveBackupKey(password: String, salt: ByteArray): ByteArray {
        val backupKey = ByteArray(SecretBox.KEYBYTES)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        
        sodium.cryptoPwHash(
            backupKey,
            backupKey.size,
            passwordBytes,
            passwordBytes.size,
            salt,
            PwHash.ARGON2ID_OPSLIMIT_INTERACTIVE,
            PwHash.MEMLIMIT_INTERACTIVE,
            PwHash.Alg.PWHASH_ALG_ARGON2ID13
        )
        return backupKey
    }
    
    fun encryptPrivateKey(privateKey: ByteArray, backupKey: ByteArray, nonce: ByteArray): String {
        val cipherText = ByteArray(privateKey.size + SecretBox.MACBYTES)
        sodium.cryptoSecretBoxEasy(cipherText, privateKey, privateKey.size.toLong(), nonce, backupKey)
        return Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }
    
    fun encryptMessage(message: String, recipientPublicKey: String, myPrivateKey: ByteArray): EncryptionResult {
        val recipientPublicKeyBytes = Base64.decode(recipientPublicKey, Base64.NO_WRAP)
        val nonce = generateNonce()
        
        val ciphertext = sodium.cryptoBoxEasy(
            message,
            nonce,
            KeyPair(
                Key.fromBytes(recipientPublicKeyBytes),
                Key.fromBytes(myPrivateKey)
            )
        )
        
        return EncryptionResult(ciphertext, Base64.encodeToString(nonce, Base64.NO_WRAP))
    }
    
    fun decryptMessage(encryptedMessage: String, nonce: String, senderPublicKey: String, myPrivateKey: ByteArray): String? {
        try {
            val senderPublicKeyBytes = Base64.decode(senderPublicKey, Base64.NO_WRAP)
            val nonceBytes = Base64.decode(nonce, Base64.NO_WRAP)
            
            return sodium.cryptoBoxOpenEasy(
                encryptedMessage,
                nonceBytes,
                KeyPair(
                    Key.fromBytes(senderPublicKeyBytes),
                    Key.fromBytes(myPrivateKey)
                )
            )
        } catch (e: Exception) {
            return null
        }
    }
}
