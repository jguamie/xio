package com.xjeffrose.xio.tracing;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.CurrentTraceContext;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import lombok.Getter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class HttpClientTracingState {

  private static final AttributeKey<TraceContext> context_key = AttributeKey.newInstance("xio_client_tracing_context");
  private static final AttributeKey<Tracer> tracer_key = AttributeKey.newInstance("xio_client_tracing_tracer");
  private static final AttributeKey<Span> span_key = AttributeKey.newInstance("xio_client_tracing_span");
  private static final AttributeKey<Tracer.SpanInScope> span_in_scope_key = AttributeKey.newInstance("xio_client_tracing_span_in_scope");

  @Getter
  private final Tracing tracing;
  @Getter
  private final Tracer tracer;
  @Getter
  private final HttpClientHandler<HttpRequest, HttpResponse> handler;
  @Getter
  private final TraceContext.Injector<HttpHeaders> injector;

  public HttpClientTracingState(HttpTracing httpTracing, boolean ssl) {
    tracing = httpTracing.tracing();
    tracer = tracing.tracer();
    handler = HttpClientHandler.create(httpTracing, new XioHttpClientAdapter(ssl));
    injector = httpTracing.tracing().propagation().injector(HttpHeaders::set);
  }

  private HttpHeaders addRemoteIp(ChannelHandlerContext ctx, HttpHeaders headers) {
    SocketAddress address = ctx.channel().remoteAddress();
    if (address instanceof InetSocketAddress) {
      headers.set("x-remote-ip", ((InetSocketAddress)address).getHostString());
    }
    return headers;
  }


  public static void attachContext(ChannelHandlerContext ctx, TraceContext context) {
    ctx.channel().attr(context_key).set(context);
  }

  public static void attachTracer(Channel ch, Tracer tracer) {
    ch.attr(tracer_key).set(tracer);
  }

  public static void attachSpan(Channel ch, Span span) {
    ch.attr(span_key).set(span);
  }

  public static void attachSpanInScope(Channel ch, Tracer.SpanInScope spanInScope) {
    ch.attr(span_in_scope_key).set(spanInScope);
  }

  public Span requestSpan(ChannelHandlerContext ctx, HttpRequest request) {
    TraceContext parent = ctx.channel().attr(context_key).getAndSet(null);
    CurrentTraceContext.Scope scope = null;
    if (parent != null) {
      scope = tracing.currentTraceContext().newScope(parent);
    }
    Span span = handler.handleSend(injector, addRemoteIp(ctx, request.headers()), request);
    attachSpan(ctx.channel(), span);
    attachTracer(ctx.channel(), tracer);

    if (scope != null) {
      scope.close();
    }

    return span;
  }

  public Tracer.SpanInScope requestSpanInScope(ChannelHandlerContext ctx, Span span) {
    Tracer.SpanInScope spanInScope = tracer.withSpanInScope(span);
    attachSpanInScope(ctx.channel(), spanInScope);

    return spanInScope;
  }

  public static Tracer tracer(Channel ch) {
    return ch.attr(tracer_key).get();
  }

  public static Tracer tracer(ChannelHandlerContext ctx) {
    return tracer(ctx.channel());
  }

  public static Span responseSpan(Channel ch) {
    return ch.attr(span_key).get();
  }

  public static Span responseSpan(ChannelHandlerContext ctx) {
    return responseSpan(ctx.channel());
  }

  public static Tracer.SpanInScope responseSpanInScope(Channel ch) {
    return ch.attr(span_in_scope_key).get();
  }

  public static Tracer.SpanInScope responseSpanInScope(ChannelHandlerContext ctx) {
    return responseSpanInScope(ctx.channel());
  }
}
