package gevorgyan.cloudstorage.server.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import gevorgyan.cloudstorage.common.callbacks.BoolCallback;
import gevorgyan.cloudstorage.common.callbacks.Callback;
import gevorgyan.cloudstorage.server.keepers.ClientStatus;
import gevorgyan.cloudstorage.server.keepers.Const;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileStorageService {
    static long start;


    public static void sendToClientFile(ChannelHandlerContext ctx, ClientStatus clientStatus, ChannelFutureListener finishListener) throws IOException {

        Path path = Paths.get(Const.CLOUD_PACKAGE + "/" + clientStatus.getCurrentDir() + "/" + clientStatus.getCurrentFileName());
        FileRegion region = new DefaultFileRegion(path.toFile(), 0, Files.size(path));
        ChannelFuture future = ctx.channel().writeAndFlush(region);
        if (finishListener != null) {
            future.addListener(finishListener);
        }
    }

    public static void uploadFile(ClientStatus clientStatus, ByteBuf accumulator, byte[] bytes, Callback callback) throws IOException {
        RandomAccessFile raf = clientStatus.getUploadFile();
        if (raf.length() == 0) {
            start = System.currentTimeMillis();
            System.out.println("start upload");
        }
        raf.seek(raf.length());
        int readableBytes = accumulator.readableBytes();
        accumulator.readBytes(bytes, 0, readableBytes);
        accumulator.clear();
        raf.write(bytes, 0, readableBytes);
        if (raf.length() == clientStatus.getCurrentFileSize()) {
            raf.close();
            clientStatus.setUploadFile(null);
            long end = System.currentTimeMillis();
            System.out.println("end upload");
            System.out.println((end - start) / 1000 + "upload time");
            callback.callback();
        }
    }

    public static void sendStorageInfo(ClientStatus clientStatus, ChannelHandlerContext ctx, String storagePath, Callback callback) {
        clientStatus.setCurrentDir(storagePath);
        String directoryList;
        try (Stream<Path> dirs = Files.list(Paths.get(Const.CLOUD_PACKAGE + "/" + storagePath))) {
            directoryList = dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName() + ":-1")
                    .collect(Collectors.joining(":"));
        } catch (IOException e) {
            throw new RuntimeException("Something wrong with stream dirs");
        }

        String filesList;
        try (Stream<Path> files = Files.list(Paths.get(Const.CLOUD_PACKAGE + "/" + storagePath))) {
            filesList = files.filter(p -> !Files.isDirectory(p))
                    .map(Path::toFile)
                    .map(file -> file.getName() + ":" + file.length())
                    .collect(Collectors.joining(":"));
        } catch (IOException e) {
            throw new RuntimeException("Something wrong with stream dirs");
        }

        StringBuilder sb_storage_info = new StringBuilder();
        sb_storage_info.append(storagePath);
        if (directoryList.length() > 0) sb_storage_info.append(":" + directoryList);
        if (filesList.length() > 0) sb_storage_info.append(":" + filesList);
        ByteBuf buf = null;
        buf = ByteBufAllocator.DEFAULT.directBuffer(4 + sb_storage_info.toString().getBytes(StandardCharsets.UTF_8).length);
        buf.writeInt(sb_storage_info.toString().getBytes(StandardCharsets.UTF_8).length);
        buf.writeBytes(sb_storage_info.toString().getBytes(StandardCharsets.UTF_8));
        ctx.writeAndFlush(buf);
        callback.callback();
    }

    public static void deleteFile(ClientStatus clientStatus, Callback callback) {
        try {
            Path directory = Paths.get(Const.CLOUD_PACKAGE + "/" + clientStatus.getCurrentDir() + "/" + clientStatus.getCurrentFileName());
            System.out.println(directory.getFileName().toString());


            FileVisitor visitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    System.out.println("удаляем файл " + file.getFileName().toString());
                    Files.delete(file);
                    System.out.println("удалили");
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Files.delete(file);
                    System.out.println("visitFileFailed");
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        System.out.println("null exc");
                        throw exc;
                    }
                    System.out.println("удаляем папку " + dir.getFileName().toString());
                    Files.delete(dir);
                    System.out.println("удалили ");
                    return FileVisitResult.CONTINUE;
                }
            };

            Path path = Files.walkFileTree(directory, visitor);

            if (!Files.exists(path)) {
                System.out.println("before callback");
                callback.callback();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void createDirectory(ClientStatus clientStatus, String dirName, BoolCallback callback) {
        try {
            Files.createDirectory(Paths.get(Const.CLOUD_PACKAGE + "/" + clientStatus.getCurrentDir() + "/" + dirName));
            callback.callback(true);
        } catch (IOException e) {
            callback.callback(false);
        }

    }

}
