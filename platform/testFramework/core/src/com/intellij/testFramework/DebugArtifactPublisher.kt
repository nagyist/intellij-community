// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalPathApi::class)

package com.intellij.testFramework

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.UniqueNameGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

/**
 * Stores files generated by a test in a temporary directory to allow publishing them as build artifacts if the test fails.
 * This is an internal class, use [TestLoggerFactory.publishArtifactIfTestFails] in tests.
 */
internal class DebugArtifactPublisher(artifactStoragePath: Path) {
  private val testArtifactStoragePath = createStorageForTest(artifactStoragePath)
  private val nameGenerator = UniqueNameGenerator()
  private val count = AtomicInteger()
  
  fun storeArtifact(artifactPath: Path, artifactName: String) {
    require(Files.exists(artifactPath)) { "File '$artifactPath' doesn't exist" }
    require(artifactName.isNotEmpty()) { "Artifact name must not be empty" }

    val uniqueName = synchronized(nameGenerator) { nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(artifactName)) }
    count.incrementAndGet()
    testArtifactStoragePath.createDirectories()
    artifactPath.copyToRecursively(testArtifactStoragePath.resolve(uniqueName), followLinks = false, overwrite = false)
  }

  fun publishArtifacts(testName: String) {
    if (count.get() == 0) return
    val fullName = "debug-artifacts/${FileUtil.sanitizeFileName(testName)}"
    logger.debug("Publishing ${count.get()} ${StringUtil.pluralize("artifact", count.get())} from '$testArtifactStoragePath' as $fullName")
    TeamCityLogger.publishArtifact(testArtifactStoragePath, fullName)
  }
  
  fun cleanup() {
    if (count.get() > 0) {
      testArtifactStoragePath.deleteRecursively()
    }
  }
}

private val logger = logger<DebugArtifactPublisher>()
private var testCount = -1

@Synchronized
private fun createStorageForTest(artifactStoragePath: Path): Path {
  if (testCount == -1) {
    //remove artifacts from previous test runs
    artifactStoragePath.deleteRecursively()
  }
  return artifactStoragePath.resolve("test-${++testCount}")
}