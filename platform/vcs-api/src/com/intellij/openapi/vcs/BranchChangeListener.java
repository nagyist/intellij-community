/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @see git4idea.repo.GitRepositoryStateChangeListener
 */
public interface BranchChangeListener extends EventListener {

  Topic<BranchChangeListener> VCS_BRANCH_CHANGED = Topic.create("VCS branch changed", BranchChangeListener.class);

  @RequiresEdt
  void branchWillChange(@NotNull String branchName);

  @RequiresEdt
  void branchHasChanged(@NotNull String branchName);
}
