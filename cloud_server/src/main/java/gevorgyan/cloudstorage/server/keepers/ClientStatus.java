package gevorgyan.cloudstorage.server.keepers;

import java.io.RandomAccessFile;

public class ClientStatus {
    private String login;
    private String currentDir;
    private CurrentAction currentAction;
    private String currentFileName;
    private long currentFileSize;
    private RandomAccessFile uploadFile;

    public RandomAccessFile getUploadFile() {
        return uploadFile;
    }

    public void setUploadFile(RandomAccessFile uploadFile) {
        this.uploadFile = uploadFile;
    }

    public String getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(String currentDir) {
        this.currentDir = currentDir;
    }

    public ClientStatus(){
        this.login = null;
        this.currentAction = CurrentAction.COMMAND_SIZE;
        this.currentFileName = null;
        this.currentFileSize = -1;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public CurrentAction getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(CurrentAction currentAction) {
        this.currentAction = currentAction;
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public void setCurrentFileName(String currentFileName) {
        this.currentFileName = currentFileName;
    }

    public long getCurrentFileSize() {
        return currentFileSize;
    }

    public void setCurrentFileSize(long currentFileSize) {
        this.currentFileSize = currentFileSize;
    }
}