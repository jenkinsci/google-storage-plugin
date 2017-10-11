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
package com.google.jenkins.plugins.storage.reports;

import com.google.jenkins.plugins.storage.StorageUtil.BucketPath;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Iterables;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

/**
 * Unit test for {@link BuildGcsUploadReport}.
 */
public class BuildGcsUploadReportTest {

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  private AbstractProject<?, ?> project;
  private AbstractBuild<?, ?> build;
  private BuildGcsUploadReport underTest;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    project = jenkins.createFreeStyleProject();
    build = new FreeStyleBuild((FreeStyleProject) project);
    underTest = new BuildGcsUploadReport(build);
  }

  @Test
  public void getters() {
    assertEquals(build, underTest.getParent());
    assertEquals(build.getNumber(), underTest.getBuildNumber().intValue());
  }

  @Test
  public void addBucket() {
    assertEquals(0, underTest.getBuckets().size());
    underTest.addBucket("bucket");
    assertEquals("bucket", Iterables.getLast(underTest.getBuckets()));
  }

  @Test
  public void addUpload() throws Exception {
    String relativePath = "relative/path";
    assertEquals(0, underTest.getStorageObjects().size());
    underTest.addUpload(relativePath, new BucketPath("myBucket", "helloworld/18"));
    assertEquals("myBucket/helloworld/18/" + relativePath,
        Iterables.getLast(underTest.getStorageObjects()));
  }

  @Test
  public void of() {
    BuildGcsUploadReport report = BuildGcsUploadReport.of(build);
    assertNotNull(report);
  }

  @Test
  public void of_existing() {
    build.addAction(underTest);
    assertEquals(underTest, BuildGcsUploadReport.of(build));
  }

  @Test
  public void of_project_noLastBuild() {
    assertNull(BuildGcsUploadReport.of(project));
  }

  @Test
  public void of_project_hasLastBuild() throws InterruptedException,
      ExecutionException {
    project.scheduleBuild2(0).get();
    project.getLastBuild().addAction(underTest);
    assertEquals(underTest, BuildGcsUploadReport.of(project));
  }
}
