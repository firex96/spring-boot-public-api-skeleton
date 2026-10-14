package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.TestDto
import com.rex.api.pub.skeleton.persistence.jpa.entity.main.TestJpaEntity
import org.mapstruct.Mapper

@Mapper(config = MapstructConfiguration::class)
interface TestMapper {
    fun toDto(entity: TestJpaEntity): TestDto

    fun toDtoList(entityList: List<TestJpaEntity>): List<TestDto>

    fun toEntity(dto: TestDto): TestJpaEntity
}