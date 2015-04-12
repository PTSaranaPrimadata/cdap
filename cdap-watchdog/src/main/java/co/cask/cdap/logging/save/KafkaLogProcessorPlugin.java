/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.logging.save;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.logging.LoggingConfiguration;
import co.cask.cdap.logging.appender.kafka.LoggingEventSerializer;
import co.cask.cdap.logging.kafka.KafkaLogEvent;
import co.cask.cdap.logging.write.AvroFileWriter;
import co.cask.cdap.logging.write.FileMetaDataManager;
import co.cask.cdap.logging.write.LogFileWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.RowSortedTable;
import com.google.common.collect.TreeBasedTable;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.apache.twill.common.Threads;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class KafkaLogProcessorPlugin extends AbstractIdleService implements KafkaLogProcessor {

  private final String logBaseDir;
  private final ListeningScheduledExecutorService scheduledExecutor;
  private final LogFileWriter<KafkaLogEvent> logFileWriter;
  private final RowSortedTable<Long, String, Map.Entry<Long, List<KafkaLogEvent>>> messageTable;


  private final long eventBucketIntervalMs;
  private final int logCleanupIntervalMins;
  private final long maxNumberOfBucketsInTable;
  private final LoggingEventSerializer serializer;
  private ScheduledFuture<?> logWriterFuture;
  private  CountDownLatch countDownLatch;

  private static final long SLEEP_TIME_MS = 100;

  private static final Logger LOG = LoggerFactory.getLogger(KafkaLogProcessorPlugin.class);

  @Inject
  public KafkaLogProcessorPlugin(CConfiguration cConfig, FileMetaDataManager fileMetaDataManager,
                                 CheckpointManager checkpointManager, LocationFactory locationFactory)
                                 throws Exception {

    this.serializer = new LoggingEventSerializer();

    this.scheduledExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor(
      Threads.createDaemonThreadFactory("log-saver-log-processor")));;

    messageTable = TreeBasedTable.create();
    this.logBaseDir = cConfig.get(LoggingConfiguration.LOG_BASE_DIR);
    Preconditions.checkNotNull(this.logBaseDir, "Log base dir cannot be null");
    LOG.info(String.format("Log base dir is %s", this.logBaseDir));

    long retentionDurationDays = cConfig.getLong(LoggingConfiguration.LOG_RETENTION_DURATION_DAYS,
                                                 LoggingConfiguration.DEFAULT_LOG_RETENTION_DURATION_DAYS);
    Preconditions.checkArgument(retentionDurationDays > 0,
                                "Log file retention duration is invalid: %s", retentionDurationDays);
    long maxLogFileSizeBytes = cConfig.getLong(LoggingConfiguration.LOG_MAX_FILE_SIZE_BYTES, 20 * 1024 * 1024);
    Preconditions.checkArgument(maxLogFileSizeBytes > 0,
                                "Max log file size is invalid: %s", maxLogFileSizeBytes);

    int syncIntervalBytes = cConfig.getInt(LoggingConfiguration.LOG_FILE_SYNC_INTERVAL_BYTES, 50 * 1024);
    Preconditions.checkArgument(syncIntervalBytes > 0,
                                "Log file sync interval is invalid: %s", syncIntervalBytes);

    long checkpointIntervalMs = cConfig.getLong(LoggingConfiguration.LOG_SAVER_CHECKPOINT_INTERVAL_MS,
                                                LoggingConfiguration.DEFAULT_LOG_SAVER_CHECKPOINT_INTERVAL_MS);
    Preconditions.checkArgument(checkpointIntervalMs > 0,
                                "Checkpoint interval is invalid: %s", checkpointIntervalMs);

    long inactiveIntervalMs = cConfig.getLong(LoggingConfiguration.LOG_SAVER_INACTIVE_FILE_INTERVAL_MS,
                                              LoggingConfiguration.DEFAULT_LOG_SAVER_INACTIVE_FILE_INTERVAL_MS);
    Preconditions.checkArgument(inactiveIntervalMs > 0,
                                "Inactive interval is invalid: %s", inactiveIntervalMs);

    this.eventBucketIntervalMs = cConfig.getLong(LoggingConfiguration.LOG_SAVER_EVENT_BUCKET_INTERVAL_MS,
                                                 LoggingConfiguration.DEFAULT_LOG_SAVER_EVENT_BUCKET_INTERVAL_MS);
    Preconditions.checkArgument(this.eventBucketIntervalMs > 0,
                                "Event bucket interval is invalid: %s", this.eventBucketIntervalMs);

    this.maxNumberOfBucketsInTable = cConfig.getLong
      (LoggingConfiguration.LOG_SAVER_MAXIMUM_INMEMORY_EVENT_BUCKETS,
       LoggingConfiguration.DEFAULT_LOG_SAVER_MAXIMUM_INMEMORY_EVENT_BUCKETS);
    Preconditions.checkArgument(this.maxNumberOfBucketsInTable > 0,
                                "Maximum number of event buckets in memory is invalid: %s",
                                this.maxNumberOfBucketsInTable);


    long topicCreationSleepMs = cConfig.getLong(LoggingConfiguration.LOG_SAVER_TOPIC_WAIT_SLEEP_MS,
                                                LoggingConfiguration.DEFAULT_LOG_SAVER_TOPIC_WAIT_SLEEP_MS);
    Preconditions.checkArgument(topicCreationSleepMs > 0,
                                "Topic creation wait sleep is invalid: %s", topicCreationSleepMs);

    logCleanupIntervalMins = cConfig.getInt(LoggingConfiguration.LOG_CLEANUP_RUN_INTERVAL_MINS,
                                            LoggingConfiguration.DEFAULT_LOG_CLEANUP_RUN_INTERVAL_MINS);
    Preconditions.checkArgument(logCleanupIntervalMins > 0,
                                "Log cleanup run interval is invalid: %s", logCleanupIntervalMins);

    AvroFileWriter avroFileWriter = new AvroFileWriter(fileMetaDataManager, cConfig, locationFactory.create(""),
                                                       logBaseDir, serializer.getAvroSchema(), maxLogFileSizeBytes,
                                                       syncIntervalBytes, inactiveIntervalMs);

    this.logFileWriter = new CheckpointingLogFileWriter(avroFileWriter, checkpointManager, checkpointIntervalMs);
  }

  @Override
  public void begin(Set<Integer> partitions) {

    LogWriter logWriter = new LogWriter(logFileWriter, messageTable,
                                        eventBucketIntervalMs, maxNumberOfBucketsInTable);
    logWriterFuture = scheduledExecutor.scheduleWithFixedDelay(logWriter, 100, 200, TimeUnit.MILLISECONDS);
    this.countDownLatch = new CountDownLatch(1);
  }

  @Override
  public void process(KafkaLogEvent event) {
    LoggingContext loggingContext = event.getLoggingContext();
    ILoggingEvent logEvent = event.getLogEvent();
    try {
    // Compute the bucket number for the current event
    long key = logEvent.getTimeStamp() / eventBucketIntervalMs;

    // Sleep while we can add the entry
    while (true) {
        // Get the oldest bucket in the table
        long oldestBucketKey = 0;
        synchronized (messageTable) {
          SortedSet<Long> rowKeySet = messageTable.rowKeySet();
          if (rowKeySet.isEmpty()) {
            // Table is empty so go ahead and add the current event in the table
            break;
          }
          oldestBucketKey = rowKeySet.first();
        }

        // If the current event falls in the bucket number which is not in window [oldestBucketKey, oldestBucketKey+8]
        // sleep for the time duration till event falls in the window
        if (key > (oldestBucketKey + maxNumberOfBucketsInTable)) {
          LOG.trace("key={}, oldestBucketKey={}, maxNumberOfBucketsInTable={}. Sleeping for {} ms.",
                    key, oldestBucketKey, maxNumberOfBucketsInTable, SLEEP_TIME_MS);

            if (countDownLatch.await(SLEEP_TIME_MS, TimeUnit.MILLISECONDS)) {
              // if count down occurred return
              LOG.info("Returning since callback is cancelled");
              return;
            }

        } else {
          break;
        }
      }

      synchronized (messageTable) {
        Map.Entry<Long, List<KafkaLogEvent>> entry = messageTable.get(key,
                                                                      loggingContext.getLogPathFragment(logBaseDir));
        List<KafkaLogEvent> msgList = null;
        if (entry == null) {
          long eventArrivalBucketKey = System.currentTimeMillis() / eventBucketIntervalMs;
          msgList = Lists.newArrayList();
          messageTable.put(key, loggingContext.getLogPathFragment(logBaseDir),
                           new AbstractMap.SimpleEntry<Long, List<KafkaLogEvent>>(eventArrivalBucketKey, msgList));
        } else {
          msgList = messageTable.get(key, loggingContext.getLogPathFragment(logBaseDir)).getValue();
        }
        LOG.info("ADDING event {} {}", event.getLogEvent().getMessage(), event.getLoggingContext());
        msgList.add(new KafkaLogEvent(event.getGenericRecord(), event.getLogEvent(), loggingContext,
                                      event.getPartition(), event.getNextOffset()));
      }
    } catch (Throwable th) {
      LOG.warn("Exception while processing message with nextOffset {}. Skipping it.", event.getNextOffset(), th);
    }
  }

  @Override
  public void end() {
    try {
      logFileWriter.flush();
      logFileWriter.close();

      if (logWriterFuture != null && !logWriterFuture.isCancelled() && !logWriterFuture.isDone()) {
        logWriterFuture.cancel(false);
        logWriterFuture = null;
      }
      this.countDownLatch.countDown();
    } catch (Exception e) {
      // TODO: LOG
    }
    messageTable.clear();
    LOG.info("END");
  }

  @Override
  protected void startUp() throws Exception {

  }

  @Override
  protected void shutDown() throws Exception {
    scheduledExecutor.shutdown();
  }
}
