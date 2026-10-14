package com.rex.api.pub.skeleton.persistence.jpa.entity.main

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "test")
class TestJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json", nullable = false, columnDefinition = "json")
    var json: List<Map<String, String>>,
)