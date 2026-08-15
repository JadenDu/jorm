// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apiguardian.api.API;

import io.github.jadendu.annotation.Enum.GenerationType;
import io.github.jadendu.exception.ErrorCode;
import io.github.jadendu.exception.JormException;

/**
 * Cached metadata for a JORM-mapped entity class.
 *
 * <p>A single instance is built per class by {@link EntityModelRegistry} and reused by every {@code
 * Session} / {@code Builder} thereafter, so reflection happens at most once per class loader. The
 * model resolves {@link io.github.jadendu.entity.naming.NamingStrategy}-driven physical column and
 * table names once and stores the result.
 *
 * <p>Fields declared on superclasses are included. Synthetic fields (compiler-generated bridge
 * fields, J Lambdas...) are skipped.
 *
 * @author JadenDu
 */
@API(status = API.Status.INTERNAL)
public final class EntityModel {

    private final Class<?> entityClass;
    private final String tableName;

    private final ColumnMapping idMapping;
    private final GenerationType idGenerationType;

    private final List<ColumnMapping> insertableColumns;
    private final List<ColumnMapping> updatableColumns;
    private final Map<String, ColumnMapping> columnsByPropertyName;
    private final Map<String, ColumnMapping> columnsByColumnName;
    private final List<String> validColumnNames; // for SQL-injection whitelist

    EntityModel(
            Class<?> entityClass,
            String tableName,
            ColumnMapping idMapping,
            GenerationType idGenerationType,
            List<ColumnMapping> insertableColumns,
            List<ColumnMapping> updatableColumns,
            Map<String, ColumnMapping> columnsByPropertyName,
            Map<String, ColumnMapping> columnsByColumnName,
            List<String> validColumnNames) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.idMapping = idMapping;
        this.idGenerationType = idGenerationType;
        this.insertableColumns = Collections.unmodifiableList(new ArrayList<>(insertableColumns));
        this.updatableColumns = Collections.unmodifiableList(new ArrayList<>(updatableColumns));
        this.columnsByPropertyName =
                Collections.unmodifiableMap(new LinkedHashMap<>(columnsByPropertyName));
        this.columnsByColumnName =
                Collections.unmodifiableMap(new LinkedHashMap<>(columnsByColumnName));
        this.validColumnNames = Collections.unmodifiableList(new ArrayList<>(validColumnNames));
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public String tableName() {
        return tableName;
    }

    public boolean hasId() {
        return idMapping != null;
    }

    public ColumnMapping idMapping() {
        if (idMapping == null) {
            throw new JormException(
                    ErrorCode.INVALID_ENTITY,
                    "class " + entityClass.getName() + " has no @Id field");
        }
        return idMapping;
    }

    public String idColumnName() {
        return idMapping().columnName();
    }

    public Field idField() {
        return idMapping().field();
    }

    public GenerationType idGenerationType() {
        return idGenerationType == null ? GenerationType.AUTO : idGenerationType;
    }

    /** Columns to insert — excludes primary-key auto-strategy fields and transients. */
    public List<ColumnMapping> insertableColumns() {
        return insertableColumns;
    }

    /** Columns considered for update — excludes primary key and transients. */
    public List<ColumnMapping> updatableColumns() {
        return updatableColumns;
    }

    public ColumnMapping findByName(String name) {
        ColumnMapping byProp = columnsByPropertyName.get(name);
        return byProp != null ? byProp : columnsByColumnName.get(name);
    }

    /**
     * Whitelist of physical column names used by the SQL builders to forbid SQL injection through
     * {@code Where("1=1 OR ...")} style inputs. Sorted by physical name (alphabetical order is not
     * a contract — only the elements matter).
     */
    public List<String> validColumnNames() {
        return validColumnNames;
    }

    public boolean isValidColumn(String name) {
        return columnsByColumnName.containsKey(name);
    }
}
