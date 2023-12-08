package org.mapfish.print.http;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.processor.http.matcher.MatchInfo;
import org.springframework.http.HttpMethod;

/**
 * A Route planner that obtains proxies from the configuration that is currently in {@link
 * org.mapfish.print.http.MfClientHttpRequestFactoryImpl#CURRENT_CONFIGURATION}.
 *
 * <p>{@link MfClientHttpRequestFactoryImpl.Request} will set the correct configuration before the
 * request is executed so that correct proxies will be set.
 */
public final class MfRoutePlanner extends DefaultRoutePlanner {
  /** Constructor. */
  public MfRoutePlanner() {
    super(null);
  }

  @Override
  protected HttpHost determineProxy(
      final HttpHost target, final HttpContext context)
      throws HttpException {
    Configuration config = MfClientHttpRequestFactoryImpl.getCurrentConfiguration();
    if (config == null) {
      return null;
    }
    final URI uri;
    final HttpClientContext clientContext = HttpClientContext.adapt(context);
    try {
      uri = clientContext.getRequest().getUri();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    HttpMethod method = HttpMethod.valueOf(clientContext.getRequest().getMethod());

    final List<HttpProxy> proxies = config.getProxies();
    for (HttpProxy proxy : proxies) {
      try {
        if (proxy.matches(MatchInfo.fromUri(uri, method))) {
          return proxy.getHttpHost();
        }
      } catch (SocketException | UnknownHostException | MalformedURLException e) {
        throw new HttpException(e.getMessage(), e);
      }
    }
    return null;
  }
}
