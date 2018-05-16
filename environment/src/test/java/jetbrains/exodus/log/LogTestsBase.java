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
package jetbrains.exodus.log;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.TestUtil;
import jetbrains.exodus.core.dataStructures.Pair;
import jetbrains.exodus.env.DummyProcessCoordinator;
import jetbrains.exodus.io.DataReader;
import jetbrains.exodus.io.DataWriter;
import jetbrains.exodus.io.FileDataReader;
import jetbrains.exodus.io.FileDataWriter;
import jetbrains.exodus.util.IOUtil;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

class LogTestsBase {

    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    protected volatile Log log;
    private File logDirectory = null;
    protected DataReader reader;
    protected DataWriter writer;

    @Before
    public void setUp() throws IOException {
        final File testsDirectory = getLogDirectory();
        if (testsDirectory.exists()) {
            IOUtil.deleteRecursively(testsDirectory);
        } else if (!testsDirectory.mkdir()) {
            throw new IOException("Failed to create directory for tests.");
        }

        synchronized (this) {
            final Pair<DataReader, DataWriter> logRW = createLogRW();
            reader = logRW.getFirst();
            writer = logRW.getSecond();
        }
    }

    @After
    public void tearDown() throws Exception {
        closeLog();
        final File testsDirectory = getLogDirectory();
        IOUtil.deleteRecursively(testsDirectory);
        IOUtil.deleteFile(testsDirectory);
        logDirectory = null;
    }

    protected Pair<DataReader, DataWriter> createLogRW() {
        return new Pair<DataReader, DataWriter>(new FileDataReader(logDirectory, 16), new FileDataWriter(logDirectory));
    }

    void initLog(final long fileSize) {
        initLog(new LogConfig().setFileSize(fileSize));
    }

    void initLog(final long fileSize, final int cachePageSize) {
        initLog(new LogConfig().setFileSize(fileSize).setCachePageSize(cachePageSize));
    }

    void initLog(final LogConfig config) {
        if (log == null) {
            synchronized (this) {
                if (log == null) {
                    config.setReader(reader);
                    config.setWriter(writer);
                    log = new Log(config);
                }
            }
        }
    }

    protected Log getLog() {
        if (log == null) {
            synchronized (this) {
                if (log == null) {
                    log = new Log(LogConfig.create(reader, writer), new DummyProcessCoordinator());
                }
            }
        }
        return log;
    }

    void closeLog() throws IOException {
        if (log != null) {
            log.close();
            log = null;
        }
    }

    File getLogDirectory() {
        if (logDirectory == null) {
            logDirectory = TestUtil.createTempDir();
        }
        return logDirectory;
    }

    static TestLoggable createOneKbLoggable() {
        return new TestLoggable((byte) 126, new ArrayByteIterable(new byte[1024], 1024), Loggable.NO_STRUCTURE_ID);
    }

}
