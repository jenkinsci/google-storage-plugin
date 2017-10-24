/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.jenkins.plugins.credentials.oauth.AbstractGoogleRobotCredentialsDescriptor;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.MockExecutor;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.IOUtils;


/**
 * Tests for {@link AbstractUpload}.
 */
public class DownloadStepTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Mock
  private GoogleRobotCredentials credentials;
  private GoogleCredential credential;

  private final MockExecutor executor = new MockExecutor();

  @Mock
  private AbstractGoogleRobotCredentialsDescriptor descriptor;

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

    // Return ourselves as remotable
    when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credentials);
  }

  private void ConfigurationRoundTripTest(DownloadStep s)
      throws Exception {
    DownloadStep after = jenkins.configRoundtrip(s);
    jenkins
        .assertEqualBeans(s, after,
            "bucketUri,localDirectory,pathPrefix,credentialsId");
  }

  @Test
  public void testRoundtrip() throws Exception {
    DownloadStep step = new DownloadStep(CREDENTIALS_ID, "bucket",
        "Dir", new MockUploadModule(executor));
    ConfigurationRoundTripTest(step);

    step.setPathPrefix("prefix");
    ConfigurationRoundTripTest(step);
  }

  @Test
  public void testBuild() throws Exception {
    MockUploadModule module = new MockUploadModule(executor);
    DownloadStep step = new DownloadStep(CREDENTIALS_ID,
        "gs://bucket/path/to/object.txt",
        "", module);
    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    // Set up mock to retrieve the object
    StorageObject objToGet = new StorageObject();
    objToGet.setBucket("bucket");
    objToGet.setName("path/to/obj.txt");
    executor.when(Storage.Objects.Get.class, objToGet,
        MockUploadModule.checkGetObject("path/to/object.txt"));

    module.addNextMedia(IOUtils.toInputStream("test", "UTF-8"));

    project.getBuildersList().add(step);
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

    FilePath result = build.getWorkspace().withSuffix("/path/to/obj.txt");
    assertTrue(result.exists());
    assertEquals("test", result.readToString());
  }

  @Test
  public void testBuildPrefix() throws Exception {
    MockUploadModule module = new MockUploadModule(executor);
    DownloadStep step = new DownloadStep(CREDENTIALS_ID,
        "gs://bucket/path/to/object.txt",
        "subPath", module);
    step.setPathPrefix("path/to/");
    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    // Set up mock to retrieve the object
    StorageObject objToGet = new StorageObject();
    objToGet.setBucket("bucket");
    objToGet.setName("path/to/obj.txt");
    executor.when(Storage.Objects.Get.class, objToGet,
        MockUploadModule.checkGetObject("path/to/object.txt"));

    module.addNextMedia(IOUtils.toInputStream("test", "UTF-8"));

    project.getBuildersList().add(step);
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

    FilePath result = build.getWorkspace().withSuffix("/subPath/obj.txt");
    assertTrue(result.exists());
    assertEquals("test", result.readToString());
  }

  @Test
  public void testBuildMoreComplex() throws Exception {
    MockUploadModule module = new MockUploadModule(executor);
    DownloadStep step = new DownloadStep(CREDENTIALS_ID,
        "gs://bucket/download/$BUILD_ID/path/$BUILD_ID/test_$BUILD_ID.txt",
        "output", module);
    step.setPathPrefix("download/$BUILD_ID/");
    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    // Set up mock to retrieve the object
    StorageObject objToGet = new StorageObject();
    objToGet.setBucket("bucket");
    objToGet.setName("download/1/path/1/test_1.txt");
    executor.when(Storage.Objects.Get.class, objToGet,
        MockUploadModule.checkGetObject("download/1/path/1/test_1.txt"));

    module.addNextMedia(IOUtils.toInputStream("contents 1", "UTF-8"));

    project.getBuildersList().add(step);
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

    FilePath result = build.getWorkspace()
        .withSuffix("/output/path/1/test_1.txt");
    assertTrue(result.exists());
    assertEquals("contents 1", result.readToString());
  }

  private static final String PROJECT_ID = "foo.com:bar-baz";
  private static final String CREDENTIALS_ID = "bazinga";
}
