package org.crossref.eventdata.twitter;

import com.twitter.hbc.core.endpoint.StreamingEndpoint;
import com.google.common.base.Preconditions;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.twitter.hbc.core.HttpConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

// StreamingEndpoint that uses a configurable path.
// The Endpoint implementations in HBC library doesn't support the 2.0 version of Gnip endpoints, so override with complete URL.
public class CustomUrlStreamingEndpoint implements StreamingEndpoint {
  protected final ConcurrentMap<String, String> queryParameters = Maps.newConcurrentMap();
  private String customEndpointPath;

  public CustomUrlStreamingEndpoint(String endpointPath) {
    this.customEndpointPath = Preconditions.checkNotNull(endpointPath);
  }

  @Override
  public String getURI() {
    System.out.println("GET URI:"  + this.customEndpointPath);
    if (queryParameters.isEmpty()) {
      return this.customEndpointPath;
    } else {
      System.out.println("Q :" + generateParamString(queryParameters));

      return this.customEndpointPath + "?" + generateParamString(queryParameters);
    }
  }

  protected String generateParamString(Map<String, String> params) {
    return Joiner.on("&")
            .withKeyValueSeparator("=")
            .join(params);
  }

  @Override
  public String getHttpMethod() {
    return HttpConstants.HTTP_GET;
  }

  @Override
  public String getPostParamString() {
    return null;
  }

  @Override
  public String getQueryParamString() {
    return generateParamString(queryParameters);
  }

  @Override
  public void addQueryParameter(String param, String value) {
    queryParameters.put(param, value);
  }

  @Override
  public void removeQueryParameter(String param) {
    queryParameters.remove(param);
  }

  // These don't do anything
  @Override
  public void setBackfillCount(int count) { }

  @Override
  public void setApiVersion(String apiVersion) { }

  @Override
  public void addPostParameter(String param, String value) { }

  @Override
  public void removePostParameter(String param) { }

}


