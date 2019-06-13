package com.google.jenkins.plugins.storage.client;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.security.GeneralSecurityException;

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
   * @throws GeneralSecurityException
   * @throws IOException
   */
  public void deleteFromBucket(String bucket, String pattern) throws IOException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
    deleteFromBucketRequest(bucket, pattern).execute();
  }

  public Storage.Objects.Delete deleteFromBucketRequest(String bucket, String pattern)
      throws IOException {
    return storage.objects().delete(bucket, pattern);
  }
  /**
   * Uploads item with path pattern to Google Cloud Storage bucket of name bucket.
   *
   * @param pattern Pattern to match object name to upload to bucket.
   * @param bucket Name of the bucket to upload to.
   * @param content InputStreamContent of desired file to upload.
   * @throws Exception
   */
  public StorageObject uploadToBucket(String pattern, String bucket, InputStreamContent content)
      throws IOException {
    Preconditions.checkNotNull(content);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));
    return uploadToBucketRequest(pattern, bucket, content).execute();
  }

  public Storage.Objects.Insert uploadToBucketRequest(
      String pattern, String bucket, InputStreamContent content) throws IOException {
    return storage.objects().insert(bucket, null, content).setName(pattern);
  }
}
