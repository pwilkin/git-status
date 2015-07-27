package com.syndatis.idea.gitstatus;

import com.google.common.collect.Sets;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Pair;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.apache.commons.lang.StringUtils;
import zielu.gittoolbox.UtfSeq;
import zielu.gittoolbox.status.GitAheadBehindCount;
import zielu.gittoolbox.status.Status;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by pwilkin on 30.05.2015.
 */
public class GitStatusViewNodeDecorator implements ProjectViewNodeDecorator {

    IdentityHashMap<GitRepository, Pair<Hash, Hash>> hashes = new IdentityHashMap<GitRepository, Pair<Hash, Hash>>();
    IdentityHashMap<GitRepository, String> cachedInfo = new IdentityHashMap<GitRepository, String>();

    @Override
    public void decorate(ProjectViewNode projectViewNode, PresentationData presentationData) {
        if (projectViewNode.getVirtualFile() != null && ProjectRootsUtil.isModuleContentRoot(projectViewNode.getVirtualFile(), projectViewNode.getProject())) {
            GitRepositoryManager repoManager = GitUtil.getRepositoryManager(projectViewNode.getProject());
            if (repoManager.getRepositoryForFile(projectViewNode.getVirtualFile()) != null) {
                StringBuilder sb = new StringBuilder();
                if (StringUtils.isNotBlank(presentationData.getLocationString())) {
                    sb.append(presentationData.getLocationString()).append(" - ");
                }
                GitRepository repo = repoManager.getRepositoryForFile(projectViewNode.getVirtualFile());
                GitLocalBranch currentBranch;
                if (repo != null) {
                    currentBranch = repo.getInfo().getCurrentBranch();
                    if (currentBranch != null) { // might be when rebasing or merging
                        GitBranchTrackInfo ti = GitUtil.getTrackInfoForCurrentBranch(repo);
                        sb.append(currentBranch.getName());
                        if (ti != null) {
                            if (hashes.containsKey(repo) && Pair.create(currentBranch.getHash(), ti.getRemoteBranch().getHash()).equals(hashes.get(repo))) {
                                presentationData.setLocationString(cachedInfo.get(repo));
                            } else {
                                ProgressIndicator progress = new ProgressIndicatorBase();
                                GitStatusCalculator calc = GitStatusCalculator.create(projectViewNode.getProject(), progress);
                                Map<GitRepository, GitAheadBehindCount> gitRepositoryGitAheadBehindStatusMap = calc.aheadBehindStatus(Sets.newHashSet(repo));
                                GitAheadBehindCount status = gitRepositoryGitAheadBehindStatusMap.get(repo);
                                if (status.ahead.status() == Status.Success && status.ahead.value() > 0) {
                                    sb.append(" ").append(status.ahead.value()).append(UtfSeq.arrowUp);
                                }
                                if (status.behind.status() == Status.Success && status.behind.value() > 0) {
                                    sb.append(" ").append(status.behind.value()).append(UtfSeq.arrowDown);
                                }
                                cachedInfo.put(repo, sb.toString());
                                hashes.put(repo, Pair.create(currentBranch.getHash(), ti.getRemoteBranch().getHash()));
                                presentationData.setLocationString(sb.toString());
                            }
                        }
                        presentationData.setChanged(true);
                    }
                }
            }
        }
    }

    @Override
    public void decorate(PackageDependenciesNode packageDependenciesNode, ColoredTreeCellRenderer coloredTreeCellRenderer) {

    }
}
