// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.DeleteChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class FileListeningTest extends IntegrationTestCase {
  static final String IGNORED_EXTENSION = "pyc";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assertTrue(
      "Aaaah, extension '" + IGNORED_EXTENSION + "' is no longer ignored. Please pick another ignored extension, tests count on you!",
      FileTypeManager.getInstance().isFileIgnored("x." + IGNORED_EXTENSION));
  }

  public void testCreatingFiles() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getChangesFor(f).size());
  }

  public void testCreatingDirectories() throws IOException {
    VirtualFile f = createDirectory("dir");
    assertEquals(1, getChangesFor(f).size());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    int before = getChangesFor(myRoot).size();
    createFile("file." + IGNORED_EXTENSION);

    assertEquals(before, getChangesFor(myRoot).size());
  }

  public void testIgnoringFilteredDirectories() throws IOException {
    int before = getChangesFor(myRoot).size();

    createDirectory(FILTERED_DIR_NAME);
    assertEquals(before, getChangesFor(myRoot).size());
  }

  public void testIgnoringFilesRecursively() throws IOException {
    String excluded = "dir/excluded";
    addExcludedDir(myRoot.getPath() + "/" + excluded);
    String contentUnderExcluded = excluded + "/content";
    addContentRoot(createModule("foo"), myRoot.getPath() + "/" + contentUnderExcluded);

    String dir = createDirectoryExternally("dir");
    String dir1_fTxt = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    String contentUnderExcludedPath = createDirectoryExternally(contentUnderExcluded);
    String contentUnderExcluded_fTxt = createFileExternally(contentUnderExcluded + "/f.txt");
    createDirectoryExternally(excluded + "/subsubdir2");
    createFileExternally(excluded + "/subsubdir2/f.txt");

    myRoot.refresh(false, true);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    List<String> actual = new SmartList<>();
    for (Change each : changes) {
      actual.add(((StructuralChange)each).getPath());
    }

    List<String> expected = new ArrayList<>(Arrays.asList(dir, contentUnderExcludedPath, dir1_fTxt, contentUnderExcluded_fTxt));

    Collections.sort(actual);
    Collections.sort(expected);
    assertOrderedEquals(actual, expected);

    // ignored folders should not be loaded in VFS
    assertEquals("""
                   dir
                    excluded
                     content
                      f.txt
                    f.class
                    f.txt
                   """
      , buildDBFileStructure(myRoot, 0, new StringBuilder()).toString()
    );
  }

  private static StringBuilder buildDBFileStructure(@NotNull VirtualFile from, int level, @NotNull StringBuilder builder) {
    List<VirtualFile> children = new ArrayList<>(((NewVirtualFile)from).getCachedChildren());
    Collections.sort(children, Comparator.comparing(VirtualFile::getName));
    for (VirtualFile eachChild : children) {
      builder.append(StringUtil.repeat(" ", level)).append(eachChild.getName()).append("\n");
      buildDBFileStructure(eachChild, level + 1, builder);
    }
    return builder;
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getChangesFor(f).size());

    setBinaryContent(f, new byte[]{1});
    assertEquals(2, getChangesFor(f).size());

    setBinaryContent(f, new byte[]{2});
    assertEquals(3, getChangesFor(f).size());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getChangesFor(f).size());

    rename(f, "file2.txt");
    assertEquals(2, getChangesFor(f).size());
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    VirtualFile file = createFile("old.txt");
    int[] log = new int[2];
    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void beforePropertyChange(@NotNull VirtualFilePropertyEvent e) {
        log[0] = getChangesFor(file).size();
      }
    };

    assertEquals(1, getChangesFor(file).size());

    addFileListenerDuring(l, () -> rename(file, "new.txt"));

    assertEquals(1, log[0]);
    assertEquals(2, getChangesFor(file).size());
  }

  public void testRenamingFilteredFileToNonFiltered() throws Exception {
    int before = getChangesFor(myRoot).size();

    VirtualFile file = createFile("file." + IGNORED_EXTENSION);
    assertThat(getChangesFor(myRoot)).hasSize(before);

    rename(file, "file.txt");
    assertThat(getChangesFor(myRoot)).hasSize(before + 1);
    assertThat(getChangesFor(file)).hasSize(3);
  }

  public void testRenamingNonFilteredFileToFiltered() throws Exception {
    int before = getChangesFor(myRoot).size();

    VirtualFile f = createFile("file.txt");
    assertEquals(before + 1, getChangesFor(myRoot).size());

    rename(f, "file." + IGNORED_EXTENSION);
    assertEquals(before + 2, getChangesFor(myRoot).size());
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    int before = getChangesFor(myRoot).size();

    VirtualFile file = createFile(FILTERED_DIR_NAME);
    assertEquals(before, getChangesFor(myRoot).size());

    rename(file, "not_filtered");
    assertEquals(before + 1, getChangesFor(myRoot).size());

    assertThat(getChangesFor(file)).hasSize(3);
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() {
    int before = getChangesFor(myRoot).size();

    VirtualFile f = createDirectory("not_filtered");
    assertEquals(before + 1, getChangesFor(myRoot).size());

    rename(f, FILTERED_DIR_NAME);
    assertEquals(before + 2, getChangesFor(myRoot).size());
  }

  public void testChangingROStatusForFile() throws Exception {
    VirtualFile f = createFile("f.txt");
    assertEquals(1, getChangesFor(f).size());

    setReadOnlyAttribute(f, true);
    assertEquals(2, getChangesFor(f).size());

    setReadOnlyAttribute(f, false);
    assertEquals(3, getChangesFor(f).size());
  }

  public void testIgnoringROStatusChangeForUnversionedFiles() throws Exception {
    int before = getChangesFor(myRoot).size();

    VirtualFile f = createFile("f." + IGNORED_EXTENSION);
    setReadOnlyAttribute(f, true);

    assertEquals(before, getChangesFor(myRoot).size());
  }

  private static void setReadOnlyAttribute(VirtualFile f, boolean status) throws IOException {
    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(f, status); // shouldn't throw
      return null;
    });
  }

  public void testDeletion() throws IOException {
    VirtualFile f = createDirectory("f.txt");

    int before = getChangesFor(myRoot).size();

    delete(f);
    assertEquals(before + 1, getChangesFor(myRoot).size());
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws IOException {
    int before = getChangesFor(myRoot).size();

    VirtualFile f = createDirectory(FILTERED_DIR_NAME);
    delete(f);
    assertEquals(before, getChangesFor(myRoot).size());
  }

  public void testDeletionDoesNotVersionIgnoredFilesRecursively() throws IOException {
    String dir1 = createDirectoryExternally("dir");
    createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    createDirectoryExternally("dir/subdir/subdir2");
    createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(myRoot.getPath() + "/dir/subdir/subdir2");

    final VirtualFile vDir1 = LocalFileSystem.getInstance().findFileByPath(dir1);
    assertNotNull(dir1, vDir1);
    delete(vDir1);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(1, changes.size());
    Entry e = ((DeleteChange)changes.get(0)).getDeletedEntry();
    List<Entry> children = e.getChildren();
    children = sortEntries(children);
    assertEquals(2, children.size());
    assertEquals("f.txt", children.get(0).getName());
    assertEquals("subdir", children.get(1).getName());
    assertEquals(1, children.get(1).getChildren().size());
    assertEquals("subdir2", children.get(1).getChildren().get(0).getName());
  }

  public void testCreationAndDeletionOfUnversionedFile() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<ChangeSet> changes = getChangesFor(myRoot);
    assertEquals(4, changes.size());
    assertNotNull(getCurrentEntry(myRoot).findEntry("dir/subDir/file.txt"));
    assertNull(getEntryFor(changes.get(0), myRoot).findEntry("dir/subDir/file.txt"));
    assertNotNull(getEntryFor(changes.get(1), myRoot).findEntry("dir/subDir/file.txt"));
    assertNull(getEntryFor(changes.get(2), myRoot).findEntry("dir/subDir/file.txt"));
  }

  public void testCreationAndDeletionOfFileUnderUnversionedDir() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir/subDir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<ChangeSet> revs = getChangesFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(getCurrentEntry(myRoot).findEntry("dir/subDir/file.txt"));
    assertNull(getEntryFor(revs.get(0), myRoot).findEntry("dir/subDir"));
    assertNotNull(getEntryFor(revs.get(1), myRoot).findEntry("dir/subDir/file.txt"));
    assertNull(getEntryFor(revs.get(2), myRoot).findEntry("dir/subDir"));
  }

  private static @Unmodifiable @NotNull List<Entry> sortEntries(@Unmodifiable List<Entry> entries) {
    return ContainerUtil.sorted(entries, Comparator.comparing(Entry::getName));
  }
}
