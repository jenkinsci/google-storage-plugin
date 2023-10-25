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
package com.google.jenkins.plugins.storage.client;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** Tests {@link ClientFactory}. */
public class ClientFactoryTest {
  public static final String ACCOUNT_ID = "test-account-id";
  public static final byte[] PK_BYTES =
      "{\"client_email\": \"example@example.com\"}".getBytes(StandardCharsets.UTF_8);

  @Rule public JenkinsRule r = new JenkinsRule();

  @Test
  public void defaultTransport() throws Exception {
    final String credentialId = "my-google-credential";

    SecretBytes bytes = SecretBytes.fromBytes(PK_BYTES);
    JsonServiceAccountConfig serviceAccountConfig = new JsonServiceAccountConfig();
    serviceAccountConfig.setSecretJsonKey(bytes);
    assertNotNull(serviceAccountConfig.getAccountId());

    GoogleRobotPrivateKeyCredentials c =
        new GoogleRobotPrivateKeyCredentials(
            CredentialsScope.GLOBAL, credentialId, ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    assertNotNull(store);
    store.addCredentials(Domain.global(), c);

    // Ensure correct exception is thrown
    assertThrows(
        GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class,
        () -> new ClientFactory(r.jenkins, ImmutableList.of(), credentialId, Optional.empty()));
  }
}
