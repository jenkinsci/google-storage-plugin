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

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;

/**
 * Client for communicating with the Google GCS API.
 *
 * @see <a href="https://cloud.google.com/storage/docs/json_api/v1/">Cloud Storage</a>
 */
public class StorageClient {
    private final Storage storage;

    /**
     * Constructs a new {@link StorageClient} instance.
     *
     * @param storage The {@link Storage} instance this class will utilize for interacting with the
     *     GCS API.
     */
    public StorageClient(Storage storage) {
        this.storage = Preconditions.checkNotNull(storage);
    }

    /**
     * Delete object matching pattern from Google Cloud Storage bucket of name bucket.
     *
     * @param bucket GCS bucket to delete from.
     * @param pattern Pattern to match object name to delete from bucket.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public void deleteFromBucket(String bucket, String pattern) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
        deleteFromBucketRequest(bucket, pattern).execute();
    }

    /**
     * Helper method to return the delete request.
     *
     * @param bucket GCS bucket to delete from.
     * @param pattern Pattern to match object name to delete from bucket.
     * @return Delete request.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public Storage.Objects.Delete deleteFromBucketRequest(String bucket, String pattern) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
        return storage.objects().delete(bucket, pattern);
    }

    /**
     * Uploads item with path pattern to Google Cloud Storage bucket of name bucket.
     *
     * @param pattern Pattern to match object name to upload to bucket.
     * @param bucket Name of the bucket to upload to.
     * @param content InputStreamContent of desired file to upload.
     * @return The parsed HTTP response from the request.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public StorageObject uploadToBucket(String pattern, String bucket, InputStreamContent content) throws IOException {
        Preconditions.checkNotNull(content);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
        return uploadToBucketRequest(pattern, bucket, content).execute();
    }

    /**
     * Helper method to return the insert request.
     *
     * @param pattern Pattern to match object name to upload to bucket.
     * @param bucket Name of the bucket to upload to.
     * @param content InputStreamContent of desired file to upload.
     * @return Insert request.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public Storage.Objects.Insert uploadToBucketRequest(String pattern, String bucket, InputStreamContent content)
            throws IOException {
        Preconditions.checkNotNull(content);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
        return storage.objects().insert(bucket, null, content).setName(pattern);
    }

    /**
     * Delete bucket from GCS with name bucket. Bucket must be empty.
     *
     * @param bucket Name of GCS bucket to delete.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public void deleteBucket(String bucket) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        deleteBucketRequest(bucket).execute();
    }

    /**
     * Helper method to return the delete request.
     *
     * @param bucket Name of GCS bucket to delete.
     * @return Delete request.
     * @throws IOException If there was an issue calling the GCS API.
     */
    public Storage.Buckets.Delete deleteBucketRequest(String bucket) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
        return storage.buckets().delete(bucket);
    }
}
