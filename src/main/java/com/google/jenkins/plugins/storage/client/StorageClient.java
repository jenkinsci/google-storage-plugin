package com.google.jenkins.plugins.storage.client;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
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
    try {
      storage.objects().delete(bucket, pattern);
    } catch (IOException ioe) {
      throw new IOException(ioe);
    }
  }

  /**
   * Uploads item with path pattern to Google Cloud Storage bucket of name bucket.
   *
   * @param pattern Pattern to match object name to upload to bucket.
   * @param bucket Name of the bucket to upload to.
   * @param callingClass Class with path to load the resource file.
   * @throws Exception
   */
  public void uploadToBucket(String pattern, String bucket, Class callingClass) throws IOException {
    InputStream stream = callingClass.getResourceAsStream(pattern);
    String contentType = URLConnection.guessContentTypeFromStream(stream);
    InputStreamContent content = new InputStreamContent(contentType, stream);
    storage.objects().insert(bucket, null, content).setName(pattern).execute();
  }
}
