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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.jenkins.plugins.storage.AbstractUploadDescriptor.GCS_SCHEME;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.GoogleCloudStorageUploader.DescriptorImpl;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.Shell;

/**
 * Tests for {@link GoogleCloudStorageUploader}.
 */
public class GoogleCloudStorageUploaderTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Mock
  private GoogleRobotCredentials credentials;

  private GoogleCredential credential;

  private String bucket;
  private String glob;

  private FreeStyleProject project;
  private GoogleCloudStorageUploader underTest;
  private boolean sharedPublicly;
  private boolean forFailedJobs;

  private final MockExecutor executor = new MockExecutor();
  private ConflictException conflictException;
  private ForbiddenException forbiddenException;
  private NotFoundException notFoundException;

  private static class MockUploadModule extends UploadModule {
    public MockUploadModule(MockExecutor executor) {
      this.executor = executor;
    }

    @Override
    public MockExecutor newExecutor() {
      return executor;
    }
    private final MockExecutor executor;
  }

  @Rule
  public Verifier verifySawAll = new Verifier() {
      @Override
      public void verify() {
        assertTrue(executor.sawAll());
        assertFalse(executor.sawUnexpected());
      }
    };

  /**
   * Checks that any object insertion that we do has certain
   * properties at the point of execution.
   */
  private Predicate<Storage.Objects.Insert> checkFieldsMatch =
      new Predicate<Storage.Objects.Insert>() {
         public boolean apply(Storage.Objects.Insert insertion) {
           assertNotNull(insertion.getMediaHttpUploader());
           assertEquals(bucket.substring(GCS_SCHEME.length()),
               insertion.getBucket());

           StorageObject object = (StorageObject) insertion.getJsonContent();

           if (sharedPublicly) {
             assertNotNull(object.getAcl());
           } else {
             assertNull(object.getAcl());
           }
           return true;
         }
      };

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(credentials.getId()).thenReturn(CREDENTIALS_ID);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);

    if (jenkins.jenkins != null) {
      SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

      // Create a project to which we may attach our uploader.
      project = jenkins.createFreeStyleProject("test");
    }

    credential = new GoogleCredential();
    when(credentials.getGoogleCredential(isA(
        GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credential);

    // Return ourselves as remotable
    when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credentials);

    notFoundException = new NotFoundException();
    conflictException = new ConflictException();
    forbiddenException = new ForbiddenException();

    bucket = "gs://bucket";
    glob = "bar.txt";
    sharedPublicly = false;
    forFailedJobs = false;
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg*/, null /* legacy arg */)));
  }

  @Test
  @WithoutJenkins
  public void testGetters() {
    assertEquals(CREDENTIALS_ID, underTest.getCredentialsId());
    assertEquals(1, underTest.getUploads().size());
  }

  @Test(expected = NullPointerException.class)
  @WithoutJenkins
  public void testCheckNull() throws Exception {
    new GoogleCloudStorageUploader(null, ImmutableList.<AbstractUpload>of());
  }

  @Test
  @WithoutJenkins
  public void testCheckNullOnNullables() throws Exception {
    // The uploader should handle null for the other fields.
    new GoogleCloudStorageUploader("", null);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testFilePlain() throws Exception {
    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testFilePlain_uploadFailed() throws Exception {
    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, forbiddenException);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString("Forbidden"));
  }

  @Test
  public void testBadBucket() throws Exception {
    bucket = "bucket";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    project.getPublishersList().add(underTest);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());

    dumpLog(build);
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString(
            Messages.AbstractUploadDescriptor_BadPrefix(bucket, GCS_SCHEME)));
  }

  @Test
  public void testNoFileFailure() throws Exception {
    project.getBuildersList().add(new Shell("echo foo > foo.txt"));
    project.getPublishersList().add(underTest);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());

    dumpLog(build);
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString(
            Messages.ClassicUpload_NoArtifacts(glob)));
  }

  @Test
  public void testFilePlainWithFailure() throws Exception {
    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    // Fail the build to show that the uploader does nothing.
    project.getBuildersList().add(new FailureBuilder());
    project.getPublishersList().add(underTest);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.FAILURE, build.getResult());
  }

  @Test
  public void testFilePlainWithFailureAndUpload() throws Exception {
    forFailedJobs = true;
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    // Fail the build to show that the uploader does nothing.
    project.getBuildersList().add(new FailureBuilder());
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.FAILURE, build.getResult());
  }

  @Test
  public void testStdoutUpload() throws Exception {
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new StdoutUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), "build-log.txt",
                null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo Hello World!"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testFileGlob() throws Exception {
    glob = "*.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo foo > bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testAbsolutePath() throws Exception {
    String absoluteFilePath = "/tmp/bar.txt";
    glob = absoluteFilePath;
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo foo > " +
            absoluteFilePath));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testAbsoluteGlob() throws Exception {
    String absoluteFilePath1 = "/tmp/bar.1.txt";
    String absoluteFilePath2 = "/tmp/bar.2.txt";
    glob = "/tmp/bar.*.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(new Shell("echo foo > " +
            absoluteFilePath1));
    project.getBuildersList().add(new Shell("echo foo > " +
            absoluteFilePath2));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testFileWithVar() throws Exception {
    glob = "bar.$BUILD_NUMBER.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(
        new Shell("echo foo > bar.$BUILD_NUMBER.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testFileWithDir() throws Exception {
    glob = "blah/bar.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(
        new Shell("mkdir blah; echo foo > blah/bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testFileWithRecursiveGlob() throws Exception {
    glob = "**/*.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(
        new Shell("mkdir blah; echo foo > blah/bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  public void testMultiFileGlob() throws Exception {
    glob = "*.txt";
    underTest = new GoogleCloudStorageUploader(CREDENTIALS_ID,
        ImmutableList.<AbstractUpload>of(
            new ClassicUpload(bucket, sharedPublicly, forFailedJobs,
                new MockUploadModule(executor), glob,
                null /* legacy arg */, null /* legacy arg */)));

    project.getBuildersList().add(
        new Shell("echo foo > foo.txt; echo bar > bar.txt"));
    project.getPublishersList().add(underTest);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);
    executor.passThruWhen(Storage.Objects.Insert.class, checkFieldsMatch);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    assertEquals(Result.SUCCESS, build.getResult());
  }

  @Test
  @WithoutJenkins
  public void testDescriptor() {
    DescriptorImpl descriptor = new DescriptorImpl();
    assertTrue(descriptor.isApplicable(AbstractProject.class));
    assertEquals(Messages.GoogleCloudStorageUploader_DisplayName(),
        descriptor.getDisplayName());
  }

  @Test
  public void testGetDefaultUploads() {
    DescriptorImpl descriptor = new DescriptorImpl();
    List<AbstractUpload> defaultUploads = descriptor.getDefaultUploads();
    assertEquals(1, defaultUploads.size());
    assertThat(defaultUploads.get(0), instanceOf(StdoutUpload.class));
  }

  private void dumpLog(Run<?, ?> run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());

    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }

  private static final String PROJECT_ID = "foo.com:bar-baz";
  private static final String CREDENTIALS_ID = "bazinga";
  private static final String NAME = "Source (foo.com:bar-baz)";
}