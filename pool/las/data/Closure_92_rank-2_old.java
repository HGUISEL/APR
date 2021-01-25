/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.apache.hadoop.gateway.filter.GatewayResponseWrapper;
import org.apache.hadoop.gateway.filter.ResponseStreamer;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStreamFilterFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.util.Urls;
import org.apache.hadoop.gateway.util.urltemplate.Params;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.io.IOUtils;

import javax.activation.MimeType;
import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteUtil.getRewriteFilterConfig;
import static org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteUtil.pickFirstRuleWithEqualsIgnoreCasePathMatch;

/**
 *
 */
public class UrlRewriteResponse extends GatewayResponseWrapper implements Params, ResponseStreamer {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private static final int STREAM_BUFFER_SIZE = 4096;

  private static final Set<String> IGNORE_HEADER_NAMES = new HashSet<String>();
  static {
    IGNORE_HEADER_NAMES.add( "Content-Length" );
  }

  private static final String REQUEST_PARAM_PREFIX = "request.";
  private static final String CLUSTER_PARAM_PREFIX = "cluster.";
  private static final String GATEWAY_PARAM_PREFIX = "gateway.";

  private UrlRewriter rewriter;
  private FilterConfig config;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private ServletOutputStream output;
  private String bodyFilterName;
  private String headersFilterName;
  private UrlRewriteFilterContentDescriptor headersFilterConfig;
  private String cookiesFilterName;
  private UrlRewriteFilterContentDescriptor cookiesFilterConfig;

  public UrlRewriteResponse( FilterConfig config, HttpServletRequest request, HttpServletResponse response )
      throws IOException {
    super( response );
    this.rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
    this.config = config;
    this.request = request;
    this.response = response;
    this.output = null;
    this.bodyFilterName = config.getInitParameter( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM );
    this.headersFilterName = config.getInitParameter( UrlRewriteServletFilter.RESPONSE_HEADERS_FILTER_PARAM );
    this.headersFilterConfig = getRewriteFilterConfig( rewriter.getConfig(), headersFilterName, UrlRewriteServletFilter.HEADERS_MIME_TYPE );
    this.cookiesFilterName = config.getInitParameter( UrlRewriteServletFilter.RESPONSE_COOKIES_FILTER_PARAM );
    this.cookiesFilterConfig = getRewriteFilterConfig( rewriter.getConfig(), cookiesFilterName, UrlRewriteServletFilter.COOKIES_MIME_TYPE );
  }

  protected boolean ignoreHeader( String name ) {
    return IGNORE_HEADER_NAMES.contains( name );
  }

  private String rewriteValue( String value, String rule ) {
    try {
      Template input = Parser.parse( value );
      Template output = rewriter.rewrite( this, input, UrlRewriter.Direction.OUT, rule );
      if( output != null ) {
        value = output.toString();
      }
    } catch( URISyntaxException e ) {
      LOG.failedToParseValueForUrlRewrite( value );
    }
    return value;
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void setHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
      value = rewriteValue( value, pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name ) );
      super.setHeader( name, value );
    }
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void addHeader( String name, String value ) {
    if( !ignoreHeader( name ) ) {
      String rule = pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name );
      value = rewriteValue( value, rule );
      super.addHeader( name, value );
    }
  }

  @Override
  public OutputStream getRawOutputStream() throws IOException {
    return response.getOutputStream();
  }

  @Override
  public void streamResponse( InputStream input, OutputStream output ) throws IOException {
    MimeType mimeType = getMimeType();
    UrlRewriteFilterContentDescriptor filterContentConfig =
        getRewriteFilterConfig( rewriter.getConfig(), bodyFilterName, mimeType );
    InputStream filteredInput = UrlRewriteStreamFilterFactory.create(
        mimeType, null, input, rewriter, this, UrlRewriter.Direction.OUT, filterContentConfig );
    IOUtils.copyBytes( filteredInput, output, STREAM_BUFFER_SIZE );
    output.close();
  }

  //TODO: Need to buffer the output here and when it is closed, rewrite it and then write the result to the stream.
  // This should only happen if the caller isn't using the streaming model.
  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if( output == null ) {
      output = new UrlRewriteResponseStream( this );
    }
    return output;
  }

  @Override
  public Set<String> getNames() {
    return Collections.emptySet();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public List<String> resolve( String name ) {
    if( name.startsWith( REQUEST_PARAM_PREFIX ) ) {
      return Arrays.asList( getRequestParam( name.substring( REQUEST_PARAM_PREFIX.length() ) ) );
    } else if ( name.startsWith( GATEWAY_PARAM_PREFIX ) ) {
      return Arrays.asList( getGatewayParam( name.substring( GATEWAY_PARAM_PREFIX.length() ) ) );
    } else if ( name.startsWith( CLUSTER_PARAM_PREFIX ) ) {
      return Arrays.asList( getClusterParam( name.substring( GATEWAY_PARAM_PREFIX.length() ) ) );
    }  else {
      return Arrays.asList( config.getInitParameter( name ) );
    }
  }

  private String getGatewayParam( String name ) {
    if( "url".equals( name ) ) {
      return request.getScheme() + "://" + request.getServerName() + ":" + request.getLocalPort() + request.getContextPath();
    } else if( "address".equals( name ) ) {
      return request.getServerName() + ":" + request.getLocalPort();
    } else if( "path".equals( name ) ) {
      return request.getContextPath();
    } else {
      return null;
    }
  }

  private String getClusterParam( String name ) {
    if( "name".equals( name ) ) {
      return config.getServletContext().getServletContextName();
    } else {
      return null;
    }
  }

  private String getRequestParam( String name ) {
    if( "host".equals( name ) ) {
      return request.getServerName();
    } else if ( "port".equals( name ) ) {
      return Integer.toString( request.getLocalPort() );
    } else if ( "scheme".equals( name ) ) {
      return request.getScheme();
    } else if ( "context-path".equals( name ) ) {
      return Urls.stripLeadingSlash( request.getContextPath() );
    } else {
      config.getServletContext().getServletContextName();
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  public String encodeUrl( String url ) {
    return this.encodeURL( url );
  }

  //TODO: Route these through the rewriter.
  public String encodeURL( String url ) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("deprecation")
  public String encodeRedirectUrl( String url ) {
    return this.encodeRedirectURL( url );
  }

  //TODO: Route these through the rewriter.
  public String encodeRedirectURL( String url ) {
    throw new UnsupportedOperationException();
  }

}
