package org.luma.lumacompanion3

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

class RedactingInterceptor : Interceptor {
    // crude list; expand later or plug in Bloom filter
    private val phiRegex =
        "(\\d{3}-\\d{2}-\\d{4})|\\b(\\d{5})\\b|\\b([A-Z]{1}\\d{2}(?:\\.\\d+)?)\\b".toRegex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        
        // If there's no body, just proceed with the original request
        val originalBody = original.body ?: return chain.proceed(original)
        
        val buffer = Buffer()
        originalBody.writeTo(buffer)
        val bodyStr = buffer.readUtf8()
        
        // Redact sensitive information
        val redacted = bodyStr.replace(phiRegex, "***")
        
        // If nothing was redacted, proceed with original request to avoid overhead
        if (redacted == bodyStr) {
            return chain.proceed(original)
        }
        
        // Create new request with redacted body
        val newBody = redacted.toRequestBody(originalBody.contentType())
        val newReq = original.newBuilder()
            .method(original.method, newBody)
            .build()
            
        return chain.proceed(newReq)
    }
} 