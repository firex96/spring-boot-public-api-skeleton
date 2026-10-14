package com.rex.api.pub.skeleton.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kotlin extension function for creating SLF4J loggers.
 *
 * Usage:
 * ```
 * class MyService {
 *     private val logger = logger()
 *
 *     fun doSomething() {
 *         logger.info("Doing something")
 *     }
 * }
 * ```
 *
 * For companion objects:
 * ```
 * class MyService {
 *     companion object {
 *         private val logger = logger()
 *     }
 * }
 * ```
 */
inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)