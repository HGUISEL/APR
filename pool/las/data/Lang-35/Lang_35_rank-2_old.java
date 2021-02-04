/**
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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.regionserver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.hfile.HFileScanner;
import org.apache.hadoop.hbase.regionserver.StoreFile.Reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * KeyValueScanner adaptor over the Reader.  It also provides hooks into
 * bloom filter things.
 */
class StoreFileScanner implements KeyValueScanner {
  static final Log LOG = LogFactory.getLog(Store.class);

  // the reader it comes from:
  private final StoreFile.Reader reader;
  private final HFileScanner hfs;
  private KeyValue cur = null;

  private static final AtomicLong seekCount = new AtomicLong();

  /**
   * Implements a {@link KeyValueScanner} on top of the specified {@link HFileScanner}
   * @param hfs HFile scanner
   */
  public StoreFileScanner(StoreFile.Reader reader, HFileScanner hfs) {
    this.reader = reader;
    this.hfs = hfs;
  }

  /**
   * Return an array of scanners corresponding to the given
   * set of store files.
   */
  public static List<StoreFileScanner> getScannersForStoreFiles(
      Collection<StoreFile> filesToCompact,
      boolean cacheBlocks,
      boolean usePread) throws IOException {
    List<StoreFileScanner> scanners =
      new ArrayList<StoreFileScanner>(filesToCompact.size());
    for (StoreFile file : filesToCompact) {
      StoreFile.Reader r = file.createReader();
      scanners.add(r.getStoreFileScanner(cacheBlocks, usePread));
    }
    return scanners;
  }

  public String toString() {
    return "StoreFileScanner[" + hfs.toString() + ", cur=" + cur + "]";
  }

  public KeyValue peek() {
    return cur;
  }

  public KeyValue next() throws IOException {
    KeyValue retKey = cur;
    try {
      // only seek if we aren't at the end. cur == null implies 'end'.
      if (cur != null) {
        hfs.next();
        cur = hfs.getKeyValue();
      }
    } catch(IOException e) {
      throw new IOException("Could not iterate " + this, e);
    }
    return retKey;
  }

  public boolean seek(KeyValue key) throws IOException {
    seekCount.incrementAndGet();
    try {
      if(!seekAtOrAfter(hfs, key)) {
        close();
        return false;
      }
      cur = hfs.getKeyValue();
      return true;
    } catch(IOException ioe) {
      throw new IOException("Could not seek " + this, ioe);
    }
  }

  public boolean reseek(KeyValue key) throws IOException {
    seekCount.incrementAndGet();
    try {
      if (!reseekAtOrAfter(hfs, key)) {
        close();
        return false;
      }
      cur = hfs.getKeyValue();
      return true;
    } catch (IOException ioe) {
      throw new IOException("Could not seek " + this, ioe);
    }
  }

  public void close() {
    // Nothing to close on HFileScanner?
    cur = null;
  }

  /**
   *
   * @param s
   * @param k
   * @return
   * @throws IOException
   */
  public static boolean seekAtOrAfter(HFileScanner s, KeyValue k)
  throws IOException {
    int result = s.seekTo(k.getBuffer(), k.getKeyOffset(), k.getKeyLength());
    if(result < 0) {
      // Passed KV is smaller than first KV in file, work from start of file
      return s.seekTo();
    } else if(result > 0) {
      // Passed KV is larger than current KV in file, if there is a next
      // it is the "after", if not then this scanner is done.
      return s.next();
    }
    // Seeked to the exact key
    return true;
  }

  static boolean reseekAtOrAfter(HFileScanner s, KeyValue k)
  throws IOException {
    //This function is similar to seekAtOrAfter function
    int result = s.reseekTo(k.getBuffer(), k.getKeyOffset(), k.getKeyLength());
    if (result <= 0) {
      return true;
    } else {
      // passed KV is larger than current KV in file, if there is a next
      // it is after, if not then this scanner is done.
      return s.next();
    }
  }

  // StoreFile filter hook.
  public boolean shouldSeek(Scan scan, final SortedSet<byte[]> columns) {
    return reader.shouldSeek(scan, columns);
  }

  @Override
  public long getSequenceID() {
    return reader.getSequenceID();
  }

  @Override
  public boolean seekExactly(KeyValue kv, boolean forward)
      throws IOException {
    if (reader.getBloomFilterType() != StoreFile.BloomType.ROWCOL ||
        kv.getRowLength() == 0 || kv.getQualifierLength() == 0) {
      return forward ? reseek(kv) : seek(kv);
    }

    boolean isInBloom = reader.passesBloomFilter(kv.getBuffer(),
        kv.getRowOffset(), kv.getRowLength(), kv.getBuffer(),
        kv.getQualifierOffset(), kv.getQualifierLength());
    if (isInBloom) {
      // This row/column might be in this store file. Do a normal seek.
      return forward ? reseek(kv) : seek(kv);
    }

    // Create a fake key/value, so that this scanner only bubbles up to the top
    // of the KeyValueHeap in StoreScanner after we scanned this row/column in
    // all other store files. The query matcher will then just skip this fake
    // key/value and the store scanner will progress to the next column.
    cur = kv.createLastOnRowCol();
    return true;
  }

  Reader getReaderForTesting() {
    return reader;
  }

  // Test methods

  static final long getSeekCount() {
    return seekCount.get();
  }

}
