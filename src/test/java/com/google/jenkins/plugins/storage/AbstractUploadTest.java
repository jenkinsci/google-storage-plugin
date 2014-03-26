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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ObjectAccessControl;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.ClassicUpload.DescriptorImpl;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

/**
 * Tests for {@link AbstractUpload}.
 */
public class AbstractUploadTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  private FilePath workspace;
  private FilePath nonWorkspace;
  private FilePath workspaceFile;
  private String workspaceFileContent;

  @Mock
  private GoogleRobotCredentials credentials;
  private GoogleCredential credential;

  private final MockExecutor executor = new MockExecutor();
  private ConflictException conflictException;
  private ForbiddenException forbiddenException;
  private NotFoundException notFoundException;

  private Predicate<Storage.Buckets.Insert> checkBucketName(
      final String bucketName) {
    return new Predicate<Storage.Buckets.Insert>() {
      @Override
      public boolean apply(Storage.Buckets.Insert operation) {
        Bucket bucket = (Bucket) operation.getJsonContent();
        assertEquals(bucketName, bucket.getName());
        return true;
      }
    };
  }

  private Predicate<Storage.Objects.Insert> checkObjectName(
      final String objectName) {
    return new Predicate<Storage.Objects.Insert>() {
      @Override
      public boolean apply(Storage.Objects.Insert operation) {
        StorageObject object = (StorageObject) operation.getJsonContent();
        assertEquals(objectName, object.getName());
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
  public Verifier verifySawAll = new Verifier() {
      @Override
      public void verify() {
        assertTrue(executor.sawAll());
        assertFalse(executor.sawUnexpected());
      }
    };

  private static class FakeUpload extends AbstractUpload {
    public FakeUpload(String bucket, boolean isPublic, boolean forFailed,
        MockUploadModule module, String details,
        @Nullable UploadSpec uploads) {
      super(bucket, isPublic, forFailed, module);
      this.details = details;
      this.uploads = uploads;
    }

    @Override
    public String getDetails() {
      return details;
    }

    @Override
    @Nullable
    protected UploadSpec getInclusions(AbstractBuild<?, ?> build,
        FilePath workspace, TaskListener listener) throws UploadException {
      return uploads;
    }

    private final String details;
    @Nullable private final UploadSpec uploads;

    /**
     * We need this because it is used to retrieve the module when
     * it is null.0
     */
    @Extension
    public static class DescriptorImpl extends AbstractUploadDescriptor {
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
      project.getPublishersList().add(
          // Create a storage plugin with no uploaders to fake things out.
          new GoogleCloudStorageUploader(CREDENTIALS_ID, null));
      build = project.scheduleBuild2(0).get();
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

    workspace = new FilePath(makeTempDir("workspace"));
    workspaceFile = workspace.child(FILENAME);
    workspaceFileContent = "Some filler content";
    workspaceFile.write(workspaceFileContent, Charsets.UTF_8.name());

    nonWorkspace = new FilePath(makeTempDir("non-workspace"));
  }

  @Test
  @WithoutJenkins
  public void testGetters() {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;
    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        null /* uploads */);

    assertEquals(BUCKET_URI, underTest.getBucketNameWithVars());
    assertEquals(sharedPublicly, underTest.isSharedPublicly());
    assertEquals(forFailedJobs, underTest.isForFailedJobs());
  }

  @Test(expected = NullPointerException.class)
  @WithoutJenkins
  public void testCheckNullBucket() throws Exception {
    new FakeUpload(null /* TESTING NULL BUCKET*/,
        false /* sharedPublicly */,
        true /* forFailedJobs */,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        null /* uploads */);
  }

  @Test
  public void testCheckNullOnNullables() throws Exception {
    // The upload should handle null for the other fields.
    new FakeUpload(BUCKET_URI,
        false /* sharedPublicly */,
        true /* forFailedJobs */,
        null /* TESTING NULL MODULE*/,
        FAKE_DETAILS,
        null /* uploads */);
  }

  @Test
  public void testOnePartPrefix() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class,
        checkObjectName(FILENAME));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testTwoPartPrefix() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI + "/" + STORAGE_PREFIX,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class,
        checkObjectName(STORAGE_PREFIX + "/" + FILENAME));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testRetryOnFailure() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor, 2 /* retries */),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    executor.throwWhen(Storage.Objects.Insert.class,
        new IOException("should trigger retry"));
    executor.passThruWhen(Storage.Objects.Insert.class,
        checkObjectName(FILENAME));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test(expected = UploadException.class)
  public void testRetryOnFailureStillFails() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor, 2 /* retries */),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    executor.throwWhen(Storage.Objects.Insert.class,
        new IOException("should trigger retry"));
    executor.throwWhen(Storage.Objects.Insert.class,
        new IOException("should trigger failure"));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testNullUploadSpec() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    FakeUpload underTest = new FakeUpload(BUCKET_URI + "/" + STORAGE_PREFIX,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        null /* uploads */);

    // Verify that we see no RPCs by pushing nothing into the MockExecutor
    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testWorkspaceNoFiles() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace, ImmutableList.<FilePath>of());

    FakeUpload underTest = new FakeUpload(BUCKET_URI + "/" + STORAGE_PREFIX,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    // No object insertions

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testBucketConflict() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace, ImmutableList.<FilePath>of());

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    Bucket bucket = new Bucket();
    bucket.setName(BUCKET_NAME);
    bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.throwWhen(Storage.Buckets.Insert.class, conflictException,
        checkBucketName(BUCKET_NAME));
    executor.when(Storage.Buckets.Get.class, bucket);

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test(expected = UploadException.class)
  public void testBucketException() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace, ImmutableList.<FilePath>of());

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class,
        new IOException("test"));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testTrailingSlash() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(
        BUCKET_URI + "/" + STORAGE_PREFIX + "/",
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class,
        // Verify there isn't a double-'/'
        checkObjectName(STORAGE_PREFIX + "/" + FILENAME));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testSharedPublicly() throws Exception {
    final boolean sharedPublicly = true;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    final Bucket bucket = new Bucket();
    bucket.setName(BUCKET_NAME);
    bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

    executor.when(Storage.Buckets.Get.class, bucket);
    executor.passThruWhen(Storage.Objects.Insert.class,
        new Predicate<Storage.Objects.Insert>() {
          @Override
          public boolean apply(Storage.Objects.Insert operation) {
            StorageObject object = (StorageObject) operation.getJsonContent();

            assertTrue(object.getAcl().containsAll(
                bucket.getDefaultObjectAcl()));

            List<ObjectAccessControl> addedAcl =
                Lists.newArrayList(Iterables.filter(
                object.getAcl(), not(in(bucket.getDefaultObjectAcl()))));
            Set<String> addedEntities = Sets.newHashSet();
            for (ObjectAccessControl access : addedAcl) {
              assertEquals("READER", access.getRole());
              addedEntities.add(access.getEntity());
            }
            assertTrue(addedEntities.contains("allUsers"));
            return true;
          }
        });

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  public void testNotShared() throws Exception {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(workspaceFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    Bucket bucket = new Bucket();
    bucket.setName(BUCKET_NAME);
    bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

    executor.when(Storage.Buckets.Get.class, bucket);
    executor.passThruWhen(Storage.Objects.Insert.class,
        new Predicate<Storage.Objects.Insert>() {
          @Override
          public boolean apply(Storage.Objects.Insert operation) {
            StorageObject object = (StorageObject) operation.getJsonContent();

            assertNull(object.getAcl());
            return true;
          }
        });

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test(expected = UploadException.class)
  public void upload_nofile() throws UploadException, IOException {
    final boolean sharedPublicly = false;
    final boolean forFailedJobs = true;

    FilePath nonExistentFile = workspace.child("non-existent-file");
    final AbstractUpload.UploadSpec uploads =
        new AbstractUpload.UploadSpec(workspace,
            ImmutableList.of(nonExistentFile));

    FakeUpload underTest = new FakeUpload(BUCKET_URI,
        sharedPublicly, forFailedJobs,
        new MockUploadModule(executor),
        FAKE_DETAILS,
        uploads);

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(Storage.Buckets.Insert.class,
        checkBucketName(BUCKET_NAME));

    underTest.perform(credentials, build, TaskListener.NULL);
  }

  @Test
  @WithoutJenkins
  public void getRelativePositiveTest() throws Exception {
    FilePath one = workspace.child(FIRST_NAME);

    assertEquals(FIRST_NAME, AbstractUpload.getRelative(one, workspace));

    FilePath second = one.child(SECOND_NAME);
    assertEquals(SECOND_NAME, AbstractUpload.getRelative(second, one));
    assertEquals(FIRST_NAME + '/' + SECOND_NAME,
        AbstractUpload.getRelative(second, workspace));
  }

  @Test
  @WithoutJenkins
  public void getRelativeNegativeTest() throws Exception {
    FilePath one = workspace.child(FIRST_NAME);

    assertEquals(workspace.toString(),
        "/" + AbstractUpload.getRelative(workspace, one));
    assertEquals(nonWorkspace.toString(),
        "/" + AbstractUpload.getRelative(nonWorkspace, workspace));
  }

  @Test
  @WithoutJenkins
  public void doCheckBucketTest() throws IOException {
    DescriptorImpl descriptor = new DescriptorImpl();

    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckBucketNameWithVars("gs://asdf").kind);
    // Successfully resolved
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckBucketNameWithVars("gs://asdf$BUILD_NUMBER").kind);
    // UN-successfully resolved
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckBucketNameWithVars("gs://$foo").kind);
    // Escaped $BUILD_NUMBER
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckBucketNameWithVars("gs://$$BUILD_NUMBER").kind);
    // Empty
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckBucketNameWithVars("").kind);
    // Not a gs:// URI
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckBucketNameWithVars("foo").kind);
  }

  private File makeTempDir(String name) throws IOException {
    File dir = new File(tempDir.getRoot(), name);
    dir.mkdir();
    return dir;
  }


  private static final String PROJECT_ID = "foo.com:bar-baz";
  private static final String CREDENTIALS_ID = "bazinga";
  private static final String NAME = "Source (foo.com:bar-baz)";

  private static final String BUCKET_NAME = "ma-bucket";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
  private static final String STORAGE_PREFIX = "foo";
  private static final String FILENAME = "bar.baz";
  private static final String FAKE_DETAILS = "These are my fake details";

  private static final String FIRST_NAME = "foo";
  private static final String SECOND_NAME = "bar";
}