// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiMethodUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.*;

public final class RefJavaUtilImpl extends RefJavaUtil {
  private static final Logger LOG = Logger.getInstance(RefJavaUtilImpl.class);

  @Override
  public void addReferences(@NotNull PsiModifierListOwner psiFrom, @NotNull RefJavaElement ref, @Nullable PsiElement findIn) {
    UDeclaration decl = UastContextKt.toUElement(psiFrom, UDeclaration.class);
    UElement uFindIn = UastContextKt.toUElement(findIn);
    if (decl != null && findIn != null) {
      addReferencesTo(decl, ref, uFindIn);
    }
  }

  @Override
  public void addReferencesTo(@NotNull UElement decl, @NotNull RefJavaElement ref, UElement @Nullable ... findIn) {
    final RefJavaElementImpl refFrom = (RefJavaElementImpl)ref;
    final RefManagerImpl refManager = refFrom.getRefManager();
    if (findIn == null) {
      return;
    }
    for (UElement element : findIn) {
      if (element == null) continue;
      element.accept(new AbstractUastVisitor() {
                       @Override
                       public boolean visitEnumConstant(@NotNull UEnumConstant node) {
                         processNewLikeConstruct(node.resolve(), node);
                         return false;
                       }

                       @Override
                       public boolean visitNamedExpression(@NotNull UNamedExpression node) {
                         PsiElement source = node.getSourcePsi();
                         if (source instanceof PsiNameValuePair pair) {
                           PsiReference reference = pair.getReference();
                           if (reference != null) {
                             PsiElement target = reference.resolve();
                             final RefElement refElement = refManager.getReference(target);
                             if (refElement != null) {
                               refElement.initializeIfNeeded();
                               refFrom.addReference(refElement, target, decl, false, true, node);
                             }
                           }
                         }
                         return false;
                       }

                       @Override
                       public boolean visitAnnotation(@NotNull UAnnotation node) {
                         PsiClass javaClass = node.resolve();
                         if (javaClass != null) {
                           final RefElement refClass = refManager.getReference(javaClass.getOriginalElement());
                           if (refClass != null) refClass.initializeIfNeeded();
                           refFrom.addReference(refClass, javaClass.getOriginalElement(), decl, false, true, null);
                           checkRepeatableAnnotation(javaClass, node);
                         }
                         return false;
                       }

                       private void checkRepeatableAnnotation(PsiClass annotationClass, UAnnotation annotation) {
                         if (!CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE.equals(annotationClass.getQualifiedName())) return;
                         UExpression value = annotation.findAttributeValue("value");
                         while (value instanceof UParenthesizedExpression parenth) {
                           value = parenth.getExpression();
                         }
                         if (!(value instanceof UClassLiteralExpression classLiteralExpression)) return;
                         PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(classLiteralExpression.getType());
                         if (aClass == null) return;
                         if (!(refManager.getReference(aClass.getOriginalElement()) instanceof RefClassImpl refClass)) return;
                         refClass.initializeIfNeeded();
                         for (RefEntity child : refClass.getChildren()) {
                           if (child instanceof RefMethod method && "value()".equals(method.getName())) {
                             refFrom.addReference(method, null, decl, false, true, null);
                             break;
                           }
                         }
                       }

                       @Override
                       public boolean visitTypeReferenceExpression(@NotNull UTypeReferenceExpression node) {
                         PsiType type = node.getType();
                         visitTypeRefs(type);
                         return false;
                       }

                       private void visitTypeRefs(PsiType type) {
                         type = type.getDeepComponentType();
                         if (type instanceof PsiClassType) {
                           type.accept(new PsiTypeVisitor<Void>() {
                             @Override
                             public Void visitClassType(@NotNull PsiClassType classType) {
                               for (PsiType parameter : classType.getParameters()) {
                                 parameter.accept(this);
                               }
                               PsiClass aClass = classType.resolve();
                               UClass target = UastContextKt.toUElement(aClass, UClass.class);
                               if (target != null) {
                                 final RefElement refElement = refManager.getReference(target.getSourcePsi());
                                 if (refElement != null) refElement.initializeIfNeeded();
                                 refFrom.addReference(refElement, aClass, decl, false, true, null);
                               }
                               return null;
                             }
                           });
                         }
                       }

                       @Override
                       public boolean visitVariable(@NotNull UVariable node) {
                         visitTypeRefs(node.getType());
                         return false;
                       }

                       @Override
                       public boolean visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitLiteralExpression(@NotNull ULiteralExpression node) {
                         PsiElement sourcePsi = node.getSourcePsi();
                         if (sourcePsi != null) {
                           for (PsiReference reference : sourcePsi.getReferences()) {
                             PsiElement resolve = reference.resolve();
                             if (resolve instanceof PsiMember) {
                               final RefElement refResolved = refManager.getReference(resolve);
                               if (refResolved != null) refResolved.initializeIfNeeded();
                               refFrom.addReference(refResolved, resolve, decl, false, true, null);
                               if (refResolved instanceof RefMethodImpl refMethod) {
                                 updateRefMethod(resolve, refMethod, node, decl);
                               }
                             }
                           }
                         }
                         return false;
                       }

                       @Override
                       public boolean visitPrefixExpression(@NotNull UPrefixExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitPostfixExpression(@NotNull UPostfixExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitUnaryExpression(@NotNull UUnaryExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitObjectLiteralExpression(@NotNull UObjectLiteralExpression node) {
                         visitReferenceExpression(node);
                         visitClass(node.getDeclaration());
                         return false;
                       }

                       @Override
                       public boolean visitCallExpression(@NotNull UCallExpression node) {
                         visitReferenceExpression(node);
                         if (node instanceof UObjectLiteralExpression objectLiteralExpression) {
                           visitClass(objectLiteralExpression.getDeclaration());
                         }
                         if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                           PsiElement resolvedMethod = returnToPhysical(node.resolve());
                           processNewLikeConstruct(resolvedMethod, node);
                         }
                         try {
                           node.getTypeArguments().forEach(this::visitTypeRefs);
                         }
                         catch (UnsupportedOperationException e) {
                           //TODO happens somewhere in kotlin plugin. Please assign those exception for Dmitry Batkovich
                           LOG.error(e);
                         }
                         return false;
                       }

                       private void visitReferenceExpression(@NotNull UExpression node) {
                         UElement uastParent = node.getUastParent();
                         if (uastParent instanceof UQualifiedReferenceExpression qualifiedReference && qualifiedReference.getSelector() == node) {
                           return;
                         }
                         PsiElement psiResolved = null;
                         if (node instanceof UCallExpression callExpression &&
                             "invoke".equals(callExpression.getMethodName()) &&
                             callExpression.getReceiver() instanceof UResolvable resolvable) {
                           psiResolved = resolvable.resolve();
                         }
                         else if (node instanceof UResolvable resolvable) {
                           psiResolved = resolvable.resolve();
                         }
                         else if (node instanceof UBinaryExpression binaryExpression) {
                           psiResolved = binaryExpression.resolveOperator();
                         }
                         else if (node instanceof UUnaryExpression unaryExpression) {
                           psiResolved = unaryExpression.resolveOperator();
                         }

                         psiResolved = returnToPhysical(psiResolved);
                         RefElement refResolved = refManager.getReference(psiResolved);
                         boolean writing = isAccessedForWriting(node);
                         boolean reading = isAccessedForReading(node);
                         if (refResolved != null) refResolved.initializeIfNeeded();
                         refFrom.addReference(refResolved, psiResolved, decl, writing, reading, node);

                         if (refResolved instanceof RefMethodImpl refMethod) {
                           updateRefMethod(psiResolved, refMethod, node, decl);
                         }
                         else if (refResolved instanceof RefField) {
                           if (node instanceof UResolvable resolvable) {
                             UMethod uProperty = UastContextKt.toUElement(resolvable.resolve(), UMethod.class);
                             if (uProperty != null) {
                             //  PsiMethod property = uProperty.getJavaPsi();
                             //  RefElement refProperty = refManager.getReference(uProperty);
                             //  if (refProperty != null) {
                             //    refProperty.waitForInitialized();
                             //    refFrom.addReference(refProperty, property, decl, false, true, node);
                             //  }
                             }
                           }
                         }
                         else if (refResolved ==  null && psiResolved instanceof PsiMethod method) {
                           markEnumUsedIfValuesMethod(method, node);
                         }

                         if (psiResolved instanceof PsiMember psiMember) {
                           //TODO support kotlin
                           addClassReferenceForStaticImport(node, psiMember, refFrom, decl);
                         }
                       }

                       @Override
                       public boolean visitLambdaExpression(@NotNull ULambdaExpression lambda) {
                         processFunctionalExpression(lambda, lambda.getFunctionalInterfaceType());
                         return true;
                       }

                       private void markEnumUsedIfValuesMethod(PsiMethod psiWhat, UExpression expression) {
                         //TODO support kotlin enums
                         final PsiClass containingClass = psiWhat.getContainingClass();
                         if (containingClass == null || !containingClass.isEnum()) {
                           return;
                         }
                         String methodName = psiWhat.getName();
                         if (!"values".equals(methodName) && !"valueOf".equals(methodName)) {
                           return;
                         }
                         for (PsiField enumConstant : containingClass.getFields()) {
                           if (enumConstant instanceof PsiEnumConstant) {
                             final RefJavaElementImpl enumConstantReference = (RefJavaElementImpl)refFrom.getRefManager().getReference(enumConstant);
                             if (enumConstantReference != null) {
                               refFrom.addOutReference(enumConstantReference);
                               enumConstantReference.markReferenced(refFrom, false, true, expression);
                             }
                           }
                         }
                       }

                       @Override
                       public boolean visitCallableReferenceExpression(@NotNull UCallableReferenceExpression methodRef) {
                         UExpression qualifierExpression = methodRef.getQualifierExpression();
                         if (qualifierExpression != null) {
                           qualifierExpression.accept(this);
                         }
                         RefElement refMethod = refManager.getReference(methodRef.getSourcePsi());
                         if (refFrom == refMethod) {
                           visitReferenceExpression(methodRef);
                           return false;
                         }
                         else {
                           processFunctionalExpression(methodRef, getFunctionalInterfaceType(methodRef));
                           return true;
                         }
                       }

                       private void processFunctionalExpression(@NotNull UExpression expression, @Nullable PsiType type) {
                         PsiElement aClass = PsiUtil.resolveClassInType(type);
                         if (aClass != null) {
                           aClass = ((PsiClass)aClass).getSourceElement();
                         }
                         if (aClass != null) {
                           final RefElement refWhat = refManager.getReference(aClass);
                           if (refWhat != null) refWhat.initializeIfNeeded();
                           refFrom.addReference(refWhat, aClass, decl, false, true, null);
                         }
                         PsiElement functionalExpr = expression.getSourcePsi();
                         RefElement refFunctionalExpr = refManager.getReference(functionalExpr);
                         if (refFunctionalExpr != null) refFunctionalExpr.initializeIfNeeded();
                         refFrom.addReference(refFunctionalExpr, functionalExpr, decl, false, true, expression);
                       }

                       private void processNewLikeConstruct(PsiElement javaConstructor, UCallExpression call) {
                         if (javaConstructor != null
                             && refManager.getReference(javaConstructor.getOriginalElement()) instanceof RefMethodImpl refConstructor) {
                           refConstructor.initializeIfNeeded();
                           refFrom.addReference(refConstructor, javaConstructor, decl, false, true, call);
                           refConstructor.updateParameterValues(call, javaConstructor);
                         }
                         else {
                           UReferenceExpression reference = call.getClassReference();
                           if (reference != null) {
                             PsiElement constructorClass = reference.resolve();
                             if (constructorClass instanceof PsiClass psiClass) {
                               processClassReference(psiClass, true, call);
                             }
                           }
                         }
                         for (UExpression arg : call.getValueArguments()) {
                           arg.accept(this);
                         }
                       }

                       @Override
                       public boolean visitClass(@NotNull UClass uClass) {
                         for (UTypeReferenceExpression type : uClass.getUastSuperTypes()) {
                           type.accept(this);
                         }
                         PsiElement sourcePsi = uClass.getSourcePsi();
                         RefElement refWhat = refManager.getReference(sourcePsi);
                         if (refWhat != null) refWhat.initializeIfNeeded();
                         refFrom.addReference(refWhat, sourcePsi, decl, false, true, null);
                         return true;
                       }

                       @Override
                       public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                         RefMethodImpl refMethod = null;
                         if (refFrom instanceof RefMethodImpl &&
                             UastUtils.getParentOfType(node, UMethod.class, false, UClass.class, ULambdaExpression.class) == decl) {
                           refMethod = (RefMethodImpl)refFrom;
                         }
                         else if (refFrom instanceof RefFunctionalExpression) {
                           UElement target = node.getJumpTarget();
                           if (target instanceof UMethod) {
                             refMethod = refManager.getReference(target.getSourcePsi()) instanceof RefMethodImpl m ? m : null;
                           }
                           else if (decl instanceof ULambdaExpression lambdaExpression) {
                             PsiMethod lambdaMethod = LambdaUtil.getFunctionalInterfaceMethod(lambdaExpression.getFunctionalInterfaceType());
                             refMethod = refManager.getReference(lambdaMethod) instanceof RefMethodImpl m ? m : null;
                           }
                         }
                         if (refMethod != null) {
                           refMethod.initializeIfNeeded();
                           refMethod.updateReturnValueTemplate(node.getReturnExpression());
                         }
                         return false;
                       }

                       @Override
                       public boolean visitClassLiteralExpression(@NotNull UClassLiteralExpression node) {
                         processClassReference(PsiUtil.resolveClassInClassTypeOnly(node.getType()), false, node);
                         return false;
                       }

                       private void processClassReference(PsiClass psiClass, boolean defaultConstructorOnly, UExpression node) {
                         if (psiClass != null) {
                           if (refManager.getReference(psiClass.getNavigationElement()) instanceof RefClassImpl refClass) {
                             boolean hasConstructorsMarked = false;
                             refClass.initializeIfNeeded();

                             if (defaultConstructorOnly) {
                               WritableRefElement refDefaultConstructor = (WritableRefElement)refClass.getDefaultConstructor();
                               if (refDefaultConstructor != null) {
                                 refDefaultConstructor.addInReference(refFrom);
                                 refFrom.addOutReference(refDefaultConstructor);
                                 hasConstructorsMarked = true;
                               }
                             }
                             else {
                               for (RefMethod cons : refClass.getConstructors()) {
                                 if (cons instanceof RefImplicitConstructor) continue;
                                 ((WritableRefElement)cons).addInReference(refFrom);
                                 refFrom.addOutReference(cons);
                                 hasConstructorsMarked = true;
                               }

                               if (refClass.isEnum()) {
                                 for (RefField field : refClass.getFields()) {
                                   if (field.isEnumConstant()) {
                                     ((RefFieldImpl)field).markReferenced(refFrom, false, true, node);
                                     refFrom.addOutReference(field);
                                   }
                                 }
                               }
                             }

                             if (!hasConstructorsMarked) {
                               refFrom.addReference(refClass, psiClass, decl, false, true, node);
                             }
                           }
                         }
                       }
                     }
      );
    }
  }

  private static void addClassReferenceForStaticImport(UExpression node,
                                                       PsiMember psiResolved,
                                                       RefJavaElementImpl refFrom, UElement decl) {
    PsiElement sourcePsi = node.getSourcePsi();
    if (sourcePsi instanceof PsiReferenceExpression ref) {
      JavaResolveResult result = ref.advancedResolve(false);
      if (result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        final PsiClass containingClass = psiResolved.getContainingClass();
        if (containingClass != null) {
          RefElement refContainingClass = refFrom.getRefManager().getReference(containingClass);
          if (refContainingClass != null) {
            refContainingClass.initializeIfNeeded();
            refFrom.addReference(refContainingClass, containingClass, decl, false, true, node);
          }
        }
      }
    }
  }

  private void updateRefMethod(PsiElement psiResolved,
                               RefMethodImpl refMethod,
                               @NotNull UExpression uExpression,
                               UElement uFrom) {
    UMethod uMethod = Objects.requireNonNull(UastContextKt.toUElement(psiResolved, UMethod.class));
    refMethod.initializeIfNeeded();
    if (uExpression instanceof UCallableReferenceExpression callableReference) {
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor()) {
        final PsiType type = getFunctionalInterfaceType(callableReference);
        if (!PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(type))) {
          refMethod.setReturnValueUsed(true);
          addTypeReference(uFrom, returnType, refMethod.getRefManager());
        }
      }
      refMethod.setParametersAreUnknown();
      return;
    }
    if (uExpression instanceof ULiteralExpression) { //references in literal expressions
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor() && !PsiTypes.voidType().equals(returnType)) {
        refMethod.setReturnValueUsed(true);
        addTypeReference(uFrom, returnType, refMethod.getRefManager());
      }
      return;
    }

    PsiType returnType = uMethod.getReturnType();
    if (!uMethod.isConstructor() && !PsiTypes.voidType().equals(returnType)) {
      if (!(uExpression.getJavaPsi() instanceof PsiExpression expression) || !ExpressionUtils.isVoidContext(expression)) {
        refMethod.setReturnValueUsed(true);
      }

      addTypeReference(uFrom, returnType, refMethod.getRefManager());
    }

    UCallExpression call = null;
    if (uExpression instanceof UCallExpression callExpression) {
      call = callExpression;
    }
    else if (uExpression instanceof UQualifiedReferenceExpression qualifiedReference) {
      call = qualifiedReference.getSelector() instanceof UCallExpression c ? c : null;
    }
    if (call != null) {
      refMethod.updateParameterValues(call, psiResolved);

      final PsiType usedType = call.getReceiverType();
      if (usedType != null) {
        UClass containingClass = UDeclarationKt.getContainingDeclaration(uMethod, UClass.class);
        if (containingClass != null) {
          final String fqName = containingClass.getQualifiedName();
          if (fqName != null) {
            final Project project = psiResolved.getProject();
            final PsiClassType methodOwnerType = JavaPsiFacade.getElementFactory(project)
              .createTypeByFQClassName(fqName, GlobalSearchScope.allScope(project));
            if (!usedType.equals(methodOwnerType)) {
              refMethod.setCalledOnSubClass(true);
            }
          }
        }
      }
    }
  }

  private static PsiType getFunctionalInterfaceType(@NotNull UCallableReferenceExpression expression) {
    PsiElement psi = expression.getSourcePsi();
    if (psi instanceof PsiFunctionalExpression functionalExpression) {
      return functionalExpression.getFunctionalInterfaceType();
    }
    return null;
  }

  @Override
  public RefClass getTopLevelClass(@NotNull RefElement refElement) {
    LOG.assertTrue(refElement.isInitialized(), refElement.getName() + " not initialized");
    RefEntity refParent = refElement.getOwner();

    while (refParent instanceof RefElement && !(refParent instanceof RefFile)) {
      refElement = (RefElement)refParent;
      refParent = refParent.getOwner();
    }

    return refElement instanceof RefClass refClass ? refClass : null;
  }

  @Override
  public boolean isInheritor(@NotNull RefClass subClass, RefClass superClass) {
    if (subClass == superClass) return true;
    LOG.assertTrue(subClass.isInitialized());

    for (RefClass baseClass : subClass.getBaseClasses()) {
      if (isInheritor(baseClass, superClass)) return true;
    }

    return false;
  }

  @Override
  public @Nullable String getPackageName(RefEntity refEntity) {
    if (refEntity instanceof RefProject || refEntity instanceof RefJavaModule) {
      return null;
    }
    if (!(refEntity instanceof RefJavaElement) && !(refEntity instanceof RefPackage) && !(refEntity instanceof RefJavaFileImpl)) {
      return null;
    }
    RefPackage refPackage = getPackage(refEntity);

    return refPackage == null ? JavaAnalysisBundle.message("inspection.reference.default.package") : refPackage.getQualifiedName();
  }

  @Override
  public @NotNull String getAccessModifier(@NotNull PsiModifierListOwner psiElement) {
    if (psiElement instanceof PsiParameter) return PsiModifier.PACKAGE_LOCAL;

    PsiModifierList list = psiElement.getModifierList();
    String result = PsiModifier.PACKAGE_LOCAL;

    if (list != null) {
      if (list.hasModifierProperty(PsiModifier.PRIVATE)) {
        result = PsiModifier.PRIVATE;
      }
      else if (list.hasModifierProperty(PsiModifier.PROTECTED)) {
        result = PsiModifier.PROTECTED;
      }
      else if (list.hasModifierProperty(PsiModifier.PUBLIC)) {
        result = PsiModifier.PUBLIC;
      }
      else if (psiElement.getParent() instanceof PsiClass ownerClass && ownerClass.isInterface()) {
        result = PsiModifier.PUBLIC;
      }
    }

    return result;
  }

  @Override
  public @Nullable RefClass getOwnerClass(RefManager refManager, UElement uElement) {
    while (uElement != null && !(uElement instanceof UClass)) {
      uElement = uElement.getUastParent();
    }

    if (uElement != null) {
      RefElement reference = refManager.getReference(uElement.getSourcePsi());
      return reference instanceof RefClass refClass ? refClass : null;
    }

    return null;
  }

  @Override
  public @Nullable RefClass getOwnerClass(RefElement refElement) {
    LOG.assertTrue(refElement.isInitialized(), refElement.getName() + " not initialized");
    RefEntity parent = refElement.getOwner();

    while (!(parent instanceof RefClass) && parent instanceof RefElement) {
      LOG.assertTrue(((RefElement)parent).isInitialized());
      parent = parent.getOwner();
    }

    return parent instanceof RefClass refClass ? refClass : null;
  }


  @Override
  public boolean isMethodOnlyCallsSuper(UMethod method) {
    PsiMethod javaMethod = method.getJavaPsi();
    boolean hasStatements = false;
    UExpression body = method.getUastBody();
    if (body != null) {
      List<UExpression> statements =
        body instanceof UBlockExpression blockExpression ? blockExpression.getExpressions() : Collections.singletonList(body);
      if (statements.size() > 1) return false;
      for (UExpression expression : statements) {
        boolean isCallToSameSuper = false;
        if (expression instanceof UReturnExpression returnExpression) {
          UExpression returnExpr = returnExpression.getReturnExpression();
          isCallToSameSuper = returnExpr == null || isCallToSuperMethod(returnExpr, method);
        }
        else if (!(expression instanceof UBlockExpression)) {
          isCallToSameSuper = isCallToSuperMethod(expression, method);
        }

        hasStatements = true;
        if (isCallToSameSuper) continue;
        return false;
      }
    }

    if (hasStatements) {
      final PsiMethod[] superMethods = javaMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(superMethod.getModifierList()),
                                   VisibilityUtil.getVisibilityModifier(javaMethod.getModifierList())) > 0) {
          return false;
        }
      }
      PsiClass aClass = javaMethod.getContainingClass();
      if (aClass == null || hasUnrelatedDefaults(aClass, Arrays.asList(superMethods))) {
        return false;
      }
    }
    return hasStatements;
  }

  @Override
  public boolean isCallToSuperMethod(UExpression expression, UMethod method) {
    if (expression instanceof UQualifiedReferenceExpression qualifiedReference) {
      UExpression receiver = qualifiedReference.getReceiver();
      UExpression selector = qualifiedReference.getSelector();

      if (receiver instanceof USuperExpression && selector instanceof UCallExpression callExpression) {
        PsiMethod superMethod = callExpression.resolve();
        if (superMethod == null || !MethodSignatureUtil.areSignaturesEqual(method.getJavaPsi(), superMethod)) return false;

        List<UExpression> args = callExpression.getValueArguments();
        List<UParameter> params = method.getUastParameters();

        for (int i = 0; i < args.size(); i++) {
          UExpression arg = args.get(i);
          if (!(arg instanceof USimpleNameReferenceExpression simpleNameReference)) return false;
          if (!params.get(i).equals(simpleNameReference.resolve())) return false;
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public int compareAccess(String a1, String a2) {
    return Integer.compare(getAccessNumber(a1), getAccessNumber(a2));
  }

  private static int getAccessNumber(String modifier) {
    return switch (modifier) {
      case PsiModifier.PRIVATE -> 0;
      case PsiModifier.PACKAGE_LOCAL -> 1;
      case PsiModifier.PROTECTED -> 2;
      case PsiModifier.PUBLIC -> 3;
      default -> -1;
    };
  }

  @Override
  public void setAccessModifier(@NotNull RefJavaElement refElement, @NotNull String newAccess) {
    ((RefJavaElementImpl)refElement).setAccessModifier(newAccess);
  }

  @Override
  public void setIsStatic(RefJavaElement refElement, boolean isStatic) {
    ((RefJavaElementImpl)refElement).setIsStatic(isStatic);
  }

  @Override
  public void setIsFinal(RefJavaElement refElement, boolean isFinal) {
    ((RefJavaElementImpl)refElement).setIsFinal(isFinal);
  }

  @Override
  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager) {
    addTypeReference(uElement, psiType, refManager, null);
  }

  @Override
  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refMethod) {
    if (!(psiType instanceof PsiClassType)) return;
    final RefClass ownerClass = getOwnerClass(refManager, uElement);
    if (ownerClass == null) return;
    psiType = psiType.getDeepComponentType();
    PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
    if (psiClass != null && refManager.belongsToScope(psiClass)) {
      RefClassImpl refClass = (RefClassImpl)refManager.getReference(psiClass);
      if (refClass != null) {
        refClass.addTypeReference(ownerClass);
        if (refMethod != null) {
          refClass.addClassExporter(refMethod);
        }
      }
    }
    else {
      ((RefManagerImpl)refManager).fireNodeMarkedReferenced(psiClass, uElement.getSourcePsi());
    }
  }

  private static boolean isAccessedForWriting(@NotNull UElement expression) {
    if (isOnAssignmentLeftHand(expression)) return true;
    UElement parent = skipParentheses(expression);
    return isIncrementDecrement(parent);
  }

  private static boolean isIncrementDecrement(UElement element) {
    if (!(element instanceof UUnaryExpression unaryExpression)) return false;
    UastOperator operator = unaryExpression.getOperator();
    return operator == UastPostfixOperator.DEC
           || operator == UastPostfixOperator.INC
           || operator == UastPrefixOperator.DEC
           || operator == UastPrefixOperator.INC;
  }

  private static boolean isAccessedForReading(@NotNull UElement expression) {
    UElement parent = skipParentheses(expression);
    return !(parent instanceof UBinaryExpression binaryExpression) ||
           binaryExpression.getOperator() != UastBinaryOperator.ASSIGN ||
           UastUtils.isUastChildOf(binaryExpression.getRightOperand(), expression, false);
  }

  private static boolean isOnAssignmentLeftHand(@NotNull UElement expression) {
    if (!(skipParentheses(expression) instanceof UExpression parent)) return false;
    return parent instanceof UBinaryExpression binaryExpression
           && binaryExpression.getOperator() instanceof UastBinaryOperator.AssignOperator
           && UastUtils.isUastChildOf(expression, binaryExpression.getLeftOperand(), false);
  }

  private static UElement skipParentheses(@NotNull UElement expression) {
    return UastUtils.skipParentOfType(expression, true, UParenthesizedExpression.class);
  }

  public static PsiElement returnToPhysical(PsiElement element) {
    if (element instanceof LightElement) {
      UElement uElement = UastContextKt.toUElement(element);
      PsiElement el = uElement == null ? null : uElement.getSourcePsi();
      if (el != null) return el;
      return element.getNavigationElement();
    }
    return element;
  }

  private static boolean hasUnrelatedDefaults(@NotNull PsiClass aClass,
                                              @NotNull Collection<? extends PsiMethod> overrideEquivalentSuperMethods) {
    return JavaPsiMethodUtil.getAbstractMethodToImplementWhenDefaultPresent(aClass, overrideEquivalentSuperMethods, true) != null ||
           JavaPsiMethodUtil.getUnrelatedSuperMethods(aClass, overrideEquivalentSuperMethods) != null;
  }
}
