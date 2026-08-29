package top.ortus.lightmark.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class PageResponse<T> {

    private long total;
    private int page;
    private int size;
    private List<T> list;

    public PageResponse() {
    }

    public PageResponse(long total, int page, int size, List<T> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }

    public PageResponse(long total, List<T> list) {
        this(total, 1, list == null ? 0 : list.size(), list);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    @JsonIgnore
    public List<T> getItems() {
        return list;
    }

    public void setItems(List<T> items) {
        this.list = items;
    }
}
