/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.AbstractGoogleRobotCredentialsDescriptor;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;

import hudson.AbortException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;

/**
 * Tests for {@link ClassicUpload}.
 */
public class ClassicUploadStepTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Mock
  private GoogleRobotCredentials credentials;

  private GoogleCredential credential;
  @Mock
  private AbstractGoogleRobotCredentialsDescriptor descriptor;

  private final MockExecutor executor = new MockExecutor();

  private NotFoundException notFoundException = new NotFoundException();

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
    when(credentials.getGoogleCredential(isA(
        GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credential);
    when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credentials);
  }

  private void ConfigurationRoundTripTest(ClassicUploadStep s)
      throws Exception {
    ClassicUploadStep after = jenkins.configRoundtrip(s);
    jenkins
        .assertEqualBeans(s, after, "bucket,pattern,pathPrefix,credentialsId");
  }

  @Test
  public void testRoundtrip() throws Exception {
    ClassicUploadStep step = new ClassicUploadStep(CREDENTIALS_ID, "bucket", 
            "pattern", 
            "metadata");
    ConfigurationRoundTripTest(step);

    step.setPathPrefix("prefix");
    ConfigurationRoundTripTest(step);

    step.setSharedPublicly(true);
    ConfigurationRoundTripTest(step);

    step.setShowInline(true);
    ConfigurationRoundTripTest(step);
  }

  @Test
  public void testBuild() throws Exception {
    ClassicUploadStep step = new ClassicUploadStep(CREDENTIALS_ID, BUCKET_URI,
        new MockUploadModule(executor), "*.$BUILD_ID.txt", "gzip");
    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class,
        MockUploadModule.checkObjectName("abc.1.txt"));

    project.getBuildersList().add(step);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    // Put three different files in the workspace. Only one should match if
    // the expansion is done correctly.
    build.getWorkspace().child("abc.7.txt").write("hello", "UTF-8");
    build.getWorkspace().child("abc.1.txt").write("hello", "UTF-8");
    build.getWorkspace().child("abc.$BUILD_ID.txt").write("hello", "UTF-8");
    step.perform(build, build.getWorkspace(),
        build.getWorkspace().createLauncher(TaskListener.NULL),
        TaskListener.NULL);
  }

  @Test
  public void testInvalidCredentials() throws Exception {
    ClassicUploadStep step = new ClassicUploadStep("bad-credentials",
        BUCKET_URI, new MockUploadModule(executor), "*.$BUILD_ID.txt", "gzip");
    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class,
        MockUploadModule.checkObjectName("abc.1.txt"));

    project.getBuildersList().add(step);
    FreeStyleBuild build = project.scheduleBuild2(0).get();

    try {
      step.perform(build, build.getWorkspace(),
          build.getWorkspace().createLauncher(TaskListener.NULL),
          TaskListener.NULL);
    } catch (AbortException e) {
      assertTrue(e.getMessage().contains("bad-credentials"));
      return;
    }
    // Expected exception to happen.
    assertTrue(false);
  }

  private static final String PROJECT_ID = "foo.com:project-build";
  private static final String CREDENTIALS_ID = "creds";

  private static final String BUCKET_NAME = "test-bucket-42";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
}
