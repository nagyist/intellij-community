// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.psi;

import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public interface DebuggerPositionResolver {
  @Nullable
  PsiElement getNearestElementToBreakpoint(@NotNull XDebugSession session);
}
