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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.common.base.Predicate;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.IOException;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link AbstractBucketLifecycleManager}. */
public class AbstractBucketLifecycleManagerTest {

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Mock private GoogleRobotCredentials credentials;
  private GoogleCredential credential;

  private final MockExecutor executor = new MockExecutor();
  private ConflictException conflictException;
  private ForbiddenException forbiddenException;
  private NotFoundException notFoundException;

  private Predicate<Storage.Buckets.Insert> checkBucketName(final String bucketName) {
    return new Predicate<Storage.Buckets.Insert>() {
      @Override
      public boolean apply(Storage.Buckets.Insert operation) {
        Bucket bucket = (Bucket) operation.getJsonContent();
        assertEquals(bucketName, bucket.getName());
        return true;
      }
    };
  }

  private Predicate<Storage.Buckets.Update> checkSameBucket(final Bucket theBucket) {
    return new Predicate<Storage.Buckets.Update>() {
      @Override
      public boolean apply(Storage.Buckets.Update operation) {
        Bucket bucket = (Bucket) operation.getJsonContent();
        assertSame(bucket, theBucket);
        return true;
      }
    };
  }

  private static class MockUploadModule extends UploadModule {

    public MockUploadModule(MockExecutor executor) {
      this(executor, 1 /* retries */);
    }

    public MockUploadModule(MockExecutor executor, int retries) {
      this.executor = executor;
      this.retryCount = retries;
    }

    @Override
    public int getInsertRetryCount() {
      return retryCount;
    }

    @Override
    public MockExecutor newExecutor() {
      return executor;
    }

    private final MockExecutor executor;
    private final int retryCount;
  }

  @Rule
  public Verifier verifySawAll =
      new Verifier() {
        @Override
        public void verify() {
          assertTrue(executor.sawAll());
          assertFalse(executor.sawUnexpected());
        }
      };

  private static class FakeUpload extends AbstractBucketLifecycleManager {

    public FakeUpload(
        String bucketName, MockUploadModule module, String details, @Nullable Bucket bucket) {
      super(bucketName, module);
      this.details = details;
      this.bucket = bucket;
    }

    @Override
    public String getDetails() {
      return details;
    }

    private final String details;

    @Override
    public Bucket checkBucket(Bucket bucket) throws InvalidAnnotationException {
      if (this.bucket == null) {
        return bucket;
      }
      throw new InvalidAnnotationException(bucket);
    }

    @Override
    public Bucket decorateBucket(Bucket bucket) {
      return checkNotNull(this.bucket);
    }

    @Nullable private final Bucket bucket;

    /** We need this because it is used to retrieve the module when it is null. */
    @Extension
    public static class DescriptorImpl extends AbstractBucketLifecycleManagerDescriptor {

      public DescriptorImpl() {
        super(FakeUpload.class);
      }

      public String getDisplayName() {
        return "asdf";
      }
    }
  }

  private FreeStyleProject project;
  private FreeStyleBuild build;

  @Before
  public void setUp() throws Exception {
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
    conflictException = new ConflictException();
    forbiddenException = new ForbiddenException();
  }

  @Test
  @WithoutJenkins
  public void testGetters() {
    FakeUpload underTest =
        new FakeUpload(BUCKET_URI, new MockUploadModule(executor), FAKE_DETAILS, null /* bucket */);

    assertEquals(BUCKET_URI, underTest.getBucket());
    assertEquals(FAKE_DETAILS, underTest.getDetails());
  }

  @Test
  public void testFailingBucketCheck() throws Exception {
    final Bucket bucket = new Bucket().setName(BUCKET_NAME);

    FakeUpload underTest =
        new FakeUpload(BUCKET_URI, new MockUploadModule(executor), FAKE_DETAILS, bucket);

    // A get that returns a bucket should trigger a check/decorate/update
    executor.when(Storage.Buckets.Get.class, new Bucket());
    executor.passThruWhen(Storage.Buckets.Update.class, checkSameBucket(bucket));

    underTest.perform(CREDENTIALS_ID, build, build.getWorkspace(), TaskListener.NULL);
  }

  @Test
  public void testPassingBucketCheck() throws Exception {
    final Bucket bucket = new Bucket().setName(BUCKET_NAME);

    FakeUpload underTest =
        new FakeUpload(
            BUCKET_URI,
            new MockUploadModule(executor),
            FAKE_DETAILS,
            null /* pass the bucket check */);

    // A get that passes our check should incur no further RPC
    executor.when(Storage.Buckets.Get.class, bucket);

    underTest.perform(CREDENTIALS_ID, build, build.getWorkspace(), TaskListener.NULL);
  }

  @Test
  public void testPassingBucketCheckAfterNotFoundThenConflict() throws Exception {
    final Bucket bucket = new Bucket().setName(BUCKET_NAME);

    FakeUpload underTest =
        new FakeUpload(BUCKET_URI, new MockUploadModule(executor), FAKE_DETAILS, bucket);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.throwWhen(Storage.Buckets.Insert.class, conflictException);
    // Verify that our final "get" handles updating the bucket as well
    executor.when(Storage.Buckets.Get.class, new Bucket());
    executor.passThruWhen(Storage.Buckets.Update.class, checkSameBucket(bucket));

    underTest.perform(CREDENTIALS_ID, build, build.getWorkspace(), TaskListener.NULL);
  }

  @Test(expected = UploadException.class)
  public void testRandomErrorExecutor() throws Exception {
    FakeUpload underTest =
        new FakeUpload(
            BUCKET_URI,
            new MockUploadModule(executor),
            FAKE_DETAILS,
            null /* pass the bucket check */);

    executor.throwWhen(Storage.Buckets.Get.class, conflictException);

    underTest.perform(CREDENTIALS_ID, build, build.getWorkspace(), TaskListener.NULL);
  }

  @Test(expected = UploadException.class)
  public void testRandomErrorIOException() throws Exception {
    FakeUpload underTest =
        new FakeUpload(
            BUCKET_URI,
            new MockUploadModule(executor),
            FAKE_DETAILS,
            null /* pass the bucket check */);

    executor.throwWhen(Storage.Buckets.Get.class, new IOException("test"));

    underTest.perform(CREDENTIALS_ID, build, build.getWorkspace(), TaskListener.NULL);
  }

  @Test
  public void testCustomBucketNameValidation() throws Exception {
    FakeUpload underTest =
        new FakeUpload(
            BUCKET_URI,
            new MockUploadModule(executor),
            FAKE_DETAILS,
            null /* pass the bucket check */);

    AbstractBucketLifecycleManagerDescriptor descriptor = underTest.getDescriptor();

    assertEquals(FormValidation.Kind.OK, descriptor.doCheckBucketNameWithVars("gs://asdf").kind);
    // Successfully resolved
    assertEquals(
        FormValidation.Kind.OK,
        descriptor.doCheckBucketNameWithVars("gs://asdf$BUILD_NUMBER").kind);
    // Not a gs:// URI
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("foo").kind);

    // Multi-part not allowed for bucket lifecycle plugins
    assertEquals(
        FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("gs://foo/bar").kind);
  }

  private static final String PROJECT_ID = "foo.com:bar-baz";
  private static final String CREDENTIALS_ID = "bazinga";

  private static final String BUCKET_NAME = "ma-bucket";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
  private static final String FAKE_DETAILS = "These are my fake details";
}
