package com.rex.api.pub.skeleton.controller.demo

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 데모 메인 페이지 Controller
 */
@Controller
@RequestMapping("/demo")
class DemoController {
    @GetMapping("")
    fun index(): String = "demo/index"
}