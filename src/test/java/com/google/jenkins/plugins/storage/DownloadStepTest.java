/*
 * Copyright 2017 Google LLC
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.MockExecutor;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
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
class DownloadStepTest {

    private JenkinsRule jenkins;

    @TempDir
    private File tempDir;

    @Mock
    private GoogleRobotCredentials credentials;

    private GoogleCredential credential;

    private final MockExecutor executor = new MockExecutor();

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        jenkins = rule;

        when(credentials.getId()).thenReturn(CREDENTIALS_ID);
        when(credentials.getProjectId()).thenReturn(PROJECT_ID);

        if (jenkins.jenkins != null) {
            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        }

        credential = new GoogleCredential();
        when(credentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
                .thenReturn(credential);

        // Return ourselves as remotable
        when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class))).thenReturn(credentials);
    }

    private void ConfigurationRoundTripTest(DownloadStep s) throws Exception {
        DownloadStep after = jenkins.configRoundtrip(s);
        jenkins.assertEqualBeans(s, after, "bucketUri,localDirectory,pathPrefix,credentialsId");
    }

    @Test
    void testRoundtrip() throws Exception {
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "bucket", "Dir", new MockUploadModule(executor));
        ConfigurationRoundTripTest(step);

        step.setPathPrefix("prefix");
        ConfigurationRoundTripTest(step);
    }

    @Test
    void testBuild() throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/path/to/object.txt", "", module);
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Set up mock to retrieve the object
        StorageObject objToGet = new StorageObject();
        objToGet.setBucket("bucket");
        objToGet.setName("path/to/obj.txt");
        executor.when(Storage.Objects.Get.class, objToGet, MockUploadModule.checkGetObject("path/to/object.txt"));

        module.addNextMedia(IOUtils.toInputStream("test", StandardCharsets.UTF_8));

        project.getBuildersList().add(step);
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        FilePath result = build.getWorkspace().withSuffix("/path/to/obj.txt");
        assertTrue(result.exists());
        assertEquals("test", result.readToString());
    }

    @Test
    void testBuildPrefix() throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/path/to/object.txt", "subPath", module);
        step.setPathPrefix("path/to/");
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Set up mock to retrieve the object
        StorageObject objToGet = new StorageObject();
        objToGet.setBucket("bucket");
        objToGet.setName("path/to/obj.txt");
        executor.when(Storage.Objects.Get.class, objToGet, MockUploadModule.checkGetObject("path/to/object.txt"));

        module.addNextMedia(IOUtils.toInputStream("test", StandardCharsets.UTF_8));

        project.getBuildersList().add(step);
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        FilePath result = build.getWorkspace().withSuffix("/subPath/obj.txt");
        assertTrue(result.exists());
        assertEquals("test", result.readToString());
    }

    @Test
    void testBuildMoreComplex() throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(
                CREDENTIALS_ID, "gs://bucket/download/$BUILD_ID/path/$BUILD_ID/test_$BUILD_ID.txt", "output", module);
        step.setPathPrefix("download/$BUILD_ID/");
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Set up mock to retrieve the object
        StorageObject objToGet = new StorageObject();
        objToGet.setBucket("bucket");
        objToGet.setName("download/1/path/1/test_1.txt");
        executor.when(
                Storage.Objects.Get.class, objToGet, MockUploadModule.checkGetObject("download/1/path/1/test_1.txt"));

        module.addNextMedia(IOUtils.toInputStream("contents 1", StandardCharsets.UTF_8));

        project.getBuildersList().add(step);
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        FilePath result = build.getWorkspace().withSuffix("/output/path/1/test_1.txt");
        assertTrue(result.exists());
        assertEquals("contents 1", result.readToString());
    }

    private void checkSplitException(String s) {
        try {
            DownloadStep.split(s);
        } catch (AbortException e) {
            assertTrue(e.getMessage().contains("Multiple asterisks"));
            return;
        }
        fail("Expected split to fail on input " + s);
    }

    @Test
    @WithoutJenkins
    void testSplit() throws Exception {
        assertArrayEquals(new String[] {"a"}, DownloadStep.split("a"));
        assertArrayEquals(
                new String[] {"asdjfkl2358/9/8024@#$@%^$#^#"}, DownloadStep.split("asdjfkl2358/9/8024@#$@%^$#^#"));

        assertArrayEquals(new String[] {"a", ""}, DownloadStep.split("a*"));
        assertArrayEquals(new String[] {"", ""}, DownloadStep.split("*"));

        assertArrayEquals(new String[] {"pre-", "-post"}, DownloadStep.split("pre-*-post"));

        // Not yet supported
        checkSplitException("**");
        checkSplitException("a**");
        checkSplitException("a*b*c");
        checkSplitException("a/*b/*c");
    }

    /**
     * Create the Objects object that would have been returned from the Cloud.
     *
     * @param prefix the requested object prefix
     * @param names a list of object names that are available
     */
    private Objects createObjects(String prefix, List<String> names) {
        Objects o = new Objects();
        List<StorageObject> items = new ArrayList<>();
        Set<String> prefixes = new HashSet<>();
        for (String s : names) {
            if (!s.startsWith(prefix)) {
                continue;
            }

            String[] subdirectory = s.substring(prefix.length()).split("/");
            if (subdirectory.length > 1) {
                // This object is nested deeper. Add a subdirectory
                prefixes.add(prefix + subdirectory[0]);
            } else {
                // Add the object
                StorageObject objToGet = new StorageObject();
                objToGet.setBucket("bucket");
                objToGet.setName(s);
                items.add(objToGet);
            }
        }
        o.setItems(items);
        o.setPrefixes(new ArrayList<>(prefixes));
        return o;
    }

    public void tryWildcards(String uriPostfix, String[] matches, String[] notMatches) throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/" + uriPostfix, ".", module);

        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        final List<String> objectNames = new ArrayList<>();
        objectNames.addAll(Arrays.asList(matches));
        objectNames.addAll(Arrays.asList(notMatches));

        int index = uriPostfix.indexOf('*');

        final String prefix;
        if (index >= 0) {
            prefix = uriPostfix.substring(0, index);
        } else {
            prefix = uriPostfix;
        }

        Objects objects = createObjects(prefix, objectNames);

        for (String s : objectNames) {
            // ensure module has enough streams. Since the order in which they
            // will be queries is undefined, we will not attempt to verify
            // which one belongs to which.
            module.addNextMedia(IOUtils.toInputStream("contents 1", StandardCharsets.UTF_8));
        }

        // Stub out the response from the Cloud
        executor.when(Storage.Objects.List.class, objects);

        project.getBuildersList().add(step);
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        for (String s : matches) {
            FilePath result = build.getWorkspace().withSuffix("/" + s);
            assertTrue(result.exists());
            assertEquals("contents 1", result.readToString());
        }
        for (String s : notMatches) {
            FilePath result = build.getWorkspace().withSuffix("/" + s);
            assertFalse(result.exists(), "File exists but shouldn't:" + result);
        }
    }

    @Test
    void testBuildWildcards() throws Exception {
        tryWildcards(
                "download/log_*.txt",
                new String[] {
                    "download/log_1.txt", "download/log_1_.txt", "download/log_.txt", "download/log_ajkl23-d.txt"
                },
                new String[] {
                    "downloa/log_1.txt", "download/log.txt", "download/log_", "download/log_1/a.txt", "download/log_1"
                });
    }

    @Test
    void testBuildWildcardsOnly() throws Exception {
        tryWildcards("*", new String[] {"a", "b.txt", "l_a_b_d_f"}, new String[] {"a/b.txt", "/b"});
    }

    @Test
    void testBuildWildcardEnd() throws Exception {
        tryWildcards("a/*", new String[] {"a/a.txt", "a/b.txt", "a/log"}, new String[] {"a/b/c.txt"});
    }

    private static final String PROJECT_ID = "foo.com:bar-baz";
    private static final String CREDENTIALS_ID = "bazinga";
}
