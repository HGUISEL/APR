/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.config;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.*;

import org.apache.avro.util.Utf8;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import org.apache.cassandra.db.marshal.TimeUUIDType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.io.SerDeUtils;
import org.apache.cassandra.db.ColumnFamilyType;
import org.apache.cassandra.db.ClockType;
import org.apache.cassandra.db.clock.AbstractReconciler;
import org.apache.cassandra.db.clock.TimestampReconciler;
import org.apache.cassandra.db.HintedHandOffManager;
import org.apache.cassandra.db.SystemTable;
import org.apache.cassandra.db.StatisticsTable;
import org.apache.cassandra.db.Table;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.BytesType;
import org.apache.cassandra.db.migration.Migration;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;


public final class CFMetaData
{
    public final static double DEFAULT_READ_REPAIR_CHANCE = 1.0;
    public final static double DEFAULT_KEY_CACHE_SIZE = 200000;
    public final static double DEFAULT_ROW_CACHE_SIZE = 0.0;
    public final static boolean DEFAULT_PRELOAD_ROW_CACHE = false;
    public final static int DEFAULT_GC_GRACE_SECONDS = 864000;
    private static final int MIN_CF_ID = 1000;

    private static final AtomicInteger idGen = new AtomicInteger(MIN_CF_ID);
    
    private static final BiMap<Pair<String, String>, Integer> cfIdMap = HashBiMap.create();
    
    public static final CFMetaData StatusCf = newSystemTable(SystemTable.STATUS_CF, 0, "persistent metadata for the local node", BytesType.instance, null);
    public static final CFMetaData HintsCf = newSystemTable(HintedHandOffManager.HINTS_CF, 1, "hinted handoff data", BytesType.instance, BytesType.instance);
    public static final CFMetaData MigrationsCf = newSystemTable(Migration.MIGRATIONS_CF, 2, "individual schema mutations", TimeUUIDType.instance, null);
    public static final CFMetaData SchemaCf = newSystemTable(Migration.SCHEMA_CF, 3, "current state of the schema", UTF8Type.instance, null);
    public static final CFMetaData StatisticsCf = newSystemTable(StatisticsTable.STATISTICS_CF, 4, "persistent CF statistics for the local node", UTF8Type.instance, BytesType.instance);

    private static CFMetaData newSystemTable(String cfName, int cfId, String comment, AbstractType comparator, AbstractType subComparator)
    {
        return new CFMetaData(Table.SYSTEM_TABLE,
                              cfName,
                              subComparator == null ? ColumnFamilyType.Standard : ColumnFamilyType.Super,
                              ClockType.Timestamp,
                              comparator,
                              subComparator,
                              TimestampReconciler.instance,
                              comment,
                              0,
                              false,
                              0.01,
                              0,
                              0,
                              BytesType.instance,
                              cfId,
                              Collections.<byte[], ColumnDefinition>emptyMap());
    }

    /**
     * @return An immutable mapping of (ksname,cfname) to id.
     */
    public static final Map<Pair<String, String>, Integer> getCfToIdMap()
    {
        return Collections.unmodifiableMap(cfIdMap);
    }
    
    /**
     * @return An immutable mapping of id to (ksname,cfname).
     */
    public static final Map<Integer, Pair<String, String>> getIdToCfMap()
    {
        return Collections.unmodifiableMap(cfIdMap.inverse());
    }
    
    /**
     * @return The (ksname,cfname) pair for the given id, or null if it has been dropped.
     */
    public static final Pair<String,String> getCF(Integer cfId)
    {
        return cfIdMap.inverse().get(cfId);
    }
    
    /**
     * @return The id for the given (ksname,cfname) pair, or null if it has been dropped.
     */
    public static final Integer getId(String table, String cfName)
    {
        return cfIdMap.get(new Pair<String, String>(table, cfName));
    }
    
    // this gets called after initialization to make sure that id generation happens properly.
    public static final void fixMaxId()
    {
        // never set it to less than 1000. this ensures that we have enough system CFids for future use.
        idGen.set(cfIdMap.size() == 0 ? MIN_CF_ID : Math.max(Collections.max(cfIdMap.values()) + 1, MIN_CF_ID));
    }
    
    public final String tableName;            // name of table which has this column family
    public final String cfName;               // name of the column family
    public final ColumnFamilyType cfType;     // type: super, standard, etc.
    public final ClockType clockType;         // clock type: timestamp, etc.
    public final AbstractType comparator;       // name sorted, time stamp sorted etc.
    public final AbstractType subcolumnComparator; // like comparator, for supercolumns
    public final AbstractReconciler reconciler; // determine correct column from conflicting versions
    public final String comment; // for humans only
    public final double rowCacheSize; // default 0
    public final double keyCacheSize; // default 0.01
    public final double readRepairChance; //chance 0 to 1, of doing a read repair; defaults 1.0 (always)
    public final Integer cfId;
    public boolean preloadRowCache;
    public final int gcGraceSeconds; // default 864000 (ten days)
    public final AbstractType defaultValidator; // values are longs, strings, bytes (no-op)...

    public final Map<byte[], ColumnDefinition> column_metadata;

    private CFMetaData(String tableName,
                       String cfName,
                       ColumnFamilyType cfType,
                       ClockType clockType,
                       AbstractType comparator,
                       AbstractType subcolumnComparator,
                       AbstractReconciler reconciler,
                       String comment,
                       double rowCacheSize,
                       boolean preloadRowCache,
                       double keyCacheSize,
                       double readRepairChance,
                       int gcGraceSeconds,
                       AbstractType defaultValidator,
                       Integer cfId,
                       Map<byte[], ColumnDefinition> column_metadata)
    {
        assert column_metadata != null;
        this.tableName = tableName;
        this.cfName = cfName;
        this.cfType = cfType;
        this.clockType = clockType;
        this.comparator = comparator;
        // the default subcolumncomparator is null per thrift spec, but only should be null if cfType == Standard. If
        // cfType == Super, subcolumnComparator should default to BytesType if not set.
        this.subcolumnComparator = subcolumnComparator == null && cfType == ColumnFamilyType.Super ? BytesType.instance : subcolumnComparator;
        this.reconciler = reconciler;
        this.comment = comment == null ? "" : comment;
        this.rowCacheSize = rowCacheSize;
        this.preloadRowCache = preloadRowCache;
        this.keyCacheSize = keyCacheSize;
        this.readRepairChance = readRepairChance;
        this.gcGraceSeconds = gcGraceSeconds;
        this.defaultValidator = defaultValidator;
        this.cfId = cfId;
        this.column_metadata = Collections.unmodifiableMap(column_metadata);
    }
    
    /** adds this cfm to the map. */
    public static void map(CFMetaData cfm) throws ConfigurationException
    {
        Pair<String, String> key = new Pair<String, String>(cfm.tableName, cfm.cfName);
        if (cfIdMap.containsKey(key))
            throw new ConfigurationException("Attempt to assign id to existing column family.");
        else
        {
            cfIdMap.put(key, cfm.cfId);
        }
    }

    public CFMetaData(String tableName, String cfName, ColumnFamilyType cfType, ClockType clockType, AbstractType comparator, AbstractType subcolumnComparator, AbstractReconciler reconciler, String comment, double rowCacheSize, boolean preloadRowCache, double keyCacheSize, double readRepairChance, int gcGraceSeconds, AbstractType defaultvalidator, Map<byte[], ColumnDefinition> column_metadata)
    {
        this(tableName, cfName, cfType, clockType, comparator, subcolumnComparator, reconciler, comment, rowCacheSize, preloadRowCache, keyCacheSize, readRepairChance, gcGraceSeconds, defaultvalidator, nextId(), column_metadata);
    }

    /** clones an existing CFMetaData using the same id. */
    public static CFMetaData rename(CFMetaData cfm, String newName)
    {
        CFMetaData newCfm = new CFMetaData(cfm.tableName, newName, cfm.cfType, cfm.clockType, cfm.comparator, cfm.subcolumnComparator, cfm.reconciler, cfm.comment, cfm.rowCacheSize, cfm.preloadRowCache, cfm.keyCacheSize, cfm.readRepairChance, cfm.gcGraceSeconds, cfm.defaultValidator, cfm.cfId, cfm.column_metadata);
        return newCfm;
    }
    
    /** clones existing CFMetaData. keeps the id but changes the table name.*/
    public static CFMetaData renameTable(CFMetaData cfm, String tableName)
    {
        return new CFMetaData(tableName, cfm.cfName, cfm.cfType, cfm.clockType, cfm.comparator, cfm.subcolumnComparator, cfm.reconciler, cfm.comment, cfm.rowCacheSize, cfm.preloadRowCache, cfm.keyCacheSize, cfm.readRepairChance, cfm.gcGraceSeconds, cfm.defaultValidator, cfm.cfId, cfm.column_metadata);
    }
    
    /** used for evicting cf data out of static tracking collections. */
    public static void purge(CFMetaData cfm)
    {
        cfIdMap.remove(new Pair<String, String>(cfm.tableName, cfm.cfName));
    }

    // a quick and dirty pretty printer for describing the column family...
    public String pretty()
    {
        return tableName + "." + cfName + "\n"
               + "Column Family Type: " + cfType + "\n"
               + "Column Family Clock Type: " + clockType + "\n"
               + "Columns Sorted By: " + comparator + "\n";
    }

    public org.apache.cassandra.config.avro.CfDef deflate()
    {
        org.apache.cassandra.config.avro.CfDef cf = new org.apache.cassandra.config.avro.CfDef();
        cf.id = cfId;
        cf.keyspace = new Utf8(tableName);
        cf.name = new Utf8(cfName);
        cf.column_type = new Utf8(cfType.name());
        cf.clock_type = new Utf8(clockType.name());
        cf.comparator_type = new Utf8(comparator.getClass().getName());
        if (subcolumnComparator != null)
            cf.subcomparator_type = new Utf8(subcolumnComparator.getClass().getName());
        cf.reconciler = new Utf8(reconciler.getClass().getName());
        cf.comment = new Utf8(comment);
        cf.row_cache_size = rowCacheSize;
        cf.key_cache_size = keyCacheSize;
        cf.preload_row_cache = preloadRowCache;
        cf.read_repair_chance = readRepairChance;
        cf.gc_grace_seconds = gcGraceSeconds;
        cf.default_validation_class = new Utf8(defaultValidator.getClass().getName());
        cf.column_metadata = SerDeUtils.createArray(column_metadata.size(),
                                                    org.apache.cassandra.config.avro.ColumnDef.SCHEMA$);
        for (ColumnDefinition cd : column_metadata.values())
            cf.column_metadata.add(cd.deflate());
        return cf;
    }

    public static CFMetaData inflate(org.apache.cassandra.config.avro.CfDef cf)
    {
        AbstractType comparator;
        AbstractType subcolumnComparator = null;
        AbstractReconciler reconciler;
        AbstractType validator;
        try
        {
            comparator = DatabaseDescriptor.getComparator(cf.comparator_type.toString());
            if (cf.subcomparator_type != null)
                subcolumnComparator = DatabaseDescriptor.getComparator(cf.subcomparator_type.toString());
            reconciler = DatabaseDescriptor.getReconciler(cf.reconciler.toString());
            validator = DatabaseDescriptor.getComparator(cf.default_validation_class.toString());
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Could not inflate CFMetaData for " + cf, ex);
        }
        Map<byte[], ColumnDefinition> column_metadata = new TreeMap<byte[], ColumnDefinition>(FBUtilities.byteArrayComparator);
        Iterator<org.apache.cassandra.config.avro.ColumnDef> cditer = cf.column_metadata.iterator();
        while (cditer.hasNext())
        {
            ColumnDefinition cd = ColumnDefinition.inflate(cditer.next());
            column_metadata.put(cd.name, cd);
        }
        return new CFMetaData(cf.keyspace.toString(), cf.name.toString(), ColumnFamilyType.create(cf.column_type.toString()), ClockType.create(cf.clock_type.toString()), comparator, subcolumnComparator, reconciler, cf.comment.toString(), cf.row_cache_size, cf.preload_row_cache, cf.key_cache_size, cf.read_repair_chance, cf.gc_grace_seconds, validator, cf.id, column_metadata);
    }

    public boolean equals(Object obj) 
    {
        if (obj == this)
        {
            return true;
        }
        else if (obj == null || obj.getClass() != getClass())
        {
            return false;
        }

        CFMetaData rhs = (CFMetaData) obj;
        return new EqualsBuilder()
            .append(tableName, rhs.tableName)
            .append(cfName, rhs.cfName)
            .append(cfType, rhs.cfType)
            .append(clockType, rhs.clockType)
            .append(comparator, rhs.comparator)
            .append(subcolumnComparator, rhs.subcolumnComparator)
            .append(reconciler, rhs.reconciler)
            .append(comment, rhs.comment)
            .append(rowCacheSize, rhs.rowCacheSize)
            .append(keyCacheSize, rhs.keyCacheSize)
            .append(readRepairChance, rhs.readRepairChance)
            .append(gcGraceSeconds, rhs.gcGraceSeconds)
            .append(cfId.intValue(), rhs.cfId.intValue())
            .append(column_metadata, rhs.column_metadata)
            .isEquals();
    }

    public int hashCode()
    {
        return new HashCodeBuilder(29, 1597)
            .append(tableName)
            .append(cfName)
            .append(cfType)
            .append(clockType)
            .append(comparator)
            .append(subcolumnComparator)
            .append(reconciler)
            .append(comment)
            .append(rowCacheSize)
            .append(keyCacheSize)
            .append(readRepairChance)
            .append(gcGraceSeconds)
            .append(cfId)
            .append(column_metadata)
            .toHashCode();
    }

    private static int nextId()
    {
        return idGen.getAndIncrement();
    }

    public AbstractType getValueValidator(byte[] column)
    {
        AbstractType validator = defaultValidator;
        ColumnDefinition columnDefinition = column_metadata.get(column);
        if (columnDefinition != null)
            validator = columnDefinition.validator;
        return validator;
    }
}
