package org.jenkinsci.plugins.bbprb;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.ParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

import org.jenkinsci.plugins.bbprb.bitbucket.ApiClient;
import org.jenkinsci.plugins.bbprb.bitbucket.BuildState;

public class BitbucketBuildTrigger extends Trigger<AbstractProject<?, ?>> {
  private final String ciKey;
  private final String ciName;
  private final String credentialsId;
  private final String destinationRepository;
  private final boolean cancelOutdatedJobs;

  // XXX: This is for Jelly.
  // https://wiki.jenkins.io/display/JENKINS/Basic+guide+to+Jelly+usage+in+Jenkins
  public String getCiKey() {
    return this.ciKey;
  }
  public String getCiName() {
    return this.ciName;
  }
  public String getCredentialsId() {
    return this.credentialsId;
  }
  public String getDestinationRepository() {
    return this.destinationRepository;
  }
  public boolean getCancelOutdatedJobs() {
    return this.cancelOutdatedJobs;
  }

  private transient ApiClient apiClient;

  private static final transient ArrayList<String> bbprbSafeParameters =
      new ArrayList<String>() {
        {
          add("bbprbDestinationBranch");
          add("bbprbDestinationCommitHash");
          add("bbprbDestinationRepository");
          add("bbprbPullRequestAuthor");
          add("bbprbPullRequestId");
          add("bbprbPullRequestTitle");
          add("bbprbSourceBranch");
          add("bbprbSourceCommitHash");
          add("bbprbSourceRepository");
        }
      };

  public static final BitbucketBuildTriggerDescriptor descriptor =
      new BitbucketBuildTriggerDescriptor();

  @DataBoundConstructor
  public BitbucketBuildTrigger(String credentialsId,
                               String destinationRepository, String ciKey,
                               String ciName, boolean cancelOutdatedJobs)
      throws ANTLRException {
    super();
    this.apiClient = null;
    this.cancelOutdatedJobs = cancelOutdatedJobs;
    this.ciKey = ciKey;
    this.ciName = ciName;
    this.credentialsId = credentialsId;
    this.destinationRepository = destinationRepository;
  }

  @Override
  public void start(AbstractProject<?, ?> project, boolean newInstance) {
    logger.log(Level.FINE, "Started for `{0}`", project.getFullName());

    super.start(project, newInstance);

    if (credentialsId != null && !credentialsId.isEmpty()) {
      logger.log(Level.FINE, "Looking up credentials `{0}`",
                 this.credentialsId);
      List<UsernamePasswordCredentials> all =
          CredentialsProvider.lookupCredentials(
              UsernamePasswordCredentials.class, (Item)null, ACL.SYSTEM,
              URIRequirementBuilder.fromUri("https://bitbucket.org").build());
      UsernamePasswordCredentials creds = CredentialsMatchers.firstOrNull(
          all, CredentialsMatchers.withId(this.credentialsId));
      if (creds != null) {
        logger.log(Level.INFO, "Creating Bitbucket API client");
        this.apiClient = new ApiClient(creds.getUsername(),
                                       creds.getPassword().getPlainText(),
                                       this.ciKey, this.ciName);
      } else {
        logger.log(Level.SEVERE, "Credentials `{0}` not found",
                   this.credentialsId);
      }
    } else {
      logger.log(Level.WARNING, "Missing Bitbucket API credentials");
    }
  }

  public void setPRState(BitbucketCause cause, BuildState state, String path) {
    if (this.apiClient != null) {
      logger.log(Level.INFO, "Setting status of PR #{0} to {1} for {2}",
                 new Object[] {cause.getPullRequestId(), state,
                               cause.getDestinationRepository()});
      this.apiClient.setBuildStatus(
          cause.getSourceRepository(), cause.getSourceCommitHash(), state,
          getInstance().getRootUrl() + path, null, this.job.getFullName());
    } else {
      logger.log(Level.INFO,
                 "Will not set Bitbucket PR build status (not configured)");
    }
  }

  private void startJob(BitbucketCause cause) {

    List<ParameterValue> bbprb = new ArrayList<>();

    bbprb.add(new StringParameterValue("bbprbDestinationBranch",
                                       cause.getDestinationBranch()));
    bbprb.add(new StringParameterValue("bbprbDestinationCommitHash",
                                       cause.getDestinationCommitHash()));
    bbprb.add(new StringParameterValue("bbprbDestinationRepository",
                                       cause.getDestinationRepository()));
    bbprb.add(new StringParameterValue("bbprbPullRequestAuthor",
                                       cause.getPullRequestAuthor()));
    bbprb.add(new StringParameterValue("bbprbPullRequestId",
                                       cause.getPullRequestId()));
    bbprb.add(new StringParameterValue("bbprbPullRequestTitle",
                                       cause.getPullRequestTitle()));
    bbprb.add(
        new StringParameterValue("bbprbSourceBranch", cause.getSourceBranch()));
    bbprb.add(new StringParameterValue("bbprbSourceCommitHash",
                                       cause.getSourceCommitHash()));
    bbprb.add(new StringParameterValue("bbprbSourceRepository",
                                       cause.getSourceRepository()));

    setPRState(cause, BuildState.INPROGRESS, this.job.getUrl());

    this.job.scheduleBuild2(0, cause,
                            new ParametersAction(bbprb, bbprbSafeParameters));
  }

  private Jenkins getInstance() {
    final Jenkins instance = Jenkins.getInstance();
    if (instance == null) {
      throw new IllegalStateException("Jenkins instance is NULL!");
    }
    return instance;
  }

  private boolean
  hasCauseFromTheSamePullRequest(@Nullable List<Cause> causes,
                                 @Nullable BitbucketCause pullRequestCause) {
    if (causes != null && pullRequestCause != null) {
      for (Cause cause : causes) {
        if (cause instanceof BitbucketCause) {
          BitbucketCause sc = (BitbucketCause)cause;
          if (StringUtils.equals(sc.getPullRequestId(),
                                 pullRequestCause.getPullRequestId()) &&
              StringUtils.equals(sc.getSourceRepository(),
                                 pullRequestCause.getSourceRepository())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void cancelPR(BitbucketCause cause) {
    SecurityContext orig = ACL.impersonate(ACL.SYSTEM);

    logger.log(Level.FINE, "Looking for queued jobs that match PR #{0}",
               cause.getPullRequestId());
    Queue queue = getInstance().getQueue();
    for (Queue.Item item : queue.getItems()) {
      if (hasCauseFromTheSamePullRequest(item.getCauses(), cause)) {
        logger.fine("Canceling item in queue: " + item);
        queue.cancel(item);
      }
    }

    logger.log(Level.FINE, "Looking for running jobs that match PR #{0}",
               cause.getPullRequestId());
    for (Object o : job.getBuilds()) {
      if (o instanceof Run) {
        Run build = (Run)o;
        if (build.isBuilding() &&
            hasCauseFromTheSamePullRequest(build.getCauses(), cause)) {
          logger.fine("Aborting '" + build + "' since the PR is outdated");
          try {
            build.setDescription("Aborted build since the PR is outdated");
          } catch (IOException e) {
            logger.warning("Could not set build description: " +
                           e.getMessage());
          }
          final Executor executor = build.getExecutor();
          if (executor == null) {
            throw new IllegalStateException("Executor can't be NULL");
          }
          executor.interrupt(Result.ABORTED);
        }
      }
    }
    SecurityContextHolder.setContext(orig);
  }

  public void handlePR(String event, JSONObject pr) {

    JSONObject src = pr.getJSONObject("source");
    JSONObject dst = pr.getJSONObject("destination");
    String dstRepository =
        dst.getJSONObject("repository").getString("full_name");
    if (!dstRepository.equals(this.destinationRepository)) {
      logger.log(Level.FINE,
                 "Job `{0}`: repository `{1}` does not match `{2}`. Skipping.",
                 new Object[] {this.job.getFullName(), dstRepository,
                               this.destinationRepository});
      return;
    }
    BitbucketCause cause = new BitbucketCause(
        src.getJSONObject("branch").getString("name"),
        dst.getJSONObject("branch").getString("name"),
        src.getJSONObject("repository").getString("full_name"),
        pr.getString("id"), // FIXME: it is integer
        dstRepository, pr.getString("title"),
        src.getJSONObject("commit").getString("hash"),
        dst.getJSONObject("commit").getString("hash"),
        pr.getJSONObject("author").getString("display_name"));
    switch (event) {
      case "pullrequest:created":
        startJob(cause);
        break;
      case "pullrequest:updated":
        if (this.cancelOutdatedJobs) {
          cancelPR(cause);
        }
        startJob(cause);
        break;
      default:
        logger.log(Level.WARNING, "Unhandled event: `{0}`",
                   new Object[] {event});
    }
  }

  @Extension
  @Symbol("bbprb")
  public static final class BitbucketBuildTriggerDescriptor
      extends TriggerDescriptor {
    public BitbucketBuildTriggerDescriptor() {
      load();
    }

    @Override
    public boolean isApplicable(Item item) {
      return item instanceof AbstractProject;
    }

    @Override
    public String getDisplayName() {
      return "Bitbucket Pull Requests Builder";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
        throws FormException {
      save();
      return super.configure(req, json);
    }

    public ListBoxModel doFillCredentialsIdItems() {
      return new StandardListBoxModel().withEmptySelection().withMatching(
          instanceOf(UsernamePasswordCredentials.class),
          CredentialsProvider.lookupCredentials(
              StandardUsernamePasswordCredentials.class));
    }
  }
  private static final Logger logger =
      Logger.getLogger(BitbucketBuildTrigger.class.getName());
}
