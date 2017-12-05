package org.jenkinsci.plugins.bbprb;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.triggers.Trigger;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.Result;

import org.jenkinsci.plugins.bbprb.bitbucket.BuildState;

@Extension
public class BitbucketBuildListener extends RunListener<AbstractBuild<?, ?>> {

  @Override
  public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
    BitbucketCause cause = build.getCause(BitbucketCause.class);
    if (cause == null) {
      return;
    }

    BitbucketBuildTrigger trigger = extractTrigger(build);
    if (trigger == null) {
      return;
    }

    LOGGER.log(Level.FINE, "Started by BitbucketBuildTrigger");
    trigger.setPRState(cause, BuildState.INPROGRESS, build.getUrl());
    try {
      build.setDescription(
          build.getCause(BitbucketCause.class).getShortDescription());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not set build description: {0}",
                 e.getMessage());
    }
  }

  @Override
  public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
    BitbucketBuildTrigger trigger = extractTrigger(build);
    if (trigger != null) {
      LOGGER.log(Level.FINE, "Completed after BitbucketBuildTrigger");
      Result result = build.getResult();
      BuildState state = (result == Result.SUCCESS) ? BuildState.SUCCESSFUL
                                                    : BuildState.FAILED;
      BitbucketCause cause = build.getCause(BitbucketCause.class);
      trigger.setPRState(cause, state, build.getUrl());
    }
  }

  private static BitbucketBuildTrigger
  extractTrigger(AbstractBuild<?, ?> build) {
    BitbucketBuildTrigger trigger =
        build.getProject().getTrigger(BitbucketBuildTrigger.class);

    if ((trigger != null) && (trigger instanceof BitbucketBuildTrigger)) {
      return trigger;
    } else {
      return null;
    }
  }

  private static final Logger LOGGER =
      Logger.getLogger(BitbucketBuildListener.class.getName());
}
