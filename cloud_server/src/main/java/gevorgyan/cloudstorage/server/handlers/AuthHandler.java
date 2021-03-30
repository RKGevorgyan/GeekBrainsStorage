package gevorgyan.cloudstorage.server.handlers;

import gevorgyan.cloudstorage.server.keepers.CurrentAction;
import gevorgyan.cloudstorage.server.dataBase.DBService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import gevorgyan.cloudstorage.common.infoBytes.Command;
import gevorgyan.cloudstorage.server.keepers.Const;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;


public class AuthHandler extends ChannelInboundHandlerAdapter {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client connected");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("try auth");
        ByteBuf accumulator = (ByteBuf)msg;
        byte[] data = new byte[accumulator.capacity()];
        accumulator.readBytes(data);
        accumulator.clear();
        String[] authData = new String(data, StandardCharsets.UTF_8).split(" ");   // login pass #  ili login pass &
        if(FirstHandler.getConnectedUsers().contains(authData[0])){
            restartAuth(ctx);
            return;
        }
        if (authData[2].equals("#")) {
            if (DBService.checkLoginPass(authData[0], authData[1])) {
                if(!Files.exists(Paths.get(Const.CLOUD_PACKAGE + "/" + authData[0]))) {
                    try {
                        Files.createDirectory(Paths.get(Const.CLOUD_PACKAGE + "/" + authData[0]));
                    } catch (IOException e) {
                        throw new RuntimeException();
                    }
                }

                finishAuth(ctx, authData[0]);
                System.out.println("auth ok");
            }else{
                System.out.println("wrong log pass");
                restartAuth(ctx);
            }
        }

        if (authData[2].equals("&")) {
            if (DBService.addUser(authData[0], authData[1])) {
                Files.createDirectory(Paths.get(Const.CLOUD_PACKAGE + "/" + authData[0]));
                finishAuth(ctx, authData[0]);

            }else {
                restartAuth(ctx);
            }
        }
    }

    private void finishAuth(ChannelHandlerContext ctx, String login){
        ctx.pipeline().get(FirstHandler.class).getClientStatus().setCurrentAction(CurrentAction.COMMAND_SIZE);
        ctx.pipeline().get(FirstHandler.class).getClientStatus().setLogin(login);
        ctx.pipeline().get(FirstHandler.class).getClientStatus().setCurrentDir(login);
        ctx.pipeline().addLast(new MainHandler(ctx.pipeline().get(FirstHandler.class).getClientStatus()));
        ctx.pipeline().get(FirstHandler.class).setAccumulatorCapacity(ctx,ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY, ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY);
        FirstHandler.getConnectedUsers().add(login);
        sendCommand(ctx, Command.commandOK);
        ctx.pipeline().remove(this);
    }
    private void restartAuth(ChannelHandlerContext ctx){
        ctx.pipeline().get(FirstHandler.class).getClientStatus().setCurrentAction(CurrentAction.COMMAND_SIZE);
        ctx.pipeline().get(FirstHandler.class).setAccumulatorCapacity(ctx,ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY, ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY);
        sendCommand(ctx, Command.commandNO);
    }

    private void sendCommand(ChannelHandlerContext ctx, byte command) {
        ByteBuf buf = null;
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(command);
        ctx.writeAndFlush(buf);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
