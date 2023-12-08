package org.mapfish.print.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;

import org.mapfish.print.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.AbstractClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/** Default implementation. */
public class MfClientHttpRequestFactoryImpl extends HttpComponentsClientHttpRequestFactory {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MfClientHttpRequestFactoryImpl.class);
  private static final ThreadLocal<Configuration> CURRENT_CONFIGURATION =
      new InheritableThreadLocal<>();

  /**
   * Constructor.
   *
   * @param maxConnTotal Maximum total connections.
   * @param maxConnPerRoute Maximum connections per route.
   */
  public MfClientHttpRequestFactoryImpl(final int maxConnTotal, final int maxConnPerRoute) {
    super(createHttpClient(maxConnTotal, maxConnPerRoute));
  }

  @Nullable
  static Configuration getCurrentConfiguration() {
    return CURRENT_CONFIGURATION.get();
  }

  private static int getIntProperty(final String name) {
    final String value = System.getProperty(name);
    if (value == null) {
      return -1;
    }
    return Integer.parseInt(value);
  }

  private static CloseableHttpClient createHttpClient(
      final int maxConnTotal, final int maxConnPerRoute) {
      
      int connectionRequestTimeout = getIntProperty("http.connectionRequestTimeout");
      int connectTimeout = getIntProperty("http.connectTimeout");
      int socketTimeout = getIntProperty("http.socketTimeout");
      
    final RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout((connectionRequestTimeout>0)?Timeout.ofMilliseconds(connectionRequestTimeout):Timeout.DISABLED)
            .setConnectTimeout((connectTimeout>0)?Timeout.ofMilliseconds(connectTimeout):Timeout.DISABLED)
            .setResponseTimeout((socketTimeout>0)?Timeout.ofMilliseconds(socketTimeout):Timeout.DISABLED)
            .build();

    final HttpClientBuilder httpClientBuilder =
        HttpClients.custom()
            .disableCookieManagement()
            // Removed in HttpClient 5
            //.setDnsResolver(new RandomizingDnsResolver())
            .setRoutePlanner(new MfRoutePlanner())
            //.setSSLSocketFactory(new MfSSLSocketFactory())
            .setDefaultCredentialsProvider(new MfCredentialsProvider())
            .setDefaultRequestConfig(requestConfig)
            //.setMaxConnTotal(maxConnTotal)
            //.setMaxConnPerRoute(maxConnPerRoute)
            .setUserAgent(UserAgentCreator.getUserAgent());
    return httpClientBuilder.build();
  }

  // allow extension only for testing
  @Override
  public ConfigurableRequest createRequest(
      @Nonnull final URI uri, @Nonnull final HttpMethod httpMethod) throws IOException {
      HttpUriRequestBase httpRequest = (HttpUriRequestBase) createHttpUriRequest(httpMethod, uri);
    return new Request(getHttpClient(), httpRequest, createHttpContext(httpMethod, uri));
  }

  /**
   * Randomized order DnsResolver.
   *
   * <p>The default DnsResolver is using the results of InetAddress.getAllByName which is cached and
   * returns the IP addresses always in the same order (think about DNS round robin). The callers
   * always try the addresses in the order returned by the DnsResolver. This implementation adds
   * randomizing to it's result.
   */
  private static final class RandomizingDnsResolver extends SystemDefaultDnsResolver {
    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
      final List<InetAddress> list = Arrays.asList(super.resolve(host));
      Collections.shuffle(list);
      return list.toArray(new InetAddress[list.size()]);
    }
  }

  /**
   * A request that can be configured at a low level.
   *
   * <p>It is an http components based request.
   */
  public static final class Request extends AbstractClientHttpRequest
      implements ConfigurableRequest {

    private final HttpClient client;
    private final HttpUriRequestBase request;
    private final HttpContext context;
    private final ByteArrayOutputStream outputStream;
    private Configuration configuration;

    Request(
        @Nonnull final HttpClient client,
        @Nonnull final HttpUriRequestBase request,
        @Nullable final HttpContext context) {
      this.client = client;
      this.request = request;
      this.context = context;
      this.outputStream = new ByteArrayOutputStream();
    }

    public void setConfiguration(final Configuration configuration) {
      this.configuration = configuration;
    }

    public HttpClient getClient() {
      return this.client;
    }

    public HttpContext getContext() {
      return this.context;
    }

    public HttpUriRequestBase getUnderlyingRequest() {
      return this.request;
    }

    public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.request.getMethod());
    }

    public String getMethodValue() {
      return this.request.getMethod();
    }

    public URI getURI() {
      try {
        return this.request.getUri();
      } catch (URISyntaxException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected OutputStream getBodyInternal(@Nonnull final HttpHeaders headers) {
      return this.outputStream;
    }

    @Override
    protected Response executeInternal(@Nonnull final HttpHeaders headers) throws IOException {
      CURRENT_CONFIGURATION.set(this.configuration);

      LOGGER.debug(
          "Preparing request {} {}: {}",
          this.getMethod(),
          this.getURI(),
          String.join("\n", Utils.getPrintableHeadersList(headers)));

      for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
        String headerName = entry.getKey();
        if (!headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)
            && !headerName.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
          for (String headerValue : entry.getValue()) {
            this.request.addHeader(headerName, headerValue);
          }
        }
      }
      final HttpEntity requestEntity = new ByteArrayEntity(this.outputStream.toByteArray(), null);
      this.request.setEntity(requestEntity);
      ClassicHttpResponse response = (ClassicHttpResponse)this.client.execute(this.request, this.context, r -> { return r;});
      LOGGER.debug("Response: {} -- {}", response.getCode(), this.getURI());

      return new Response(response);
    }
  }

  static class Response extends AbstractClientHttpResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(Response.class);
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private final ClassicHttpResponse response;
    private final int id = ID_COUNTER.incrementAndGet();
    private InputStream inputStream;

    Response(@Nonnull final ClassicHttpResponse response) {
      this.response = response;
      LOGGER.trace("Creating Http Response object: {}", this.id);
    }

    @Override
    public int getRawStatusCode() {
      return this.response.getCode();
    }

    @Override
    public String getStatusText() {
      return this.response.getReasonPhrase();
    }

    @Override
    protected void finalize() throws Throwable {
      super.finalize();
      close();
    }

    @Override
    public void close() {
      try {
        getBody();
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (IOException e) {
        LOGGER.error(
            "Error occurred while trying to retrieve Http Response {} in order to close it.",
            this.id,
            e);
      }
      LOGGER.trace("Closed Http Response object: {}", this.id);
    }

    @Override
    public synchronized InputStream getBody() throws IOException {
      if (this.inputStream == null) {
        final HttpEntity entity = this.response.getEntity();
        if (entity != null) {
          this.inputStream = entity.getContent();
        }

        if (this.inputStream == null) {
          this.inputStream = new ByteArrayInputStream(new byte[0]);
        }
      }
      return this.inputStream;
    }

    @Override
    public HttpHeaders getHeaders() {
      final HttpHeaders translatedHeaders = new HttpHeaders();
      final Header[] allHeaders = this.response.getHeaders();
      for (Header header : allHeaders) {
          translatedHeaders.add(header.getName(), header.getValue());
      }
      return translatedHeaders;
    }
  }
}
