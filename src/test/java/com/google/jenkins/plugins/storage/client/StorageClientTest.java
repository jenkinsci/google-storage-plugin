package com.google.jenkins.plugins.storage.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests {@link StorageClient}. */
@RunWith(MockitoJUnitRunner.class)
public class StorageClientTest {
  private static final String TEST_BUCKET = "test-bucket";
  private static final String TEST_PATTERN = "test-pattern";
  private static final InputStreamContent TEST_CONTENT =
      new InputStreamContent("", Mockito.mock(InputStream.class));

  @Test(expected = IllegalArgumentException.class)
  public void testInsertErrorWithNullPattern() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    storageClient.uploadToBucket(null, TEST_BUCKET, TEST_CONTENT);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInsertErrorWithNullBucket() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    storageClient.uploadToBucket(TEST_PATTERN, null, TEST_CONTENT);
  }

  @Test(expected = NullPointerException.class)
  public void testInsertErrorWithNullContent() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    storageClient.uploadToBucket(TEST_PATTERN, TEST_BUCKET, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInsertErrorWithEmptyPattern() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    storageClient.uploadToBucket("", TEST_BUCKET, TEST_CONTENT);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInsertErrorWithEmptyBucket() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    storageClient.uploadToBucket(TEST_PATTERN, "", TEST_CONTENT);
  }

  @Test
  public void testInsertObjectReturnsCorrectly() throws IOException {
    StorageClient storageClient = setUpInsertClient();
    Storage.Objects.Insert insertRequest =
        storageClient.uploadToBucketRequest(TEST_PATTERN, TEST_BUCKET, TEST_CONTENT);
    assertNotNull(insertRequest);
    assertEquals(TEST_BUCKET, insertRequest.getBucket());
  }

  @Test
  public void testDeleteObjectReturnscorrectly() throws IOException {
    StorageClient storageClient = setUpDeleteClient();
    Storage.Objects.Delete deleteRequest =
        storageClient.deleteFromBucketRequest(TEST_BUCKET, TEST_PATTERN);
    assertNotNull(deleteRequest);
    assertEquals(TEST_BUCKET, deleteRequest.getBucket());
  }

  private static StorageClient setUpClient(
      Storage.Objects.Insert insertCall, Storage.Objects.Delete deleteCall) throws IOException {
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

  private static StorageClient setUpInsertClient() throws IOException {
    Storage.Objects.Insert insertCall = Mockito.mock(Storage.Objects.Insert.class);
    StorageClient storage = setUpClient(insertCall, null);
    return storage;
  }

  private static StorageClient setUpDeleteClient() throws IOException {
    Storage.Objects.Delete deleteCall = Mockito.mock(Storage.Objects.Delete.class);
    StorageClient storage = setUpClient(null, deleteCall);
    return storage;
  }
}
