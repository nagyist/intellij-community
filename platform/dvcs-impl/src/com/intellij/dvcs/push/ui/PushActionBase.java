// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Any OK-action in the push dialog must inherit from this base class.
 */
@ApiStatus.Internal
public abstract class PushActionBase extends DumbAwareAction {

  public PushActionBase(@NotNull @NlsActions.ActionText String actionName) {
    super(actionName);
  }

  protected PushActionBase() {
    setEnabledInModalContext(true);
  }

  protected abstract boolean isEnabled(@NotNull VcsPushUi dialog);

  protected @Nls @NotNull String getText(@NotNull VcsPushUi dialog, boolean enabled) {
    return Objects.requireNonNull(getTemplatePresentation().getTextWithMnemonic());
  }

  protected abstract @Nls @Nullable String getDescription(@NotNull VcsPushUi dialog, boolean enabled);

  protected abstract void actionPerformed(@NotNull Project project, @NotNull VcsPushUi dialog);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsPushUi ui = e.getData(VcsPushUi.VCS_PUSH_DIALOG);
    if (ui == null) return;
    actionPerformed(project, ui);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    VcsPushUi dialog = e.getData(VcsPushUi.VCS_PUSH_DIALOG);
    if (dialog == null || e.getProject() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);

    boolean enabled = isEnabled(dialog);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText(getText(dialog, enabled));
    String description = getDescription(dialog, enabled);
    e.getPresentation().setDescription(description);
    e.getPresentation().putClientProperty(ActionUtil.TOOLTIP_TEXT, description);
  }
}
