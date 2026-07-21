package com.example.data.cos

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CosSigner {

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha1(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun sha1(data: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return bytes.toHexString()
    }

    /**
     * Generates a Tencent COS V5 PUT signature.
     *
     * @param tmpSecretId Temporary Secret ID
     * @param tmpSecretKey Temporary Secret Key
     * @param startTime Unix timestamp in seconds for credential start time
     * @param expiration Unix timestamp in seconds for credential expiration
     * @param bucket COS Bucket name
     * @param region COS Region
     * @param path File path in COS (e.g. "/poster/12345.png")
     */
    fun generatePutSignature(
        tmpSecretId: String,
        tmpSecretKey: String,
        startTime: Long,
        expiration: Long,
        bucket: String,
        region: String,
        path: String
    ): String {
        val keyTime = "$startTime;$expiration"
        val signTime = "$startTime;$expiration"
        
        // 1. Generate SignKey
        val signKey = hmacSha1(tmpSecretKey.toByteArray(Charsets.UTF_8), keyTime)
        
        // 2. Generate FormatString
        val formatMethod = "put"
        val formatUri = if (path.startsWith("/")) path else "/$path"
        val formatParameters = ""
        
        // We will sign the Host header.
        val hostValue = "$bucket.cos.$region.myqcloud.com"
        val formatHeaders = "host=$hostValue\n"
        val headerList = "host"
        val parameterList = ""
        
        val formatString = "$formatMethod\n$formatUri\n$formatParameters\n$formatHeaders"
        
        // 3. Generate StringToSign
        val formatStringSha1 = sha1(formatString)
        val stringToSign = "sha1\n$signTime\n$formatStringSha1\n"
        
        // 4. Generate Signature
        val signatureBytes = hmacSha1(signKey, stringToSign)
        val signature = signatureBytes.toHexString()
        
        // 5. Construct Authorization string
        return "q-sign-algorithm=sha1" +
                "&q-ak=$tmpSecretId" +
                "&q-sign-time=$signTime" +
                "&q-key-time=$keyTime" +
                "&q-header-list=$headerList" +
                "&q-url-param-list=$parameterList" +
                "&q-signature=$signature"
    }
}
