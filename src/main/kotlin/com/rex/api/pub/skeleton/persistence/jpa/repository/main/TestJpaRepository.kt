package com.rex.api.pub.skeleton.persistence.jpa.repository.main

import com.rex.api.pub.skeleton.persistence.jpa.entity.main.TestJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TestJpaRepository : JpaRepository<TestJpaEntity, Long>