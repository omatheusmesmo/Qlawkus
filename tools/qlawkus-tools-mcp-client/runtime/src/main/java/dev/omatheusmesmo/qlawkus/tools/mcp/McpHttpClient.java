package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Vert.x HTTP client of one MCP connection, built by {@link McpHttpClientBuilder} so that the
 * connect handler of {@link McpEndpointPolicy#guard} can close a connection to a non-public peer before any
 * request is written. The upstream MCP transports expose no connection hook of their own, but they accept
 * the HTTP client from their builder, and this is the one they receive.
 *
 * <p>It only streams, which is all the Streamable HTTP transport calls: {@link #stream} delivers the
 * response head, then the parsed events with backpressure (the response is paused while no event is
 * demanded). Redirects are never followed, so a credential cannot be sent to the address a server names.
 * A non-2xx answer is an {@link HttpErrorResponseReceived} followed by an {@link HttpException} that carries
 * at most {@value #ERROR_BODY_LIMIT} bytes of the body. The synchronous and callback calls are refused,
 * because nothing in the MCP transports uses them and an untested second path would sit beside the guarded one.
 */
final class McpHttpClient implements HttpClient, AutoCloseable {
  static final int ERROR_BODY_LIMIT = 64 * 1024;
  private static final long CLOSE_WAIT_SECONDS = 5;

  private final HttpClientAgent client;
  private final Duration readTimeout;

  McpHttpClient(HttpClientAgent client, Duration readTimeout) {
    this.client = client;
    this.readTimeout = readTimeout;
  }

  @Override
  public SuccessfulHttpResponse execute(HttpRequest request) {
    throw new UnsupportedOperationException("The MCP HTTP client only streams");
  }

  @Override
  public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
    throw new UnsupportedOperationException("The MCP HTTP client only streams");
  }

  @Override
  public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(parser, "parser");
    return subscriber -> new Exchange(subscriber, request, parser.incremental()).start();
  }

  @Override
  public void close() throws IOException {
    Future<Void> closing = client.close();
    if (Context.isOnEventLoopThread()) return;
    try {
      closing.toCompletionStage().toCompletableFuture().get(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while closing the MCP HTTP client", interrupted);
    } catch (ExecutionException | TimeoutException failure) {
      throw new IOException("The MCP HTTP client did not close", failure);
    }
  }

  private RequestOptions options(HttpRequest request) {
    RequestOptions options = new RequestOptions()
        .setMethod(HttpMethod.valueOf(request.method().name()))
        .setAbsoluteURI(request.url())
        .setFollowRedirects(false);
    if (!readTimeout.isZero()) options.setIdleTimeout(readTimeout.toMillis());
    return options;
  }

  private final class Exchange implements Flow.Subscription {
    private final Flow.Subscriber<? super HttpStreamingEvent> subscriber;
    private final HttpRequest request;
    private final ServerSentEventParser.Incremental parser;
    private final Deque<HttpStreamingEvent> queue = new ArrayDeque<>();
    private long demand;
    private boolean draining;
    private boolean cancelled;
    private boolean ended;
    private Throwable failure;
    private boolean terminated;
    private boolean paused;
    private HttpClientRequest inFlight;
    private HttpClientResponse response;
    private Context context;
    private final ByteArrayOutputStream errorBody = new ByteArrayOutputStream();

    Exchange(Flow.Subscriber<? super HttpStreamingEvent> subscriber, HttpRequest request,
        ServerSentEventParser.Incremental parser) {
      this.subscriber = Objects.requireNonNull(subscriber, "subscriber");
      this.request = request;
      this.parser = parser;
    }

    void start() {
      subscriber.onSubscribe(this);
      try {
        client.request(options(request)).compose(sent -> {
          synchronized (this) {
            inFlight = sent;
            if (cancelled) {
              sent.reset();
              return sent.send();
            }
          }
          request.headers().forEach((name, values) -> values.forEach(value -> sent.headers().add(name, value)));
          return request.body() == null ? sent.send()
              : sent.send(Buffer.buffer(request.body().getBytes(StandardCharsets.UTF_8)));
        }).onSuccess(this::opened).onFailure(this::fail);
      } catch (RuntimeException invalid) {
        fail(invalid);
      }
    }

    private void opened(HttpClientResponse received) {
      received.exceptionHandler(this::fail);
      synchronized (this) {
        if (cancelled) {
          received.request().reset();
          return;
        }
        response = received;
        context = Vertx.currentContext();
      }
      int status = received.statusCode();
      boolean success = status >= 200 && status < 300;
      if (success) {
        received.handler(chunk -> body(chunk.getBytes()));
        received.endHandler(done -> finish(null));
      } else {
        received.handler(chunk -> {
          int room = ERROR_BODY_LIMIT - errorBody.size();
          if (room > 0) errorBody.write(chunk.getBytes(), 0, Math.min(room, chunk.length()));
        });
        received.endHandler(done -> finish(new HttpException(status,
            errorBody.toString(StandardCharsets.UTF_8))));
      }
      Map<String, List<String>> headers = headers(received);
      enqueue(success ? new HttpResponseReceived(SuccessfulHttpResponse.builder().statusCode(status)
          .headers(headers).build()) : new HttpErrorResponseReceived(status, headers));
    }

    private void body(byte[] bytes) {
      try {
        List<ServerSentEvent> parsed = parser.feed(ByteBuffer.wrap(bytes));
        synchronized (this) {
          queue.addAll(parsed);
        }
        drain();
        pauseWhileFull();
      } catch (RuntimeException broken) {
        fail(broken);
      }
    }

    private void finish(Throwable error) {
      List<ServerSentEvent> rest = List.of();
      if (error == null) {
        try {
          rest = parser.flush();
        } catch (RuntimeException broken) {
          error = broken;
        }
      }
      synchronized (this) {
        queue.addAll(rest);
        ended = true;
        failure = failure == null ? error : failure;
      }
      drain();
    }

    private void fail(Throwable error) {
      synchronized (this) {
        if (ended || cancelled) return;
        ended = true;
        failure = error;
      }
      drain();
    }

    private void enqueue(HttpStreamingEvent event) {
      synchronized (this) {
        queue.add(event);
      }
      drain();
      pauseWhileFull();
    }

    private void pauseWhileFull() {
      HttpClientResponse current;
      synchronized (this) {
        current = response;
        if (current == null || paused || queue.isEmpty() || ended || cancelled) return;
        paused = true;
      }
      current.pause();
    }

    @Override
    public void request(long count) {
      if (count <= 0) {
        cancel();
        fail(new IllegalArgumentException("Demand must be positive"));
        return;
      }
      synchronized (this) {
        demand = demand + count < 0 ? Long.MAX_VALUE : demand + count;
      }
      drain();
      resumeWhenEmpty();
    }

    private void resumeWhenEmpty() {
      HttpClientResponse current;
      Context target;
      synchronized (this) {
        current = response;
        target = context;
        if (current == null || !paused || !queue.isEmpty() || cancelled) return;
        paused = false;
      }
      if (target != null) target.runOnContext(ignored -> current.resume());
      else current.resume();
    }

    @Override
    public void cancel() {
      HttpClientRequest current;
      synchronized (this) {
        if (cancelled) return;
        cancelled = true;
        queue.clear();
        current = inFlight;
      }
      if (current != null) current.reset();
    }

    private void drain() {
      synchronized (this) {
        if (draining) return;
        draining = true;
      }
      while (true) {
        HttpStreamingEvent next = null;
        Throwable error = null;
        boolean complete = false;
        synchronized (this) {
          if (cancelled || terminated) {
            draining = false;
            return;
          }
          if (!queue.isEmpty() && demand > 0) {
            next = queue.poll();
            if (demand != Long.MAX_VALUE) demand--;
          } else if (queue.isEmpty() && ended) {
            terminated = true;
            error = failure;
            complete = failure == null;
          } else {
            draining = false;
            return;
          }
        }
        try {
          if (next != null) subscriber.onNext(next);
          else if (complete) subscriber.onComplete();
          else subscriber.onError(error);
        } catch (RuntimeException misbehaving) {
          cancel();
        }
      }
    }
  }

  private static Map<String, List<String>> headers(HttpClientResponse response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.headers().names()) headers.put(name, new ArrayList<>(response.headers().getAll(name)));
    return headers;
  }
}
