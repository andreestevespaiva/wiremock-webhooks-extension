/*
 * Copyright (C) 2011 Thomas Akehurst
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
package org.wiremock.webhooks.tranformer;

import com.github.tomakehurst.wiremock.common.ListOrSingle;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestLine;
import com.github.tomakehurst.wiremock.extension.responsetemplating.UrlPath;
import com.github.tomakehurst.wiremock.http.*;
import com.google.common.base.Function;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.TreeMap;

public class ResponseTemplateModel {

    private final Map<String, ListOrSingle<String>> headers;
    private final String body;


    protected ResponseTemplateModel(Map<String, ListOrSingle<String>> headers, String body) {
        this.headers = headers;
        this.body = body;
    }

    public static ResponseTemplateModel from(final LoggedResponse response) {
        Map<String, ListOrSingle<String>> adaptedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        adaptedHeaders.putAll(Maps.toMap(response.getHeaders().keys(), new Function<String, ListOrSingle<String>>() {
            @Override
            public ListOrSingle<String> apply(String input) {
                return ListOrSingle.of(response.getHeaders().getHeader(input).values());
            }
        }));

        return new ResponseTemplateModel(
            adaptedHeaders,
            response.getBodyAsString()
        );
    }

    public Map<String, ListOrSingle<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

}
