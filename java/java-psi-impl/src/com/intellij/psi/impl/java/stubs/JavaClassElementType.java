// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.PsiAnonymousClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl;
import com.intellij.psi.impl.source.PsiImplicitClassImpl;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.AnonymousClassElement;
import com.intellij.psi.impl.source.tree.java.EnumConstantInitializerElement;
import com.intellij.psi.impl.source.tree.java.ImplicitClassElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class JavaClassElementType extends JavaStubElementType<PsiClassStub<PsiClass>, PsiClass> {
  JavaClassElementType(@NotNull String id, @NotNull IElementType parentElementType) {
    super(id, parentElementType);
  }

  @Override
  public PsiClass createPsi(final @NotNull PsiClassStub<PsiClass> stub) {
    return getPsiFactory(stub).createClass(stub);
  }

  @Override
  public PsiClass createPsi(final @NotNull ASTNode node) {
    if (node instanceof EnumConstantInitializerElement) {
      return new PsiEnumConstantInitializerImpl(node);
    }
    if (node instanceof AnonymousClassElement) {
      return new PsiAnonymousClassImpl(node);
    }
    if (node instanceof ImplicitClassElement) {
      return new PsiImplicitClassImpl(node);
    }

    return new PsiClassImpl(node);
  }

  @Override
  public @NotNull PsiClassStub<PsiClass> createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    boolean isDeprecatedByComment = false;
    boolean isInterface = false;
    boolean isEnum = false;
    boolean isRecord = false;
    boolean isValueClass = false;
    boolean isEnumConst = false;
    boolean isAnonymous = false;
    boolean isAnnotation = false;
    boolean isInQualifiedNew = false;
    boolean classKindFound = false;
    boolean hasDeprecatedAnnotation = false;
    boolean hasDocComment = false;

    String qualifiedName = null;
    String name = null;
    String baseRef = null;

    if (node.getTokenType() == JavaElementType.ANONYMOUS_CLASS) {
      isAnonymous = true;
      classKindFound = true;
    }
    else if (node.getTokenType() == JavaElementType.ENUM_CONSTANT_INITIALIZER) {
      isAnonymous = isEnumConst = true;
      classKindFound = true;
      baseRef = ((PsiClassStub<?>)parentStub.getParentStub()).getName();
    }

    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        hasDocComment = true;
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
        isValueClass = RecordUtil.hasValueModifier(tree, child);
      }
      else if (type == JavaTokenType.AT) {
        isAnnotation = true;
      }
      else if (type == JavaTokenType.INTERFACE_KEYWORD && !classKindFound) {
        isInterface = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.ENUM_KEYWORD && !classKindFound) {
        isEnum = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.RECORD_KEYWORD && !classKindFound) {
        isRecord = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.CLASS_KEYWORD) {
        classKindFound = true;
      }
      else if (!isAnonymous && type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (isAnonymous && !isEnumConst && type == JavaElementType.JAVA_CODE_REFERENCE) {
        baseRef = LightTreeUtil.toFilteredString(tree, child, null);
      }
    }

    if (name != null) {
      if (parentStub instanceof PsiJavaFileStub) {
        final String pkg = ((PsiJavaFileStub)parentStub).getPackageName();
        if (!pkg.isEmpty()) qualifiedName = pkg + '.' + name; else qualifiedName = name;
      }
      else if (parentStub instanceof PsiClassStub) {
        final String parentFqn = ((PsiClassStub<?>)parentStub).getQualifiedName();
        qualifiedName = parentFqn != null ? parentFqn + '.' + name : null;
      }
    }

    if (isAnonymous) {
      final LighterASTNode parent = tree.getParent(node);
      if (parent != null && parent.getTokenType() == JavaElementType.NEW_EXPRESSION) {
        isInQualifiedNew = LightTreeUtil.firstChildOfType(tree, parent, JavaTokenType.DOT) != null;
      }
    }


    boolean isImplicit = node.getTokenType() == JavaElementType.IMPLICIT_CLASS;
    final short flags = PsiClassStubImpl.packFlags(isDeprecatedByComment, isInterface, isEnum, isEnumConst, isAnonymous, isAnnotation,
                                                  isInQualifiedNew, hasDeprecatedAnnotation, false, false, hasDocComment, isRecord,
                                                   isImplicit, isValueClass);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst, isImplicit);
    return new PsiClassStubImpl<>(type, parentStub, qualifiedName, name, baseRef, flags);
  }

  private static @NotNull JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst, final boolean implicitClass) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : implicitClass ? JavaStubElementTypes.IMPLICIT_CLASS
           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
  }

  @Override
  public void serialize(@NotNull PsiClassStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeShort(((PsiClassStubImpl<?>)stub).getFlags());
    if (!stub.isAnonymous()) {
      String name = stub.getName();
      TypeInfo info = ((PsiClassStubImpl<?>)stub).getQualifiedNameTypeInfo();
      dataStream.writeName(info.getShortTypeText().equals(name) ? null : name);
      TypeInfo.writeTYPE(dataStream, info);
      dataStream.writeName(stub.getSourceFileName());
    }
    else {
      dataStream.writeName(stub.getBaseClassReferenceText());
    }
  }

  @Override
  public @NotNull PsiClassStub<PsiClass> deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    short flags = dataStream.readShort();
    boolean isAnonymous = PsiClassStubImpl.isAnonymous(flags);
    boolean isEnumConst = PsiClassStubImpl.isEnumConstInitializer(flags);
    boolean isImplicit = PsiClassStubImpl.isImplicit(flags);
    JavaClassElementType type = typeForClass(isAnonymous, isEnumConst, isImplicit);

    if (!isAnonymous) {
      String name = dataStream.readNameString();
      TypeInfo typeInfo = TypeInfo.readTYPE(dataStream);
      if (name == null) {
        name = typeInfo.getShortTypeText();
      }
      String sourceFileName = dataStream.readNameString();
      PsiClassStubImpl<PsiClass> classStub = new PsiClassStubImpl<>(type, parentStub, typeInfo, name, null, flags);
      classStub.setSourceFileName(sourceFileName);
      return classStub;
    }
    else {
      String baseRef = dataStream.readNameString();
      return new PsiClassStubImpl<>(type, parentStub, TypeInfo.SimpleTypeInfo.NULL, null, baseRef, flags);
    }
  }

  @Override
  public void indexStub(@NotNull PsiClassStub stub, @NotNull IndexSink sink) {
    boolean isAnonymous = stub.isAnonymous();
    if (isAnonymous) {
      String baseRef = stub.getBaseClassReferenceText();
      if (baseRef != null) {
        sink.occurrence(JavaStubIndexKeys.ANONYMOUS_BASEREF, PsiNameHelper.getShortClassName(baseRef));
      }
    }
    else {
      final String shortName = stub.getName();
      if (shortName != null && (!(stub instanceof PsiClassStubImpl) || !((PsiClassStubImpl<?>)stub).isAnonymousInner())) {
        sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, shortName);
      }

      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(JavaStubIndexKeys.CLASS_FQN, fqn);
      }
    }
  }

}