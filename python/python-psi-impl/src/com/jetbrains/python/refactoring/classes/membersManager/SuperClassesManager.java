// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Lists;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Plugin that moves superclasses from one class to another
 *
 * @author Ilya.Kazakevich
 */
final class SuperClassesManager extends MembersManager<PyClass> {
  SuperClassesManager() {
    super(PyClass.class);
  }


  @Override
  protected @NotNull Collection<PyElement> getDependencies(final @NotNull MultiMap<PyClass, PyElement> usedElements) {
    return Lists.newArrayList(usedElements.keySet());
  }

  @Override
  protected @NotNull MultiMap<PyClass, PyElement> getDependencies(@NotNull PyElement member) {
    return MultiMap.empty();
  }

  @Override
  public boolean hasConflict(final @NotNull PyClass member, final @NotNull PyClass aClass) {
    final List<PyExpression> expressionList = getExpressionsBySuperClass(aClass, Collections.singleton(member));
    return !expressionList.isEmpty();
  }

  @Override
  protected @NotNull List<PyElement> getMembersCouldBeMoved(final @NotNull PyClass pyClass) {
    return Lists.newArrayList(Arrays.asList(pyClass.getSuperClasses(null)));
  }

  @Override
  protected Collection<PyElement> moveMembers(final @NotNull PyClass from,
                                              final @NotNull Collection<PyMemberInfo<PyClass>> members,
                                              final PyClass @NotNull ... to) {
    final Collection<PyClass> elements = fetchElements(members);
    for (final PyClass destClass : to) {
      PyPsiRefactoringUtil.addSuperclasses(from.getProject(), destClass, elements.toArray(new PyClass[members.size()]));
    }

    final List<PyExpression> expressionsToDelete = getExpressionsBySuperClass(from, elements);
    for (final PyExpression expressionToDelete : expressionsToDelete) {
      expressionToDelete.delete();
    }

    return Collections.emptyList(); //Hack: we know that "superclass expression" can't have reference
  }

  /**
   * Returns superclass expressions that are resolved to one or more classes from collection
   * @param from class to get superclass expressions from
   * @param classes classes to check superclasses against
   * @return collection of expressions that are resolved to one or more class from classes param
   */
  private static @NotNull List<PyExpression> getExpressionsBySuperClass(final @NotNull PyClass from, final @NotNull Collection<PyClass> classes) {
    final List<PyExpression> expressionsToDelete = new ArrayList<>(classes.size());

    for (final PyExpression expression : from.getSuperClassExpressions()) {
      // Remove all superclass expressions that point to class from memberinfo
      if (!(expression instanceof PyQualifiedExpression)) {
        continue;
      }
      final PyReferenceExpression reference = (PyReferenceExpression)expression;
      for (final PyClass element : classes) {
        if (reference.getReference().isReferenceTo(element)) {
          expressionsToDelete.add(expression);
        }
      }
    }
    return expressionsToDelete;
  }

  @Override
  public @NotNull PyMemberInfo<PyClass> apply(final @NotNull PyClass input) {
    final String name = RefactoringBundle.message("member.info.extends.0", input.getName());
    //TODO: Check for "overrides"
    return new PyMemberInfo<>(input, false, name, false, this, false);
  }
}