package gevorgyan.cloudstorage.clientside;

import gevorgyan.cloudstorage.clientside.filedata.StorageFileInfo;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class StoragePanelController implements Initializable {
    private final Network network = Network.getInstance();
    private final List<StorageFileInfo> listStorage = new ArrayList<>();



    private String login;

    @FXML
    Button storageBtnUp;
    @FXML
    TextField storagePathField;
    @FXML
    TableView<StorageFileInfo> storageFilesTable;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //настройка серверной таблицы
        TableColumn<StorageFileInfo, String> storage_fileTypeColumn = new TableColumn<>();
        storage_fileTypeColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getType().getName()));
        storage_fileTypeColumn.setPrefWidth(24);

        TableColumn<StorageFileInfo, String> storage_filenameColumn = new TableColumn<>("Имя");
        storage_filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFilename()));
        storage_filenameColumn.setPrefWidth(240);

        TableColumn<StorageFileInfo, Long> storage_fileSizeColumn = new TableColumn<>("Размер");
        storage_fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        storage_fileSizeColumn.setCellFactory(column -> {
            return new TableCell<StorageFileInfo, Long>() {
                @Override
                protected void updateItem(Long item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        String text = String.format("%,d bytes", item);
                        if (item == -1L) {
                            text = "[DIR]";
                        }
                        setText(text);
                    }
                }
            };
        });
        storage_fileSizeColumn.setPrefWidth(120);


        storageFilesTable.getColumns().addAll(storage_fileTypeColumn, storage_filenameColumn, storage_fileSizeColumn);
        storageFilesTable.getSortOrder().add(storage_fileTypeColumn);



        storageFilesTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    if(storageFilesTable.getSelectionModel().getSelectedItem() == null) return;
                    if(storagePathField.getText().equals("")) return;
                    if(getSelectedStorageFileSize() != -1L) return;
                    String wantedDir = storagePathField.getText() + "/" + getSelectedStorageFilename();
                    updateStorageList(network.askStorageInfo(wantedDir));
                }
            }
        });
    }

    public void updateStorageList(String info) {
        if(info == null) {
            new Alert(Alert.AlertType.ERROR, "Возникла ошибка обновления списка файлов из облака", ButtonType.APPLY).showAndWait();
            return;
        }
        listStorage.clear();
        String[] infoList = info.split(":");
        storagePathField.setText(infoList[0]);
        for(int i = 1; i < infoList.length; i += 2){
            listStorage.add(new StorageFileInfo(infoList[i], Long.parseLong(infoList[i + 1])));
        }

        storageFilesTable.getItems().clear();
        storageFilesTable.getItems().addAll(listStorage);
        storageFilesTable.sort();
        if(storageFilesTable.getItems().size() == 0) storageFilesTable.setPlaceholder(new Text("Папка пуста"));
    }

    public String getSelectedStorageFilename() {
        if (!storageFilesTable.isFocused()) {
            return null;
        }
        return storageFilesTable.getSelectionModel().getSelectedItem().getFilename();
    }

    public long getSelectedStorageFileSize() {
        return storageFilesTable.getSelectionModel().getSelectedItem().getSize();
    }
    public void storageDirUp(ActionEvent actionEvent) {
        if(login == null) return;
        if(storagePathField.getText().equals(login)) return;
        String wantedDir = storagePathField.getText().substring(0,storagePathField.getText().lastIndexOf("/"));
        updateStorageList(network.askStorageInfo(wantedDir));
    }


    public void setLogin(String login) {
        this.login = login;
    }
}
