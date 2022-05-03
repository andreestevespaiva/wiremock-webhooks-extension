package org.wiremock.webhooks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.*;
import org.wiremock.webhooks.adapters.DelayDistributionAdapter;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;

public class WebhookDefinition {
    
    private RequestMethod method;
    private String url;
    private List<HttpHeader> headers;
    private Body body = Body.none();
    private DelayDistribution delay;
    private Parameters parameters;

    @JsonCreator
    public WebhookDefinition(@JsonProperty("method") RequestMethod method,
                             @JsonProperty("url") String url,
                             @JsonProperty("headers") HttpHeaders headers,
                             @JsonProperty("body") String body,
                             @JsonProperty("jsonBody") JsonNode jsonBody,
                             @JsonProperty("base64Body") String base64Body,
                             @JsonProperty("delay") DelayDistribution delay,
                             @JsonProperty("parameters") Parameters parameters) {
        this.method = method;
        this.url = url;
        this.headers = newArrayList(headers.all());
        this.body = Body.fromOneOf(null,body,jsonBody,base64Body);
        this.delay = delay;
        this.delay = delay != null ? delay : getDelayDistribution(parameters);
        this.parameters = parameters;
    }

    public WebhookDefinition() {
    }

    public static WebhookDefinition from(Parameters parameters) {
        String body = parameters.getString("body", null);
        JsonNode jsonBody = getBody(parameters);
        return new WebhookDefinition(
                new RequestMethod(parameters.getString("method", "GET")),
                parameters.getString("url"),
                toHttpHeaders(parameters.getMetadata("headers")),
                body,
                jsonBody,
                parameters.getString("base64Body", null),
                getDelayDistribution(parameters),
                parameters
        );
    }

    private static JsonNode getBody(Parameters parameters) {
        Object body = parameters.get("jsonBody");
        if (body == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(body, JsonNode.class);
    }

    private static HttpHeaders toHttpHeaders(Metadata headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            return new HttpHeaders();
        }
        return new HttpHeaders(
                headerMap.entrySet().stream()
                        .map(entry -> new HttpHeader(
                                entry.getKey(),
                                getHeaderValues(entry.getValue()))
                        )
                        .collect(Collectors.toList())
        );
    }
    @SuppressWarnings("unchecked")
    private static Collection<String> getHeaderValues(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof List) {
            return ((List<String>) obj);
        }

        return singletonList(obj.toString());
    }

    private static Metadata getExtraParametersFromParam(Parameters parameters){
        try {
            return parameters.getMetadata("extraParameters");
        }catch (Exception ex) {
            return null;
        }
    }

    private static DelayDistribution getDelayDistribution(Parameters parameters) {
        Metadata delayParams = null;

        try {
            delayParams = getExtraParametersFromParam(parameters);
            delayParams = delayParams == null ? parameters.getMetadata("delay"): delayParams.getMetadata("delay");
        }catch (Exception ex) {
            return null;
        }

        return delayParams.as(DelayDistributionAdapter.class);
    }

    public RequestMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public HttpHeaders getHeaders() {
        return new HttpHeaders(headers);
    }

    public String getBase64Body() {
        return body.isBinary() ? body.asBase64() : null;
    }

    public String getBody() {
        return body.isBinary() ? null : body.asString();
    }

    @JsonIgnore
    public byte[] getBinaryBody() {
        return body.asBytes();
    }

    public WebhookDefinition withMethod(RequestMethod method) {
        this.method = method;
        return this;
    }

    public WebhookDefinition withUrl(String url) {
        this.url = url;
        return this;
    }

    public WebhookDefinition withHeaders(List<HttpHeader> headers) {
        this.headers = headers;
        return this;
    }

    public WebhookDefinition withHeader(String key, String... values) {
        if (headers == null) {
            headers = newArrayList();
        }

        headers.add(new HttpHeader(key, values));
        return this;
    }

    public WebhookDefinition withBody(String body) {
        this.body = new Body(body);
        return this;
    }

    public WebhookDefinition withExtraParameters(Parameters parameters) {
        this.parameters = parameters;
        return this;
    }

    public WebhookDefinition withBinaryBody(byte[] body) {
        this.body = new Body(body);
        return this;
    }

    public Parameters getExtraParameters() {
        return this.parameters;
    }

    @JsonIgnore
    public long getDelaySampleMillis() {
        if (delay == null){
            delay = getDelayDistribution(parameters);
        }
        return delay != null ? delay.sampleMillis() : 0L;
    }
}
