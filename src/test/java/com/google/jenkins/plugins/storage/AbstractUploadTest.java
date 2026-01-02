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

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_UNAUTHORIZED;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.StubHttpResponseException;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ObjectAccessControl;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.ClassicUpload.DescriptorImpl;
import com.google.jenkins.plugins.storage.reports.BuildGcsUploadReport;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link AbstractUpload}. */
@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbstractUploadTest {

    private JenkinsRule jenkins;

    @TempDir
    private File tempDir;

    private FilePath workspace;
    private FilePath nonWorkspace;
    private FilePath workspaceFile;
    private FilePath workspaceFile2;
    private FilePath workspaceSubdir;
    private FilePath workspaceSubdirFile;
    private String workspaceFileContent;

    @Mock
    private GoogleRobotCredentials credentials;

    private GoogleCredential credential;

    private final MockExecutor executor = new MockExecutor();
    private ConflictException conflictException;
    private ForbiddenException forbiddenException;
    private NotFoundException notFoundException;

    @Mock
    private HttpResponseException httpResponseException;

    private static class FakeUpload extends AbstractUpload {

        public FakeUpload(
                String bucket,
                boolean isPublic,
                boolean forFailed,
                boolean showInline,
                @Nullable String pathPrefix,
                MockUploadModule module,
                String details,
                @Nullable UploadSpec uploads) {
            super(bucket, module);
            setSharedPublicly(isPublic);
            setForFailedJobs(forFailed);
            setShowInline(showInline);
            setPathPrefix(pathPrefix);
            this.details = details;
            this.uploads = uploads;
        }

        @Override
        public String getDetails() {
            return details;
        }

        @Override
        @Nullable
        protected UploadSpec getInclusions(Run<?, ?> run, FilePath workspace, TaskListener listener) {
            return uploads;
        }

        private final String details;

        @Nullable
        private final UploadSpec uploads;

        /** We need this because it is used to retrieve the module when it is null.0 */
        @Extension
        public static class DescriptorImpl extends AbstractUploadDescriptor {

            public DescriptorImpl() {
                super(FakeUpload.class);
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "asdf";
            }
        }
    }

    private FreeStyleProject project;
    private FreeStyleBuild build;

    @BeforeAll
    static void beforeAll() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        jenkins = rule;

        when(credentials.getId()).thenReturn(CREDENTIALS_ID);
        when(credentials.getProjectId()).thenReturn(PROJECT_ID);

        if (jenkins.jenkins != null) {
            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

            project = jenkins.createFreeStyleProject("test");
            project.getPublishersList()
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
        httpResponseException = new StubHttpResponseException(STATUS_CODE_UNAUTHORIZED, "Stub!");

        workspace = new FilePath(makeTempDir("workspace"));
        workspaceFile = workspace.child(FILENAME);
        workspaceFileContent = "Some filler content";
        workspaceFile.write(workspaceFileContent, StandardCharsets.UTF_8.name());
        workspaceFile2 = workspace.child(FILENAME2);
        workspaceFile2.write(workspaceFileContent, StandardCharsets.UTF_8.name());

        workspaceSubdir = workspace.child(SUBDIR_PREFIX);
        workspaceSubdir.mkdirs();
        workspaceSubdirFile = workspaceSubdir.child(FILENAME);
        workspaceSubdirFile.write(workspaceFileContent, StandardCharsets.UTF_8.name());

        nonWorkspace = new FilePath(makeTempDir("non-workspace"));
    }

    @AfterEach
    void afterEach() {
        assertTrue(executor.sawAll());
        assertFalse(executor.sawUnexpected());
    }

    @Test
    @WithoutJenkins
    void testGetters() {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = true;
        final String pathPrefix = null;
        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                null /* uploads */);

        assertEquals(BUCKET_URI, underTest.getBucket());
        assertEquals(sharedPublicly, underTest.isSharedPublicly());
        assertEquals(forFailedJobs, underTest.isForFailedJobs());
        assertEquals(showInline, underTest.isShowInline());
    }

    @Test
    @WithoutJenkins
    void testCheckNullBucket() {
        assertThrows(
                NullPointerException.class,
                () -> new FakeUpload(
                        null /* TESTING NULL BUCKET*/,
                        false /* sharedPublicly */,
                        true /* forFailedJobs */,
                        false /* showInline */,
                        null /* pathPrefix */,
                        new MockUploadModule(executor),
                        FAKE_DETAILS,
                        null /* uploads */));
    }

    @Test
    void testCheckNullOnNullables() {
        // The upload should handle null for the other fields.
        new FakeUpload(
                BUCKET_URI,
                false /* sharedPublicly */,
                true /* forFailedJobs */,
                false /* showInline */,
                null /* pathPrefix */,
                null /* TESTING NULL MODULE*/,
                FAKE_DETAILS,
                null /* uploads */);
    }

    @Test
    void testKeepPathPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceSubdirFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(SUBDIR_FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testStripPathPrefixWithCorrectPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = STRIP_PREFIX;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceSubdirFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(PREFIX_STRIPPED_FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);

        BuildGcsUploadReport buildReport = BuildGcsUploadReport.of(build);
        assertNotNull(buildReport);
        assertEquals(1, buildReport.getStorageObjects().size());
        assertEquals(BUCKET_PREFIX_STRIPPED_FILENAME, Iterables.getLast(buildReport.getStorageObjects()));
    }

    @Test
    void testStripPathPrefixWithWrongPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = WRONG_PREFIX;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceSubdirFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(
                Storage.Objects.Insert.class,
                MockUploadModule.checkObjectName(SUBDIR_FILENAME)); // full, non-stripped filename

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testStripPathPrefixNoTrailingSlash() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = STRIP_PREFIX_NO_SLASH;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceSubdirFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(PREFIX_STRIPPED_FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testStripPathPrefixWithNonDirectoryPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        // This string is a prefix of the input file string,
        // but is not at a directory boundary;  it should not be stripped.
        final String pathPrefix = STRIP_PREFIX_MALFORMED;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceSubdirFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(
                Storage.Objects.Insert.class,
                MockUploadModule.checkObjectName(SUBDIR_FILENAME)); // full, non-stripped filename

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testOnePartPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testTwoPartPrefix() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI + "/" + STORAGE_PREFIX,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(
                Storage.Objects.Insert.class, MockUploadModule.checkObjectName(STORAGE_PREFIX + "/" + FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testRetryOnFailure() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor, 2 /* retries */),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.throwWhen(Storage.Objects.Insert.class, new IOException("should trigger retry"));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testRetryOnFailureStillFails() {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;
        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));
        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor, 2 /* retries */),
                FAKE_DETAILS,
                uploads);
        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.throwWhen(Storage.Objects.Insert.class, new IOException("should trigger retry"));
        executor.throwWhen(Storage.Objects.Insert.class, new IOException("should trigger failure"));
        TaskListener x = TaskListener.NULL;
        assertThrows(UploadException.class, () -> underTest.perform(CREDENTIALS_ID, build, x));
    }

    @Test
    void testRetryOn401() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile, workspaceFile2));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor), /* no retries */
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(FILENAME));
        executor.throwWhen(
                Storage.Objects.Insert.class, httpResponseException, MockUploadModule.checkObjectName(FILENAME2));
        executor.when(Storage.Buckets.Get.class, bucket);
        executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(FILENAME2));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testRetryOn401StillFails() {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;
        Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));
        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));
        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor), /* no retries */
                FAKE_DETAILS,
                uploads);
        int maxRetriesPlus1 = RetryStorageOperation.MAX_REMOTE_CREDENTIAL_EXPIRED_RETRIES + 1;
        for (int i = 0; i < maxRetriesPlus1; i++) {
            executor.when(Storage.Buckets.Get.class, bucket);
            executor.throwWhen(
                    Storage.Objects.Insert.class, httpResponseException, MockUploadModule.checkObjectName(FILENAME));
        }
        TaskListener x = TaskListener.NULL;
        assertThrows(UploadException.class, () -> underTest.perform(CREDENTIALS_ID, build, x));
    }

    @Test
    void testNullUploadSpec() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI + "/" + STORAGE_PREFIX,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                null /* uploads */);

        // Verify that we see no RPCs by pushing nothing into the MockExecutor
        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testWorkspaceNoFiles() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads = new AbstractUpload.UploadSpec(workspace, ImmutableList.of());

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI + "/" + STORAGE_PREFIX,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        // No object insertions

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testBucketConflict() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads = new AbstractUpload.UploadSpec(workspace, ImmutableList.of());

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.throwWhen(
                Storage.Buckets.Insert.class, conflictException, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.when(Storage.Buckets.Get.class, bucket);

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testBucketException() {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;
        final AbstractUpload.UploadSpec uploads = new AbstractUpload.UploadSpec(workspace, ImmutableList.of());
        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);
        executor.throwWhen(Storage.Buckets.Get.class, new IOException("test"));
        TaskListener x = TaskListener.NULL;
        assertThrows(UploadException.class, () -> underTest.perform(CREDENTIALS_ID, build, x));
    }

    @Test
    void testTrailingSlash() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI + "/" + STORAGE_PREFIX + "/",
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        executor.passThruWhen(
                Storage.Objects.Insert.class,
                // Verify there isn't a double-'/'
                MockUploadModule.checkObjectName(STORAGE_PREFIX + "/" + FILENAME));

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testSharedPublicly() throws Exception {
        final boolean sharedPublicly = true;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        final Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

        executor.when(Storage.Buckets.Get.class, bucket);
        executor.passThruWhen(Storage.Objects.Insert.class, operation -> {
            StorageObject object = (StorageObject) operation.getJsonContent();

            assertTrue(object.getAcl().containsAll(bucket.getDefaultObjectAcl()));

            List<ObjectAccessControl> addedAcl =
                    Lists.newArrayList(Iterables.filter(object.getAcl(), not(in(bucket.getDefaultObjectAcl()))));
            Set<String> addedEntities = Sets.newHashSet();
            for (ObjectAccessControl access : addedAcl) {
                assertEquals("READER", access.getRole());
                addedEntities.add(access.getEntity());
            }
            assertTrue(addedEntities.contains("allUsers"));
            return true;
        });

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void testNotShared() throws Exception {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;

        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(workspaceFile));

        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);

        Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setDefaultObjectAcl(Lists.newArrayList(new ObjectAccessControl()));

        executor.when(Storage.Buckets.Get.class, bucket);
        executor.passThruWhen(Storage.Objects.Insert.class, operation -> {
            StorageObject object = (StorageObject) operation.getJsonContent();

            assertNull(object.getAcl());
            return true;
        });

        underTest.perform(CREDENTIALS_ID, build, TaskListener.NULL);
    }

    @Test
    void upload_nofile() {
        final boolean sharedPublicly = false;
        final boolean forFailedJobs = true;
        final boolean showInline = false;
        final String pathPrefix = null;
        FilePath nonExistentFile = workspace.child("non-existent-file");
        final AbstractUpload.UploadSpec uploads =
                new AbstractUpload.UploadSpec(workspace, ImmutableList.of(nonExistentFile));
        FakeUpload underTest = new FakeUpload(
                BUCKET_URI,
                sharedPublicly,
                forFailedJobs,
                showInline,
                pathPrefix,
                new MockUploadModule(executor),
                FAKE_DETAILS,
                uploads);
        executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
        executor.passThruWhen(Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
        TaskListener x = TaskListener.NULL;
        assertThrows(UploadException.class, () -> underTest.perform(CREDENTIALS_ID, build, x));
    }

    @Test
    @WithoutJenkins
    void doCheckBucketTest() throws IOException {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBucketNameWithVars("gs://asdf").kind);
        // Successfully resolved
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBucketNameWithVars("gs://asdf$BUILD_NUMBER").kind);
        // UN-successfully resolved
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("gs://$foo").kind);
        // Escaped $BUILD_NUMBER
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("gs://$$BUILD_NUMBER").kind);
        // Empty
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("").kind);
        // Not a gs:// URI
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBucketNameWithVars("foo").kind);
    }

    private File makeTempDir(String name) {
        File dir = new File(tempDir, name);
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
    private static final String FILENAME2 = "bar2.baz";
    private static final String SUBDIR_PREFIX = "foo/bar";
    private static final String SUBDIR_FILENAME = "foo/bar/bar.baz";
    private static final String WRONG_PREFIX = "qqq/";
    private static final String STRIP_PREFIX = "foo/";
    private static final String STRIP_PREFIX_NO_SLASH = "foo";
    private static final String STRIP_PREFIX_MALFORMED = "foo/ba";
    private static final String PREFIX_STRIPPED_FILENAME = "bar/bar.baz";
    private static final String BUCKET_PREFIX_STRIPPED_FILENAME = "ma-bucket/bar/bar.baz";
    private static final String FAKE_DETAILS = "These are my fake details";
    private static final String FIRST_NAME = "foo";
    private static final String SECOND_NAME = "bar";
}
