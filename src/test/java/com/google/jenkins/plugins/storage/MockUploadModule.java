/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.jenkins.plugins.storage;

import static org.junit.Assert.assertEquals;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Predicate;
import com.google.jenkins.plugins.util.MockExecutor;

/**
 * Mock upload module to stub out executor for testing.
 */
public class MockUploadModule extends UploadModule {
  public MockUploadModule(MockExecutor executor) {
    this(executor, 1 /* retries */);
  }

  public MockUploadModule(MockExecutor executor, int retries) {
    this.executor = executor;
    this.retryCount = retries;
  }

  @Override
  public int getInsertRetryCount() {
    return retryCount;
  }

  @Override
  public MockExecutor newExecutor() {
    return executor;
  }
  private final MockExecutor executor;
  private final int retryCount;

  public static Predicate<Storage.Objects.Insert> checkObjectName(
      final String objectName) {
    return new Predicate<Storage.Objects.Insert>() {
      @Override
      public boolean apply(Storage.Objects.Insert operation) {
        StorageObject object = (StorageObject) operation.getJsonContent();
        assertEquals(objectName, object.getName());
        return true;
      }
    };
  }

  public static Predicate<Storage.Buckets.Insert> checkBucketName(
      final String bucketName) {
    return new Predicate<Storage.Buckets.Insert>() {
      @Override
      public boolean apply(Storage.Buckets.Insert operation) {
        Bucket bucket = (Bucket) operation.getJsonContent();
        assertEquals(bucketName, bucket.getName());
        return true;
      }
    };
  }
};
