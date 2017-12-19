package org.jenkinsci.plugins.bbprb;

import hudson.model.Cause;

/**
 * Created by nishio
 */
public class BitbucketCause extends Cause {
  private final String sourceBranch;
  private final String destinationBranch;
  private final String sourceRepository;
  private final String pullRequestId;
  private final String destinationRepository;
  private final String pullRequestTitle;
  private final String sourceCommitHash;
  private final String destinationCommitHash;
  private final String pullRequestAuthor;
  public static final String BITBUCKET_URL = "https://bitbucket.org/";

  public BitbucketCause(String sourceBranch, String destinationBranch,
                        String sourceRepository, String pullRequestId,
                        String destinationRepository, String pullRequestTitle,
                        String sourceCommitHash, String destinationCommitHash,
                        String pullRequestAuthor) {
    this.sourceBranch = sourceBranch;
    this.destinationBranch = destinationBranch;
    this.sourceRepository = sourceRepository;
    this.pullRequestId = pullRequestId;
    this.destinationRepository = destinationRepository;
    this.pullRequestTitle = pullRequestTitle;
    this.sourceCommitHash = sourceCommitHash;
    this.destinationCommitHash = destinationCommitHash;
    this.pullRequestAuthor = pullRequestAuthor;
  }

  public String getSourceBranch() {
    return sourceBranch;
  }
  public String getDestinationBranch() {
    return destinationBranch;
  }

  public String getSourceRepository() {
    return sourceRepository;
  }

  public String getPullRequestId() {
    return pullRequestId;
  }

  public String getDestinationRepository() {
    return destinationRepository;
  }

  public String getPullRequestTitle() {
    return pullRequestTitle;
  }

  public String getSourceCommitHash() {
    return sourceCommitHash;
  }

  public String getDestinationCommitHash() {
    return destinationCommitHash;
  }

  @Override
  public String getShortDescription() {
    String description =
        "<a href=\"" + BITBUCKET_URL + this.getDestinationRepository();
    description += "/pull-request/" + this.getPullRequestId();
    description += "\">#" + this.getPullRequestId() + " " +
                   this.getPullRequestTitle() + "</a>";
    return description;
  }

  public String getPullRequestAuthor() {
    return this.pullRequestAuthor;
  }
}
