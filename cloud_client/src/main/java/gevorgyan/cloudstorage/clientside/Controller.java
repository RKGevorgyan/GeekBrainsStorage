package gevorgyan.cloudstorage.clientside;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import gevorgyan.cloudstorage.clientside.filedata.FileInfo;
import gevorgyan.cloudstorage.clientside.filedata.StorageFileInfo;
import gevorgyan.cloudstorage.common.callbacks.BoolCallback;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class Controller implements Initializable {

    private final Network network = Network.getInstance();
    private String login;

    @FXML
    VBox localePanel, storagePanel;

    private LocalePanelController localPC;// = (LocalePanelController)localePanel.getProperties().get("ctrl");
    private StoragePanelController storagePC;// = (StoragePanelController) storagePanel.getProperties().get("ctrl");

    @FXML
    ProgressBar progressBar;
    @FXML
    HBox buttonsBox;
    @FXML
    Button uploadBtn, downloadBtn, deleteBtn;
    @FXML
    Button createDirBtn;
    @FXML
    TextField loginField;
    @FXML
    Button authBtn;
    @FXML
    CheckBox regField;
    @FXML
    PasswordField passField;
    @FXML
    HBox auth;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        localPC = (LocalePanelController)localePanel.getProperties().get("ctrl");
        storagePC = (StoragePanelController) storagePanel.getProperties().get("ctrl");
        try {
            CountDownLatch networkStarter = new CountDownLatch(1);
            network.start(networkStarter);
            networkStarter.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        progressBar.setVisible(false);
    }



    public void tryAuth(ActionEvent actionEvent) {
        if(loginField.getText() == null || loginField.getText().equals("\\s+") || passField.getText() == null || passField.getText().equals("\\s+")) return;
        StringBuilder sb = new StringBuilder();
        sb.append(loginField.getText().trim()).append(" ").append(passField.getText().trim());
        if(regField.isSelected()){
            sb.append(" &");
        } else {
            sb.append(" #");
        }

        try {
            network.sendAuth(sb.toString(), new BoolCallback() {
                @Override
                public void callback(boolean getAuth) {
                    if(getAuth){
                        auth.setVisible(false);
                        auth.setManaged(false);
                        storagePC.updateStorageList(network.askStorageInfo(loginField.getText()));
                        storagePC.setLogin(loginField.getText());
                        login = loginField.getText();
                    } else {
                        new Alert(Alert.AlertType.INFORMATION, "Неправильные данные", ButtonType.APPLY).showAndWait();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void btnExitAction(ActionEvent actionEvent) {
        exitAction();
    }

    public void exitAction() {
        network.closeConnection();
        Platform.exit();
    }

    public void btnDownload(ActionEvent actionEvent) {
        hideButtons();
        network.downloadFile(localPC.pathField.getText(), storagePC.getSelectedStorageFilename(), storagePC.getSelectedStorageFileSize(), progressBar, new BoolCallback() {
            @Override
            public void callback(boolean bool) {
                localPC.updateList(Paths.get(localPC.pathField.getText()));
                showButtons();
                if(bool) {
                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.INFORMATION, "File downloaded from cloud", ButtonType.APPLY).showAndWait();
                    });
                } else {
                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.ERROR, "Error while downloading", ButtonType.APPLY).showAndWait();
                    });
                }
            }
        });
    }
    public void btnUpload(ActionEvent actionEvent) {

        hideButtons();
        network.uploadFIle(localPC.getCurrentPath(), localPC.getSelectedFilename(), localPC.getSelectedFileSize(), progressBar,new BoolCallback() {
            @Override
            public void callback(boolean bool) {
                showButtons();
                if(bool) {
                    storagePC.updateStorageList(network.askStorageInfo(storagePC.storagePathField.getText()));
                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.INFORMATION, "File uploaded to cloud", ButtonType.APPLY).showAndWait();
                    });
                } else {
                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.ERROR, "Error while uploading", ButtonType.APPLY).showAndWait();
                    });
                }
            }
        });
    }

    private void showButtons(){
        buttonsBox.setDisable(false);
        storagePC.storageFilesTable.setDisable(false);
        storagePC.storageBtnUp.setDisable(false);
        progressBar.setVisible(false);
    }
    private void hideButtons(){
        buttonsBox.setDisable(true);
        storagePC.storageFilesTable.setDisable(true);
        storagePC.storageBtnUp.setDisable(true);
        progressBar.setVisible(true);
    }


    public void btnDelete(ActionEvent actionEvent) {
        if(localPC.filesTable.isFocused()){
            try {
                localPC.deleteDir(Paths.get(localPC.getCurrentPath() + "/" + localPC.getSelectedFilename()));
                localPC.updateList(Paths.get(localPC.getCurrentPath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (storagePC.storageFilesTable.isFocused()){
            network.deleteFile(storagePC.getSelectedStorageFilename(), new BoolCallback() {
                @Override
                public void callback(boolean bool) {
                    if(bool){
                        storagePC.updateStorageList(network.askStorageInfo(storagePC.storagePathField.getText()));
                    } else{
                        new Alert(Alert.AlertType.ERROR, "Error while deleting", ButtonType.APPLY).showAndWait();
                    }
                }
            });
        }

    }


    public void btnCreateDir(ActionEvent actionEvent) {
        if(storagePC.storageFilesTable.isFocused() && login != null){
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Создание новой папки");
            dialog.setHeaderText("Создаем папку на облачном хранилище");
            dialog.setContentText("Введите название новой папки:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(dirName -> network.createDirectory(dirName, new BoolCallback() {
                @Override
                public void callback(boolean bool) {
                    if(bool){
                        storagePC.updateStorageList(network.askStorageInfo(storagePC.storagePathField.getText()));
                    } else{
                        new Alert(Alert.AlertType.ERROR, "Ошибка создания папки", ButtonType.APPLY).showAndWait();
                    }
                }
            }));
        } else if(localPC.filesTable.isFocused()){

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Создание новой папки");
            dialog.setHeaderText("Создаем папку на локальном компьютере");
            dialog.setContentText("Введите название новой папки:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(dirName -> {
                try {
                    Files.createDirectory(Paths.get(localPC.pathField.getText() + "/" + dirName));
                    localPC.updateList(Paths.get(localPC.pathField.getText()));
                } catch (IOException e) {
                    new Alert(Alert.AlertType.ERROR, "Ошибка создания папки", ButtonType.APPLY).showAndWait();
                }
            });
        }
    }
}
