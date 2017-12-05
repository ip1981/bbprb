package org.jenkinsci.plugins.bbprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;

import java.io.IOException;

@Extension
public class BitbucketAdditionalParameterEnvironmentContributor
    extends EnvironmentContributor {
  @Override
  public void buildEnvironmentFor(Run run, EnvVars envVars,
                                  TaskListener taskListener)
      throws IOException, InterruptedException {

    BitbucketCause cause = (BitbucketCause)run.getCause(BitbucketCause.class);
    if (cause == null) {
      return;
    }

    putEnvVar(envVars, "destinationRepository",
              cause.getDestinationRepository());
    putEnvVar(envVars, "pullRequestAuthor", cause.getPullRequestAuthor());
    putEnvVar(envVars, "pullRequestId", cause.getPullRequestId());
    putEnvVar(envVars, "pullRequestTitle", cause.getPullRequestTitle());
    putEnvVar(envVars, "sourceBranch", cause.getSourceBranch());
    putEnvVar(envVars, "sourceRepository", cause.getSourceRepository());
    putEnvVar(envVars, "targetBranch", cause.getTargetBranch());
  }

  private static void putEnvVar(EnvVars envs, String name, String value) {
    envs.put(name, getString(value, ""));
  }

  private static String getString(String actual, String d) {
    return actual == null ? d : actual;
  }
}
