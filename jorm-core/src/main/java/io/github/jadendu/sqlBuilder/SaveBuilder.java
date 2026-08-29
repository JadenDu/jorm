// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.sqlBuilder;

import java.util.stream.Collectors;

import org.apiguardian.api.API;

/**
 * 静态 INSERT 构建器。将列解析委托给缓存的 {@link
 * io.github.jadendu.entity.EntityModel}（它使用配置的 {@link
 * io.github.jadendu.entity.naming.NamingStrategy}）。
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class SaveBuilder {

    private SaveBuilder() {}

    private static String render(Class<?> cls, int batchSize) {
        var model = io.github.jadendu.entity.EntityModelRegistry.get(cls);
        String table = model.tableName();
        var cols = model.insertableColumns();
        var columnNames =
                cols.stream()
                        .map(io.github.jadendu.entity.ColumnMapping::columnName)
                        .collect(Collectors.joining(", "));
        int fields = cols.size();
        String single = String.join(", ", java.util.Collections.nCopies(fields, "?"));

        if (batchSize <= 1) {
            return String.format("INSERT INTO %s (%s) VALUES (%s)", table, columnNames, single);
        }
        String allRows =
                java.util.Collections.nCopies(batchSize, "(" + single + ")").stream()
                        .collect(Collectors.joining(", "));
        return String.format("INSERT INTO %s (%s) VALUES %s", table, columnNames, allRows);
    }

    @API(status = API.Status.STABLE)
    public static String buildInsert(Class<?> cls) {
        return render(cls, 1);
    }

    @API(status = API.Status.STABLE)
    public static String buildBatchInsert(Class<?> cls, int batchSize) {
        return render(cls, Math.max(1, batchSize));
    }
}
