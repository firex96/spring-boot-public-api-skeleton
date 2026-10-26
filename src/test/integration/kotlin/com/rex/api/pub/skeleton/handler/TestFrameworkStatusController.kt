package com.rex.api.pub.skeleton.handler

import org.springframework.beans.ConversionNotSupportedException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException

/** `FrameworkStatusResponseIntegrationTest` 가 쓰는 컨트롤러 — 전용 핸들러가 없는 Spring 예외를 일으킨다. */
@RestController
@RequestMapping("/test/framework-status")
class TestFrameworkStatusController {
    @GetMapping("/get-only")
    fun getOnly(): String = "ok"

    @PostMapping("/json-only", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun jsonOnly(
        @RequestBody body: Map<String, Any>,
    ): Map<String, Any> = body

    @GetMapping("/text-only", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun textOnly(): String = "ok"

    @GetMapping("/upload-too-large")
    fun uploadTooLarge(): String = throw MaxUploadSizeExceededException(1)

    @GetMapping("/async-timeout")
    fun asyncTimeout(): String = throw AsyncRequestTimeoutException()

    @GetMapping("/teapot")
    fun teapot(): String = throw ResponseStatusException(HttpStatus.I_AM_A_TEAPOT)

    @GetMapping("/gateway-timeout")
    fun gatewayTimeout(): String = throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)

    @GetMapping("/conversion-not-supported")
    fun conversionNotSupported(): String = throw ConversionNotSupportedException("value", Long::class.java, null)
}