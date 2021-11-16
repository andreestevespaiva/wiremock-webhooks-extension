package org.wiremock.webhooks;

import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.wiremock.webhooks.interceptors.WebhookTransformer;
import org.wiremock.webhooks.tranformer.ResponseTemplateModel;
import org.wiremock.webhooks.tranformer.TemplateEngine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static com.github.tomakehurst.wiremock.common.LocalNotifier.notifier;
import static com.github.tomakehurst.wiremock.http.HttpClientFactory.getHttpRequestFor;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

public class Webhooks extends PostServeAction {

    private final ScheduledExecutorService scheduler;
    private final CloseableHttpClient httpClient;
    private final List<WebhookTransformer> transformers;
    private final TemplateEngine templateEngine;

    private Webhooks(
            ScheduledExecutorService scheduler,
            CloseableHttpClient httpClient,
            List<WebhookTransformer> transformers) {
      this.scheduler = scheduler;
      this.httpClient = httpClient;
      this.transformers = transformers;

      this.templateEngine = new TemplateEngine(
                Collections.emptyMap(),
                null,
                Collections.emptySet()
        );
    }


    public Webhooks() {
      this(Executors.newScheduledThreadPool(10), HttpClientFactory.createClient(), new ArrayList<WebhookTransformer>());
    }

    public Webhooks(WebhookTransformer... transformers) {
      this(Executors.newScheduledThreadPool(10), HttpClientFactory.createClient(), Arrays.asList(transformers));
    }

    @Override
    public String getName() {
        return "webhook";
    }

    @Override
    public void doAction(final ServeEvent serveEvent, final Admin admin, final Parameters parameters) {
        final Notifier notifier = notifier();

        scheduler.schedule(
            new Runnable() {
                @Override
                public void run() {
                    WebhookDefinition definition = WebhookDefinition.from(parameters);
                    for (WebhookTransformer transformer: transformers) {
                        definition = transformer.transform(serveEvent, definition);
                    }

                    definition = applyTemplating(definition, serveEvent);
                    HttpUriRequest request = buildRequest(definition);

                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        notifier.info(
                            String.format("Webhook %s request to %s returned status %s\n\n%s",
                                definition.getMethod(),
                                definition.getUrl(),
                                response.getStatusLine(),
                                EntityUtils.toString(response.getEntity())
                            )
                        );
                    } catch (IOException e) {
                        notifier().error(String.format("Failed to fire webhook %s %s", definition.getMethod(), definition.getUrl()), e);
                    }
                }
            },
            0L,
            SECONDS
        );
    }

    private static HttpUriRequest buildRequest(WebhookDefinition definition) {
        HttpUriRequest request = getHttpRequestFor(
                definition.getMethod(),
                definition.getUrl()
        );

        for (HttpHeader header: definition.getHeaders().all()) {
            request.addHeader(header.key(), header.firstValue());
        }

        if (definition.getMethod().hasEntity()) {
            HttpEntityEnclosingRequestBase entityRequest = (HttpEntityEnclosingRequestBase) request;
            entityRequest.setEntity(new ByteArrayEntity(definition.getBinaryBody()));
        }

        return request;
    }

    public static WebhookDefinition webhook() {
        return new WebhookDefinition();
    }

    private WebhookDefinition applyTemplating(WebhookDefinition webhookDefinition, ServeEvent serveEvent) {

        final Map<String, Object> model = new HashMap<>();
        model.put("parameters", webhookDefinition.getExtraParameters() != null ?
                webhookDefinition.getExtraParameters() :
                Collections.<String, Object>emptyMap());
        model.put("originalRequest", RequestTemplateModel.from(serveEvent.getRequest()));
        model.put("originalResponse", ResponseTemplateModel.from(serveEvent.getResponse()));

        WebhookDefinition renderedWebhookDefinition = webhookDefinition
                .withUrl(renderTemplate(model, webhookDefinition.getUrl()))
                .withHeaders(
                webhookDefinition.getHeaders().all().stream()
                        .map(header -> new HttpHeader(header.key(), header.values().stream()
                                .map(value -> renderTemplate(model, value))
                                .collect(toList()))
                        ).collect(toList())
        );

        if (webhookDefinition.getBody() != null) {
            try {
                renderedWebhookDefinition = webhookDefinition.withBody(renderTemplate(model, webhookDefinition.getBody()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return renderedWebhookDefinition;
    }

    private String renderTemplate(Object context, String value) {
        String template = null;
        try {
            template = templateEngine.getUncachedTemplate(value).apply(context);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return template;
    }
}
