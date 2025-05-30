/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;
import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.siyeh.ig.psiutils.SwitchUtils.SwitchExhaustivenessState;
import static com.siyeh.ig.psiutils.SwitchUtils.evaluateSwitchCompleteness;

public final class UnnecessaryDefaultInspection extends BaseInspection {

  public boolean onlyReportSwitchExpressions = true;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportSwitchExpressions", InspectionGadgetsBundle.message("unnecessary.default.expressions.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return LocalQuickFix.from(new DeleteDefaultFix());
  }

  public static class DeleteDefaultFix extends PsiUpdateModCommandAction<PsiElement> {
    public DeleteDefaultFix() {
      super(PsiElement.class);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.default.quickfix");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if(element instanceof PsiKeyword) element = element.getParent();
      if (element instanceof PsiSwitchLabelStatementBase label) {
        DeleteSwitchLabelFix.deleteLabel(label);
      }
      else if (element instanceof PsiDefaultCaseLabelElement defaultElement) {
        DeleteSwitchLabelFix.deleteLabelElement(context.project(), defaultElement);
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !onlyReportSwitchExpressions || PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryDefaultVisitor();
  }

  private class UnnecessaryDefaultVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      checkSwitchBlock(expression);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      checkSwitchBlock(statement);
    }

    private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
      final PsiElement defaultStatement = retrieveUnnecessaryDefault(switchBlock);
      if (defaultStatement == null) {
        return;
      }
      PsiSwitchLabeledRuleStatement ruleStatement = null;
      if (defaultStatement instanceof PsiSwitchLabeledRuleStatement) {
        ruleStatement = (PsiSwitchLabeledRuleStatement)defaultStatement;
      }
      else if (defaultStatement instanceof PsiDefaultCaseLabelElement) {
        PsiSwitchLabelStatementBase pDefaultStatement = PsiTreeUtil.getParentOfType(defaultStatement, PsiSwitchLabelStatementBase.class);
        if (pDefaultStatement instanceof PsiSwitchLabeledRuleStatement) {
          ruleStatement = (PsiSwitchLabeledRuleStatement)pDefaultStatement;
        }
      }
      final boolean ruleBasedSwitch = ruleStatement != null;
      final boolean statementSwitch = switchBlock instanceof PsiStatement;
      PsiStatement nextStatement = ruleBasedSwitch
                                   ? ruleStatement.getBody()
                                   : PsiTreeUtil.getNextSiblingOfType(defaultStatement, PsiStatement.class);
      if (statementSwitch && nextStatement instanceof PsiThrowStatement) {
        // consider a single throw statement a guard against future changes that update the code only partially
        return;
      }
      if (isDefaultNeededForInitializationOfVariable(switchBlock)) {
        return;
      }
      while (nextStatement != null) {
        if (statementSwitch && !ControlFlowUtils.statementMayCompleteNormally(nextStatement)) {
          final PsiMethod method =
            PsiTreeUtil.getParentOfType(switchBlock, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
          if (method != null && !PsiTypes.voidType().equals(method.getReturnType()) &&
              !ControlFlowUtils.statementContainsNakedBreak(nextStatement)) {
            final PsiCodeBlock body = method.getBody();
            assert body != null;
            if (ControlFlowUtils.blockCompletesWithStatement(body, (PsiStatement)switchBlock)) {
              return;
            }
          }
          else {
            break;
          }
        }
        if (ruleBasedSwitch) {
          break;
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      final ProblemHighlightType highlightType;
      if (onlyReportSwitchExpressions && statementSwitch) {
        if (!isOnTheFly()) {
          return;
        }
        highlightType = INFORMATION;
      }
      else {
        highlightType = GENERIC_ERROR_OR_WARNING;
      }
      registerError(defaultStatement.getFirstChild(), highlightType);
    }
  }

  private static boolean isDefaultNeededForInitializationOfVariable(PsiSwitchBlock switchBlock) {
    final Collection<PsiReferenceExpression> expressions = PsiTreeUtil.findChildrenOfType(switchBlock, PsiReferenceExpression.class);
    final Set<PsiElement> checked = new HashSet<>();
    for (PsiReferenceExpression expression : expressions) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
        continue;
      }
      if (JavaTokenType.EQ != assignmentExpression.getOperationTokenType()) {
        continue;
      }
      if (!PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), expression, false)) {
        continue;
      }
      final PsiElement target = expression.resolve();
      if (target != null && !checked.add(target)) {
        continue;
      }
      if (target instanceof PsiLocalVariable || target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.FINAL)) {
        final PsiVariable variable = (PsiVariable)target;
        if (variable.getInitializer() != null) {
          continue;
        }
        final PsiElement context = getContext(switchBlock);
        if (context == null) {
          return true;
        }
        try {
          final LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
          final int switchStart = controlFlow.getStartOffset(switchBlock);
          final int switchEnd = controlFlow.getEndOffset(switchBlock);
          final ControlFlow beforeFlow = new ControlFlowSubRange(controlFlow, 0, switchStart);
          final ControlFlow switchFlow = new ControlFlowSubRange(controlFlow, switchStart, switchEnd);
          if (!ControlFlowUtil.isVariableDefinitelyAssigned(variable, beforeFlow) &&
              ControlFlowUtil.isVariableDefinitelyAssigned(variable, switchFlow) &&
              ControlFlowUtil.needVariableValueAt(variable, controlFlow, switchEnd)) {
            return true;
          }
        }
        catch (AnalysisCanceledException e) {
          return true;
        }
      }
    }
    return false;
  }

  private static PsiElement getContext(PsiElement element) {
    final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiLambdaExpression.class);
    if (context instanceof PsiField) {
      return ((PsiField)context).getInitializer();
    }
    else if (context instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)context).getBody();
    }
    else if (context instanceof PsiMethod) {
      return ((PsiMethod)context).getBody();
    }
    else if (context instanceof PsiLambdaExpression) {
      return ((PsiLambdaExpression)context).getBody();
    }
    return null;
  }

  private static @Nullable PsiElement retrieveUnnecessaryDefault(PsiSwitchBlock switchBlock) {
    final PsiExpression expression = switchBlock.getExpression();
    if (expression == null) {
      return null;
    }
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    final PsiElement result = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (result == null) {
      return null;
    }
    final SwitchExhaustivenessState completenessResult = evaluateSwitchCompleteness(switchBlock, false);
    return completenessResult == SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT
           ? result : null;
  }
}