package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserSocialAccountRepository
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 기존 소셜 계정 연동 데이터 조회 테스트.
 *
 * dev 데이터베이스에 이미 저장된 소셜 계정 정보를 조회하여
 * provider-id를 확인합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local", "e2e")
@Tags("discover-provider-ids", "manual")
class SocialAccountQueryTest(
    private val userSocialAccountRepository: UserSocialAccountRepository,
) : DescribeSpec({

    describe("기존 소셜 계정 데이터 조회") {
        it("데이터베이스에 저장된 모든 소셜 계정 정보를 출력한다") {
            val accounts = userSocialAccountRepository.findAll()

            println("\n" + "=".repeat(80))
            println("데이터베이스에 저장된 소셜 계정 정보")
            println("=".repeat(80))

            if (accounts.isEmpty()) {
                println("저장된 소셜 계정이 없습니다.")
            } else {
                accounts.forEach { account ->
                    println(
                        """
                        |
                        |[${account.provider}]
                        |  - provider-id: ${account.providerId}
                        |  - email: ${account.email}
                        |  - name: ${account.name}
                        |  - user_id: ${account.userId}
                        |  - created_at: ${account.createdAt}
                        """.trimMargin(),
                    )
                }

                println("\n" + "-".repeat(80))
                println("application-e2e.yml에 추가할 설정:")
                println("-".repeat(80))

                accounts.groupBy { it.provider }.forEach { (provider, providerAccounts) ->
                    val account = providerAccounts.first()
                    println(
                        """
                        |
                        |  ${provider.lowercase()}:
                        |    provider-id: "${account.providerId}"
                        """.trimMargin(),
                    )
                }
            }

            println("\n" + "=".repeat(80) + "\n")
        }
    }
})