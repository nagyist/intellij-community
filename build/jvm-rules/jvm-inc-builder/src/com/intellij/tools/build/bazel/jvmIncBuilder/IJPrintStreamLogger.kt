package com.intellij.tools.build.bazel.jvmIncBuilder

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.log4j.Level
import java.io.PrintStream

@Suppress("DEPRECATION")
internal class IJPrintStreamLogger(category: String, private val stream: PrintStream, private val span: Span) : Logger() {
  private val maxLevel = LogLevel.INFO
  private val sharedAttributes = Attributes.of(AttributeKey.stringKey("category"), category)

  override fun isDebugEnabled(): Boolean = maxLevel >= LogLevel.DEBUG

  override fun isTraceEnabled(): Boolean = maxLevel >= LogLevel.TRACE

  override fun trace(message: String) {
    addEvent(LogLevel.TRACE, message, null)
  }

  override fun trace(t: Throwable?) {
    addEvent(LogLevel.TRACE, "", t)
  }

  override fun debug(message: String, t: Throwable?) {
    addEvent(LogLevel.DEBUG, message, t)
  }

  private fun addEvent(level: LogLevel, message: String, t: Throwable?) {
    if (level > maxLevel) {
      return
    }

    span.addEvent(message, sharedAttributes)
    stream.append(message)
    t?.let {
      span.recordException(it, sharedAttributes)
      it.printStackTrace(stream)
    }
  }

  override fun info(message: String?, t: Throwable?) {
    addEvent(LogLevel.INFO, message ?: "", t)
  }

  override fun warn(message: String, t: Throwable?) {
    addEvent(LogLevel.WARNING, message, t)
  }

  override fun error(message: String?, t: Throwable?, vararg details: String) {
    addEvent(
      level = LogLevel.ERROR,
      message = (message ?: "") + DefaultLogger.detailsToString(*details) + DefaultLogger.attachmentsToString(t),
      t = t,
    )
  }

  override fun setLevel(level: LogLevel) {
    throw UnsupportedOperationException()
  }

  override fun setLevel(p0: Level) {
    throw UnsupportedOperationException()
  }
}