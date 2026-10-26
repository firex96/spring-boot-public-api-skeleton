package com.rex.api.pub.skeleton.client.social

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/** 어떤 요청에도 [json] 본문의 200 응답을 돌려주는 `WebClient` — 서버 없이 소셜 클라이언트의 응답 처리만 본다. */
fun stubWebClient(json: String): WebClient =
    WebClient
        .builder()
        .exchangeFunction {
            Mono.just(
                ClientResponse
                    .create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json)
                    .build(),
            )
        }.build()