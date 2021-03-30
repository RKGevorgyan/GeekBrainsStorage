package gevorgyan.cloudstorage.clientside;

import javafx.application.Platform;
import javafx.scene.control.ProgressBar;
import gevorgyan.cloudstorage.common.callbacks.BoolCallback;
import gevorgyan.cloudstorage.common.infoBytes.Command;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;


public class Network {

    private static Network ourInstance = new Network();

    public static Network getInstance() {
        return ourInstance;
    }

    private Network() {
    }

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;



    public void start(CountDownLatch countDownLatch) {

        try {
            socket = new Socket("localhost", 8189);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            countDownLatch.countDown();

        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void sendAuth(String authData, BoolCallback callback) throws IOException{
        out.writeInt(authData.getBytes(StandardCharsets.UTF_8).length);
        if (in.readByte() == Command.commandOK) {
            out.write(authData.getBytes(StandardCharsets.UTF_8));
            callback.callback(in.readByte() == Command.commandOK);
        } else {
            System.err.println("ошибка ответа сервера");
        }
    }

    public void closeConnection(){
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String askStorageInfo(String storagePath){
        byte[] byteInfo;
        int commandSize = Byte.BYTES + Integer.BYTES + storagePath.getBytes(StandardCharsets.UTF_8).length;
        try{
            out.writeInt(commandSize);
            if (in.readByte() == Command.commandOK) {
                out.writeByte(Command.storage_INFO);
                out.writeInt(storagePath.getBytes(StandardCharsets.UTF_8).length);
                out.write(storagePath.getBytes(StandardCharsets.UTF_8));
                byteInfo = new byte[in.readInt()];
                in.read(byteInfo);
                return new String(byteInfo, StandardCharsets.UTF_8);
            } else {
                System.err.println("ошибка ответа сервера");
                return null;
            }
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    public void uploadFIle(String path, String selectedFilename, long selectedFileSize, ProgressBar progressBar, BoolCallback callback) {
        new Thread(() -> {
            int commandSize = Byte.BYTES + Integer.BYTES + selectedFilename.getBytes(StandardCharsets.UTF_8).length + Long.BYTES;
            try {
                out.writeInt(commandSize);
                if (in.readByte() != Command.commandOK) {
                    callback.callback(false);
                    return;
                }
                out.writeByte(Command.upload);
                out.writeInt(selectedFilename.getBytes(StandardCharsets.UTF_8).length);
                out.write(selectedFilename.getBytes(StandardCharsets.UTF_8));
                out.writeLong(selectedFileSize);
                if (in.readByte() != Command.commandOK) {
                    callback.callback(false);
                    return;
                }
                byte[] buffer = new byte[1024 * 5 * 1024];
                int readBytes;
                try(RandomAccessFile raf = new RandomAccessFile(path + "/" + selectedFilename, "r")){
                    AtomicLong count = new AtomicLong(0L);
                    while(raf.getFilePointer() != raf.length()){
                        readBytes = raf.read(buffer);
                        out.write(buffer, 0, readBytes);
                        count.getAndAdd(readBytes);
                        Platform.runLater(() -> {
                            progressBar.setProgress((double) count.get() /selectedFileSize);
                        });
                    }
                }
                callback.callback(in.readByte() == Command.commandOK);
            } catch (IOException e) {
                callback.callback(false);
                e.printStackTrace();
            }
        }).start();
    }

    public void downloadFile(String path, String filename, long fileSize, ProgressBar progressBar, BoolCallback callback) {
        new Thread(() -> {
            try {
                int commandSize = Byte.BYTES + Integer.BYTES + filename.getBytes(StandardCharsets.UTF_8).length;
                out.writeInt(commandSize);
                if(in.readByte() != Command.commandOK){
                    callback.callback(false);
                    return;
                }

                out.writeByte(Command.download);
                out.writeInt(filename.getBytes(StandardCharsets.UTF_8).length);
                out.write(filename.getBytes(StandardCharsets.UTF_8));

                Path file = Paths.get(path + "/" + filename);
                Files.deleteIfExists(Paths.get(path + "/" + filename));
                Files.createFile(Paths.get(path + "/" + filename));

                try (FileOutputStream fos = new FileOutputStream(file.toFile(), true))
                {
                    byte[] buffer = new byte[65536];
                    int readBytes;
                    while (Files.size(file) != fileSize) {
                        readBytes = in.read(buffer);
                        //System.out.println(readBytes);
                        fos.write(buffer, 0, readBytes);
                        Platform.runLater(() -> {
                            try {
                                progressBar.setProgress((double) Files.size(file)/fileSize);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                    System.out.println("file download");
                    callback.callback(true);
                }

            }catch (IOException e){
                e.printStackTrace();
            }
        }).start();
    }

    public void deleteFile(String filename, BoolCallback callback) {
        try {
            int commandSize = Byte.BYTES + Integer.BYTES + filename.getBytes(StandardCharsets.UTF_8).length;
            out.writeInt(commandSize);
            if(in.readByte() != Command.commandOK){
                callback.callback(false);
                return;
            }
            out.writeByte(Command.delete_FILE);
            out.writeInt(filename.getBytes(StandardCharsets.UTF_8).length);
            out.write(filename.getBytes(StandardCharsets.UTF_8));
            if (in.readByte() == Command.commandOK){
                callback.callback(true);
            } else {callback.callback(false);}
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void createDirectory(String dirName, BoolCallback callback){
        try{
            int commandSize = Byte.BYTES + Integer.BYTES + dirName.getBytes(StandardCharsets.UTF_8).length;
            out.writeInt(commandSize);
            if(in.readByte() != Command.commandOK){
                callback.callback(false);
                return;
            }
            out.writeByte(Command.create_DIRECTORY);
            out.writeInt(dirName.getBytes(StandardCharsets.UTF_8).length);
            out.write(dirName.getBytes(StandardCharsets.UTF_8));
            if (in.readByte() == Command.commandOK){
                callback.callback(true);
            } else {callback.callback(false);}
        } catch (IOException e){
            e.printStackTrace();
        }
    }


}
