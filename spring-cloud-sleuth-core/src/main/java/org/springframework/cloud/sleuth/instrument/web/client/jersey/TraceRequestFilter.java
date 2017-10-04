package org.springframework.cloud.sleuth.instrument.web.client.jersey;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

public class TraceRequestFilter implements ClientRequestFilter {
    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {

    }
}
