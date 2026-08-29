// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.query;

import org.apiguardian.api.API;

/**
 * 分页请求:从零开始的 {@link #pageNumber()} 和 {@link #pageSize()}。实例
 * 不可变且线程安全。
 *
 * <p>从旧的 {@code Limit (offset, count)} 扁平调用迁移到更丰富的抽象;底层
 * 构建器仍然接收 {@code limit}/{@code offset} 整数。
 *
 * @author JadenDu
 */
@API(status = API.Status.STABLE)
public final class Pageable {

    private final int pageNumber;
    private final int pageSize;
    private final Sort sort;

    private Pageable(int pageNumber, int pageSize, Sort sort) {
        if (pageNumber < 0)
            throw new IllegalArgumentException("page number must be >= 0, got " + pageNumber);
        if (pageSize <= 0)
            throw new IllegalArgumentException("page size must be > 0, got " + pageSize);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    /** 第 0 页从第一行开始。 */
    @API(status = API.Status.STABLE)
    public static Pageable of(int pageNumber, int pageSize) {
        return new Pageable(pageNumber, pageSize, Sort.unsorted());
    }

    @API(status = API.Status.STABLE)
    public static Pageable of(int pageNumber, int pageSize, Sort sort) {
        return new Pageable(pageNumber, pageSize, sort);
    }

    @API(status = API.Status.STABLE)
    public static Pageable first(int pageSize) {
        return new Pageable(0, pageSize, Sort.unsorted());
    }

    /** 从零开始的页码。 */
    public int pageNumber() {
        return pageNumber;
    }

    /** 每页最大行数。 */
    public int pageSize() {
        return pageSize;
    }

    public Sort sort() {
        return sort;
    }

    /** 行偏移量,即 {@code pageNumber * pageSize}。 */
    public int offset() {
        return pageNumber * pageSize;
    }

    /** 为下一页构造一个 {@link Pageable}。 */
    public Pageable next() {
        return new Pageable(pageNumber + 1, pageSize, sort);
    }

    public Pageable previous() {
        return pageNumber == 0 ? this : new Pageable(pageNumber - 1, pageSize, sort);
    }
}
