package com.syndatis.idea.gitstatus;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import zielu.gittoolbox.status.GitAheadBehindCount;
import zielu.gittoolbox.status.GitRevListCounter;
import zielu.gittoolbox.status.RevListCount;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class GitStatusCalculator {
    private final Project myProject;
    private final ProgressIndicator myIndicator;

    private GitStatusCalculator(Project project, ProgressIndicator indicator) {
        this.myProject = Preconditions.checkNotNull(project);
        this.myIndicator = Preconditions.checkNotNull(indicator);
    }

    public static GitStatusCalculator create(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        return new GitStatusCalculator(project, indicator);
    }

    public Map<GitRepository, RevListCount> behindStatus(Collection<GitRepository> repositories) {
        LinkedHashMap result = Maps.newLinkedHashMap();
        Iterator i$ = repositories.iterator();

        while(i$.hasNext()) {
            GitRepository repository = (GitRepository)i$.next();
            result.put(repository, this.behindStatus(repository));
        }

        return result;
    }

    public Map<GitRepository, GitAheadBehindCount> aheadBehindStatus(Collection<GitRepository> repositories) {
        LinkedHashMap result = Maps.newLinkedHashMap();
        Iterator i$ = repositories.iterator();

        while(i$.hasNext()) {
            GitRepository repository = (GitRepository)i$.next();
            result.put(repository, this.aheadBehindStatus(repository));
        }

        return result;
    }

    private RevListCount behindStatus(GitRepository repository) {
        Optional trackInfo = this.trackInfoForCurrentBranch(repository);
        return trackInfo.isPresent()?this.behindStatus(repository.getCurrentBranch(), (GitBranchTrackInfo)trackInfo.get(), repository):RevListCount.noRemote();
    }

    private GitAheadBehindCount aheadBehindStatus(GitRepository repository) {
        Optional trackInfo = this.trackInfoForCurrentBranch(repository);
        return trackInfo.isPresent()?this.aheadBehindStatus(repository.getCurrentBranch(), (GitBranchTrackInfo)trackInfo.get(), repository): GitAheadBehindCount.noRemote();
    }

    private RevListCount behindStatus(GitLocalBranch currentBranch, GitBranchTrackInfo trackInfo, GitRepository repository) {
        String localName = currentBranch.getName();
        String remoteName = trackInfo.getRemoteBranch().getNameForLocalOperations();
        return this.behindCount(localName, remoteName, repository);
    }

    private Optional<GitBranchTrackInfo> trackInfoForCurrentBranch(GitRepository repository) {
        GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
        return Optional.fromNullable(trackInfo);
    }

    private GitAheadBehindCount aheadBehindStatus(GitLocalBranch localBranch, GitBranchTrackInfo trackInfo, GitRepository repository) {
        String localName = localBranch.getName();
        String remoteName = trackInfo.getRemoteBranch().getNameForLocalOperations();
        RevListCount behind = this.behindCount(localName, remoteName, repository);
        RevListCount ahead = this.aheadCount(localName, remoteName, repository);
        return GitAheadBehindCount.success(ahead.value(), behind.value());
    }

    private RevListCount behindCount(String localName, String remoteName, GitRepository repository) {
        return this.doRevListCount(localName + ".." + remoteName, repository);
    }

    private RevListCount aheadCount(String localName, String remoteName, GitRepository repository) {
        return this.doRevListCount(remoteName + ".." + localName, repository);
    }

    private RevListCount doRevListCount(String branches, GitRepository repository) {
        GitLineHandler handler = new GitLineHandler(this.myProject, repository.getRoot(), GitCommand.REV_LIST);
        handler.addParameters(new String[]{branches, "--count"});
        final GitRevListCounter counter = new GitRevListCounter();
        handler.addLineListener(counter);
        GitTask task = new GitTask(this.myProject, handler, branches);
        task.setProgressIndicator(this.myIndicator);
        final AtomicReference result = new AtomicReference();
        task.execute(true, false, new GitTaskResultHandlerAdapter() {
            protected void onSuccess() {
                result.set(RevListCount.success(counter.count()));
            }

            protected void onCancel() {
                result.set(RevListCount.cancel());
            }

            protected void onFailure() {
                result.set(RevListCount.failure());
            }
        });
        return (RevListCount)Preconditions.checkNotNull(result.get(), "Null rev list count");
    }
}