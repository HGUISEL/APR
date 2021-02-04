/*
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;

import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.index.ValueGetter;
import org.apache.hadoop.hbase.index.covered.update.ColumnReference;
import org.apache.hadoop.hbase.index.util.ImmutableBytesPtr;
import org.apache.phoenix.client.KeyValueBuilder;
import org.apache.phoenix.exception.SQLExceptionCode;
import org.apache.phoenix.exception.SQLExceptionInfo;
import org.apache.phoenix.index.IndexMaintainer;
import org.apache.phoenix.query.QueryConstants;
import org.apache.phoenix.schema.ColumnFamilyNotFoundException;
import org.apache.phoenix.schema.ColumnNotFoundException;
import org.apache.phoenix.schema.PColumn;
import org.apache.phoenix.schema.PColumnFamily;
import org.apache.phoenix.schema.PDataType;
import org.apache.phoenix.schema.PTable;

public class IndexUtil {
    public static final String INDEX_COLUMN_NAME_SEP = ":";
    public static final byte[] INDEX_COLUMN_NAME_SEP_BYTES = Bytes.toBytes(INDEX_COLUMN_NAME_SEP);

    private IndexUtil() {
    }

    // Since we cannot have nullable fixed length in a row key
    // we need to translate to variable length.
    public static PDataType getIndexColumnDataType(PColumn dataColumn) throws SQLException {
        PDataType type = getIndexColumnDataType(dataColumn.isNullable(),dataColumn.getDataType());
        if (type == null) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.CANNOT_INDEX_COLUMN_ON_TYPE).setColumnName(dataColumn.getName().getString())
            .setMessage("Type="+dataColumn.getDataType()).build().buildException();
        }
        return type;
    }
    
    // Since we cannot have nullable fixed length in a row key
    // we need to translate to variable length. The verification that we have a valid index
    // row key was already done, so here we just need to covert from one built-in type to
    // another.
    public static PDataType getIndexColumnDataType(boolean isNullable, PDataType dataType) {
        if (dataType == null || !isNullable || !dataType.isFixedWidth() || dataType == PDataType.BINARY) {
            return dataType;
        }
        // for INT, BIGINT
        if (dataType.isCoercibleTo(PDataType.TIMESTAMP) || dataType.isCoercibleTo(PDataType.DECIMAL)) {
            return PDataType.DECIMAL;
        }
        // for CHAR
        if (dataType.isCoercibleTo(PDataType.VARCHAR)) {
            return PDataType.VARCHAR;
        }
        throw new IllegalArgumentException("Unsupported non nullable index type " + dataType);
    }
    

    public static String getDataColumnName(String name) {
        return name.substring(name.indexOf(INDEX_COLUMN_NAME_SEP) + 1);
    }

    public static String getDataColumnFamilyName(String name) {
        return name.substring(0,name.indexOf(INDEX_COLUMN_NAME_SEP));
    }

    public static String getDataColumnFullName(String name) {
        int index = name.indexOf(INDEX_COLUMN_NAME_SEP) ;
        if (index == 0) {
            return name.substring(index+1);
        }
        return SchemaUtil.getColumnDisplayName(name.substring(0, index), name.substring(index+1));
    }

    public static String getIndexColumnName(String dataColumnFamilyName, String dataColumnName) {
        return (dataColumnFamilyName == null ? "" : dataColumnFamilyName) + INDEX_COLUMN_NAME_SEP + dataColumnName;
    }
    
    public static byte[] getIndexColumnName(byte[] dataColumnFamilyName, byte[] dataColumnName) {
        return ByteUtil.concat(dataColumnFamilyName == null ?  ByteUtil.EMPTY_BYTE_ARRAY : dataColumnFamilyName, INDEX_COLUMN_NAME_SEP_BYTES, dataColumnName);
    }
    
    public static String getIndexColumnName(PColumn dataColumn) {
        String dataColumnFamilyName = SchemaUtil.isPKColumn(dataColumn) ? null : dataColumn.getFamilyName().getString();
        return getIndexColumnName(dataColumnFamilyName, dataColumn.getName().getString());
    }

    public static PColumn getDataColumn(PTable dataTable, String indexColumnName) {
        int pos = indexColumnName.indexOf(INDEX_COLUMN_NAME_SEP);
        if (pos < 0) {
            throw new IllegalArgumentException("Could not find expected '" + INDEX_COLUMN_NAME_SEP +  "' separator in index column name of \"" + indexColumnName + "\"");
        }
        if (pos == 0) {
            try {
                return dataTable.getPKColumn(indexColumnName.substring(1));
            } catch (ColumnNotFoundException e) {
                throw new IllegalArgumentException("Could not find PK column \"" +  indexColumnName.substring(pos+1) + "\" in index column name of \"" + indexColumnName + "\"", e);
            }
        }
        PColumnFamily family;
        try {
            family = dataTable.getColumnFamily(indexColumnName.substring(0, pos));
        } catch (ColumnFamilyNotFoundException e) {
            throw new IllegalArgumentException("Could not find column family \"" +  indexColumnName.substring(0, pos) + "\" in index column name of \"" + indexColumnName + "\"", e);
        }
        try {
            return family.getColumn(indexColumnName.substring(pos+1));
        } catch (ColumnNotFoundException e) {
            throw new IllegalArgumentException("Could not find column \"" +  indexColumnName.substring(pos+1) + "\" in index column name of \"" + indexColumnName + "\"", e);
        }
    }

    private static boolean isEmptyKeyValue(PTable table, ColumnReference ref) {
        byte[] emptyKeyValueCF = SchemaUtil.getEmptyColumnFamily(table.getColumnFamilies());
        return (Bytes.compareTo(emptyKeyValueCF, ref.getFamily()) == 0 &&
                Bytes.compareTo(QueryConstants.EMPTY_COLUMN_BYTES, ref.getQualifier()) == 0);
    }

    public static List<Mutation> generateIndexData(final PTable table, PTable index,
            List<Mutation> dataMutations, ImmutableBytesWritable ptr, KeyValueBuilder builder)
            throws SQLException {
        try {
            IndexMaintainer maintainer = index.getIndexMaintainer(table);
            maintainer.setKvBuilder(builder);
            List<Mutation> indexMutations = Lists.newArrayListWithExpectedSize(dataMutations.size());
           for (final Mutation dataMutation : dataMutations) {
                long ts = MetaDataUtil.getClientTimeStamp(dataMutation);
                ptr.set(dataMutation.getRow());
                if (dataMutation instanceof Put) {
                    // TODO: is this more efficient than looking in our mutation map
                    // using the key plus finding the PColumn?
                    ValueGetter valueGetter = new ValueGetter() {
        
                        @Override
                        public ImmutableBytesPtr getLatestValue(ColumnReference ref) {
                            // Always return null for our empty key value, as this will cause the index
                            // maintainer to always treat this Put as a new row.
                            if (isEmptyKeyValue(table, ref)) {
                                return null;
                            }
                            Map<byte [], List<KeyValue>> familyMap = dataMutation.getFamilyMap();
                            byte[] family = ref.getFamily();
                            List<KeyValue> kvs = familyMap.get(family);
                            if (kvs == null) {
                                return null;
                            }
                            byte[] qualifier = ref.getQualifier();
                            for (KeyValue kv : kvs) {
                                if (Bytes.compareTo(kv.getBuffer(), kv.getFamilyOffset(), kv.getFamilyLength(), family, 0, family.length) == 0 &&
                                    Bytes.compareTo(kv.getBuffer(), kv.getQualifierOffset(), kv.getQualifierLength(), qualifier, 0, qualifier.length) == 0) {
                                    return new ImmutableBytesPtr(kv.getBuffer(), kv.getValueOffset(), kv.getValueLength());
                                }
                            }
                            return null;
                        }
                        
                    };
                    indexMutations.add(maintainer.buildUpdateMutation(valueGetter, ptr, ts));
                } else {
                    if (!maintainer.getIndexedColumns().isEmpty()) {
                        throw new SQLExceptionInfo.Builder(SQLExceptionCode.NO_DELETE_IF_IMMUTABLE_INDEX).setSchemaName(table.getSchemaName().getString())
                        .setTableName(table.getTableName().getString()).build().buildException();
                    }
                    indexMutations.add(maintainer.buildDeleteMutation(ptr, ts));
                }
            }
            return indexMutations;
        } catch (IOException e) {
            throw new SQLException(e);
        }
    }

    public static boolean isDataPKColumn(PColumn column) {
        return column.getName().getString().startsWith(INDEX_COLUMN_NAME_SEP);
    }
}
