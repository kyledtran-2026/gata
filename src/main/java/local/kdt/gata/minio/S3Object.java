package local.kdt.gata.minio;

import java.util.List;

public class S3Object {
    private S3Type type;
    private String name;
    private List<S3Object> list;
    private Long size;

    public S3Type getType() {
        return type;
    }
    public void setType(S3Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Long getSize() {
        return size;
    }
    public void setSize(Long size) {
        this.size = size;
    }

    public List<S3Object> getList() {
        return list;
    }
    public void setList(List<S3Object> list) {
        this.list = list;
    }

    @Override
    public String toString() {
        return "S3Object{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", list=" + list +
                '}';
    }
}
