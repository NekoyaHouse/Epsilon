package me.sofurry.via.protocoltranslator.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.flow.FlowControlHandler;

public class NoReadFlowControlHandler extends FlowControlHandler {

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().config().isAutoRead()) {
            super.read(ctx);
        }
    }

}
