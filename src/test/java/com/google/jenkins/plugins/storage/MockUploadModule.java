package com.google.jenkins.plugins.storage;

import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.util.MockExecutor;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Predicate;
import com.google.inject.AbstractModule;

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
