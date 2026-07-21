package com.example.data.cos

import com.example.core.common.AppError
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.IOException

data class CosCredential(
    val appId: String? = null,
    val tmpSecretId: String,
    val tmpSecretKey: String,
    val token: String,
    val expiration: Long,
    val startTime: Long,
    val bucket: String,
    val region: String,
    val path: String,
    val cdnHost: String? = null
)

class CosUploadClient(private val okHttpClient: OkHttpClient = OkHttpClient()) {

    /**
     * Uploads image bytes to Tencent COS using PUT request.
     */
    @Throws(AppError::class)
    fun upload(
        step: Int,
        credential: CosCredential,
        imageBytes: ByteArray,
        contentType: String,
        onProgress: (Float) -> Unit
    ) {
        val host = "${credential.bucket}.cos.${credential.region}.myqcloud.com"
        val url = "https://$host${if (credential.path.startsWith("/")) credential.path else "/${credential.path}"}"

        val signature = try {
            CosSigner.generatePutSignature(
                tmpSecretId = credential.tmpSecretId,
                tmpSecretKey = credential.tmpSecretKey,
                startTime = credential.startTime,
                expiration = credential.expiration,
                bucket = credential.bucket,
                region = credential.region,
                path = credential.path
            )
        } catch (e: Exception) {
            throw AppError(
                step = step,
                title = "Lỗi chữ ký COS",
                message = "Không thể tạo chữ ký bảo mật Tencent COS: ${e.message}",
                sanitizedDetails = e.stackTraceToString()
            )
        }

        val mediaType = contentType.toMediaTypeOrNull()
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mediaType
            override fun contentLength(): Long = imageBytes.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                val bufferSize = 2048
                var uploaded = 0L
                val total = imageBytes.size.toLong()

                var offset = 0
                while (offset < imageBytes.size) {
                    val len = Math.min(bufferSize, imageBytes.size - offset)
                    sink.write(imageBytes, offset, len)
                    offset += len
                    uploaded += len
                    onProgress(uploaded.toFloat() / total)
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Host", host)
            .header("Authorization", signature)
            .header("x-cos-security-token", credential.token)
            // Prevent overwriting if not allowed or keep standard behavior
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    throw AppError(
                        step = step,
                        title = "Lỗi upload Tencent COS",
                        message = "Tencent COS trả về lỗi HTTP ${response.code}",
                        httpStatus = response.code,
                        sanitizedDetails = "Body: $bodyString",
                        retryable = true
                    )
                }
            }
        } catch (e: IOException) {
            throw AppError(
                step = step,
                title = "Lỗi kết nối upload COS",
                message = "Không thể kết nối đến máy chủ Tencent COS: ${e.message}",
                retryable = true,
                sanitizedDetails = e.stackTraceToString()
            )
        }
    }
}
