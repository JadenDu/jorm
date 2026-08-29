// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.query;

import java.util.Collections;
import java.util.List;

import org.apiguardian.api.API;

/**
 * 分页查询的结果:当前页的行数据,以及元素总数和总页数。
 *
 * <p>同时暴露裸方法访问器({@code content()}、{@code totalElements()} 等)和 JavaBean
 * getter({@code getContent()}、{@code getTotalElements()} 等)。JavaBean getter
 * 确保像 Jackson 这样的 JSON 序列化器 —— 它们只识别 {@code getXxx}/{@code isXxx} 方法 ——
 * 能够序列化每个字段,而不仅仅是 {@code isXxx} 标志。
 *
 * @param <T> 实体类型
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Page<T> {

    private final List<T> content;
    private final long totalElements;
    private final int pageNumber;
    private final int pageSize;

    public Page(List<T> content, long totalElements, int pageNumber, int pageSize) {
        this.content =
                content == null ? Collections.emptyList() : Collections.unmodifiableList(content);
        this.totalElements = Math.max(0, totalElements);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public List<T> content() {
        return content;
    }

    public long totalElements() {
        return totalElements;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public int pageSize() {
        return pageSize;
    }

    public int totalPages() {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) totalElements / (double) pageSize);
    }

    public boolean hasNext() {
        return pageNumber + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return pageNumber > 0;
    }

    public boolean isFirst() {
        return pageNumber == 0;
    }

    public boolean isLast() {
        return !hasNext();
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public int numberOfElements() {
        return content.size();
    }

    // ---- 供 JSON 序列化使用的 JavaBean getter(Jackson / Gson 等)----

    public List<T> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalPages() {
        return totalPages();
    }

    public boolean isHasNext() {
        return hasNext();
    }

    public boolean isHasPrevious() {
        return hasPrevious();
    }

    public int getNumberOfElements() {
        return numberOfElements();
    }
}
