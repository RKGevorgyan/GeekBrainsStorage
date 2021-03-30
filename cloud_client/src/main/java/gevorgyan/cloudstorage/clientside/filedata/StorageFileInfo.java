package gevorgyan.cloudstorage.clientside.filedata;

public class StorageFileInfo {
    private String filename;
    private FileType type;
    private long size;


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }



    public StorageFileInfo(String name, long size) {
            this.filename = name;
            this.size = size;
            if (this.size >= 0) {
                this.type = FileType.FILE;
            } else {
                this.type = FileType.DIRECTORY;
            }
    }

    public void print(){
        System.out.println(this.getType());
        System.out.println(this.getFilename());
        System.out.println(this.getSize());
    }
}
