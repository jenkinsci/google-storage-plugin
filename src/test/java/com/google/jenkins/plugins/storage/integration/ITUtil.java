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

package com.google.jenkins.plugins.storage.integration;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
   * Returns the credentialsId, which is the same as the projectId.
   *
   * @return the credentialsId
   */
  static String getCredentialsId() {
    return projectId;
  }

  static String getBucket() {
    return bucket;
  }

  /**
   * Initializes the env variables needed to run pipeline integration tests.
   *
   * @param pattern pattern needed to run the integration test. Varies depending on which pipeline
   *     integration test is being run.
   * @return Returns handle to EnvVars to change env variables as needed.
   * @throws Exception If there was an issue initializing or storing credentials.
   */
  static EnvVars initializePipelineITEnvironment(String pattern, JenkinsRule jenkinsRule)
      throws Exception {
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);
    // This bucket is only used for DownloadStepPipelineIT to download objects from.
    assertNotNull("GOOGLE_BUCKET env var must be set", bucket);
    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);
    String credentialsId = getCredentialsId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern));

    SecretBytes secretBytes =
        SecretBytes.fromBytes(serviceAccountKeyJson.getBytes(StandardCharsets.UTF_8));
    JsonServiceAccountConfig sac = new JsonServiceAccountConfig();
    sac.setSecretJsonKey(secretBytes);
    Credentials c = new GoogleRobotPrivateKeyCredentials(credentialsId, sac, null);
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
