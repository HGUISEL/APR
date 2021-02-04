/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.metamodel.mongodb;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bson.types.ObjectId;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.MetaModelException;
import org.apache.metamodel.QueryPostprocessDataContext;
import org.apache.metamodel.UpdateScript;
import org.apache.metamodel.UpdateableDataContext;
import org.apache.metamodel.data.DataSet;
import org.apache.metamodel.query.FilterItem;
import org.apache.metamodel.query.FromItem;
import org.apache.metamodel.query.Query;
import org.apache.metamodel.query.SelectItem;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.ColumnType;
import org.apache.metamodel.schema.MutableSchema;
import org.apache.metamodel.schema.MutableTable;
import org.apache.metamodel.schema.Schema;
import org.apache.metamodel.schema.Table;
import org.apache.metamodel.util.SimpleTableDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

/**
 * DataContext implementation for MongoDB.
 * 
 * Since MongoDB has no schema, a virtual schema will be used in this
 * DataContext. This implementation supports either automatic discovery of a
 * schema or manual specification of a schema, through the
 * {@link MongoDbTableDef} class.
 * 
 * @author Kasper Sørensen
 */
public class MongoDbDataContext extends QueryPostprocessDataContext implements UpdateableDataContext {

    private static final Logger logger = LoggerFactory.getLogger(MongoDbDataSet.class);

    private final DB _mongoDb;
    private final SimpleTableDef[] _tableDefs;
    private WriteConcernAdvisor _writeConcernAdvisor;
    private Schema _schema;

    /**
     * Constructor available for backwards compatibility
     * 
     * @param mongoDb
     * @param tableDefs
     * 
     * @deprecated use {@link #MongoDbDataContext(DB, SimpleTableDef...)}
     *             instead
     */
    @Deprecated
    public MongoDbDataContext(DB mongoDb, MongoDbTableDef... tableDefs) {
        this(mongoDb, (SimpleTableDef[]) tableDefs);
    }

    /**
     * Constructs a {@link MongoDbDataContext}. This constructor accepts a
     * custom array of {@link MongoDbTableDef}s which allows the user to define
     * his own view on the collections in the database.
     * 
     * @param mongoDb
     *            the mongo db connection
     * @param tableDefs
     *            an array of {@link MongoDbTableDef}s, which define the table
     *            and column model of the mongo db collections. (consider using
     *            {@link #detectSchema(DB)} or {@link #detectTable(DB, String)}
     *            ).
     */
    public MongoDbDataContext(DB mongoDb, SimpleTableDef... tableDefs) {
        _mongoDb = mongoDb;
        _tableDefs = tableDefs;
        _schema = null;
    }

    /**
     * Constructs a {@link MongoDbDataContext} and automatically detects the
     * schema structure/view on all collections (see {@link #detectSchema(DB)}).
     * 
     * @param mongoDb
     *            the mongo db connection
     */
    public MongoDbDataContext(DB mongoDb) {
        this(mongoDb, detectSchema(mongoDb));
    }

    /**
     * Performs an analysis of the available collections in a Mongo {@link DB}
     * instance and tries to detect the table's structure based on the first
     * 1000 documents in each collection.
     * 
     * @see #detectTable(DB, String)
     * 
     * @param db
     *            the mongo db to inspect
     * @return a mutable schema instance, useful for further fine tuning by the
     *         user.
     */
    public static SimpleTableDef[] detectSchema(DB db) {
        Set<String> collectionNames = db.getCollectionNames();
        SimpleTableDef[] result = new SimpleTableDef[collectionNames.size()];
        int i = 0;
        for (String collectionName : collectionNames) {
            SimpleTableDef table = detectTable(db, collectionName);
            result[i] = table;
            i++;
        }
        return result;
    }

    /**
     * Performs an analysis of an available collection in a Mongo {@link DB}
     * instance and tries to detect the table structure based on the first 1000
     * documents in the collection.
     * 
     * @param db
     *            the mongo DB
     * @param collectionName
     *            the name of the collection
     * @return a table definition for mongo db.
     */
    public static SimpleTableDef detectTable(DB db, String collectionName) {
        final DBCollection collection = db.getCollection(collectionName);
        final DBCursor cursor = collection.find().limit(1000);

        final SortedMap<String, Set<Class<?>>> columnsAndTypes = new TreeMap<String, Set<Class<?>>>();
        while (cursor.hasNext()) {
            DBObject object = cursor.next();
            Set<String> keysInObject = object.keySet();
            for (String key : keysInObject) {
                Set<Class<?>> types = columnsAndTypes.get(key);
                if (types == null) {
                    types = new HashSet<Class<?>>();
                    columnsAndTypes.put(key, types);
                }
                Object value = object.get(key);
                if (value != null) {
                    types.add(value.getClass());
                }
            }
        }
        cursor.close();

        final String[] columnNames = new String[columnsAndTypes.size()];
        final ColumnType[] columnTypes = new ColumnType[columnsAndTypes.size()];

        int i = 0;
        for (Entry<String, Set<Class<?>>> columnAndTypes : columnsAndTypes.entrySet()) {
            final String columnName = columnAndTypes.getKey();
            final Set<Class<?>> columnTypeSet = columnAndTypes.getValue();
            final Class<?> columnType;
            if (columnTypeSet.size() == 1) {
                columnType = columnTypeSet.iterator().next();
            } else {
                columnType = Object.class;
            }
            columnNames[i] = columnName;
            if (columnType == ObjectId.class) {
                columnTypes[i] = ColumnType.ROWID;
            } else {
                columnTypes[i] = ColumnType.convertColumnType(columnType);
            }
            i++;
        }

        return new SimpleTableDef(collectionName, columnNames, columnTypes);
    }

    @Override
    protected Schema getMainSchema() throws MetaModelException {
        if (_schema == null) {
            MutableSchema schema = new MutableSchema(getMainSchemaName());
            for (SimpleTableDef tableDef : _tableDefs) {

                MutableTable table = tableDef.toTable().setSchema(schema);

                schema.addTable(table);
            }

            _schema = schema;
        }
        return _schema;
    }

    @Override
    protected String getMainSchemaName() throws MetaModelException {
        return _mongoDb.getName();
    }

    @Override
    protected Number executeCountQuery(Table table, List<FilterItem> whereItems, boolean functionApproximationAllowed) {
        final DBCollection collection = _mongoDb.getCollection(table.getName());

        final DBObject query = createMongoDbQuery(table, whereItems);

        logger.info("Executing MongoDB 'count' query: {}", query);
        final long count = collection.count(query);

        return count;
    }

    @Override
    public DataSet executeQuery(Query query) {
        // Check for queries containing only simple selects and where clauses,
        // or if it is a COUNT(*) query.

        // if from clause only contains a main schema table
        List<FromItem> fromItems = query.getFromClause().getItems();
        if (fromItems.size() == 1 && fromItems.get(0).getTable() != null && fromItems.get(0).getTable().getSchema() == _schema) {
            final Table table = fromItems.get(0).getTable();

            // if GROUP BY, HAVING and ORDER BY clauses are not specified
            if (query.getGroupByClause().isEmpty() && query.getHavingClause().isEmpty() && query.getOrderByClause().isEmpty()) {

                final List<FilterItem> whereItems = query.getWhereClause().getItems();

                // if all of the select items are "pure" column selection
                boolean allSelectItemsAreColumns = true;
                List<SelectItem> selectItems = query.getSelectClause().getItems();

                // if it is a
                // "SELECT [columns] FROM [table] WHERE [conditions]"
                // query.
                for (SelectItem selectItem : selectItems) {
                    if (selectItem.getFunction() != null || selectItem.getColumn() == null) {
                        allSelectItemsAreColumns = false;
                        break;
                    }
                }

                if (allSelectItemsAreColumns) {
                    logger.debug("Query can be expressed in full MongoDB, no post processing needed.");

                    // prepare for a non-post-processed query
                    Column[] columns = new Column[selectItems.size()];
                    for (int i = 0; i < columns.length; i++) {
                        columns[i] = selectItems.get(i).getColumn();
                    }

                    int firstRow = (query.getFirstRow() == null ? 1 : query.getFirstRow());
                    int maxRows = (query.getMaxRows() == null ? -1 : query.getMaxRows());

                    final DataSet dataSet = materializeMainSchemaTableInternal(table, columns, whereItems, firstRow, maxRows,
                            false);
                    return dataSet;
                }
            }
        }

        logger.debug("Query will be simplified for MongoDB and post processed.");
        return super.executeQuery(query);
    }

    private DataSet materializeMainSchemaTableInternal(Table table, Column[] columns, List<FilterItem> whereItems, int firstRow,
            int maxRows, boolean queryPostProcessed) {
        final DBCollection collection = _mongoDb.getCollection(table.getName());

        final DBObject query = createMongoDbQuery(table, whereItems);

        logger.info("Executing MongoDB 'find' query: {}", query);
        DBCursor cursor = collection.find(query);

        if (maxRows > 0) {
            cursor = cursor.limit(maxRows);
        }
        if (firstRow > 1) {
            final int skip = firstRow - 1;
            cursor = cursor.skip(skip);
        }

        return new MongoDbDataSet(cursor, columns, queryPostProcessed);
    }

    protected BasicDBObject createMongoDbQuery(Table table, List<FilterItem> whereItems) {
        assert _schema == table.getSchema();

        final BasicDBObject query = new BasicDBObject();
        if (whereItems != null && !whereItems.isEmpty()) {
            for (FilterItem item : whereItems) {
                convertToCursorObject(query, item);
            }
        }

        return query;
    }

    private void convertToCursorObject(BasicDBObject query, FilterItem item) {
        if (item.isCompoundFilter()) {

            BasicDBList orList = new BasicDBList();

            final FilterItem[] childItems = item.getChildItems();
            for (FilterItem childItem : childItems) {
                BasicDBObject childObject = new BasicDBObject();
                convertToCursorObject(childObject, childItem);
                orList.add(childObject);
            }

            query.put("$or", orList);

        } else {

            final Column column = item.getSelectItem().getColumn();
            final String columnName = column.getName();
            final Object operand = item.getOperand();
            final String operatorName = getOperatorName(item);

            final BasicDBObject existingFilterObject = (BasicDBObject) query.get(columnName);
            if (existingFilterObject == null) {
                if (operatorName == null) {
                    query.put(columnName, operand);
                } else {
                    query.put(columnName, new BasicDBObject(operatorName, operand));
                }
            } else {
                if (operatorName == null) {
                    throw new IllegalStateException("Cannot retrieve records for a column with two EQUALS_TO operators");
                } else {
                    existingFilterObject.append(operatorName, operand);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String getOperatorName(FilterItem item) {
        final String operatorName;
        switch (item.getOperator()) {
        case EQUALS_TO:
            operatorName = null;
            break;
        case LESS_THAN:
        case LOWER_THAN:
            operatorName = "$lt";
            break;
        case GREATER_THAN:
        case HIGHER_THAN:
            operatorName = "$gt";
            break;
        case DIFFERENT_FROM:
            operatorName = "$ne";
            break;
        case IN:
            operatorName = "$in";
            break;
        default:
            throw new IllegalStateException("Unsupported operator type: " + item.getOperator());
        }
        return operatorName;
    }

    @Override
    protected DataSet materializeMainSchemaTable(Table table, Column[] columns, int maxRows) {
        return materializeMainSchemaTableInternal(table, columns, null, 1, maxRows, true);
    }

    @Override
    protected DataSet materializeMainSchemaTable(Table table, Column[] columns, int firstRow, int maxRows) {
        return materializeMainSchemaTableInternal(table, columns, null, firstRow, maxRows, true);
    }

    /**
     * Executes an update with a specific {@link WriteConcernAdvisor}.
     * 
     * @param update
     * @param writeConcernAdvisor
     */
    public void executeUpdate(UpdateScript update, WriteConcernAdvisor writeConcernAdvisor) {
        MongoDbUpdateCallback callback = new MongoDbUpdateCallback(this, writeConcernAdvisor);
        try {
            update.run(callback);
        } finally {
            callback.close();
        }
    }

    /**
     * Executes an update with a specific {@link WriteConcern}.
     * 
     * @param update
     * @param writeConcern
     */
    public void executeUpdate(UpdateScript update, WriteConcern writeConcern) {
        executeUpdate(update, new SimpleWriteConcernAdvisor(writeConcern));
    }

    @Override
    public void executeUpdate(UpdateScript update) {
        executeUpdate(update, getWriteConcernAdvisor());
    }

    /**
     * Gets the {@link WriteConcernAdvisor} to use on
     * {@link #executeUpdate(UpdateScript)} calls.
     * 
     * @return
     */
    public WriteConcernAdvisor getWriteConcernAdvisor() {
        if (_writeConcernAdvisor == null) {
            return new DefaultWriteConcernAdvisor();
        }
        return _writeConcernAdvisor;
    }

    /**
     * Sets a global {@link WriteConcern} advisor to use on
     * {@link #executeUpdate(UpdateScript)}.
     * 
     * @param writeConcernAdvisor
     */
    public void setWriteConcernAdvisor(WriteConcernAdvisor writeConcernAdvisor) {
        _writeConcernAdvisor = writeConcernAdvisor;
    }

    /**
     * Gets the {@link DB} instance that this {@link DataContext} is backed by.
     * 
     * @return
     */
    public DB getMongoDb() {
        return _mongoDb;
    }

    protected void addTable(MutableTable table) {
        if (_schema instanceof MutableSchema) {
            MutableSchema mutableSchema = (MutableSchema) _schema;
            mutableSchema.addTable(table);
        } else {
            throw new UnsupportedOperationException("Schema is not mutable");
        }
    }
}
