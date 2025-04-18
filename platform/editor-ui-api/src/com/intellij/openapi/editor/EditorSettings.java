// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

@ApiStatus.NonExtendable
public interface EditorSettings {
  boolean isRightMarginShown();
  void setRightMarginShown(boolean val);
  
  boolean isHighlightSelectionOccurrences();
  void setHighlightSelectionOccurrences(boolean val);

  boolean isWhitespacesShown();
  void setWhitespacesShown(boolean val);

  boolean isLeadingWhitespaceShown();
  void setLeadingWhitespaceShown(boolean val);

  boolean isInnerWhitespaceShown();
  void setInnerWhitespaceShown(boolean val);

  boolean isTrailingWhitespaceShown();
  void setTrailingWhitespaceShown(boolean val);

  boolean isSelectionWhitespaceShown();
  void setSelectionWhitespaceShown(boolean val);

  int getRightMargin(@Nullable Project project);
  void setRightMargin(int myRightMargin);

  /**
   * Retrieves a list of soft margins (visual indent guides) to be used in the editor. If soft margins haven't been explicitly set
   * with {@link #setSoftMargins(List)} method, they are obtained from code style settings: {@code CodeStyleSettings.getSoftMargins()}.
   * @return A list of current editor soft margins. The list may be empty if no soft margins are defined.
   */
  @NotNull
  List<Integer> getSoftMargins();

  /**
   * Explicitly sets soft margins (visual indent guides) to be used in the editor instead of obtaining them from code style settings via
   * {@code CodeStyleSettings.getSoftMargins()} method. It is important to distinguish and empty list from {@code null} value: the first
   * will define no soft margins for the editor while the latter will restore the default behavior of using them from code style settings.
   * @param softMargins A list of soft margins or {@code null} to use margins from code style settings.
   */
  void setSoftMargins(@Nullable List<Integer> softMargins);

  boolean isWrapWhenTypingReachesRightMargin(Project project);
  void setWrapWhenTypingReachesRightMargin(boolean val);

  boolean isLineNumbersShown();
  void setLineNumbersShown(boolean val);

  boolean isLineNumbersAfterIcons();
  void setLineNumbersAfterIcons(boolean val);

  int getAdditionalLinesCount();
  void setAdditionalLinesCount(int additionalLinesCount);

  int getAdditionalColumnsCount();
  void setAdditionalColumnsCount(int additionalColumnsCount);

  boolean isLineMarkerAreaShown();
  void setLineMarkerAreaShown(boolean lineMarkerAreaShown);

  boolean areGutterIconsShown();
  void setGutterIconsShown(boolean gutterIconsShown);

  boolean isFoldingOutlineShown();
  void setFoldingOutlineShown(boolean val);

  boolean isAutoCodeFoldingEnabled();
  void setAutoCodeFoldingEnabled(boolean val);

  boolean isUseTabCharacter(Project project);
  void setUseTabCharacter(boolean useTabCharacter);

  int getTabSize(@Nullable Project project);
  void setTabSize(int tabSize);

  boolean isSmartHome();
  void setSmartHome(boolean val);

  boolean isVirtualSpace();
  void setVirtualSpace(boolean allow);

  /**
   * Vertical scroll offset - number of lines to keep above and below the caret.
   * If the number is too big for the editor height, the caret will be centered.
   */
  int getVerticalScrollOffset();
  void setVerticalScrollOffset(int val);

  /**
   * Vertical scroll jump - minimum number of lines to scroll at a time.
   */
  int getVerticalScrollJump();
  void setVerticalScrollJump(int val);

  /**
   * Horizontal scroll offset - number of characters to keep to the left and right of the caret.
   * If the number is too big for the editor width, the caret will be centered.
   */
  int getHorizontalScrollOffset();
  void setHorizontalScrollOffset(int val);

  /**
   * Horizontal scroll jump - minimum number of characters to scroll horizontally at a time.
   */
  int getHorizontalScrollJump();
  void setHorizontalScrollJump(int val);

  boolean isCaretInsideTabs();
  void setCaretInsideTabs(boolean allow);

  boolean isBlinkCaret();
  void setBlinkCaret(boolean blinkCaret);

  int getCaretBlinkPeriod();
  void setCaretBlinkPeriod(int blinkPeriod);

  boolean isBlockCursor();
  void setBlockCursor(boolean blockCursor);

  boolean isFullLineHeightCursor();
  void setFullLineHeightCursor(boolean fullLineHeightCursor);

  boolean isCaretRowShown();
  void setCaretRowShown(boolean caretRowShown);

  int getLineCursorWidth();
  void setLineCursorWidth(int width);

  boolean isAnimatedScrolling();
  void setAnimatedScrolling(boolean val);

  boolean isCamelWords();
  void setCamelWords(boolean val);
  /** Allows to remove 'use camel words' setup specific to the current settings object (if any) and use the shared one. */
  void resetCamelWords();

  boolean isAdditionalPageAtBottom();
  void setAdditionalPageAtBottom(boolean val);

  boolean isDndEnabled();
  void setDndEnabled(boolean val);

  boolean isWheelFontChangeEnabled();
  void setWheelFontChangeEnabled(boolean val);
  void resetWheelFontChangeEnabled();

  boolean isMouseClickSelectionHonorsCamelWords();
  void setMouseClickSelectionHonorsCamelWords(boolean val);


  boolean isVariableInplaceRenameEnabled();
  void setVariableInplaceRenameEnabled(boolean val);

  boolean isRefrainFromScrolling();
  void setRefrainFromScrolling(boolean b);

  boolean isIndentGuidesShown();
  void setIndentGuidesShown(boolean val);

  boolean isUseSoftWraps();
  void setUseSoftWraps(boolean use);
  boolean isAllSoftWrapsShown();

  default boolean isPaintSoftWraps() {
    return true;
  }
  default void setPaintSoftWraps(boolean val) {}

  boolean isUseCustomSoftWrapIndent();
  void setUseCustomSoftWrapIndent(boolean useCustomSoftWrapIndent);
  int getCustomSoftWrapIndent();
  void setCustomSoftWrapIndent(int indent);

  /**
   * @see #setAllowSingleLogicalLineFolding(boolean)
   */
  boolean isAllowSingleLogicalLineFolding();

  /**
   * By default, gutter mark (for collapsing/expanding the region using mouse) is not shown for a folding region, if it's contained within
   * a single document line. If overridden by the call to this method, marks will be displayed for such a region if it occupies multiple
   * visual lines (due to soft wrapping). Displaying a gutter mark can be also enabled for a region unconditionally using
   * {@link FoldRegion#setGutterMarkEnabledForSingleLine(boolean)}.
   */
  void setAllowSingleLogicalLineFolding(boolean allow);

  boolean isPreselectRename();
  void setPreselectRename(final boolean val);

  boolean isShowIntentionBulb();
  void setShowIntentionBulb(boolean show);

  /**
   * Sets the language which determines certain editor settings (right margin and soft margins, 'wrap on reaching right margin').
   *
   * @see #getRightMargin(Project)
   * @see #getSoftMargins()
   * @see #isWrapWhenTypingReachesRightMargin(Project)
   */
  void setLanguageSupplier(@Nullable Supplier<? extends Language> languageSupplier);

  boolean isShowingSpecialChars();
  void setShowingSpecialChars(boolean value);

  LineNumerationType getLineNumerationType();
  void setLineNumerationType(LineNumerationType value);

  boolean isInsertParenthesesAutomatically();

  boolean areStickyLinesShown();
  int getStickyLinesLimit();

  /**
   * Returns the width of single-width characters
   *
   * @see #setCharacterGridWidthMultiplier(Float)
   * @return the width of single-width characters or {@code null} if cell grid alignment is disabled
   */
  @ApiStatus.Internal
  @Nullable Float getCharacterGridWidthMultiplier();

  /**
   * Sets the width of single-width characters and enables the character grid mode
   * <p>
   *   Settings this to a non-null value enables a special mode in which the editor tries to align all
   *   characters to a grid of cells with their width determined by multiplying this value
   *   by {@link com.intellij.openapi.editor.impl.view.EditorView#getMaxCharWidth() EditorView.getMaxCharWidth}.
   *   Double-width characters take two cells each.
   * </p>
   * <p>
   *   If this mode is enabled, all kinds of grid properties can be queried by calling
   *   {@link com.intellij.openapi.editor.impl.EditorImpl#getCharacterGrid()}.
   * </p>
   * @param value the width of single-width characters or {@code null} to disable cell grid alignment
   */
  @ApiStatus.Internal
  void setCharacterGridWidthMultiplier(@Nullable Float value);

  enum LineNumerationType {
    ABSOLUTE,
    RELATIVE,
    HYBRID,
  }
}
