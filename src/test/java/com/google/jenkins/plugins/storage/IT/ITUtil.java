package com.google.jenkins.plugins.storage.IT;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.common.io.ByteStreams;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.StorageScopeRequirement;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.security.ACL;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

public class ITUtil {
  /**
   * Formats a random name using the given prefix.
   *
   * @param prefix The prefix to be randomly formatted.
   * @return The randomly formatted name.
   */
  static String formatRandomName(String prefix) {
    return String.format("%s-%s", prefix, UUID.randomUUID().toString().replace("-", ""));
  }

  /**
   * Loads the content of the specified resource.
   *
   * @param testClass The test class the resource is being loaded for.
   * @param name The name of the resource being loaded.
   * @return The contents of the loaded resource.
   * @throws IOException If an error occurred during loading.
   */
  static String loadResource(Class testClass, String name) throws IOException {
    return new String(ByteStreams.toByteArray(testClass.getResourceAsStream(name)));
  }

  /**
   * Dumps the logs from the specified {@link hudson.model.Run}.
   *
   * @param logger The {@link java.util.logging.Logger} to be written to.
   * @param run The {@link hudson.model.Run} from which the logs will be read.
   * @throws IOException If an error occurred while dumping the logs.
   */
  static void dumpLog(Logger logger, Run<?, ?> run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());
    String line = null;
    while ((line = reader.readLine()) != null) {
      logger.info(line);
    }
  }

  /**
   * Retrieves the location set through environment variables
   *
   * @return A Google Compute Resource Region (us-west1) or Zone (us-west1-a) string
   */
  static String getLocation() {
    String location = System.getenv("GOOGLE_PROJECT_LOCATION");
    if (location == null) {
      location = System.getenv("GOOGLE_PROJECT_ZONE");
    }
    assertNotNull("GOOGLE_PROJECT_LOCATION env var must be set", location);
    return location;
  }

  /**
   * Given jenkins instance and credentials ID, return Credential
   *
   * @param itemGroup A handle to the Jenkins instance.
   * @param credentialsId credentialsId to retrieve credential. Must exist in credentials store.
   * @return Credential based on credentialsId.
   * @throws GeneralSecurityException
   */
  static Credential getCredential(ItemGroup itemGroup, String credentialsId)
      throws GeneralSecurityException {
    GoogleRobotCredentials robotCreds =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                GoogleRobotCredentials.class, itemGroup, ACL.SYSTEM, new ArrayList<>()),
            CredentialsMatchers.withId(credentialsId));
    try {
      return robotCreds.getGoogleCredential(new StorageScopeRequirement());
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(gse);
    }
  }

  /**
   * Given Jenkins instance and credentials ID, return a Storage object to make calls to the Google
   * Cloud Storage JSON API.
   *
   * @param itemGroup A handle to the Jenkins instance.
   * @param credentialsId credentialsId to retrieve credential. Must exist in credentials store.
   * @return Storage object authenticated with credentialsId.
   * @throws GeneralSecurityException
   */
  static Storage getService(ItemGroup itemGroup, String credentialsId)
      throws GeneralSecurityException {
    try {
      return new Storage.Builder(
              new NetHttpTransport(), new JacksonFactory(), getCredential(itemGroup, credentialsId))
          .build();
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(gse);
    }
  }

  /**
   * Delete object matching pattern from Google Cloud Storage bucket of name bucket.
   *
   * @param itemGroup A handle to the Jenkins instance.
   * @param credentialsId credentialsId to retrieve credential. Must exist in credentials store.
   * @param bucket Name of Google Cloud Storage bucket to delete from.
   * @param pattern Pattern to match object name to delete from bucket.
   * @throws GeneralSecurityException
   * @throws IOException
   */
  static void deleteFromBucket(
      ItemGroup itemGroup, String credentialsId, String bucket, String pattern)
      throws GeneralSecurityException, IOException {
    try {
      Storage service = getService(itemGroup, credentialsId);
      service.objects().delete(bucket, pattern).execute();
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(gse);
    } catch (IOException ioe) {
      throw new IOException(ioe);
    }
  }
}
