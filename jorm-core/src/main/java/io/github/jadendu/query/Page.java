// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.query;

import java.util.Collections;
import java.util.List;

import org.apiguardian.api.API;

/**
 * Result of a paginated query: rows on the current page plus total element and total page counts.
 *
 * @param <T> the entity type
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
}
