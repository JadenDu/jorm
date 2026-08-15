// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.query;

import org.apiguardian.api.API;

/**
 * Pagination request: zero-based {@link #pageNumber()} and {@link #pageSize()}. Instances are
 * immutable and thread-safe.
 *
 * <p>Migrated from the legacy {@code Limit (offset, count)} flat call to a richer abstraction; the
 * underlying builders still receive {@code limit}/{@code offset} ints.
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

    /** Page zero starts at the first row. */
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

    /** Zero-based page index. */
    public int pageNumber() {
        return pageNumber;
    }

    /** Maximum number of rows per page. */
    public int pageSize() {
        return pageSize;
    }

    public Sort sort() {
        return sort;
    }

    /** Row offset, i.e. {@code pageNumber * pageSize}. */
    public int offset() {
        return pageNumber * pageSize;
    }

    /** Synthesize a {@link Pageable} for the next page. */
    public Pageable next() {
        return new Pageable(pageNumber + 1, pageSize, sort);
    }

    public Pageable previous() {
        return pageNumber == 0 ? this : new Pageable(pageNumber - 1, pageSize, sort);
    }
}
