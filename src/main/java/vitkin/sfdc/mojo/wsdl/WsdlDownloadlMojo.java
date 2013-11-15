/*
 * #%L
 * SFDC WSDL Maven Plugin
 * %%
 * Copyright (C) 2013 Victor Itkin
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package vitkin.sfdc.mojo.wsdl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import com.thoughtworks.xstream.XStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Goal which downloads a WSDL.<br/>
 * That goals by default binds to the 'initialize' lifecycle phase.
 *
 * @author Victor Itkin
 */
@Mojo(name = "download",
      defaultPhase = LifecyclePhase.INITIALIZE,
      instantiationStrategy = InstantiationStrategy.POOLABLE,
      requiresOnline = true)
public class WsdlDownloadlMojo extends AbstractMojo
{
  /**
   * Suffix for cookie store files.
   */
  private static final String COOKIES_SUFFIX = "-cookies.xml";

  /**
   * Development/Production Authorization server URL.
   */
  private static final String DEV_PROD_AUTHORIZATION_SERVER =
    "https://login.salesforce.com";

  /**
   * Sandbox Authorization server URL.
   */
  private static final String SANDBOX_AUTHORIZATION_SERVER =
    "https://test.salesforce.com";

  private static final String DEFAULT_OUTPUT_DIRECTORY_VALUE =
    "${basedir}/src/main/wsdl";

  private static final File DEFAULT_OUTPUT_DIRECTORY = 
    new File(DEFAULT_OUTPUT_DIRECTORY_VALUE);

  private static final String DEFAULT_COOKIES_DIRECTORY_VALUE =
    "${basedir}/cookies";

  private static final File DEFAULT_COOKIES_DIRECTORY = 
    new File(DEFAULT_COOKIES_DIRECTORY_VALUE);

  private static final String DEFAULT_WSDL_URI_VALUE = "soap/wsdl.jsp";

  private static final URI DEFAULT_WSDL_URI;

  static
  {
    URI uri = null;

    try
    {
      uri = new URI(DEFAULT_WSDL_URI_VALUE);
    }
    catch (URISyntaxException ex)
    {
    }

    DEFAULT_WSDL_URI = uri;
  }

  /**
   * Location of the file.<br/>
   * The default location is '${basedir}/src/main/wsdl'.
   */
  @Parameter(property = "sfdc.wsdl.outputDirectory",
             defaultValue = DEFAULT_OUTPUT_DIRECTORY_VALUE,
             required = true)
  private File outputDirectory;

  /**
   * Name of the file to override the default one provided by the resource
   * server by the time of the download. (e.g. 'partner.wsdl')
   */
  @Parameter(property = "sfdc.wsdl.filename")
  private String filename;

  /**
   * Path to the directory where the cookies are stored.<br/>
   * The default path is '${basedir}/cookies'.
   */
  @Parameter(property = "sfdc.cookiesDirectory",
             defaultValue = DEFAULT_COOKIES_DIRECTORY_VALUE,
             required = true)
  private File cookiesDirectory;

  /**
   * Set to true if the authorization server to use is the sandbox one
   * (https://test.salesforce.com).<br/>
   * Default to false for production/development authorization server
   * (https://login.salesforce.com).
   */
  @Parameter(property = "sfdc.useSandbox")
  private boolean useSandbox;

  /**
   * Relative URI of the WSDL.<br/>
   * The default URI is 'soap/wsdl.jsp'.<br/>
   * For an Apex class based Web Service it can be
   * 'services/wsdl/class/MyApexClassName'.
   */
  @Parameter(property = "sfdc.wsdl.uri",
             defaultValue = DEFAULT_WSDL_URI_VALUE,
             required = true)
  private URI wsdlUri;

  /**
   * Email address based username to connect to the salesforce.com organization.
   */
  @Parameter(property = "sfdc.username",
             required = true)
  private String username;

  /**
   * Password (without the security token) to connect to the salesforce.com
   * organization.
   */
  @Parameter(property = "sfdc.password",
             required = true)
  private String password;

  /**
   * Used for saving/loading cookie stores as XML.
   */
  private final XStream xstream = new XStream();

  /**
   * Map of HTTP clients used by the instance.
   */
  private final HashMap<String, InnerHttpClient> clients =
    new HashMap<String, InnerHttpClient>();

  /**
   * Default constructor.
   */
  public WsdlDownloadlMojo()
  {
    xstream.alias("store", BasicCookieStore.class);
    xstream.alias("cookie", BasicClientCookie.class);
  }

  /**
   * Execute the 'download' goal.
   *
   * @throws MojoExecutionException
   */
  @Override
  public void execute() throws MojoExecutionException
  {
    final InnerHttpClient client = getHttpClient();

    String resourceServer = getResourceServer(client);

    try
    {
      if (resourceServer == null)
      {
        final String redirectUrl = logIn(client);
        resourceServer = redirect(client, redirectUrl);
      }

      downloadWsdl(client, resourceServer);
    }
    finally
    {
      client.saveCookieStore();
      reset();
    }
  }

  /**
   * Download the WSDL.
   *
   * @param client         HTTP client.
   * @param resourceServer URL of the resource server from where to download.
   *
   * @throws MojoExecutionException
   */
  private void downloadWsdl(final HttpClient client, final String resourceServer)
    throws
    MojoExecutionException
  {
    final Log logger = getLog();
    final String baseUrl = resourceServer + '/' + wsdlUri;

    logger.info("Getting WSDL from " + baseUrl);

    final HttpGet wsdlRequest = new HttpGet(baseUrl);

    try
    {
      final HttpResponse response = client.execute(wsdlRequest);

      final int code = response.getStatusLine().getStatusCode();

      if (code != HttpStatus.SC_OK)
      {
        throw new MojoExecutionException(
          "Failed getting the WSDL! Got HTTP Code " + code);
      }

      PrintWriter pw = null;
      BufferedReader br = null;

      try
      {
        if (filename == null)
        {
          logger.info("No filename defined. Using default one from server...");

          final Header header = response.getFirstHeader("Content-Disposition");

          if (header == null)
          {
            if (logger.isDebugEnabled())
            {
              debugResponse(response);
            }

            throw new MojoExecutionException(
              "Couldn't get filename from server!");
          }

          final HeaderElement[] elements = header.getElements();

          filename = elements[0].getParameterByName("filename").getValue();
        }

        if (!outputDirectory.exists())
        {
          outputDirectory.mkdirs();
        }

        final File wsdlFile = new File(outputDirectory, filename);

        logger.info("Saving WSDL to '" + wsdlFile + "'...");

        br = new BufferedReader(
          new InputStreamReader(response.getEntity().getContent()));

        pw = new PrintWriter(wsdlFile);

        for (String line = br.readLine(); line != null; line = br.readLine())
        {
          pw.println(line);
        }
      }
      catch (IOException ex)
      {
        throw new MojoExecutionException("Failed saving the WSDL!", ex);
      }
      catch (IllegalStateException ex)
      {
        throw new MojoExecutionException("Failed saving the WSDL!", ex);
      }
      catch (ParseException ex)
      {
        throw new MojoExecutionException("Failed saving the WSDL!", ex);
      }
      finally
      {
        try
        {
          if (br != null)
          {
            br.close();
          }

          if (pw != null)
          {
            pw.close();
          }
        }
        catch (IOException ex)
        {
          logger.warn(ex.getMessage(), ex);
        }
      }
    }
    catch (IOException ex)
    {
      throw new MojoExecutionException("Cannot get WSDL!", ex);
    }
  }

  /**
   * Get an existing HTTP client for the current instance or create a new
   * one.<br/>
   * When creating a new HTTP client, try to load previously saved cookies.
   *
   * @return HTTP client for the current environment and username.
   */
  private InnerHttpClient getHttpClient()
  {
    final String env = useSandbox ? "sanbox" : "dev-prod";
    final String clientId = env + '/' + username;

    InnerHttpClient client = clients.get(clientId);

    if (client == null)
    {
      client = new InnerHttpClient(env);

      final HttpParams params = client.getParams();

      HttpClientParams.setCookiePolicy(params, CookiePolicy.NETSCAPE);
      params.setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 30000);

      clients.put(clientId, client);
    }

    return client;
  }

  /**
   * Tell if a session is older than an hour.
   *
   * @param client HTTP client for which to attempt deducing if the session has
   *               expired.
   *
   * @return String The resource server for the session if the session hasn't
   *         expired. Null otherwise.
   */
  private String getResourceServer(InnerHttpClient client)
  {
    Log logger = getLog();

    // Cookie 'oid' expiry date is supposed to be in 2 years in the future from
    // the date of creation of the cookie.
    final Calendar cal = GregorianCalendar.getInstance();
    cal.add(Calendar.YEAR, 2);
    cal.add(Calendar.HOUR, -1);

    final Date futureDate = cal.getTime();

    if (logger.isDebugEnabled())
    {
      logger.debug("Future date: " + futureDate);
    }

    String resourceServer = null;

    for (Cookie cookie : client.getCookieStore().getCookies())
    {
      final String name = cookie.getName();

      if ("oid".equals(name))
      {
        final Date expiryDate = cookie.getExpiryDate();

        if (logger.isDebugEnabled())
        {
          logger.debug("Expiry date: " + expiryDate);
        }

        if (futureDate.before(expiryDate))
        {
          resourceServer = "https://" + cookie.getDomain();
        }

        break;
      }
    }

    return resourceServer;
  }

  /**
   * Log in to a Salesforce authorization server.
   *
   * @param client HTTP client.
   *
   * @return Redirection URL if successful.
   *
   * @throws MojoExecutionException
   */
  private String logIn(final DefaultHttpClient client) throws
    MojoExecutionException
  {
    final Log logger = getLog();

    final String authorizationServer =
      useSandbox ? SANDBOX_AUTHORIZATION_SERVER :
      DEV_PROD_AUTHORIZATION_SERVER;

    logger.info("Logging in as " + username + " at authorization server at " +
      authorizationServer + "...");

    // Send a post request to the login URI.
    final HttpPost loginRequest = new HttpPost(authorizationServer);

    // The request body must contain these 2 values.
    final List<BasicNameValuePair> parametersBody =
      new ArrayList<BasicNameValuePair>();

    parametersBody.add(new BasicNameValuePair("un", username));
    parametersBody.add(new BasicNameValuePair("pw", password));

    loginRequest.setEntity(
      new UrlEncodedFormEntity(parametersBody, Consts.UTF_8));

    final String location;

    try
    {
      final HttpResponse response = client.execute(loginRequest);

      if (logger.isErrorEnabled())
      {
        debugResponse(response);
      }
      else
      {
        // Low level resources should be released before initiating a new request
        HttpEntity entity = response.getEntity();

        if (entity != null)
        {
          // Do not need the rest
          loginRequest.abort();
        }
      }

      final int code = response.getStatusLine().getStatusCode();

      if (code != HttpStatus.SC_MOVED_TEMPORARILY)
      {
        // No redirection. That means we're not logged in.
        throw new MojoExecutionException(
          "Cannot log in! Wrong credentials or need for activation for the current IP.");
      }

      location = response.getFirstHeader("Location").getValue();

    }
    catch (IOException ex)
    {
      throw new MojoExecutionException("Cannot log in!", ex);
    }

    return location;
  }

  /**
   * Load cookies from the previous instance execution.
   *
   * @param env Salesforce environment. Either 'sandbox' or 'dev-prod'.
   *
   * @return The loaded cookie store or null if it can't load it.
   */
  private CookieStore loadCookies(final String env)
  {
    final Log logger = getLog();
    final File cookieStorePath = new File(cookiesDirectory, env);

    CookieStore cookieStore = null;

    if (cookieStorePath.exists())
    {
      final File cookieStoreFile = new File(cookieStorePath, username +
        COOKIES_SUFFIX);

      if (cookieStoreFile.exists())
      {
        logger.info(
          "Found cookies from previous execution. Loading from '" +
          cookieStoreFile +
          "'...");

        ObjectInputStream ois = null;

        try
        {
          ois = xstream.createObjectInputStream(
            new BufferedInputStream(
              new FileInputStream(cookieStoreFile)));

          cookieStore = (BasicCookieStore) ois.readObject();
        }
        catch (IOException ex)
        {
          logger.warn("Failed reading cookies from previous run!", ex);
        }
        catch (ClassNotFoundException ex)
        {
          logger.warn("Failed loading cookies from previous run!", ex);
        }
        finally
        {
          if (ois != null)
          {
            try
            {
              ois.close();
            }
            catch (IOException ex)
            {
              logger.warn(ex.getMessage(), ex);
            }
          }
        }
      }
    }

    return cookieStore;
  }

  /**
   * Display the content of the HTTP response.<br/>
   * <b>CAUTION:</b> Calling this method will consume the response content
   * InputStream!
   *
   * @param response The HTTP response of which to display the content.
   */
  private void debugResponse(final HttpResponse response)
  {
    final Log logger = getLog();

    logger.debug("Displaying content:");

    BufferedReader br = null;

    try
    {
      br = new BufferedReader(
        new InputStreamReader(response.getEntity().getContent()));

      for (String line = br.readLine(); line != null; line = br.readLine())
      {
        logger.debug(line);
      }
    }
    catch (IOException ex)
    {
      logger.error("Failed displaying content!", ex);
    }
    finally
    {
      if (br != null)
      {
        try
        {
          br.close();
        }
        catch (IOException ex)
        {
          logger.warn(ex.getMessage(), ex);
        }
      }
    }
  }

  /**
   * Reset all parameters to their default values.
   */
  private void reset()
  {
    cookiesDirectory = DEFAULT_COOKIES_DIRECTORY;
    filename = null;
    outputDirectory = DEFAULT_OUTPUT_DIRECTORY;
    password = null;
    useSandbox = false;
    username = null;
    wsdlUri = DEFAULT_WSDL_URI;
  }

  /**
   * Execute an HTTP redirection to set additional cookies related to the
   * resource server session.
   *
   * @param client      The HTTP client to use for that redirection.
   * @param redirectUrl The URL to redirect to.
   *
   * @return The base URL of the resource server.
   *
   * @throws MojoExecutionException
   */
  private String redirect(HttpClient client, String redirectUrl) throws
    MojoExecutionException
  {
    final Log logger = getLog();

    final String resourceServer = redirectUrl.
      substring(0, redirectUrl.indexOf('/', 8));

    if (redirectUrl.startsWith(resourceServer +
      "/_nc_external/identity/ic/ICRequired"))
    {
      throw new MojoExecutionException(
        "Need activation. Open the below URL with a browser from the same public IP:\n" +
        redirectUrl);
    }

    logger.info("Accessing resource server at " + redirectUrl);

    final HttpGet redirectRequest = new HttpGet(redirectUrl);

    try
    {
      final HttpResponse response = client.execute(redirectRequest);

      if (logger.isErrorEnabled())
      {
        debugResponse(response);
      }
      else
      {
        final HttpEntity entity = response.getEntity();

        if (entity != null)
        {
          // Do not need the rest
          redirectRequest.abort();
        }
      }
    }
    catch (IOException ex)
    {
      throw new MojoExecutionException("Cannot access resource server!", ex);
    }

    return resourceServer;
  }

  /**
   * Save the cookie store for the given Salesforce environment and current
   * username.
   *
   * @param env         Salesforce environment. Either 'sandbox' or 'dev-prod'.
   * @param cookieStore The cookie store to save.
   */
  private void saveCookies(final String env, final CookieStore cookieStore)
  {
    final Log logger = getLog();
    final File cookieStorePath = new File(cookiesDirectory, env);

    if (!cookieStorePath.exists())
    {
      cookieStorePath.mkdirs();
    }

    final File cookieStoreFile =
      new File(cookieStorePath, username + COOKIES_SUFFIX);

    logger.info("Saving cookies to '" + cookieStoreFile + "'...");

    ObjectOutputStream oos = null;

    try
    {
      oos = xstream.createObjectOutputStream(
        new BufferedOutputStream(
          new FileOutputStream(cookieStoreFile)));

      oos.writeObject(cookieStore);
    }
    catch (IOException ex)
    {
      logger.warn("Failed saving cookies from current run!", ex);
    }
    finally
    {
      if (oos != null)
      {
        try
        {
          oos.close();
        }
        catch (IOException ex)
        {
          logger.warn(ex.getMessage(), ex);
        }
      }
    }
  }

  /**
   * Inner HTTP client.
   */
  private class InnerHttpClient extends DefaultHttpClient
  {
    private final String env;

    /**
     * Initialize the instance.
     *
     * @param env Salesforce environment for that instance.
     */
    private InnerHttpClient(String env)
    {
      this.env = env;
    }

    /**
     * Create a cookie store.
     *
     * @return The new cookie store.
     */
    @Override
    protected CookieStore createCookieStore()
    {
      CookieStore cookieStore = loadCookies(env);

      if (cookieStore == null)
      {
        cookieStore = super.createCookieStore();
      }

      return cookieStore;
    }

    /**
     * Save the cookie store.
     *
     * @throws Throwable
     */
    private void saveCookieStore()
    {
      saveCookies(env, getCookieStore());
    }
  }
}
