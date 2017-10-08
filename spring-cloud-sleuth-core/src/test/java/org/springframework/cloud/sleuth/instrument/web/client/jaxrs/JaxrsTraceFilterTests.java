/*
 * Copyright 2013-2017 the original author or authors.
 *
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
 */

package org.springframework.cloud.sleuth.instrument.web.client.jaxrs;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import javax.annotation.Priority;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.junit.*;

import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.ZipkinHttpSpanInjector;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Martin Hofmann-Sobik
 */
public class JaxrsTraceFilterTests {

	private DefaultTracer tracer;
	private ArrayListSpanAccumulator spanAccumulator = new ArrayListSpanAccumulator();
	private TraceJaxrsFilter jaxrsTraceFilter;
	private WebTarget webTarget;

	@Before
	public void setup() {
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				new DefaultSpanNamer(), new NoOpSpanLogger(), this.spanAccumulator, new TraceKeys());

		this.jaxrsTraceFilter =
				new TraceJaxrsFilter(this.tracer, new ZipkinHttpSpanInjector(),
						              new HttpTraceKeysInjector(this.tracer, new TraceKeys()));

		Client client = ClientBuilder.newBuilder()
				.register(jaxrsTraceFilter)
				.register(new EchoHeadersRequestFilter())
				.build();

		this.webTarget = client.target("http://dummy");

		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clean() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void headersAddedWhenNoTracingWasPresent() {
		Response response = this.webTarget.path("/").request().get();

		then(Span.hexToId(response.getHeaderString(Span.TRACE_ID_NAME))).isNotNull();
		then(Span.hexToId(response.getHeaderString(Span.SPAN_ID_NAME))).isNotNull();
	}

	@Test
	public void headersAddedWhenTracing() {
		// given
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).parent(3L).build());

		// when
		Response response = this.webTarget.path("/").request().get();

		then(Span.hexToId(response.getHeaderString(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Span.hexToId(response.getHeaderString(Span.SPAN_ID_NAME))).isNotEqualTo(2L);
		then(Span.hexToId(response.getHeaderString(Span.PARENT_ID_NAME))).isEqualTo(2L);
	}

	// Issue #290
	@Test
	public void requestHeadersAddedWhenTracing() {
		// given
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).parent(3L).build());

		// when
		this.webTarget.path("/foo").queryParam("a", "b").request().get();

		// then
		List<Span> spans = spanAccumulator.getSpans();
		then(spans).isNotEmpty();
		then(spans.get(0))
				.hasATag("http.url", "http://dummy/foo?a=b")
				.hasATag("http.path", "/foo")
				.hasATag("http.method", "GET");
	}

	@Test
	public void notSampledHeaderAddedWhenNotExportable() {
		// given
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		// when
		Response response = this.webTarget.path("/").request().get();


		then(Span.hexToId(response.getHeaderString(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Span.hexToId(response.getHeaderString(Span.SPAN_ID_NAME))).isNotEqualTo(2L);
		then(response.getHeaderString(Span.SAMPLED_NAME)).isEqualTo(Span.SPAN_NOT_SAMPLED);
	}

	// issue #198
	@Test
	@Ignore("did not find possibility to catch exception")
	public void spanRemovedFromThreadUponException() {
		// given
		Client client = ClientBuilder.newBuilder()
				.register(jaxrsTraceFilter)
				.register(new ExceptionRequestFilter())
				.build();

		this.webTarget = client.target("http://dummy");

		Span span = this.tracer.createSpan("new trace");

		try {
			// when
			webTarget.request().get();
			Assert.fail("should throw an exception");
		} catch (RuntimeException e) {
			then(e).hasMessage("500 Internal Server Error");
		}

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	@Test
	public void createdSpanNameDoesNotHaveNullInName() {
		// given
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		// when
		this.webTarget.path("/").request().get();

		// then
		List<Span> spans = spanAccumulator.getSpans();
		then(spans).isNotEmpty();
		then(spans.get(0)).hasNameEqualTo("http:/");
	}

	@Test
	public void willShortenTheNameOfTheSpan() {
		// given
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		// when
		this.webTarget.path("/" + bigName()).request().get();

		// then
		then(this.spanAccumulator.getSpans().get(0).getName()).hasSize(50);
		then(ExceptionUtils.getLastException()).isNull();
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

	/**
	 * Filter that echoes the request headers back in the response.
	 * Has to be last of the filter chain. Therefore a {@link Priority} must be set.
	 */
	@Priority(Integer.MAX_VALUE)
	private static class EchoHeadersRequestFilter implements ClientRequestFilter {
		@Override
		public void filter(ClientRequestContext requestContext) throws IOException {
			Response response = createResponseWithRequestHeaders(requestContext);
			requestContext.abortWith(response);
		}

		private Response createResponseWithRequestHeaders(ClientRequestContext requestContext) {
			Response.ResponseBuilder responseBuilder = Response.ok("stubbed response");
			for (String headerName : requestContext.getHeaders().keySet()) {
				responseBuilder.header(headerName, requestContext.getHeaderString(headerName));
			}
			return responseBuilder.build();
		}
	}

	/**
	 * Filter that causes a {@link RuntimeException}.
	 * Has to be last of the filter chain. Therefore a {@link Priority} must be set.
	 */
	@Priority(Integer.MAX_VALUE)
	private static class ExceptionRequestFilter implements ClientRequestFilter {
		@Override
		public void filter(ClientRequestContext requestContext) throws IOException {
			throw new RuntimeException("500 Internal Server Error");
		}
	}
}
