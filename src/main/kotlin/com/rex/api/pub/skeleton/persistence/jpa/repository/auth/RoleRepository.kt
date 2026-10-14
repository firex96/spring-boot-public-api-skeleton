package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RoleRepository : JpaRepository<RoleEntity, Long> {
    fun findByCode(code: String): Optional<RoleEntity>

    fun findByIsDefaultTrue(): Optional<RoleEntity>

    fun findByIsSystemTrue(): List<RoleEntity>

    fun existsByCode(code: String): Boolean
}