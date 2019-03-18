/*
 * Copyright 2013-2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.storage;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth
    .GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;

/**
 * Tests for {@link UploadModule}.
 */
public class UploadModuleTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private GoogleRobotCredentials mockGoogleRobotCredentials;
  private UploadModule underTest;

  GoogleCredential credential = new GoogleCredential();

  @SuppressWarnings("serial")
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    underTest = new UploadModule();

    when(mockGoogleRobotCredentials.getGoogleCredential(isA(
        GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credential);
  }

  @Test
  public void version_space() throws Exception {
    Storage storage = underTest.getStorageService(mockGoogleRobotCredentials,
        "0.14-SNAPSHOT (other details)");
    assertEquals(storage.getApplicationName(),
        "Jenkins-GCS-Plugin/0.14-SNAPSHOT");
  }

  @Test
  public void version_noSpace() throws Exception {
    Storage storage = underTest
        .getStorageService(mockGoogleRobotCredentials, "v");
    assertEquals(storage.getApplicationName(), "Jenkins-GCS-Plugin/v");
  }

  @Test
  public void version_none() throws Exception {
    Storage storage = underTest
        .getStorageService(mockGoogleRobotCredentials, "");
    assertEquals(storage.getApplicationName(), "Jenkins-GCS-Plugin");
  }

  @Test
  public void newUploader_notRightScope()
      throws GeneralSecurityException, IOException, UploadException {
    GeneralSecurityException ex = new GeneralSecurityException();
    when(mockGoogleRobotCredentials.getGoogleCredential(isA(
        GoogleOAuth2ScopeRequirement.class)))
        .thenThrow(ex);
    thrown.expect(IOException.class);
    thrown.expectMessage(
        Messages.UploadModule_ExceptionStorageService());
    underTest.getStorageService(mockGoogleRobotCredentials, "");
  }
}
