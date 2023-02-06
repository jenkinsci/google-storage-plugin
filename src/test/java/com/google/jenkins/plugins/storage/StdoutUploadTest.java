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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.StdoutUpload.DescriptorImpl;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link StdoutUpload} and friends. */
public class StdoutUploadTest {

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Mock private GoogleRobotCredentials credentials;
  private GoogleCredential credential;

  private final MockExecutor executor = new MockExecutor();

  private FreeStyleProject project;
  private FreeStyleBuild build;

  private NotFoundException notFoundException;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(credentials.getId()).thenReturn(CREDENTIALS_ID);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);

    if (jenkins.jenkins != null) {
      SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

      project = jenkins.createFreeStyleProject("test");
      project
          .getPublishersList()
          .add(
              // Create a storage plugin with no uploaders to fake things out.
              new GoogleCloudStorageUploader(CREDENTIALS_ID, null));
      build = project.scheduleBuild2(0).get();
    }
    credential = new GoogleCredential();
    when(credentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credential);

    // Return ourselves as remotable
    when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class))).thenReturn(credentials);

    notFoundException = new NotFoundException();
  }

  @Test
  public void doCheckLogNameTest() throws IOException {
    DescriptorImpl descriptor = new DescriptorImpl();

    assertEquals(FormValidation.Kind.OK, descriptor.doCheckLogName("asdf").kind);
    // Successfully resolved
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckLogName("asdf$BUILD_NUMBER").kind);
    // UN-successfully resolved
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLogName("$foo").kind);
    // Escaped $BUILD_NUMBER
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLogName("$$BUILD_NUMBER").kind);
  }

  @Test
  public void doCheckLogNameExpansion() throws Exception {
    StdoutUpload underTest =
        new StdoutUpload(
            BUCKET_URI, new MockUploadModule(executor), "build.$BUILD_NUMBER.log", null);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(
        Storage.Objects.Insert.class, MockUploadModule.checkObjectName("build.1.log"));

    underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
  }

  private static final String PROJECT_ID = "foo.com:bar-baz";
  private static final String CREDENTIALS_ID = "bazinga";
  private static final String BUCKET_NAME = "bucket";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
}
