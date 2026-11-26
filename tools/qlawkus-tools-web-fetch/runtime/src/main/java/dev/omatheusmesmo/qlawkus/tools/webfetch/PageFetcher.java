package dev.omatheusmesmo.qlawkus.tools.webfetch;

import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.netty.channel.ConnectTimeoutException;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Fetches one page for the agent. Hand-written on the Vert.x HTTP client rather than Quarkus REST Client
 * because the URL is the model's, not configuration: each hop of a redirect chain must pass
 * {@link FetchPolicy} before a connection is opened, which a REST Client interface cannot express.
 *
 * <p>The connection is pinned. {@link FetchPolicy#resolve} validates the addresses a host resolves to,
 * and the request connects to one of those through {@code RequestOptions.setServer}, while the URL's own
 * host still supplies the {@code Host} header and the TLS server name and certificate check. Nothing is
 * resolved a second time, so a name that answers differently the second time (DNS rebinding) cannot lead
 * the connection to an address the policy never saw. The JDK client cannot do this: it offers no way to
 * connect to an address of the caller's choosing while keeping the host name for TLS.
 *
 * <p>The client never follows redirects and never decodes a compressed body (it sends no
 * {@code Accept-Encoding}), so the hop limit, the policy and the byte cap are all enforced by this class.
 * {@code @Retry} covers one HTTP call, not the whole redirect chain, and only a
 * {@link TransientHttpException} (429, 5xx or a network failure) is retried. A timeout is permanent: it
 * says the page is slow, and retrying it would multiply the wait. The caller blocks while the client's
 * event loop does the I/O, so this runs on a worker thread, as tool calls do.
 */
@ApplicationScoped
public class PageFetcher {

    private static final long MIN_BYTES = 1024;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final int SNIFF_BYTES = 1024;

    record Page(URI uri, int status, String mime, String charset, byte[] body, boolean cut) {
    }

    record Exchange(URI redirect, Page page) {
    }

    private final WebFetchConfig config;
    private final FetchPolicy policy;
    private final HttpClient client;
    private final Duration timeout;
    private final long byteCap;
    private final int maxRedirects;

    @Inject
    public PageFetcher(WebFetchConfig config, FetchPolicy policy, Vertx vertx) {
        this.config = config;
        this.policy = policy;
        this.timeout = Duration.ofSeconds(Math.clamp(config.timeout().toSeconds(), 1, 120));
        this.byteCap = Math.clamp(config.maxResponseBytes(), MIN_BYTES, MAX_BYTES);
        this.maxRedirects = Math.clamp(config.maxRedirects(), 0, 10);
        this.client = vertx.createHttpClient(new HttpClientOptions()
                .setDecompressionSupported(false)
                .setConnectTimeout((int) timeout.toMillis())
                .setKeepAlive(false));
    }

    Page fetch(URI start) {
        URI current = start;
        for (int hop = 0;; hop++) {
            InetAddress[] validated = resolve(current);
            Exchange exchange = exchange(current, validated[0]);
            if (exchange.page() != null) {
                return exchange.page();
            }
            if (hop >= maxRedirects) {
                throw new FetchFailure("Stopped after " + maxRedirects + " redirects without reaching a page; the last "
                        + "address was " + exchange.redirect() + ".");
            }
            current = exchange.redirect();
        }
    }

    private InetAddress[] resolve(URI uri) {
        try {
            return policy.resolve(uri);
        } catch (IllegalStateException e) {
            throw new FetchFailure("Could not resolve host '" + uri.getHost() + "'. Check the address.");
        }
    }

    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = TransientHttpException.class)
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    Exchange exchange(URI uri, InetAddress pinned) {
        CompletableFuture<Exchange> result = new CompletableFuture<>();
        AtomicReference<HttpClientRequest> inFlight = new AtomicReference<>();
        RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setFollowRedirects(false)
                .setServer(SocketAddress.inetSocketAddress(port(uri), pinned.getHostAddress()))
                .setHost(uri.getHost())
                .setPort(port(uri))
                .setSsl("https".equalsIgnoreCase(uri.getScheme()))
                .setURI(target(uri))
                .setConnectTimeout(timeout.toMillis())
                .setIdleTimeout(timeout.toMillis());
        client.request(options)
                .compose(request -> {
                    inFlight.set(request);
                    request.putHeader("User-Agent", config.userAgent());
                    request.putHeader("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5");
                    return request.send();
                })
                .onSuccess(response -> read(uri, response, result))
                .onFailure(result::completeExceptionally);
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            abandon(inFlight);
            throw timedOut(uri);
        } catch (ExecutionException e) {
            throw failure(uri, e.getCause());
        } catch (InterruptedException e) {
            abandon(inFlight);
            Thread.currentThread().interrupt();
            throw new FetchFailure("The request to " + uri.getHost() + " was interrupted.");
        }
    }

    private void read(URI uri, HttpClientResponse response, CompletableFuture<Exchange> result) {
        int status = response.statusCode();
        try {
            if (status == 429 || status >= 500) {
                discard(response);
                result.completeExceptionally(new TransientHttpException(status, "from " + uri.getHost()));
                return;
            }
            if (status >= 300 && status < 400) {
                discard(response);
                result.complete(redirect(uri, status, response.getHeader("Location")));
                return;
            }
            if (status / 100 != 2) {
                discard(response);
                result.completeExceptionally(new FetchFailure("The server answered HTTP " + status + " for " + uri + "."));
                return;
            }
            String contentType = response.getHeader("Content-Type") == null ? "" : response.getHeader("Content-Type");
            String mime = mime(contentType);
            if (!mime.isEmpty() && !readable(mime)) {
                discard(response);
                result.completeExceptionally(new FetchFailure("Not fetched: the content type " + mime
                        + " is not text the agent can read."));
                return;
            }
            collect(uri, status, mime, contentType, response, result);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }

    private void collect(URI uri, int status, String mime, String contentType, HttpClientResponse response,
            CompletableFuture<Exchange> result) {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        response.handler((Buffer chunk) -> {
            if (result.isDone()) {
                return;
            }
            long room = byteCap + 1 - collected.size();
            collected.write(chunk.getBytes(), 0, (int) Math.min(room, chunk.length()));
            if (collected.size() > byteCap) {
                finish(result, uri, status, mime, contentType, collected.toByteArray());
                discard(response);
            }
        });
        response.endHandler(done -> finish(result, uri, status, mime, contentType, collected.toByteArray()));
        response.exceptionHandler(result::completeExceptionally);
    }

    static void discard(HttpClientResponse response) {
        try {
            response.exceptionHandler(ignored -> {
            });
        } catch (IllegalStateException ended) {
            return;
        }
        response.request().reset();
    }

    private void finish(CompletableFuture<Exchange> result, URI uri, int status, String mime, String contentType,
            byte[] bytes) {
        try {
            boolean cut = bytes.length > byteCap;
            byte[] body = cut ? Arrays.copyOf(bytes, (int) byteCap) : bytes;
            String effective = mime.isEmpty() ? sniff(body) : mime;
            result.complete(new Exchange(null, new Page(uri, status, effective, charset(contentType), body, cut)));
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }

    private static Exchange redirect(URI uri, int status, String location) {
        if (location == null || location.isBlank()) {
            throw new FetchFailure("The server answered HTTP " + status + " without saying where to go.");
        }
        try {
            return new Exchange(uri.resolve(location.strip()), null);
        } catch (IllegalArgumentException e) {
            throw new FetchFailure("The server redirected to an address that is not a valid URL.");
        }
    }

    private RuntimeException failure(URI uri, Throwable cause) {
        if (cause instanceof FetchFailure fetchFailure) {
            return fetchFailure;
        }
        if (cause instanceof TransientHttpException transientFailure) {
            return transientFailure;
        }
        if (cause instanceof TimeoutException || cause instanceof ConnectTimeoutException) {
            return timedOut(uri);
        }
        return new TransientHttpException("Request to " + uri.getHost() + " failed: "
                + cause.getClass().getSimpleName(), cause);
    }

    private FetchFailure timedOut(URI uri) {
        return new FetchFailure("The request to " + uri.getHost() + " timed out after " + timeout.toSeconds()
                + " seconds.");
    }

    private static void abandon(AtomicReference<HttpClientRequest> inFlight) {
        HttpClientRequest request = inFlight.get();
        if (request != null) {
            request.reset();
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String target(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    static String mime(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
    }

    static String charset(String contentType) {
        Matcher matcher = CHARSET.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean readable(String mime) {
        return mime.startsWith("text/")
                || mime.equals("application/json")
                || mime.equals("application/xml")
                || mime.equals("application/xhtml+xml")
                || mime.endsWith("+json")
                || mime.endsWith("+xml");
    }

    private static String sniff(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, SNIFF_BYTES), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        if (head.indexOf('\0') >= 0) {
            throw new FetchFailure("Not fetched: the response has no content type and looks like binary data.");
        }
        return head.contains("<html") || head.contains("<!doctype html") ? "text/html" : "text/plain";
    }
}
