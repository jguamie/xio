package com.xjeffrose.xio.server;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.xjeffrose.xio.core.XioTimer;
import io.netty.channel.ChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.Timer;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;


import static java.util.concurrent.Executors.newCachedThreadPool;

public class XioServerConfigBuilder extends XioConfigBuilderBase<XioServerConfigBuilder> {
  private final ChannelConfig socketChannelConfig = (ChannelConfig) Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[]{ChannelConfig.class},
      new Magic("child.")
  );
  private final ServerSocketChannelConfig serverSocketChannelConfig = (ServerSocketChannelConfig) Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[]{ServerSocketChannelConfig.class},
      new Magic(""));

  @Inject
  public XioServerConfigBuilder() {
//    getSocketChannelConfig().setOption(ChannelOption.TCP_NODELAY, true);
  }

  public ChannelConfig getSocketChannelConfig() {
    return socketChannelConfig;
  }

  public ServerSocketChannelConfig getServerSocketChannelConfig() {
    return serverSocketChannelConfig;
  }

  public XioServerConfig build() {
    Timer timer = getTimer();
    ExecutorService bossExecutor = getBossExecutor();
    int bossThreadCount = getBossThreadCount();
    ExecutorService workerExecutor = getWorkerExecutor();
    int workerThreadCount = getWorkerThreadCount();

    return new XioServerConfig(
        getBootstrapOptions(),
        timer != null ? timer : new XioTimer(threadNamePattern("")),
        bossExecutor != null ? bossExecutor : buildDefaultBossExecutor(),
        bossThreadCount,
        workerExecutor != null ? workerExecutor : buildDefaultWorkerExecutor(),
        workerThreadCount
    );
  }

  private ExecutorService buildDefaultBossExecutor() {
    return newCachedThreadPool(renamingThreadFactory(threadNamePattern("-boss-%s")));
  }

  private ExecutorService buildDefaultWorkerExecutor() {
    return newCachedThreadPool(renamingThreadFactory(threadNamePattern("-worker-%s")));
  }

  private String threadNamePattern(String suffix) {
    String xioName = getXioName();
    return "xio-server" + (Strings.isNullOrEmpty(xioName) ? "" : "-" + xioName) + suffix;
  }

  private ThreadFactory renamingThreadFactory(String nameFormat) {
    return new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
  }
}