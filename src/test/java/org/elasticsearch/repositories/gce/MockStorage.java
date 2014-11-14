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

package org.elasticsearch.repositories.gce;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import org.elasticsearch.common.io.Streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomInt;

public class MockStorage extends Storage {

    private final ByteArrayOutputStream requestOutputStream;

    public MockStorage(ByteArrayOutputStream requestOutputStream) throws GeneralSecurityException, IOException {
        super(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), null);
        this.requestOutputStream = requestOutputStream;
    }

    public byte[] getRequestOutputStreamAsByteArray() {
        return requestOutputStream.toByteArray();
    }

    @Override
    public Objects objects() {
        return new MockObjects();
    }

    public class MockObjects extends Storage.Objects {

        @Override
        public Insert insert(String bucket, StorageObject content, AbstractInputStreamContent mediaContent) throws IOException {
            return new MockInsert(bucket, content, mediaContent);
        }

        public class MockInsert extends Storage.Objects.Insert {

            AbstractInputStreamContent content;

            protected MockInsert(String bucket, StorageObject content, AbstractInputStreamContent mediaContent) {
                super(bucket, content, mediaContent);
                this.content = mediaContent;
            }

            @Override
            public StorageObject execute() throws IOException {
                StorageObject result = new StorageObject();

                try {
                    long copied = Streams.copy(content.getInputStream(), requestOutputStream);
                    result.setSize(BigInteger.valueOf(copied));

                    TimeUnit.SECONDS.sleep(randomInt(10));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return result;
            }
        }
    }
}
