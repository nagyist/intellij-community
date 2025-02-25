// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrMethodElementType;

public class GrMethodStub extends StubBase<GrMethod> implements NamedStub<GrMethod> {
  public static final byte IS_DEPRECATED_BY_DOC_TAG = 0b1;
  public static final byte HAS_BLOCK = 0b10;
  public static final byte HAS_COMMENT = 0b100;

  private final StringRef myName;
  private final String[] myAnnotations;
  private final String[] myNamedParameters;
  private final String myTypeText;
  private final byte myFlags;

  public GrMethodStub(StubElement parent,
                      StringRef name,
                      final String[] annotations,
                      final String @NotNull [] namedParameters,
                      @NotNull GrMethodElementType elementType,
                      @Nullable String typeText,
                      byte flags) {
    super(parent, elementType);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
    myTypeText = typeText;
    myFlags = flags;
  }

  @Override
  public @NotNull String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  public String @NotNull [] getNamedParameters() {
    return myNamedParameters;
  }

  public @Nullable String getTypeText() {
    return myTypeText;
  }

  public boolean isDeprecatedByDoc() {
    return (myFlags & IS_DEPRECATED_BY_DOC_TAG) != 0;
  }

  public boolean hasBlock() {
    return (myFlags & HAS_BLOCK) != 0;
  }

  public boolean hasComment() {
    return (myFlags & HAS_COMMENT) != 0;
  }

  public static byte buildFlags(GrMethod method) {
    byte f = 0;

    if (PsiImplUtil.isDeprecatedByDocTag(method)) f |= IS_DEPRECATED_BY_DOC_TAG;
    if (method.hasBlock()) f |= HAS_BLOCK;
    if (method.getDocComment() != null) f |= HAS_COMMENT;

    return f;
  }

  public byte getFlags() {
    return myFlags;
  }
}
