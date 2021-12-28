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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.AbstractGoogleRobotCredentialsDescriptor;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link StdoutUploadStep} */
public class StdoutUploadStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();
  @Mock private GoogleRobotCredentials credentials;
  private GoogleCredential credential;
  @Mock private AbstractGoogleRobotCredentialsDescriptor descriptor;
  private final MockExecutor executor = new MockExecutor();
  private NotFoundException notFoundException = new NotFoundException();
  private static final String PROJECT_ID = "foo.com:project-build";
  private static final String CREDENTIALS_ID = "creds";
  private static final String BUCKET_NAME = "test-bucket-43";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
  private static final String LOG_NAME = "build-log.txt";

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

  private void ConfigurationRoundTripTest(StdoutUploadStep s) throws Exception {
    StdoutUploadStep after = jenkins.configRoundtrip(s);
    jenkins.assertEqualBeans(s, after, "bucket,logName,pathPrefix,credentialsId");
  }

  @Test
  public void testRoundtrip() throws Exception {
    StdoutUploadStep step = new StdoutUploadStep(CREDENTIALS_ID, "bucket", "logName");

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
    StdoutUploadStep step =
        new StdoutUploadStep(
            CREDENTIALS_ID, BUCKET_URI, Optional.of(new MockUploadModule(executor)), LOG_NAME);

    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    project.getPublishersList().add(step);

    // Perform is run twice: one from scheduleBuild2, one from step.perform.

    // Check behavior of scheduleBuild2.
    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(LOG_NAME));

    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

    assertTrue(executor.sawAll());
    assertFalse(executor.sawUnexpected());

    // Check behavior of step.perform called directly.
    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(LOG_NAME));

    step.perform(
        build,
        build.getWorkspace(),
        build.getWorkspace().createLauncher(TaskListener.NULL),
        TaskListener.NULL);

    assertTrue(executor.sawAll());
    assertFalse(executor.sawUnexpected());
  }

  @Test
  public void testFailedBuild() throws Exception {
    StdoutUploadStep step =
        new StdoutUploadStep(
            CREDENTIALS_ID, BUCKET_URI, Optional.of(new MockUploadModule(executor)), LOG_NAME);

    FreeStyleProject project = jenkins.createFreeStyleProject("testFailedBuild");
    project.getPublishersList().replaceBy(Collections.singleton(new FailureResultPublisher()));

    FreeStyleBuild build = jenkins.buildAndAssertStatus(Result.FAILURE, project);

    // Expect the step to still run if the build failed.
    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(LOG_NAME));

    step.perform(
        build,
        build.getWorkspace(),
        build.getWorkspace().createLauncher(TaskListener.NULL),
        TaskListener.NULL);

    assertTrue(executor.sawAll());
    assertFalse(executor.sawUnexpected());
  }

  static class FailureResultPublisher extends Publisher {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
      build.setResult(Result.FAILURE);
      return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.NONE;
    }

    @Override
    public Descriptor<Publisher> getDescriptor() {
      return new Descriptor<Publisher>(FailureResultPublisher.class) {};
    }
  }
}
