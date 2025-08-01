package org.jetbrains.plugins.textmate.regex

import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.regex.MatchData.Companion.NOT_MATCHED

interface RegexFacade: AutoCloseable {
  fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData

  fun match(
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    checkCancelledCallback: Runnable?
  ): MatchData
}

interface RegexFactory {
  fun regex(pattern: CharSequence): RegexFacade

  fun string(string: CharSequence): TextMateString
}

object NotMatchingRegexFacade : RegexFacade {
  override fun match(
    string: TextMateString,
    checkCancelledCallback: Runnable?
  ): MatchData {
    return NOT_MATCHED
  }

  override fun match(
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    checkCancelledCallback: Runnable?
  ): MatchData {
    return NOT_MATCHED
  }

  override fun close() {
  }
}
