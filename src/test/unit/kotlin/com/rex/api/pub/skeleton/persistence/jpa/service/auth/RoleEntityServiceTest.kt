package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.RoleRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.Optional

/** 그룹(Role) EntityService — create 의 isSystem 고정, update 의 null 유지, clearDefault 의 기본 그룹 해제. 매퍼는 실제 MapStruct 구현체. */
class RoleEntityServiceTest : DescribeSpec({

    val roleRepository = mockk<RoleRepository>()
    val mapper = Mappers.getMapper(AccessControlMapper::class.java)
    val service = RoleEntityService(roleRepository, mapper)

    val past = LocalDateTime.of(2026, 1, 1, 0, 0)

    fun role(id: Long = 10L, isDefault: Boolean = true) =
        RoleEntity(id = id, code = "STAFF", name = "직원", description = "기본 직원 그룹", isSystem = false, isDefault = isDefault, createdAt = past, updatedAt = past)

    beforeTest { clearMocks(roleRepository) }

    describe("create") {
        it("isSystem 은 항상 false 로 저장하고 isDefault 는 입력을 따른다 — DTO 의 is-prefix 필드까지 실제 매퍼로 확인") {
            val saved = slot<RoleEntity>()
            every { roleRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.create(code = "GUEST", name = "게스트", description = null, isDefault = true)

            saved.captured.isSystem shouldBe false
            saved.captured.isDefault shouldBe true
            result.code shouldBe "GUEST"
            result.isSystem shouldBe false
            result.isDefault shouldBe true
        }
    }

    describe("update") {
        it("description·isDefault 가 null 이면 기존 값을 유지하고 name 과 updatedAt 만 바꾼다") {
            val existing = role()
            every { roleRepository.findById(10L) } returns Optional.of(existing)
            every { roleRepository.save(existing) } returns existing

            val result = service.update(10L, name = "임직원", description = null, isDefault = null)

            existing.name shouldBe "임직원"
            existing.description shouldBe "기본 직원 그룹"
            existing.isDefault shouldBe true
            (existing.updatedAt > past) shouldBe true
            result.name shouldBe "임직원"
            result.isDefault shouldBe true
        }

        it("값이 오면 description·isDefault 를 덮어쓴다") {
            val existing = role()
            every { roleRepository.findById(10L) } returns Optional.of(existing)
            every { roleRepository.save(existing) } returns existing

            service.update(10L, name = "직원", description = "설명 변경", isDefault = false)

            existing.description shouldBe "설명 변경"
            existing.isDefault shouldBe false
        }

        it("없는 그룹이면 NoSuchElementException — 존재 검사는 호출자 몫이라는 계약") {
            every { roleRepository.findById(404L) } returns Optional.empty()

            shouldThrow<NoSuchElementException> { service.update(404L, name = "x", description = null, isDefault = null) }
            verify(exactly = 0) { roleRepository.save(any()) }
        }
    }

    describe("clearDefault") {
        it("기본 그룹 표시를 내리고 저장한다") {
            val existing = role(isDefault = true)
            every { roleRepository.findById(10L) } returns Optional.of(existing)
            every { roleRepository.save(existing) } returns existing

            service.clearDefault(10L)

            existing.isDefault shouldBe false
            verify(exactly = 1) { roleRepository.save(existing) }
        }
    }

    describe("getDefault / getCodesByIds") {
        it("기본 그룹을 DTO 로 돌려주고, 빈 id 목록은 리포지토리를 부르지 않는다") {
            every { roleRepository.findByIsDefaultTrue() } returns Optional.of(role())

            service.getDefault()?.code shouldBe "STAFF"
            service.getCodesByIds(emptyList()) shouldBe emptyList()
            verify(exactly = 0) { roleRepository.findAllById(any()) }
        }
    }
})