/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.storage.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests {@link com.google.jenkins.plugins.storage.client.StorageClient}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageClientTest {
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_PATTERN = "test-pattern";
    private static final InputStreamContent TEST_CONTENT = new InputStreamContent("", Mockito.mock(InputStream.class));

    @Test
    void testInsertObjectErrorWithNullPattern() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(
                IllegalArgumentException.class, () -> storageClient.uploadToBucket(null, TEST_BUCKET, TEST_CONTENT));
    }

    @Test
    void testInsertObjectErrorWithNullBucket() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(
                IllegalArgumentException.class, () -> storageClient.uploadToBucket(TEST_PATTERN, null, TEST_CONTENT));
    }

    @Test
    void testInsertObjectErrorWithNullContent() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(NullPointerException.class, () -> storageClient.uploadToBucket(TEST_PATTERN, TEST_BUCKET, null));
    }

    @Test
    void testInsertObjectErrorWithEmptyPattern() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.uploadToBucket("", TEST_BUCKET, TEST_CONTENT));
    }

    @Test
    void testInsertObjectErrorWithEmptyBucket() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(
                IllegalArgumentException.class, () -> storageClient.uploadToBucket(TEST_PATTERN, "", TEST_CONTENT));
    }

    @Test
    void testInsertObjectReturnsCorrectly() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        Storage.Objects.Insert insertRequest =
                storageClient.uploadToBucketRequest(TEST_PATTERN, TEST_BUCKET, TEST_CONTENT);
        assertNotNull(insertRequest);
        assertEquals(TEST_BUCKET, insertRequest.getBucket());
    }

    @Test
    void testDeleteObjectErrorWithNullPattern() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteFromBucket(TEST_BUCKET, null));
    }

    @Test
    void testDeleteObjectErrorWithNullBucket() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteFromBucket(null, TEST_PATTERN));
    }

    @Test
    void testDeleteObjectErrorWithEmptyPattern() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteFromBucket(TEST_BUCKET, ""));
    }

    @Test
    void testDeleteObjectErrorWithEmptyBucket() throws IOException {
        StorageClient storageClient = setUpObjectInsertClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteFromBucket("", TEST_PATTERN));
    }

    @Test
    void testDeleteObjectReturnsCorrectly() throws IOException {
        StorageClient storageClient = setUpObjectDeleteClient();
        Storage.Objects.Delete deleteRequest = storageClient.deleteFromBucketRequest(TEST_BUCKET, TEST_PATTERN);
        assertNotNull(deleteRequest);
        assertEquals(TEST_BUCKET, deleteRequest.getBucket());
    }

    @Test
    void testDeleteBucketErrorWithNullBucket() throws IOException {
        StorageClient storageClient = setUpBucketDeleteClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteBucket(null));
    }

    @Test
    void testDeleteBucketErrorWithEmptyBucket() throws IOException {
        StorageClient storageClient = setUpBucketDeleteClient();
        assertThrows(IllegalArgumentException.class, () -> storageClient.deleteBucket(""));
    }

    @Test
    void testDeleteBucketReturnsCorrectly() throws IOException {
        StorageClient storageClient = setUpBucketDeleteClient();
        Storage.Buckets.Delete deleteRequest = storageClient.deleteBucketRequest(TEST_BUCKET);
        assertNotNull(deleteRequest);
        assertEquals(TEST_BUCKET, deleteRequest.getBucket());
    }

    private static StorageClient setUpObjectClient(Storage.Objects.Insert insertCall, Storage.Objects.Delete deleteCall)
            throws IOException {
        Storage storage = Mockito.mock(Storage.class);
        Storage.Objects objects = Mockito.mock(Storage.Objects.class);
        when(storage.objects()).thenReturn(objects);

        if (insertCall != null) {
            when(insertCall.getBucket()).thenReturn(TEST_BUCKET);
            when(objects.insert(anyString(), ArgumentMatchers.isNull(), any(InputStreamContent.class)))
                    .thenReturn(insertCall);
            when(insertCall.setName(anyString())).thenReturn(insertCall);
        }
        if (deleteCall != null) {
            when(deleteCall.getBucket()).thenReturn(TEST_BUCKET);
            when(storage.objects().delete(anyString(), anyString())).thenReturn(deleteCall);
        }
        return new StorageClient(storage);
    }

    private static StorageClient setUpObjectInsertClient() throws IOException {
        Storage.Objects.Insert insertCall = Mockito.mock(Storage.Objects.Insert.class);
        return setUpObjectClient(insertCall, null);
    }

    private static StorageClient setUpObjectDeleteClient() throws IOException {
        Storage.Objects.Delete deleteCall = Mockito.mock(Storage.Objects.Delete.class);
        return setUpObjectClient(null, deleteCall);
    }

    private static StorageClient setUpBucketClient(Storage.Buckets.Delete deleteCall) throws IOException {
        Storage storage = Mockito.mock(Storage.class);
        Storage.Buckets buckets = Mockito.mock(Storage.Buckets.class);
        when(storage.buckets()).thenReturn(buckets);

        if (deleteCall != null) {
            when(deleteCall.getBucket()).thenReturn(TEST_BUCKET);
            when(storage.buckets().delete(anyString())).thenReturn(deleteCall);
        }
        return new StorageClient(storage);
    }

    private static StorageClient setUpBucketDeleteClient() throws IOException {
        Storage.Buckets.Delete deleteCall = Mockito.mock(Storage.Buckets.Delete.class);
        return setUpBucketClient(deleteCall);
    }
}
