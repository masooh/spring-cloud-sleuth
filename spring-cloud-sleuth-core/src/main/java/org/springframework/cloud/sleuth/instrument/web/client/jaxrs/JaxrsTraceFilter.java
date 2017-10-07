/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.jaxrs;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

/**
 * Filter that verifies whether the trace and span id has been set on the request
 * and sets them if one or both of them are missing.
 *
 * @author Martin Hofmann-Sobik
 *
 * @see javax.ws.rs.client.Client
 */
public class JaxrsTraceFilter implements ClientRequestFilter, ClientResponseFilter {

    protected static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

    private Tracer tracer;
    private HttpSpanInjector spanInjector;
    private HttpTraceKeysInjector keysInjector;

    public JaxrsTraceFilter(Tracer tracer, HttpSpanInjector spanInjector, HttpTraceKeysInjector keysInjector) {
        this.tracer = tracer;
        this.spanInjector = spanInjector;
        this.keysInjector = keysInjector;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        URI uri = requestContext.getUri();
        String spanName = getName(uri);
        Span newSpan = this.tracer.createSpan(spanName);
        spanInjector.inject(newSpan, new ClientRequestContextTextMap(requestContext));
        addRequestTags(requestContext);
        newSpan.logEvent(Span.CLIENT_SEND);
        if (log.isDebugEnabled()) {
            log.debug("Starting new client span [" + newSpan + "]");
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
		finish();
    }

    /**
     * Adds HTTP tags to the client side span
     */
    protected void addRequestTags(ClientRequestContext request) {
		this.keysInjector.addRequestTags(request.getUri().toString(),
                request.getUri().getHost(),
                request.getUri().getPath(),
                request.getMethod(),
				request.getStringHeaders());
    }

    private String getName(URI uri) {
        return SpanNameUtil.shorten(uriScheme(uri) + ":" + uri.getPath());
    }

    private String uriScheme(URI uri) {
        return uri.getScheme() == null ? "http" : uri.getScheme();
    }

	/**
	 * Close the current span and log the client received event
	 */
	private void finish() {
		if (!isTracing()) {
			return;
		}
		currentSpan().logEvent(Span.CLIENT_RECV);
		this.tracer.close(this.currentSpan());
	}

	protected Span currentSpan() {
		return this.tracer.getCurrentSpan();
	}

	protected boolean isTracing() {
		return this.tracer.isTracing();
	}

}
