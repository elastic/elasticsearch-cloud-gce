/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.gce.blobstore;

import org.elasticsearch.cloud.gce.GoogleCloudStorageService;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.repositories.gce.MockGoogleCloudStorageService;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.io.Streams.copy;
import static org.hamcrest.Matchers.equalTo;

/**
 * Unit test for {@link GoogleCloudStorageOutputStream}.
 */
public class GoogleCloudStorageOutputStreamTest extends ElasticsearchTestCase {

    private ExecutorService executor;

    @Before
    public void setUpExecutor() {
        executor = EsExecutors.newScaling(1, randomIntBetween(1, 4), 5, TimeUnit.SECONDS, EsExecutors.daemonThreadFactory("[s3_stream_test]"));
    }

    @After
    public void tearDownExecutor() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
            boolean done = executor.awaitTermination(10, TimeUnit.SECONDS);
            if (!done) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void testWriteRandomDataToMockConcurrentUpload() throws IOException {
        ConcurrentUpload<byte[]> upload = new MockConcurrentUpload();
        GoogleCloudStorageOutputStream out = new GoogleCloudStorageOutputStream(executor, upload);

        Integer randomLength = randomIntBetween(1, 10000000);
        ByteArrayOutputStream content = new ByteArrayOutputStream(randomLength);
        for (int i = 0; i < randomLength; i++) {
            content.write(randomByte());
        }

        copy(content.toByteArray(), out);

        // Checks length & content
        assertThat(upload.getUploadedObject().length, equalTo(randomLength));
        assertThat(Arrays.equals(upload.getUploadedObject(), content.toByteArray()), equalTo(true));
        assertTrue(upload.isCompleted());
    }

    @Test
    public void testWriteRandomDataToGoogleCloudStorageConcurrentUpload() throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        GoogleCloudStorageService service = new MockGoogleCloudStorageService(ImmutableSettings.EMPTY, result);

        GoogleCloudStorageConcurrentUpload upload = new GoogleCloudStorageConcurrentUpload(service, "test-bucket", "test-project");
        GoogleCloudStorageOutputStream out = new GoogleCloudStorageOutputStream(executor, upload);

        Integer randomLength = randomIntBetween(1, 10000000);
        ByteArrayOutputStream content = new ByteArrayOutputStream(randomLength);
        for (int i = 0; i < randomLength; i++) {
            content.write(randomByte());
        }

        copy(content.toByteArray(), out);

        // Checks length & content
        assertThat(upload.getUploadedObject().getSize().intValue(), equalTo(randomLength));
        assertThat(Arrays.equals(result.toByteArray(), content.toByteArray()), equalTo(true));
    }

    @Test
    public void testConcurrentUploads() throws InterruptedException {

        // ThreadPool used to execute concurrent uploads
        int min = between(1, 3);
        int max = between(min + 1, 6);
        ThreadPoolExecutor pool = EsExecutors.newScaling(min, max, between(1, 100), TimeUnit.SECONDS, EsExecutors.daemonThreadFactory("test"));

        // Number of concurrent uploads
        int uploads = randomIntBetween(1, 10);
        final CountDownLatch latch = new CountDownLatch(uploads);

        List<Integer> lengths = new ArrayList<>(uploads);
        List<ByteArrayOutputStream> contents = new ArrayList<>(uploads);
        List<ByteArrayOutputStream> results = new ArrayList<>(uploads);

        for (int i = 0; i < uploads; ++i) {

            final Integer randomLength = randomIntBetween(1, 10000000);
            lengths.add(randomLength);

            final ByteArrayOutputStream content = new ByteArrayOutputStream(randomLength);
            contents.add(content);

            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            results.add(result);

            final int num = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        GoogleCloudStorageService service = new MockGoogleCloudStorageService(ImmutableSettings.EMPTY, result);
                        GoogleCloudStorageConcurrentUpload upload = new GoogleCloudStorageConcurrentUpload(service, "test-bucket", "test-blob-" + num);
                        GoogleCloudStorageOutputStream out = new GoogleCloudStorageOutputStream(executor, upload);

                        for (int i = 0; i < randomLength; i++) {
                            content.write(randomByte());
                        }

                        logger.debug("writing bytes to upload #{}", num);
                        copy(content.toByteArray(), out);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        logger.debug("waiting for {} concurrent uploads to terminate", uploads);
        latch.await();

        logger.debug("{} concurrent uploads terminated", pool.getCompletedTaskCount());
        assertThat((int) pool.getCompletedTaskCount(), equalTo(uploads));

        for (int i = 0; i < uploads; ++i) {
            Integer length = lengths.get(i);
            ByteArrayOutputStream content = contents.get(i);
            ByteArrayOutputStream result = results.get(i);

            // Checks length & content
            assertThat(Arrays.equals(result.toByteArray(), content.toByteArray()), equalTo(true));
            assertThat(result.size(), equalTo(length));
        }

        pool.shutdownNow();
    }
}
