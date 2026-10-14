package com.rex.api.pub.skeleton.controller.demo

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 관리자 데모 페이지 Controller
 */
@Controller
@RequestMapping("/demo/admin")
class DemoAdminController {
    @GetMapping("")
    fun index(): String = "demo/admin/index"

    @GetMapping("/users")
    fun users(): String = "demo/admin/users"

    @GetMapping("/groups")
    fun groups(): String = "demo/admin/groups"

    @GetMapping("/permissions")
    fun permissions(): String = "demo/admin/permissions"

    @GetMapping("/audit-logs")
    fun auditLogs(): String = "demo/admin/audit-logs"

    @GetMapping("/oauth2-clients")
    fun oauth2Clients(): String = "demo/admin/oauth2-clients"

    @GetMapping("/security")
    fun security(): String = "demo/admin/security"
}