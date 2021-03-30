package gevorgyan.cloudstorage.server.handlers;

import gevorgyan.cloudstorage.server.keepers.ClientStatus;
import gevorgyan.cloudstorage.server.keepers.CurrentAction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import gevorgyan.cloudstorage.common.callbacks.Callback;
import gevorgyan.cloudstorage.common.infoBytes.Command;
import gevorgyan.cloudstorage.server.keepers.Const;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class FirstHandler extends ChannelInboundHandlerAdapter {
    static Set<String> connectedUsers = new HashSet<>();

    private ClientStatus clientStatus;
    private ByteBuf accumulator;
    public final int COMMAND_ACC_CAPACITY = 4;
    private byte[] bytes = new byte[65536];



    public FirstHandler(){
        clientStatus = new ClientStatus();
        if(!Files.exists(Paths.get(Const.CLOUD_PACKAGE))) {
            try {
                Files.createDirectory(Paths.get(Const.CLOUD_PACKAGE));
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
    }

    public ClientStatus getClientStatus() {
        return clientStatus;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ByteBufAllocator allocator = ctx.alloc();
        accumulator = allocator.directBuffer(COMMAND_ACC_CAPACITY, COMMAND_ACC_CAPACITY);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf inBuf = (ByteBuf) msg;
        accumulator.writeBytes(inBuf);
        inBuf.release();
        if(clientStatus.getCurrentAction() == CurrentAction.UPLOAD){
            FileStorageService.uploadFile(clientStatus, accumulator, bytes, new Callback() {

                @Override
                public void callback() {
                    System.out.println("file upload done " + clientStatus.getCurrentFileName());
                    clearStatusSetWaitCommand(ctx);
                    sendCommand(ctx, Command.commandOK);
                }
            });
            return;
        }
        if (accumulator.readableBytes() == accumulator.capacity() && clientStatus.getCurrentAction() != CurrentAction.COMMAND_SIZE)
        {
            ctx.fireChannelRead(accumulator);
        }
        else if(accumulator.readableBytes() == accumulator.capacity())
        {
            if(clientStatus.getCurrentAction() == CurrentAction.COMMAND_SIZE ){clientStatus.setCurrentAction(CurrentAction.WAIT);}
            int cap = accumulator.readInt();
            setAccumulatorCapacity(ctx, cap, cap);
            sendCommand(ctx, Command.commandOK);
        }
    }


    void setAccumulatorCapacity(ChannelHandlerContext ctx, int accumulatorCapacity, int maxCapacity){
        accumulator.release();
        ByteBufAllocator allocator = ctx.alloc();
        accumulator = allocator.directBuffer(accumulatorCapacity, maxCapacity);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private void clearStatusSetWaitCommand(ChannelHandlerContext ctx){
        clientStatus.setCurrentFileSize(-1);
        clientStatus.setCurrentFileName(null);
        clientStatus.setCurrentAction(CurrentAction.COMMAND_SIZE);
        setAccumulatorCapacity(ctx, COMMAND_ACC_CAPACITY, COMMAND_ACC_CAPACITY);
    }
    private void sendCommand(ChannelHandlerContext ctx, byte command) {
        ByteBuf buf = null;
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(command);
        ctx.writeAndFlush(buf);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connectedUsers.remove(clientStatus.getLogin());
        System.out.println(clientStatus.getLogin() + " отключился");
    }

    public static Set<String> getConnectedUsers() {
        return connectedUsers;
    }
}
