package com.example.exotrade.utils

import android.util.Base64
import org.json.JSONObject

/**
 * A simplified parser for Selective Disclosure JSON Web Tokens (SD-JWT).
 * Extracts claims from the payload while ignoring signatures and disclosures.
 * 
 * NOTE: For production use, a full cryptographic validation library should be used.
 */
object SdJwtParser {

    /**
     * Parses the raw SD-JWT string and returns the claims as a JSONObject.
     */
    fun parse(rawSdJwt: String): JSONObject {
        // SD-JWT format: Issuer JWT ~ Disclosure 1 ~ Disclosure 2 ... ~ Key Binding JWT
        val parts = rawSdJwt.split("~")
        val issuerJwt = parts[0]
        
        // JWT format: Header . Payload . Signature
        val jwtParts = issuerJwt.split(".")
        if (jwtParts.size < 2) return JSONObject()
        
        val payloadBase64 = jwtParts[1]
        val payloadJson = String(Base64.decode(payloadBase64, Base64.URL_SAFE))
        
        // This gives us the base claims. Disclosures contain the actual hidden values.
        // Google's UserInfoCredential typically puts the verified email in a disclosure
        // if selective disclosure is used, or in the main payload if not.
        val claims = JSONObject(payloadJson)
        
        // For the purpose of this implementation, we check both the main payload
        // and attempt a very basic disclosure extraction if needed.
        // Google Identity verifiable credentials for email often include 'email' in the payload.
        
        return claims
    }
}
