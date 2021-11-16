# WireMock Webhooks Extension

## THIS REPO IS RETIRED

This project has now been moved under the WireMock project as a core extension, with
enhancements for standalone support, templating and delays.

Please see the [Webhooks and Callbacks](http://wiremock.org/docs/webhooks-and-callbacks/) doc for details.

## Implementing a callback using templating
To implement the callback pattern, where the original request contains the target to be called on completion of a long-running task,
we can use templating on the URL and method.

Java:

{% raw %}
```java
wm.stubFor(post(urlPathEqualTo("/something-async"))
      .willReturn(ok())
      .withPostServeAction("webhook", webhook()
          .withMethod("{{jsonPath originalRequest.body '$.callbackMethod'}}")
          .withUrl("{{jsonPath originalRequest.body '$.callbackUrl'}}"))
  );
```
{% endraw %}


JSON:

{% raw %}
```json
{
  "request" : {
    "urlPath" : "/something-async",
    "method" : "POST"
  },
  "response" : {
    "status" : 200
  },
  "postServeActions" : [{
    "name" : "webhook",
    "parameters" : {
      "method" : "{{jsonPath originalRequest.body '$.callbackMethod'}}",
      "url" : "{{jsonPath originalRequest.body '$.callbackUrl'}}"
    }
  }]
}
```
{% endraw %}

## Using data from the original response

Webhooks use the same [templating system](/docs/response-templating/) as WireMock responses. This means that any of the
configuration fields can be provided with a template expression which will be resolved before firing the webhook.

all response data is available in variable named `originalResponse` like `originalRequest` is used.

Supposing we wanted to pass a ID from the original (triggering) response and insert it into the JSON response
body sent by the webhook call.

For an original response body JSON like this:

```json
{
  "id": "0ea7742a-dcf9-43ff-9238-6cf7fb1a56d9"
}
```

We could construct a JSON request body in the webhook like this:

Java:

{% raw %}
```java
wm.stubFor(post(urlPathEqualTo("/templating"))
      .willReturn(aResponse().withBody("{ \"id\": \"{{randomValue type='UUID'}}\" }").withStatus(200))
      .withPostServeAction("webhook", webhook()
          .withMethod(POST)
          .withUrl("http://my-target-host/callback")
          .withHeader("Content-Type", "application/json")
          .withBody("{ \"message\": \"success\", \"id\": \"{{jsonPath originalResponse.body '$.id'}}\" }")
  );
```



JSON:


```json
{
  "request" : {
    "urlPath" : "/templating",
    "method" : "POST"
  },
  "response" : {
    "status" : 200
  },
  "postServeActions" : [{
    "name" : "webhook",
    "parameters" : {
      "method" : "POST",
      "url" : "http://my-target-host/callback",
      "headers" : {
        "Content-Type" : "application/json"
      },
      "body" : "{ \"message\": \"success\", \"Id\": \"{{jsonPath originalResponse.body '$.id'}}\" }"
    }
  }]
}
```

