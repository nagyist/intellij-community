// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ConcatenationQuery;
import com.intellij.util.Query;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.multiverse.CodeInsightContexts.isShowAllInheritorsEnabled;

public class ImplementationSearcher {
  public PsiElement @Nullable [] searchImplementations(Editor editor, PsiElement element, int offset) {
    TargetElementUtil targetElementUtil = TargetElementUtil.getInstance();
    boolean onRef = ReadAction.compute(() -> targetElementUtil.findTargetElement(editor, getFlags() & ~(TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.LOOKUP_ITEM_ACCEPTED), offset) == null);
    return searchImplementations(element, editor, onRef && ReadAction.compute(() -> element == null || targetElementUtil.includeSelfInGotoImplementation(element)), onRef);
  }

  public PsiElement @Nullable [] searchImplementations(PsiElement element,
                                                       Editor editor,
                                                       boolean includeSelfAlways,
                                                       boolean includeSelfIfNoOthers) {
    if (element == null) return PsiElement.EMPTY_ARRAY;
    PsiElement[] elements = searchDefinitions(element, editor);
    if (elements == null) return null; //the search has been cancelled
    if (elements.length > 0) return filterElements(element, includeSelfAlways ? ArrayUtil.prepend(element, elements) : elements);
    if (includeSelfAlways || includeSelfIfNoOthers) return new PsiElement[]{element};
    return PsiElement.EMPTY_ARRAY;
  }

  protected static SearchScope getSearchScope(PsiElement element, Editor editor) {
    try {
      return ReadAction.compute(() -> TargetElementUtil.getInstance().getSearchScope(editor, element));
    }
    catch (PsiInvalidElementAccessException e) {
      throw new ProcessCanceledException(e);
    }
  }

  protected PsiElement @Nullable("For the case the search has been cancelled") [] searchDefinitions(PsiElement element, Editor editor) {
    Ref<PsiElement[]> result = Ref.create();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> result.set(search(element, editor).toArray(PsiElement.EMPTY_ARRAY)),
      getSearchingForImplementations(), true, element.getProject())) {
      return null;
    }
    return result.get();
  }

  @RequiresReadLock
  private static @Nullable PsiElement findSimilarElement(@NotNull PsiFile file, @NotNull PsiElement targetElement) {
    int targetOffset = targetElement.getTextOffset();

    PsiElement elementAtOffset = file.findElementAt(targetOffset);

    if (elementAtOffset == null) {
      return null;
    }
    String targetName = ReadAction.compute(() -> {
      return targetElement.getText();
    });
    PsiElement candidate = elementAtOffset;
    PsiElement bestMatch = null;
    int count = 0;
    int limit = 50;
    while (candidate != null && count < limit) {
      if (candidate.getText().equals(targetName)) {
        if (candidate.getClass().equals(targetElement.getClass())) {
          return candidate;
        }
        bestMatch = candidate;
      }
      candidate = candidate.getParent();
      count++;
    }
    return bestMatch != null ? bestMatch : elementAtOffset;
  }

  protected @NotNull Query<PsiElement> search(@NotNull PsiElement element, Editor editor) {
    List<PsiElement> elements = isShowAllInheritorsEnabled() ? findSimilarElements(element, editor) : List.of(element);
    return searchForPsiElements(elements, editor);
  }

  private static @NotNull List<PsiElement> findSimilarElements(@NotNull PsiElement element, @NotNull Editor editor) {
    var project = element.getProject();
    var currentPsiFile = element.getContainingFile();
    var curretVirtualFile = currentPsiFile.getVirtualFile();
    var psiManager = PsiManager.getInstance(project);

    List<PsiElement> elements = new ArrayList<>();
    elements.add(element);

    CodeInsightContextManager contextManager = CodeInsightContextManager.getInstance(project);
    var contextList = contextManager.getCodeInsightContexts(curretVirtualFile);

    for (var context : contextList) {
      PsiFile psiFileInModule = ReadAction.compute(() -> psiManager.findFile(curretVirtualFile, context)
      );
      if (psiFileInModule == null) continue;
      if (psiFileInModule.equals(currentPsiFile)) continue;
      var similarElement = ReadAction.compute(() -> {
        return psiFileInModule.isValid() ? findSimilarElement(psiFileInModule, element) : null;
      });
      if (similarElement != null) {
        elements.add(similarElement);
      }
    }
    return elements;
  }

  private @NotNull Query<PsiElement> searchForPsiElements(@NotNull List<PsiElement> allElements, Editor editor) {
    List<Query<PsiElement>> queries = new ArrayList<>();
    for (var element : allElements) {
      SearchScope scope = getSearchScope(element, editor);
      Query<PsiElement> query = DefinitionsScopedSearch.search(element, scope, isSearchDeep());
      queries.add(query);
    }
    if (queries.size() == 1) return queries.get(0);
    return new ConcatenationQuery<>(queries);
  }

  protected boolean isSearchDeep() {
    return true;
  }

  protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements) {
    return targetElements;
  }

  public static int getFlags() {
    return TargetElementUtil.getInstance().getDefinitionSearchFlags();
  }

  public static class FirstImplementationsSearcher extends ImplementationSearcher {
    @Override
    protected PsiElement[] searchDefinitions(PsiElement element, Editor editor) {
      if (canShowPopupWithOneItem(element)) {
        return new PsiElement[]{element};
      }

      PsiElementProcessor.FindElement<PsiElement> collectProcessor = new PsiElementProcessor.FindElement<>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          search(element, editor).forEach(new PsiElementProcessorAdapter<>(collectProcessor) {
            @Override
            public boolean processInReadAction(PsiElement element) {
              return !accept(element) || super.processInReadAction(element);
            }
          });
        }
      }, getSearchingForImplementations(), true, element.getProject())) {
        return null;
      }
      PsiElement foundElement = collectProcessor.getFoundElement();
      return foundElement != null ? new PsiElement[] {foundElement} : PsiElement.EMPTY_ARRAY;
    }

    protected boolean canShowPopupWithOneItem(PsiElement element) {
      return accept(element);
    }

    protected boolean accept(PsiElement element) {
      return true;
    }
  }

  public abstract static class BackgroundableImplementationSearcher extends ImplementationSearcher {
    @Override
    protected PsiElement[] searchDefinitions(PsiElement element, Editor editor) {
      CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>() {
        @Override
        public boolean process(PsiElement element) {
          processElement(element);
          return super.process(element);
        }
      };
      search(element, editor).forEach(processor);
      return processor.toArray(PsiElement.EMPTY_ARRAY);
    }

    protected abstract void processElement(PsiElement element);
  }

  public static @NlsContexts.ProgressTitle String getSearchingForImplementations() {
    return CodeInsightBundle.message("searching.for.implementations");
  }
}