// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service
internal class EelLocalApiService(val scope: CoroutineScope)