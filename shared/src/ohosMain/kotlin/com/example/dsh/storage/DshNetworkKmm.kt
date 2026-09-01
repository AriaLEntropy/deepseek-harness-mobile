package com.example.dsh.storage

import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.export.VBTransportContentType
import com.tencent.kmm.network.export.VBTransportInitConfig
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportResultCode
import com.tencent.kmm.network.service.VBTransportInitHelper
import com.tencent.kmm.network.service.VBTransportService
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.posix.strdup
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

private val kmmLog = object : IVBPBLog {
    override fun d(tag: String?, content: String?) {
        println("[DshKmmNet][D] [${tag.orEmpty()}] ${content.orEmpty()}")
    }

    override fun i(tag: String?, content: String?) {
        println("[DshKmmNet][I] [${tag.orEmpty()}] ${content.orEmpty()}")
    }

    override fun e(tag: String?, content: String?, throwable: Throwable?) {
        println("[DshKmmNet][E] [${tag.orEmpty()}] ${content.orEmpty()} ${throwable?.message.orEmpty()}")
    }
}

@Volatile
private var kmmReady = false

private fun ensureNetworkKmm() {
    if (kmmReady) {
        return
    }
    val config = VBTransportInitConfig()
    config.logImpl = kmmLog
    VBTransportInitHelper.init(config)
    kmmReady = true
    println("[DshKmmNet] init ok")
}

internal fun postJsonOverNetworkKmm(url: String, body: String, token: String): String {
    ensureNetworkKmm()
    println("[DshKmmNet] POST begin url=$url bodyLen=${body.length} auth=${if (token.isNotEmpty()) "yes" else "no"} curl=true")
    return runBlocking {
        suspendCancellableCoroutine { cont ->
            val request = VBTransportPostRequest()
            request.url = url
            request.data = body
            request.useCurl = true
            request.totalTimeout = 20_000
            request.logTag = "DshRelay"
            request.header["Content-Type"] = VBTransportContentType.JSON.toString()
            request.header["Accept"] = "application/json, */*"
            if (token.isNotEmpty()) {
                request.header["Authorization"] = "Bearer $token"
            }
            VBTransportService.sendPostRequest(request) { response ->
                val payload = response.data
                val text = when (payload) {
                    is String -> payload
                    is ByteArray -> payload.decodeToString()
                    null -> ""
                    else -> payload.toString()
                }
                val httpCode = httpStatusOf(response.header, response.errorCode)
                println(
                    "[DshKmmNet] POST done errorCode=${response.errorCode} httpCode=$httpCode " +
                        "ip=${response.serverIP}:${response.serverPort} bodyLen=${text.length} " +
                        "message=${response.errorMessage}",
                )
                if (cont.isActive) {
                    cont.resume(
                        "${response.errorCode}\n${response.errorMessage.replace('\n', ' ')}\n$httpCode\n$text",
                    )
                }
            }
        }
    }
}

private fun httpStatusOf(header: Map<String, List<String>>, errorCode: Int): Int {
    for (key in header.keys) {
        val lower = key.lowercase()
        if (lower == "status" || lower == ":status" || lower == "http-status") {
            val raw = header[key]?.firstOrNull().orEmpty()
            val parsed = raw.trim().substringBefore(' ').toIntOrNull()
            if (parsed != null && parsed > 0) {
                return parsed
            }
        }
    }
    return if (errorCode == VBTransportResultCode.CODE_OK) 200 else 0
}

@OptIn(ExperimentalNativeApi::class)
@CName("dsh_http_init")
fun dshHttpInit() {
    ensureNetworkKmm()
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("dsh_http_post_json")
fun dshHttpPostJson(
    url: CPointer<ByteVar>?,
    body: CPointer<ByteVar>?,
    token: CPointer<ByteVar>?,
): CPointer<ByteVar>? {
    return try {
        strdup(
            postJsonOverNetworkKmm(
                url?.toKString().orEmpty(),
                body?.toKString().orEmpty(),
                token?.toKString().orEmpty(),
            ),
        )
    } catch (error: Throwable) {
        println("[DshKmmNet] POST throw ${error.message}")
        strdup("-1\n${(error.message ?: "networkkmm failed").replace('\n', ' ')}\n0\n")
    }
}
