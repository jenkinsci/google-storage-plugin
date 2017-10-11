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

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_UNAUTHORIZED;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.StubHttpResponseException;
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
import com.google.jenkins.plugins.storage.reports.BuildGcsUploadReport;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link AbstractUpload}.
 */
public class StorageUtilTest {
  private FilePath workspace;
  private FilePath nonWorkspace;

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    workspace = new FilePath(makeTempDir("workspace"));
    nonWorkspace = new FilePath(makeTempDir("non-workspace"));
  }

  @Test
  @WithoutJenkins
  public void getRelativePositiveTest() throws Exception {
    FilePath one = workspace.child(FIRST_NAME);

    assertEquals(FIRST_NAME, StorageUtil.getRelative(one, workspace));

    FilePath second = one.child(SECOND_NAME);
    assertEquals(SECOND_NAME, StorageUtil.getRelative(second, one));
    assertEquals(FIRST_NAME + '/' + SECOND_NAME,
        StorageUtil.getRelative(second, workspace));
  }

  @Test
  @WithoutJenkins
  public void getRelativeNegativeTest() throws Exception {
    FilePath one = workspace.child(FIRST_NAME);

    assertEquals(workspace.toString(),
        "/" + StorageUtil.getRelative(workspace, one));
    assertEquals(nonWorkspace.toString(),
        "/" + StorageUtil.getRelative(nonWorkspace, workspace));
  }

  private File makeTempDir(String name) throws IOException {
    File dir = new File(tempDir.getRoot(), name);
    dir.mkdir();
    return dir;
  }

  private static final String FIRST_NAME = "foo";
  private static final String SECOND_NAME = "bar";
}
