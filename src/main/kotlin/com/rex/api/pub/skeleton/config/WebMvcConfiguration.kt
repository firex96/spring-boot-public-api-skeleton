package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC 설정.
 *
 * - Enum 대소문자 무시 컨버터 등록
 */
@Configuration
class WebMvcConfiguration : WebMvcConfigurer {

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(StringToSocialProviderConverter())
    }

    /**
     * String을 SocialProvider enum으로 변환하는 컨버터.
     * 대소문자를 무시합니다.
     */
    class StringToSocialProviderConverter : Converter<String, SocialProvider> {
        override fun convert(source: String): SocialProvider =
            SocialProvider.entries.find { it.name.equals(source, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 소셜 프로바이더입니다: $source")
    }
}