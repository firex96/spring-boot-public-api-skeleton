package com.rex.api.pub.skeleton.persistence.jdbc.service.main

import com.rex.api.pub.skeleton.annotation.CacheableNullSafe
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true, transactionManager = "mainTransactionManager")
@Service
class ProductJdbcEntityService(
    @param:Qualifier("mainNamedParameterJdbcOperations")
    private val namedParameterJdbcOperations: NamedParameterJdbcOperations,
) {
    // #region getAllProductIdByCategoryId
    @CacheableNullSafe(value = ["ProductIdList"], key = "#categoryId")
    fun getAllProductIdByCategoryId(categoryId: Long): List<Long> {
        val namedParameters: SqlParameterSource = MapSqlParameterSource(
            "CATEGORY_ID",
            categoryId,
        )

        return namedParameterJdbcOperations.queryForList(
            """
SELECT  prd.product_id
FROM    product AS prd
WHERE   prd.category_id = :CATEGORY_ID
AND     prd.status = 'OnSale'
;
            """.trimIndent(),
            namedParameters,
            Long::class.javaObjectType,
        ).filterNotNull()
    }
    // #endregion getAllProductIdByCategoryId
}