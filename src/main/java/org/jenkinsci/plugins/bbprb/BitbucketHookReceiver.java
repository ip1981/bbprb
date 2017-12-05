package org.jenkinsci.plugins.bbprb;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public class BitbucketHookReceiver implements UnprotectedRootAction {

  private static final String BITBUCKET_HOOK_URL = "bbprb-hook";
  private static final String BITBUCKET_UA = "Bitbucket-Webhooks/2.0";

  public void doIndex(StaplerRequest req, StaplerResponse resp)
      throws IOException {

    String userAgent = req.getHeader("user-agent");
    if (!BITBUCKET_UA.equals(userAgent)) {
      LOGGER.log(Level.WARNING, "Bad user agent: `{0}`, expected `{1}`",
                 new Object[] {userAgent, BITBUCKET_UA});
      resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
      return;
    }

    String uri = req.getRequestURI();
    if (!uri.contains("/" + BITBUCKET_HOOK_URL + "/")) {
      LOGGER.log(Level.WARNING,
                 "BitBucket hook URI does not contain `/{0}/`: `{1}`",
                 new Object[] {BITBUCKET_HOOK_URL, uri});
      resp.setStatus(StaplerResponse.SC_NOT_FOUND);
      return;
    }

    String event = req.getHeader("x-event-key");
    if (event == null) {
      LOGGER.log(Level.WARNING, "Missing the `x-event-key` header");
      resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
      return;
    }

    String body = IOUtils.toString(req.getInputStream());
    if (body.isEmpty()) {
      LOGGER.log(Level.WARNING, "Received empty request body");
      resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
      return;
    }

    String contentType = req.getContentType();
    if (contentType != null &&
        contentType.startsWith("application/x-www-form-urlencoded")) {
      body = URLDecoder.decode(body, "UTF-8");
    }
    if (body.startsWith("payload=")) {
      body = body.substring(8);
    }

    LOGGER.log(Level.FINE,
               "Received commit hook notification, key: `{0}`, body: `{1}`",
               new Object[] {event, body});

    try {
      JSONObject payload = JSONObject.fromObject(body);
      if (event.startsWith("pullrequest:")) {
        JSONObject pr = payload.getJSONObject("pullrequest");
        String state = pr.getString("state");
        if (!"OPEN".equals(state)) {
          LOGGER.log(
              Level.INFO, "Ignoring closed PR ({0}): #{1} {2}",
              new Object[] {state, pr.getInt("id"), pr.getString("title")});
          return;
        }
        for (BitbucketBuildTrigger trigger : getBitbucketTriggers()) {
          trigger.handlePR(pr);
        }
        return;
      }
    } catch (JSONException e) {
      LOGGER.log(Level.WARNING, e.getMessage());
      resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
      return;
    }
  }

  private static List<BitbucketBuildTrigger> getBitbucketTriggers() {
    List<BitbucketBuildTrigger> bbtriggers = new ArrayList<>();

    SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
    List<ParameterizedJob> jobs =
        Jenkins.getInstance().getAllItems(ParameterizedJob.class);
    SecurityContextHolder.setContext(orig);

    for (ParameterizedJob job : jobs) {
      String jobName = job.getFullName();
      LOGGER.log(Level.FINER, "Found job: `{0}`", jobName);

      Map<TriggerDescriptor, Trigger<?>> triggers = job.getTriggers();

      for (Trigger<?> trigger : triggers.values()) {
        if (trigger instanceof BitbucketBuildTrigger) {
          LOGGER.log(Level.FINE, "Will consider job: `{0}`", jobName);
          bbtriggers.add((BitbucketBuildTrigger)trigger);
        }
      }
    }

    return bbtriggers;
  }

  private static final Logger LOGGER =
      Logger.getLogger(BitbucketHookReceiver.class.getName());

  public String getIconFileName() {
    return null;
  }

  public String getDisplayName() {
    return null;
  }

  public String getUrlName() {
    return BITBUCKET_HOOK_URL;
  }
}
