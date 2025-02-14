package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LombokVarValPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private final String selectedTypeFQN;

  LombokVarValPostfixTemplate(String name, String example, String selectedTypeFQN) {
    super(null, name, example,
          JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset(JavaPostfixTemplatesUtils.IS_NON_VOID),
          null);
    this.selectedTypeFQN = selectedTypeFQN;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    if (super.isApplicable(context, copyDocument, newOffset)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(context);
      return LombokLibraryUtil.hasLombokClasses(module);
    }
    return false;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    IntroduceVariableHandler handler = new IntroduceLombokVariableHandler(selectedTypeFQN);
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }
}
