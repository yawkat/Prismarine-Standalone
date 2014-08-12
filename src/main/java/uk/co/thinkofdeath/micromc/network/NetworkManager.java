package uk.co.thinkofdeath.micromc.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import uk.co.thinkofdeath.micromc.MicroMC;
import uk.co.thinkofdeath.micromc.log.LogUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkManager {

    private static final Logger logger = LogUtil.get(NetworkManager.class);
    private final MicroMC server;
    private Channel channel;

    public NetworkManager(MicroMC server) {
        this.server = server;
    }

    public void listen(String address, int port) {
        logger.log(Level.INFO, "Starting on {0}:{1,number,#}",
                new Object[]{address, port});

        final EventLoopGroup group = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ConnectionInitializer(this));

        channel = bootstrap.bind(address, port)
                .channel();
        channel.closeFuture()
                .addListener(future -> group.shutdownGracefully());

    }

    public void close() {
        logger.info("Disconnecting players");
        channel.close().awaitUninterruptibly();
    }
}
