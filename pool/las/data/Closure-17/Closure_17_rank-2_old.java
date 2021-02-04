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
package org.apache.hama.graph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.util.ReflectionUtils;

/**
 * A message that is either MapWritable (for meta communication purposes) or a
 * real message (vertex ID and value). It can be extended by adding flags, for
 * example for a graph repair call.
 */
public final class GraphJobMessage implements
    WritableComparable<GraphJobMessage> {

  public static final int MAP_FLAG = 0x01;
  public static final int VERTEX_FLAG = 0x02;
  public static final int VERTICES_SIZE_FLAG = 0x04;

  // default flag to -1 "unknown"
  private int flag = -1;
  private MapWritable map;
  @SuppressWarnings("rawtypes")
  private WritableComparable vertexId;
  private IntWritable integerMessage;
  private static GraphJobMessageComparator comparator;

  private int numOfValues = 0;

  private final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

  static {
    if (comparator == null) {
      comparator = new GraphJobMessageComparator();
    }

    WritableComparator.define(GraphJobMessage.class, comparator);
  }

  public GraphJobMessage() {
  }

  public GraphJobMessage(MapWritable map) {
    this.flag = MAP_FLAG;
    this.map = map;
  }

  public GraphJobMessage(WritableComparable<?> vertexId, Writable vertexValue) {
    this.flag = VERTEX_FLAG;
    this.vertexId = vertexId;

    add(vertexValue);
  }

  public GraphJobMessage(WritableComparable<?> vertexId, List<Writable> values) {
    this.flag = VERTEX_FLAG;
    this.vertexId = vertexId;

    addAll(values);
  }

  public GraphJobMessage(WritableComparable<?> vertexID, byte[] valuesBytes,
      int numOfValues) {
    this.flag = VERTEX_FLAG;
    this.vertexId = vertexID;
    try {
      this.byteBuffer.write(valuesBytes);
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.numOfValues = numOfValues;
  }

  public MapWritable getMap() {
    return map;
  }

  public WritableComparable<?> getVertexId() {
    return vertexId;
  }

  public byte[] getValuesBytes() {
    return byteBuffer.toByteArray();
  }

  public void addValuesBytes(byte[] values, int numOfValues) {
    try {
      byteBuffer.write(values);
      this.numOfValues += numOfValues;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void add(Writable value) {
    try {
      ByteArrayOutputStream a = new ByteArrayOutputStream();
      DataOutputStream b = new DataOutputStream(a);
      value.write(b);
      byteBuffer.write(a.toByteArray());
      numOfValues++;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void addAll(List<Writable> values) {
    for (Writable v : values)
      add(v);
  }

  public int getNumOfValues() {
    return this.numOfValues;
  }

  public GraphJobMessage(IntWritable size) {
    this.flag = VERTICES_SIZE_FLAG;
    this.integerMessage = size;
  }

  @Override
  public void write(DataOutput out) throws IOException {
    out.writeByte(this.flag);
    if (isVertexMessage()) {
      // we don't need to write the classes because the other side has the same
      // classes for the two entities.
      vertexId.write(out);

      out.writeInt(numOfValues);
      out.writeInt(byteBuffer.size());
      out.write(byteBuffer.toByteArray());
    } else if (isMapMessage()) {
      map.write(out);
    } else if (isVerticesSizeMessage()) {
      integerMessage.write(out);
    } else {
      vertexId.write(out);
    }
  }

  public void fastReadFields(DataInput in) throws IOException {
    flag = in.readByte();
    if (isVertexMessage()) {
      vertexId = GraphJobRunner.createVertexIDObject();
      vertexId.readFields(in);
      /*
       * vertexValue = GraphJobRunner.createVertexValue();
       * vertexValue.readFields(in);
       */
    } else if (isMapMessage()) {
      map = new MapWritable();
      map.readFields(in);
    } else if (isVerticesSizeMessage()) {
      integerMessage = new IntWritable();
      integerMessage.readFields(in);
    } else {
      vertexId = ReflectionUtils.newInstance(GraphJobRunner.VERTEX_ID_CLASS,
          null);
      vertexId.readFields(in);
    }
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    flag = in.readByte();
    if (isVertexMessage()) {
      vertexId = GraphJobRunner.createVertexIDObject();
      vertexId.readFields(in);

      this.numOfValues = in.readInt();
      int bytesLength = in.readInt();
      byte[] temp = new byte[bytesLength];
      in.readFully(temp);
      byteBuffer.write(temp);
    } else if (isMapMessage()) {
      map = new MapWritable();
      map.readFields(in);
    } else if (isVerticesSizeMessage()) {
      integerMessage = new IntWritable();
      integerMessage.readFields(in);
    } else {
      vertexId = ReflectionUtils.newInstance(GraphJobRunner.VERTEX_ID_CLASS,
          null);
      vertexId.readFields(in);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(GraphJobMessage that) {
    if (this.flag != that.flag) {
      return (this.flag - that.flag);
    } else {
      if (this.isVertexMessage()) {
        return this.vertexId.compareTo(that.vertexId);
      } else if (this.isMapMessage()) {
        return Integer.MIN_VALUE;
      }
    }
    return 0;
  }

  /**
   * @return the number of values
   */
  public int size() {
    return this.numOfValues;
  }

  public IntWritable getVerticesSize() {
    return integerMessage;
  }

  public boolean isMapMessage() {
    return flag == MAP_FLAG;
  }

  public boolean isVertexMessage() {
    return flag == VERTEX_FLAG;
  }

  public boolean isVerticesSizeMessage() {
    return flag == VERTICES_SIZE_FLAG;
  }

  @Override
  public String toString() {
    if (isVertexMessage()) {
      return "ID: " + vertexId + " Val: " + numOfValues;
    } else if (isMapMessage()) {
      return "Map: " + map;
    } else if (isVerticesSizeMessage()) {
      return "#Vertices: " + integerMessage;
    } else {
      return "GraphJobMessage [flag=" + flag + ", map=" + map + ", vertexId="
          + vertexId + ", vertexValue=" + numOfValues + "]";
    }
  }

  public static class GraphJobMessageComparator extends WritableComparator {
    private final DataInputBuffer buffer;
    private final GraphJobMessage key1;
    private final GraphJobMessage key2;

    public GraphJobMessageComparator() {
      this(GraphJobMessage.class);
    }

    protected GraphJobMessageComparator(
        Class<? extends WritableComparable<?>> keyClass) {
      this(keyClass, false);
    }

    protected GraphJobMessageComparator(
        Class<? extends WritableComparable<?>> keyClass, boolean createInstances) {
      super(keyClass, createInstances);
      key1 = new GraphJobMessage();
      key2 = new GraphJobMessage();
      buffer = new DataInputBuffer();
    }

    @Override
    public synchronized int compare(byte[] b1, int s1, int l1, byte[] b2,
        int s2, int l2) {
      try {
        buffer.reset(b1, s1, l1); // parse key1
        key1.fastReadFields(buffer);

        buffer.reset(b2, s2, l2); // parse key2
        key2.fastReadFields(buffer);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      return compare(key1, key2); // compare them
    }
  }


  public Iterable<Writable> getIterableMessages() {

    return new Iterable<Writable>() {
      @Override
      public Iterator<Writable> iterator() {
        return new Iterator<Writable>() {
          ByteArrayInputStream bis = new ByteArrayInputStream(byteBuffer.toByteArray());
          DataInputStream dis = new DataInputStream(bis);
          int index = 0;

          @Override
          public boolean hasNext() {
            return (index < numOfValues) ? true : false;
          }

          @Override
          public Writable next() {
            Writable v = GraphJobRunner.createVertexValue();
            try {
              v.readFields(dis);
            } catch (IOException e) {
              e.printStackTrace();
            }
            index++;
            return v;
          }

          @Override
          public void remove() {
          }
        };
      }
    };
  }


}
