package org.jenkinsci.plugins.bbprb.bitbucket;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.type.TypeReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.ProxyConfiguration;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.EncodingUtil;

/**
 * Created by nishio
 */
public class ApiClient {
  private static final Logger logger =
      Logger.getLogger(ApiClient.class.getName());
  private static final String V2_API_BASE_URL =
      "https://bitbucket.org/api/2.0/repositories/";
  private static final String COMPUTED_KEY_FORMAT = "%s-%s";
  private String repository;
  private Credentials credentials;
  private String key;
  private String name;
  private HttpClientFactory factory;

  public static final byte MAX_KEY_SIZE_BB_API = 40;

  public static class HttpClientFactory {
    public static final HttpClientFactory INSTANCE = new HttpClientFactory();
    private static final int DEFAULT_TIMEOUT = 60000;

    public HttpClient getInstanceHttpClient() {
      HttpClient client = new HttpClient();

      HttpClientParams params = client.getParams();
      params.setConnectionManagerTimeout(DEFAULT_TIMEOUT);
      params.setSoTimeout(DEFAULT_TIMEOUT);

      if (Jenkins.getInstance() == null)
        return client;

      ProxyConfiguration proxy = getInstance().proxy;
      if (proxy == null)
        return client;

      logger.log(Level.FINE, "Jenkins proxy: {0}:{1}",
                 new Object[] {proxy.name, proxy.port});
      client.getHostConfiguration().setProxy(proxy.name, proxy.port);
      String username = proxy.getUserName();
      String password = proxy.getPassword();

      // Consider it to be passed if username specified. Sufficient?
      if (username != null && !"".equals(username.trim())) {
        logger.log(Level.FINE, "Using proxy authentication (user={0})",
                   username);
        client.getState().setProxyCredentials(
            AuthScope.ANY, new UsernamePasswordCredentials(username, password));
      }

      return client;
    }

    private Jenkins getInstance() {
      final Jenkins instance = Jenkins.getInstance();
      if (instance == null) {
        throw new IllegalStateException("Jenkins instance is NULL!");
      }
      return instance;
    }
  }

  public <T extends HttpClientFactory> ApiClient(String username,
                                                 String password,
                                                 String repository, String key,
                                                 String name) {
    this.credentials = new UsernamePasswordCredentials(username, password);
    this.repository = repository;
    this.key = key;
    this.name = name;
    this.factory = HttpClientFactory.INSTANCE;
  }

  public String getName() {
    return this.name;
  }

  private static MessageDigest SHA1 = null;

  /**
   * Retrun
   * @param keyExPart
   * @return key parameter for call BitBucket API
   */
  private String computeAPIKey(String keyExPart) {
    String computedKey =
        String.format(COMPUTED_KEY_FORMAT, this.key, keyExPart);

    if (computedKey.length() > MAX_KEY_SIZE_BB_API) {
      try {
        if (SHA1 == null)
          SHA1 = MessageDigest.getInstance("SHA1");
        return new String(
            Hex.encodeHex(SHA1.digest(computedKey.getBytes("UTF-8"))));
      } catch (NoSuchAlgorithmException e) {
        logger.log(Level.WARNING, "Failed to create hash provider", e);
      } catch (UnsupportedEncodingException e) {
        logger.log(Level.WARNING, "Failed to create hash provider", e);
      }
    }
    return (computedKey.length() <= MAX_KEY_SIZE_BB_API)
        ? computedKey
        : computedKey.substring(0, MAX_KEY_SIZE_BB_API);
  }

  public String buildStatusKey(String bsKey) {
    return this.computeAPIKey(bsKey);
  }

  public void setBuildStatus(String revision, BuildState state, String buildUrl,
                             String comment, String keyEx) {
    String url = v2("/commit/" + revision + "/statuses/build");
    String computedKey = this.computeAPIKey(keyEx);
    NameValuePair[] data = new NameValuePair[] {
        new NameValuePair("description", comment),
        new NameValuePair("key", computedKey),
        new NameValuePair("name", this.name),
        new NameValuePair("state", state.toString()),
        new NameValuePair("url", buildUrl),
    };
    logger.log(Level.FINE,
               "POST state {0} to {1} with key {2} with response {3}",
               new Object[] {state, url, computedKey, post(url, data)});
  }

  private HttpClient getHttpClient() {
    return this.factory.getInstanceHttpClient();
  }

  private String v2(String path) {
    return V2_API_BASE_URL + this.repository + path;
  }

  private String post(String path, NameValuePair[] data) {
    PostMethod req = new PostMethod(path);
    req.setRequestBody(data);
    req.getParams().setContentCharset("utf-8");
    return send(req);
  }

  private String send(HttpMethodBase req) {
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, credentials);
    client.getParams().setAuthenticationPreemptive(true);
    try {
      int statusCode = client.executeMethod(req);
      if (statusCode != HttpStatus.SC_OK) {
        logger.log(Level.WARNING, "Response status: " + req.getStatusLine() +
                                      " URI: " + req.getURI());
      } else {
        return req.getResponseBodyAsString();
      }
    } catch (HttpException e) {
      logger.log(Level.WARNING, "Failed to send request.", e);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to send request.", e);
    } finally {
      req.releaseConnection();
    }
    return null;
  }
}
