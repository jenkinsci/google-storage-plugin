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
package com.google.jenkins.plugins.storage.util;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.StorageScopeRequirement;
import hudson.AbortException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import jenkins.model.Jenkins;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CredentialsUtilTest {
  private static final String TEST_CREDENTIALS_ID = "test-credentials-id";
  private static final String TEST_INVALID_CREDENTIALS_ID = "test-invalid-credentials-id";
  @ClassRule public static JenkinsRule r = new JenkinsRule();
  public static Jenkins jenkins;

  @BeforeClass
  public static void init() throws IOException {
    jenkins = r.jenkins;

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(jenkins);
    GoogleRobotCredentials credentials = Mockito.mock(GoogleRobotCredentials.class);
    Mockito.when(credentials.getId()).thenReturn(TEST_CREDENTIALS_ID);
    store.addCredentials(Domain.global(), credentials);
  }

  @Test
  public void testGetRobotCredentialsReturnsFirstCredential() throws IOException {
    assertNotNull(
        CredentialsUtil.getRobotCredentials(
            jenkins.get(), ImmutableList.<DomainRequirement>of(), TEST_CREDENTIALS_ID));
  }

  @Test(expected = AbortException.class)
  public void testGetRobotCredentialsInvalidCredentialsIdAbortException() throws AbortException {
    CredentialsUtil.getRobotCredentials(
        jenkins.get(), ImmutableList.<DomainRequirement>of(), TEST_INVALID_CREDENTIALS_ID);
  }

  @Test(expected = AbortException.class)
  public void testGetGoogleCredentialAbortException()
      throws GeneralSecurityException, AbortException {
    GoogleRobotCredentials robotCreds = Mockito.mock(GoogleRobotCredentials.class);
    Mockito.when(robotCreds.getGoogleCredential(any(StorageScopeRequirement.class)))
        .thenThrow(new GeneralSecurityException());
    CredentialsUtil.getGoogleCredential(robotCreds);
  }

  @Test
  public void testGetGoogleCredentialReturnsCredential()
      throws GeneralSecurityException, AbortException {
    GoogleRobotCredentials robotCreds = Mockito.mock(GoogleRobotCredentials.class);
    Credential credential = Mockito.mock(Credential.class);
    Mockito.when(robotCreds.getGoogleCredential(any(StorageScopeRequirement.class)))
        .thenReturn(credential);
    assertNotNull(CredentialsUtil.getGoogleCredential(robotCreds));
  }

  @Test(expected = NullPointerException.class)
  public void testGetRobotCredentialsWithEmptyItemGroup() throws AbortException {
    CredentialsUtil.getRobotCredentials(
        null, ImmutableList.<DomainRequirement>of(), TEST_CREDENTIALS_ID);
  }

  @Test(expected = NullPointerException.class)
  public void testGetRobotCredentialsWithEmptyDomainRequirements() throws AbortException {
    CredentialsUtil.getRobotCredentials(jenkins.get(), null, TEST_CREDENTIALS_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRobotCredentialsWithNullCredentialsId() throws AbortException {
    CredentialsUtil.getRobotCredentials(jenkins.get(), ImmutableList.<DomainRequirement>of(), null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRobotCredentialsWithEmptyCredentialsId() throws AbortException {
    CredentialsUtil.getRobotCredentials(jenkins.get(), ImmutableList.<DomainRequirement>of(), "");
  }
}
