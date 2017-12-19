package org.jenkinsci.plugins.bbprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;

import java.io.IOException;

@Extension
public class BitbucketEnv extends EnvironmentContributor {
  @Override
  public void buildEnvironmentFor(Run run, EnvVars envVars,
                                  TaskListener taskListener)
      throws IOException, InterruptedException {

    BitbucketCause cause = (BitbucketCause)run.getCause(BitbucketCause.class);
    if (cause == null) {
      return;
    }

    putEnvVar(envVars, "bbprbDestinationBranch", cause.getDestinationBranch());
    putEnvVar(envVars, "bbprbDestinationCommitHash",
              cause.getDestinationCommitHash());
    putEnvVar(envVars, "bbprbDestinationRepository",
              cause.getDestinationRepository());
    putEnvVar(envVars, "bbprbPullRequestAuthor", cause.getPullRequestAuthor());
    putEnvVar(envVars, "bbprbPullRequestId", cause.getPullRequestId());
    putEnvVar(envVars, "bbprbPullRequestTitle", cause.getPullRequestTitle());
    putEnvVar(envVars, "bbprbSourceBranch", cause.getSourceBranch());
    putEnvVar(envVars, "bbprbSourceCommitHash", cause.getSourceCommitHash());
    putEnvVar(envVars, "bbprbSourceRepository", cause.getSourceRepository());
  }

  private static void putEnvVar(EnvVars envs, String name, String value) {
    envs.put(name, getString(value, ""));
  }

  private static String getString(String actual, String d) {
    return actual == null ? d : actual;
  }
}
