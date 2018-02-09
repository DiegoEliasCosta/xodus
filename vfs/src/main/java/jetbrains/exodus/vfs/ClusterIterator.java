/**
 * Copyright 2010 - 2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.exodus.vfs;

import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Transaction;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

class ClusterIterator implements Closeable {

    @NotNull
    private final VirtualFileSystem vfs;
    private final long fd;
    private final Cursor cursor;
    private Cluster currentCluster;
    private boolean isClosed;

    ClusterIterator(@NotNull final VirtualFileSystem vfs,
                    @NotNull final Transaction txn,
                    @NotNull final File file) {
        this(vfs, txn, file.getDescriptor());
    }

    ClusterIterator(@NotNull final VirtualFileSystem vfs,
                    @NotNull final Transaction txn,
                    final long fileDescriptor) {
        this(vfs, txn, fileDescriptor, 0L);
    }

    ClusterIterator(@NotNull final VirtualFileSystem vfs,
                    @NotNull final Transaction txn,
                    final long fileDescriptor,
                    long position) {
        this.vfs = vfs;
        fd = fileDescriptor;
        cursor = vfs.getContents().openCursor(txn);
        seek(position);
        isClosed = false;
    }

    /**
     * Seeks to the cluster that contains data by position. Doesn't navigate within cluster itself.
     *
     * @param position position in the file
     */
    void seek(long position) {
        final ClusteringStrategy cs = vfs.getConfig().getClusteringStrategy();
        final ByteIterable it;
        if (cs.isLinear()) {
            // if clustering strategy is linear then all clusters has the same size
            it = cursor.getSearchKeyRange(ClusterKey.toByteIterable(fd, position / cs.getFirstClusterSize()));
            if (it == null) {
                currentCluster = null;
            } else {
                currentCluster = readCluster(it);
                adjustCurrentCluster();
                final Cluster currentCluster = this.currentCluster;
                if (currentCluster != null) {
                    currentCluster.setStartingPosition(currentCluster.getClusterNumber() * cs.getFirstClusterSize());
                }
            }
        } else {
            it = cursor.getSearchKeyRange(ClusterKey.toByteIterable(fd, 0L));
            if (it == null) {
                currentCluster = null;
            } else {
                final int maxClusterSize = cs.getMaxClusterSize();
                int clusterSize = 0;
                currentCluster = readCluster(it);
                long startingPosition = 0L;
                adjustCurrentCluster();
                while (currentCluster != null) {
                    // if cluster size is equal to max cluster size, then all further cluster will have that size,
                    // so we don't need to load their size
                    if (clusterSize < maxClusterSize) {
                        clusterSize = currentCluster.getSize();
                    }
                    currentCluster.setStartingPosition(startingPosition);
                    if (position < clusterSize) {
                        break;
                    }
                    position -= clusterSize;
                    startingPosition += clusterSize;
                    moveToNext();
                }
            }
        }
    }

    boolean hasCluster() {
        return currentCluster != null;
    }

    Cluster getCurrent() {
        return currentCluster;
    }

    void moveToNext() {
        if (currentCluster != null) {
            if (!cursor.getNext()) {
                currentCluster = null;
            } else {
                currentCluster = readCluster(cursor.getValue());
                adjustCurrentCluster();
            }
        }
    }

    void deleteCurrent() {
        if (currentCluster != null) {
            cursor.deleteCurrent();
        }
    }

    public void close() {
        if (!isClosed) {
            cursor.close();
            isClosed = true;
        }
    }

    boolean isClosed() {
        return isClosed;
    }

    @NotNull
    private Cluster readCluster(@NotNull final ByteIterable it) {
        final ClusterConverter clusterConverter = vfs.getClusterConverter();
        return new Cluster(clusterConverter == null ? it : clusterConverter.onRead(it));
    }

    private void adjustCurrentCluster() {
        final ClusterKey clusterKey = new ClusterKey(cursor.getKey());
        if (clusterKey.getDescriptor() != fd) {
            currentCluster = null;
        } else {
            final IOCancellingPolicyProvider cancellingPolicyProvider = vfs.getCancellingPolicyProvider();
            if (cancellingPolicyProvider != null) {
                final IOCancellingPolicy cancellingPolicy = cancellingPolicyProvider.getPolicy();
                if (cancellingPolicy.needToCancel()) {
                    cancellingPolicy.doCancel();
                }
            }
            currentCluster.setClusterNumber(clusterKey.getClusterNumber());
        }
    }
}