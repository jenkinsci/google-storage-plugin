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
import hudson.FilePath;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.Run;
import hudson.security.ACL;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.rules.TemporaryFolder;

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
   * Creates a temporary workspace for testing, configuring it as a custom workspace for the
   * specified {@link hudson.model.Project}.
   *
   * @param testProject The {@link hudson.model.Project} the test workspace for.
   * @return The {@link org.junit.rules.TemporaryFolder} serving as the test workspace.
   * @throws java.io.IOException If an error occurred while creating the test workspace.
   */
  static TemporaryFolder createTestWorkspace(Project testProject) throws IOException {
    TemporaryFolder testWorkspace = new TemporaryFolder();
    testWorkspace.create();
    testProject.setCustomWorkspace(testWorkspace.getRoot().toString());
    return testWorkspace;
  }

  /**
   * Copies the contents of the specified file to the specified directory.
   *
   * @param testClass The test class related to the file being copied.
   * @param toDir The path of the target directory.
   * @param testFile The test file to copied.
   * @throws IOException If an error occurred while copying test file.
   * @throws InterruptedException If an error occurred while copying test file.
   */
  static void copyTestFileToDir(Class testClass, String toDir, String testFile)
      throws IOException, InterruptedException {
    FilePath dirPath = new FilePath(new File(toDir));
    String testFileContents = loadResource(testClass, testFile);
    FilePath testWorkspaceFile = dirPath.child(testFile);
    testWorkspaceFile.write(testFileContents, StandardCharsets.UTF_8.toString());
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

  // tODO: exceptions and doc header
  static Credential getCredential(ItemGroup itemGroup, String credentialsId) throws Exception {
    Credential credential;
    GoogleRobotCredentials robotCreds =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                GoogleRobotCredentials.class, itemGroup, ACL.SYSTEM, new ArrayList<>()),
            CredentialsMatchers.withId(credentialsId));
    try {
      credential = robotCreds.getGoogleCredential(new StorageScopeRequirement());
    } catch (Exception e) {
      throw new Exception(e);
    }
    return credential;
  }

  // todo: exceptions and doc header
  static Storage getService(ItemGroup itemGroup, String credentialsId) throws Exception {
    return new Storage.Builder(
            new NetHttpTransport(), new JacksonFactory(), getCredential(itemGroup, credentialsId))
        .build();
  }

  static void deleteFromBucket(
      ItemGroup itemGroup, String credentialsId, String bucket, String pattern) throws Exception {
    Storage service = getService(itemGroup, credentialsId);
    service.objects().delete(bucket, pattern).execute();
  }
}
