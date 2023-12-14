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
package com.google.jenkins.plugins.storage;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.util.GoogleRobotCredentialsDescriptor;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link ExpiringBucketLifecycleManagerStep} */
public class ExpiringBucketLifecycleManagerStepTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Mock
    private GoogleRobotCredentials credentials;

    private GoogleCredential credential;

    @Mock
    private GoogleRobotCredentialsDescriptor.AbstractGoogleRobotCredentialsTestDescriptor descriptor;

    private final MockExecutor executor = new MockExecutor();
    private NotFoundException notFoundException = new NotFoundException();
    private static final String PROJECT_ID = "foo.com:project-build";
    private static final String CREDENTIALS_ID = "creds";
    private static final String BUCKET_NAME = "test-bucket-43";
    private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
    private static final int TTL = 1;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(descriptor.getDisplayName()).thenReturn("Credentials Name");

        when(credentials.getId()).thenReturn(CREDENTIALS_ID);
        when(credentials.getProjectId()).thenReturn(PROJECT_ID);
        when(credentials.getDescriptor()).thenReturn(descriptor);

        if (jenkins.jenkins != null) {
            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        }

        credential = new GoogleCredential();
        when(credentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
                .thenReturn(credential);
        when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class))).thenReturn(credentials);
    }

    private void ConfigurationRoundTripTest(ExpiringBucketLifecycleManagerStep s) throws Exception {
        ExpiringBucketLifecycleManagerStep after = jenkins.configRoundtrip(s);
        jenkins.assertEqualBeans(s, after, "bucket,ttl,credentialsId");
    }

    @Test
    public void testRoundtrip() throws Exception {
        ExpiringBucketLifecycleManagerStep step = new ExpiringBucketLifecycleManagerStep(CREDENTIALS_ID, "bucket", 1);

        ConfigurationRoundTripTest(step);
    }

    @Test
    public void testBuild() throws Exception {
        ExpiringBucketLifecycleManagerStep step =
                new ExpiringBucketLifecycleManagerStep(CREDENTIALS_ID, BUCKET_URI, TTL);
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Perform is run twice: one from scheduleBuild2, one from step.perform
        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));

        project.getPublishersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        step.perform(
                build, build.getWorkspace(), build.getWorkspace().createLauncher(TaskListener.NULL), TaskListener.NULL);
    }
}
