package com.rex.api.pub.skeleton.config

import org.mapstruct.MapperConfig
import org.mapstruct.MappingConstants
import org.mapstruct.NullValueMappingStrategy
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

/**
 * MapStruct 공통 설정 — 모든 Mapper 가 `@Mapper(config = MapstructConfiguration::class)` 로 참조한다.
 *
 * unmappedTargetPolicy = ERROR: 소스에 없는 타깃은 @Mapping(target, ignore = true) 를 명시해야 컴파일된다.
 * non-null 타깃은 ignore 하지 않고 source·expression·constant 로 명시한다 — 생성자에 null 이 들어가면 런타임 NPE.
 * 정본: .claude/rules/conventions.md 「타입 선언」.
 */
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    typeConversionPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL,
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
)
interface MapstructConfiguration