// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class BranchesLoader implements Runnable {
  private final @NotNull Project myProject;
  private final @NotNull NewRootBunch myBunch;
  private final @NotNull VirtualFile myRoot;
  private final @NotNull Url myUrl;
  private final @NotNull InfoReliability myInfoReliability;
  private final boolean myPassive;

  public BranchesLoader(@NotNull Project project,
                        @NotNull NewRootBunch bunch,
                        @NotNull Url url,
                        @NotNull InfoReliability infoReliability,
                        @NotNull VirtualFile root,
                        boolean passive) {
    myProject = project;
    myBunch = bunch;
    myUrl = url;
    myInfoReliability = infoReliability;
    myRoot = root;
    myPassive = passive;
  }

  @Override
  public void run() {
    try {
      List<SvnBranchItem> branches = loadBranches();
      myBunch.updateBranches(myRoot, myUrl, new InfoStorage<>(branches, myInfoReliability));
    }
    catch (VcsException e) {
      showError(e);
    }
  }

  public @NotNull List<SvnBranchItem> loadBranches() throws VcsException {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    List<SvnBranchItem> result = new LinkedList<>();
    Target target = Target.on(myUrl);
    DirectoryEntryConsumer handler = createConsumer(result);

    vcs.getFactory(target).create(BrowseClient.class, !myPassive).list(target, Revision.HEAD, Depth.IMMEDIATES, handler);

    Collections.sort(result);
    return result;
  }

  private void showError(Exception e) {
    // already logged inside
    if (InfoReliability.setByUser.equals(myInfoReliability)) {
      showOverChangesView(myProject, message("notification.content.branches.load.error", e.getMessage()), MessageType.ERROR);
    }
  }

  private static @NotNull DirectoryEntryConsumer createConsumer(final @NotNull List<SvnBranchItem> result) {
    return entry -> {
      if (entry.getDate() != null) {
        result.add(new SvnBranchItem(entry.getUrl(), entry.getDate().getTime(), entry.getRevision()));
      }
    };
  }
}
