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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.AbortException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CredentialsUtilTest {
    private static final String TEST_CREDENTIALS_ID = "test-credentials-id";
    private static final String TEST_INVALID_CREDENTIALS_ID = "test-invalid-credentials-id";

    private static JenkinsRule r;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void testGetRobotCredentialsInvalidCredentialsIdAbortException() {
        assertThrows(
                AbortException.class,
                () -> CredentialsUtil.getRobotCredentials(r.jenkins, ImmutableList.of(), TEST_INVALID_CREDENTIALS_ID));
    }

    @Test
    void testGetGoogleCredentialAbortException() throws Exception {
        SecretBytes bytes =
                SecretBytes.fromBytes("{\"client_email\": \"example@example.com\"}".getBytes(StandardCharsets.UTF_8));
        JsonServiceAccountConfig serviceAccountConfig = new JsonServiceAccountConfig();
        serviceAccountConfig.setSecretJsonKey(bytes);
        assertNotNull(serviceAccountConfig.getAccountId());
        GoogleRobotCredentials robotCreds =
                new GoogleRobotPrivateKeyCredentials(TEST_INVALID_CREDENTIALS_ID, serviceAccountConfig, null);
        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), robotCreds);
        assertThrows(
                GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class,
                () -> CredentialsUtil.getGoogleCredential(robotCreds));
    }

    @Test
    void testGetRobotCredentialsWithEmptyItemGroup() {
        assertThrows(
                NullPointerException.class,
                () -> CredentialsUtil.getRobotCredentials(null, ImmutableList.of(), TEST_CREDENTIALS_ID));
    }

    @Test
    void testGetRobotCredentialsWithEmptyDomainRequirements() {
        assertThrows(
                NullPointerException.class,
                () -> CredentialsUtil.getRobotCredentials(r.jenkins, null, TEST_CREDENTIALS_ID));
    }

    @Test
    void testGetRobotCredentialsWithNullCredentialsId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CredentialsUtil.getRobotCredentials(r.jenkins, ImmutableList.of(), null));
    }

    @Test
    void testGetRobotCredentialsWithEmptyCredentialsId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CredentialsUtil.getRobotCredentials(r.jenkins, ImmutableList.of(), ""));
    }
}
