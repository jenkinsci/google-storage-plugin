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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.ClassicUpload.DescriptorImpl;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link ClassicUpload}. */
@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassicUploadTest {

    private JenkinsRule jenkins;

    @Mock
    private GoogleRobotCredentials credentials;

    private GoogleCredential credential;

    private String bucket;
    private String glob;

    private FreeStyleProject project;
    private ClassicUpload underTest;
    private boolean sharedPublicly;
    private boolean forFailedJobs;
    private boolean showInline;
    private boolean stripPathPrefix;
    private String pathPrefix;

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

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        jenkins = rule;

        when(credentials.getId()).thenReturn(CREDENTIALS_ID);
        when(credentials.getProjectId()).thenReturn(PROJECT_ID);

        if (jenkins.jenkins != null) {
            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

            // Create a project to which we may attach our uploader.
            project = jenkins.createFreeStyleProject("test");
        }

        credential = new GoogleCredential();
        when(credentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
                .thenReturn(credential);

        // Return ourselves as remotable
        when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class))).thenReturn(credentials);

        notFoundException = new NotFoundException();
        conflictException = new ConflictException();
        forbiddenException = new ForbiddenException();

        bucket = "gs://bucket";
        glob = "bar.txt";
        underTest = new ClassicUpload(
                bucket, new MockUploadModule(executor), glob, null /* legacy arg */, null /* legacy arg */);
    }

    @AfterEach
    void afterEach() {
        assertTrue(executor.sawAll());
        assertFalse(executor.sawUnexpected());
    }

    @Test
    @WithoutJenkins
    void testGetters() {
        assertEquals(glob, underTest.getPattern());
    }

    @Test
    @WithoutJenkins
    void testLegacyArgs() {
        ClassicUpload legacyVersion =
                new ClassicUpload(null /* bucket */, new MockUploadModule(executor), null /* glob */, bucket, glob);
        legacyVersion.setSharedPublicly(sharedPublicly);
        legacyVersion.setForFailedJobs(forFailedJobs);
        legacyVersion.setShowInline(showInline);
        legacyVersion.setPathPrefix(pathPrefix);

        assertEquals(underTest.getBucket(), legacyVersion.getBucket());
        assertEquals(underTest.isSharedPublicly(), legacyVersion.isSharedPublicly());
        assertEquals(underTest.isForFailedJobs(), legacyVersion.isForFailedJobs());
        assertEquals(underTest.getPattern(), legacyVersion.getPattern());
    }

    @Test
    @WithoutJenkins
    void testCheckNullGlob() {
        assertThrows(
                NullPointerException.class,
                () -> new ClassicUpload(
                        bucket, new MockUploadModule(executor), null, null /* legacy arg */, null /* legacy arg */));
    }

    @Test
    void testCheckNullOnNullables() {
        // The upload should handle null for the other fields.
        new ClassicUpload(bucket, null /* module */, glob, null /* legacy arg */, null /* legacy arg */);
    }

    @Test
    @WithoutJenkins
    void doCheckGlobTest() {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, descriptor.doCheckPattern("asdf").kind);
        // Some good sample globs we should accept
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckPattern("target/*.war").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckPattern("**/target/foo.*").kind);
        // Successfully resolved
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckPattern("asdf$BUILD_NUMBER").kind);
        // UN-successfully resolved
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckPattern("$foo").kind);
        // Escaped $BUILD_NUMBER
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckPattern("$$BUILD_NUMBER").kind);
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
