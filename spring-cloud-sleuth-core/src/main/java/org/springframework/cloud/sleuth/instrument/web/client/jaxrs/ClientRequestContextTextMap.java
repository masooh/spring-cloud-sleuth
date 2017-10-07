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

import java.util.*;
import javax.ws.rs.client.ClientRequestContext;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.util.StringUtils;

/**
 * A {@link SpanTextMap} abstraction over {@link ClientRequestContext}
 *
 * @author Martin Hofmann-Sobik
 */
public class ClientRequestContextTextMap implements SpanTextMap {
    private final ClientRequestContext delegate;

    public ClientRequestContextTextMap(ClientRequestContext clientRequestContext) {
        this.delegate = clientRequestContext;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        final Iterator<Map.Entry<String, List<String>>> iterator = this.delegate.getStringHeaders()
                                                                                        .entrySet().iterator();
        return new Iterator<Map.Entry<String, String>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Map.Entry<String, String> next() {
                Map.Entry<String, List<String>> next = iterator.next();
                List<String> value = next.getValue();
                return new AbstractMap.SimpleEntry<>(next.getKey(), value.isEmpty() ? "" : value.get(0));
            }
        };
    }

    @Override
    public void put(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        this.delegate.getHeaders().put(key, Collections.singletonList(value));
    }
}
