package com.google.jenkins.plugins.storage.client;


import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests {@link StorageClient}. */
@RunWith(MockitoJUnitRunner.class)
public class StorageClientTest {
  private static final String TEST_PROJECT_ID = "test-project-id";
  private static final String TEST_BUCKET = "test-bucket";
  private static final String TEST_PATTERN = "test-pattern";
  private static final InputStreamContent TEST_CONTENT =
      new InputStreamContent("", Mockito.mock(InputStream.class));

  @Test(expected = IllegalArgumentException.class)
  public void testInsertErrorWithNullPattern() throws IOException {
    StorageClient storageClient = setUpInsertClient(null);
    storageClient.uploadToBucket(null, TEST_BUCKET, TEST_CONTENT);
  }
  // set up client
  private static StorageClient setUpClient(
      Storage.Objects.Insert insertCall, Storage.Objects.Delete deleteCall) throws IOException {
    Storage storage = Mockito.mock(Storage.class);
    //    Storage.Objects objects = Mockito.mock(Storage.Objects.class);

    //    when(storage.objects()).thenReturn(objects);

    //    // think about this more... i think we have two different kind of calls here
    //    if (insertCall != null) {
    //      when(storage
    //              .objects()
    //              .insert(anyString(), any(StorageObject.class), any(InputStreamContent.class)))
    //          .thenReturn(insertCall);
    //    }
    //    if (deleteCall != null) {
    //      when(storage.objects().delete(anyString(), anyString())).thenReturn(deleteCall);
    //    }
    return new StorageClient(storage);
  }
  // set up object client? call is insert or delete
  // how to test something that doesn't return anything? ask joseph tmr

  private static StorageClient setUpInsertClient(IOException ioException) throws IOException {
    Storage.Objects.Insert insert = Mockito.mock(Storage.Objects.Insert.class);
    StorageClient storage = setUpClient(insert, null);
    //    if (ioException != null) {
    //      when(insert.execute()).thenThrow(ioException);
    //    } else {
    //      when(insert.execute()).thenReturn(new StorageObject().setBucket(TEST_BUCKET));
    //    }
    return storage;
  }
}
