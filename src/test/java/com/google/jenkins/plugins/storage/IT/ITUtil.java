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

package com.google.jenkins.plugins.storage.IT;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.common.io.ByteStreams;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import com.google.jenkins.plugins.storage.StorageScopeRequirement;
import com.google.jenkins.plugins.storage.StringJsonServiceAccountConfig;
import hudson.EnvVars;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;
import org.jvnet.hudson.test.JenkinsRule;

/** Provides a library of utility functions for integration tests. */
public class ITUtil {
  private static String projectId = System.getenv("GOOGLE_PROJECT_ID");
  private static String bucket = System.getenv("GOOGLE_BUCKET");

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
   * Retrieves the location set through environment variables.
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
   * Returns the credentialsId, which is the same as the projectId.
   *
   * @return the credentialdId
   */
  static String getCredentialsId() {
    return projectId;
  }
  /**
   * Given jenkins instance and credentials ID, return Credential.
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
    Storage service;
    try {
      service =
          new Storage.Builder(
                  new NetHttpTransport(),
                  new JacksonFactory(),
                  getCredential(itemGroup, credentialsId))
              .build();
      return service;
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(gse);
    }
  }

  /**
   * Delete object matching pattern from Google Cloud Storage bucket of name bucket.
   *
   * @param itemGroup A handle to the Jenkins instance.
   * @param pattern Pattern to match object name to delete from bucket.
   * @throws GeneralSecurityException
   * @throws IOException
   */
  static void deleteFromBucket(ItemGroup itemGroup, String pattern)
      throws GeneralSecurityException, IOException {
    try {
      Storage service = getService(itemGroup, getCredentialsId());
      service.objects().delete(bucket, pattern).execute();
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(gse);
    } catch (IOException ioe) {
      throw new IOException(ioe);
    }
  }

  /** Uploads item with path pattern to Google Cloud Storage bucket of name bucket. */
  static void uploadToBucket(ItemGroup itemGroup, String pattern) throws Exception {
    Storage service = getService(itemGroup, getCredentialsId());
    InputStream stream = DownloadStepPipelineIT.class.getResourceAsStream(pattern);
    String contentType = URLConnection.guessContentTypeFromStream(stream);
    InputStreamContent content = new InputStreamContent(contentType, stream);
    service.objects().insert(bucket, null, content).setName(pattern).execute();
  }
  /**
   * Initializes the env variables needed to run pipeline integration tests
   *
   * @param pattern pattern needed to run the integration test. Varies depending on which pipeline
   *     integration test is being run.
   * @return Returns handle to EnvVars to change env variables as needed.
   */
  static EnvVars initializePipelineITEnvironment(String pattern, JenkinsRule jenkinsRule)
      throws Exception {
    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);
    bucket = System.getenv("GOOGLE_BUCKET");
    assertNotNull("GOOGLE_BUCKET env var must be set", bucket);
    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);
    String credentialsId = projectId;
    // TODO: this part will be part of credentialsUtil?
    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);

    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(credentialsId, sac, null);

    CredentialsStore store =
        new SystemCredentialsProvider.ProviderImpl().getStore(jenkinsRule.jenkins);

    store.addCredentials(Domain.global(), c);
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars envVars = prop.getEnvVars();
    envVars.put("CREDENTIALS_ID", credentialsId);
    envVars.put("BUCKET", bucket);
    envVars.put("PATTERN", pattern);
    jenkinsRule.jenkins.getGlobalNodeProperties().add(prop);
    return envVars;
  }
}
