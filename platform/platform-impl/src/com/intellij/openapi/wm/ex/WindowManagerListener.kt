// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WindowManagerListener {
  companion object {
    val TOPIC: Topic<WindowManagerListener> = Topic(WindowManagerListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun onFramesChanged()
}
