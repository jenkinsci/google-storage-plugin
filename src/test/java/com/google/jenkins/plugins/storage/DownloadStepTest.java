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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
import hudson.util.IOUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link AbstractUpload}. */
public class DownloadStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Mock
    private GoogleRobotCredentials credentials;

    private GoogleCredential credential;

    private final MockExecutor executor = new MockExecutor();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

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
    public void testRoundtrip() throws Exception {
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "bucket", "Dir", new MockUploadModule(executor));
        ConfigurationRoundTripTest(step);

        step.setPathPrefix("prefix");
        ConfigurationRoundTripTest(step);
    }

    @Test
    public void testBuild() throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/path/to/object.txt", "", module);
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Set up mock to retrieve the object
        StorageObject objToGet = new StorageObject();
        objToGet.setBucket("bucket");
        objToGet.setName("path/to/obj.txt");
        executor.when(Storage.Objects.Get.class, objToGet, MockUploadModule.checkGetObject("path/to/object.txt"));

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
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/path/to/object.txt", "subPath", module);
        step.setPathPrefix("path/to/");
        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        // Set up mock to retrieve the object
        StorageObject objToGet = new StorageObject();
        objToGet.setBucket("bucket");
        objToGet.setName("path/to/obj.txt");
        executor.when(Storage.Objects.Get.class, objToGet, MockUploadModule.checkGetObject("path/to/object.txt"));

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

        module.addNextMedia(IOUtils.toInputStream("contents 1", "UTF-8"));

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
    public void testSplit() throws Exception {
        assertArrayEquals(DownloadStep.split("a"), new String[] {"a"});
        assertArrayEquals(
                DownloadStep.split("asdjfkl2358/9/8024@#$@%^$#^#"), new String[] {"asdjfkl2358/9/8024@#$@%^$#^#"});

        assertArrayEquals(DownloadStep.split("a*"), new String[] {"a", ""});
        assertArrayEquals(DownloadStep.split("*"), new String[] {"", ""});

        assertArrayEquals(DownloadStep.split("pre-*-post"), new String[] {"pre-", "-post"});

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
        List<StorageObject> items = new ArrayList<StorageObject>();
        Set<String> prefixes = new HashSet<String>();
        for (String s : names) {
            if (!s.startsWith(prefix)) {
                continue;
            }

            String subdirectory[] = s.substring(prefix.length()).split("/");
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
        o.setPrefixes(new ArrayList<String>(prefixes));
        return o;
    }

    public void tryWildcards(String uriPostfix, String[] matches, String[] notMatches) throws Exception {
        MockUploadModule module = new MockUploadModule(executor);
        DownloadStep step = new DownloadStep(CREDENTIALS_ID, "gs://bucket/" + uriPostfix, ".", module);

        FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

        final List<String> objectNames = new ArrayList<String>();
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
            module.addNextMedia(IOUtils.toInputStream("contents 1", "UTF-8"));
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
            assertFalse("File exists but shouldn't:" + result, result.exists());
        }
    }

    @Test
    public void testBuildWildcards() throws Exception {
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
    public void testBuildWildcardsOnly() throws Exception {
        tryWildcards("*", new String[] {"a", "b.txt", "l_a_b_d_f"}, new String[] {"a/b.txt", "/b"});
    }

    @Test
    public void testBuildWildcardEnd() throws Exception {
        tryWildcards("a/*", new String[] {"a/a.txt", "a/b.txt", "a/log"}, new String[] {"a/b/c.txt"});
    }

    private static final String PROJECT_ID = "foo.com:bar-baz";
    private static final String CREDENTIALS_ID = "bazinga";
}
