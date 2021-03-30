package gevorgyan.cloudstorage.server.handlers;

import gevorgyan.cloudstorage.server.keepers.ClientStatus;
import gevorgyan.cloudstorage.server.keepers.Const;
import gevorgyan.cloudstorage.server.keepers.CurrentAction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import gevorgyan.cloudstorage.common.callbacks.BoolCallback;
import gevorgyan.cloudstorage.common.callbacks.Callback;
import gevorgyan.cloudstorage.common.infoBytes.Command;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHandler extends ChannelInboundHandlerAdapter {
    private ClientStatus clientStatus;
    private byte[] buf = new byte[256];
    private int nameSize;



    public MainHandler(ClientStatus clientStatus){
        this.clientStatus = clientStatus;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf accumulator = (ByteBuf) msg;

        byte signalByte = accumulator.readByte();

        if(signalByte == Command.upload){
            clientStatus.setCurrentAction(CurrentAction.UPLOAD);
            int nameSize = accumulator.readInt();

            accumulator.readBytes(buf, 0, nameSize);
            clientStatus.setCurrentFileName(new String(buf, 0, nameSize , StandardCharsets.UTF_8));

            Files.deleteIfExists(Paths.get(Const.CLOUD_PACKAGE + "/" + clientStatus.getLogin() + "/" + clientStatus.getCurrentFileName()));
            long filesize = accumulator.readLong();
            clientStatus.setCurrentFileSize(filesize);
            accumulator.clear();
            clientStatus.setUploadFile(new RandomAccessFile(Const.CLOUD_PACKAGE + "/" + clientStatus.getCurrentDir() + "/" + clientStatus.getCurrentFileName(), "rw"));
            ctx.pipeline().get(FirstHandler.class).setAccumulatorCapacity(ctx,1024, 65536);
            sendCommand(ctx, Command.commandOK);
            return;
        }

        if(signalByte == Command.download){
            clientStatus.setCurrentAction(CurrentAction.DOWNLOAD);
            nameSize = accumulator.readInt();
            accumulator.readBytes(buf, 0, nameSize);
            clientStatus.setCurrentFileName(new String(buf, 0, nameSize, StandardCharsets.UTF_8));


            long start = System.currentTimeMillis();
            System.out.println("start download");
            FileStorageService.sendToClientFile(ctx, clientStatus, futureListener ->{
                if (futureListener.isSuccess()) {
                    long end = System.currentTimeMillis();
                    System.out.println("end download");
                    System.out.println((end - start)/1000 + "download time");
                    System.out.println("file send complete");
                } else {
                    futureListener.cause().printStackTrace();
                    System.out.println("download error");
                }
            });
            clearStatusSetWaitCommand(ctx);
            return;
        }

        if(signalByte == Command.storage_INFO){
            int storagePathSize = accumulator.readInt();
            accumulator.readBytes(buf, 0, storagePathSize);
            accumulator.clear();
            FileStorageService.sendStorageInfo(clientStatus, ctx, new String(buf, 0, storagePathSize, StandardCharsets.UTF_8), new Callback() {
                @Override
                public void callback() {
                    System.out.println("storage info send");
                    clearStatusSetWaitCommand(ctx);
                }
            } );
            return;
        }

        if(signalByte == Command.delete_FILE){
            nameSize = accumulator.readInt();
            accumulator.readBytes(buf, 0, nameSize);
            accumulator.clear();
            clientStatus.setCurrentFileName(new String(buf, 0, nameSize , StandardCharsets.UTF_8));

            FileStorageService.deleteFile(clientStatus, new Callback() {
                @Override
                public void callback() {
                    System.out.println("file deleted");
                    clearStatusSetWaitCommand(ctx);
                    sendCommand(ctx, Command.commandOK);
                }
            } );
            return;
        }

        if(signalByte == Command.create_DIRECTORY){
            nameSize = accumulator.readInt();
            accumulator.readBytes(buf, 0, nameSize);
            accumulator.clear();

            FileStorageService.createDirectory(clientStatus, new String(buf, 0, nameSize, StandardCharsets.UTF_8), new BoolCallback() {
                @Override
                public void callback(boolean bool) {
                    if(bool){
                        sendCommand(ctx, Command.commandOK);
                    } else {
                        sendCommand(ctx, Command.commandNO);
                    }
                }
            });
            clearStatusSetWaitCommand(ctx);
        }

    }


    private void clearStatusSetWaitCommand(ChannelHandlerContext ctx){
        clientStatus.setCurrentFileSize(-1);
        clientStatus.setCurrentFileName(null);
        clientStatus.setCurrentAction(CurrentAction.COMMAND_SIZE);
        ctx.pipeline().get(FirstHandler.class).setAccumulatorCapacity(ctx,ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY, ctx.pipeline().get(FirstHandler.class).COMMAND_ACC_CAPACITY);
    }
    private void sendCommand(ChannelHandlerContext ctx, byte command) {
        ByteBuf buf = null;
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(command);
        ctx.writeAndFlush(buf);
    }
}
