package ch.novabliz;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * created by Steven Zou on 2019/3/24
 */
public class NettyServer {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    public enum State {Created, Initialized, Starting, Started, Shutdown}

    private AtomicReference<State> serverState = new AtomicReference<>(State.Created);

    private int port;
    private String host;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    private Class<? extends ServerChannel> serverSocketChannel;

    public NettyServer(int port) {
        this.port = port;
    }

    public NettyServer(int port, String host) {
        this.port = port;
        this.host = host;
    }

    public void init() {
        if (!serverState.compareAndSet(State.Created, State.Initialized))
            throw new RuntimeException("server has been inited.");
    }

    public void start() {
        if (!serverState.compareAndSet(State.Initialized, State.Starting))
            throw new RuntimeException("server state illegal");

        if (isSupportEpoll()) {
            createEpollServer();
        } else {
            createNioServer();
        }
    }

    private boolean isSupportEpoll() {
        String osName = System.getProperty("os.name");
        if (osName.contains("linux")) {
            try {
                Class.forName("io.netty.channel.epoll.Native");
                return true;
            } catch (Throwable error) {
                LOGGER.warn("can not load netty epoll, switch nio model.");
            }
        }
        return false;
    }

    private void createEpollServer() {
        if (bossGroup == null)
            bossGroup = new EpollEventLoopGroup();
        if (workGroup == null)
            workGroup = new EpollEventLoopGroup();
        serverSocketChannel = EpollServerSocketChannel.class;

        createServer();
    }

    private void createNioServer() {
        if(bossGroup == null)
            bossGroup = new NioEventLoopGroup();
        if(workGroup == null)
            workGroup = new NioEventLoopGroup();

        serverSocketChannel = NioServerSocketChannel.class;

        createServer();
    }

    private void createServer() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workGroup).channel(serverSocketChannel).option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel channel) {
            }
        });
        bootstrap.bind(port).addListener(future -> {
                    if (future.isSuccess()) {
                        serverState.set(State.Started);
                        LOGGER.info("server start success on port:{}", port);
                    } else {
                        LOGGER.warn("server start fail on port:{}", port);
                    }
                }
        );
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkGroup() {
        return workGroup;
    }
}
