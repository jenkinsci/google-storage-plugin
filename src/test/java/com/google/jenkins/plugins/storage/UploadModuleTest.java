/*
 * Copyright 2013 Google LLC
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link UploadModule}. */
@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadModuleTest {

    private JenkinsRule jenkins;

    @Mock
    private GoogleRobotCredentials mockGoogleRobotCredentials;

    private UploadModule underTest;

    private final GoogleCredential credential = new GoogleCredential();

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        jenkins = rule;
        underTest = new UploadModule();

        when(mockGoogleRobotCredentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
                .thenReturn(credential);
    }

    @Test
    void version_space() throws Exception {
        Storage storage = underTest.getStorageService(mockGoogleRobotCredentials, "0.14-SNAPSHOT (other details)");
        assertEquals("Jenkins-GCS-Plugin/0.14-SNAPSHOT", storage.getApplicationName());
    }

    @Test
    void version_noSpace() throws Exception {
        Storage storage = underTest.getStorageService(mockGoogleRobotCredentials, "v");
        assertEquals("Jenkins-GCS-Plugin/v", storage.getApplicationName());
    }

    @Test
    void version_none() throws Exception {
        Storage storage = underTest.getStorageService(mockGoogleRobotCredentials, "");
        assertEquals("Jenkins-GCS-Plugin", storage.getApplicationName());
    }

    @Test
    void newUploader_notRightScope() throws Exception {
        GeneralSecurityException ex = new GeneralSecurityException();
        when(mockGoogleRobotCredentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
                .thenThrow(ex);
        Throwable exception =
                assertThrows(IOException.class, () -> underTest.getStorageService(mockGoogleRobotCredentials, ""));
        assertThat(exception.getMessage(), containsString(Messages.UploadModule_ExceptionStorageService()));
    }
}
