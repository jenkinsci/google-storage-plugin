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
package com.google.jenkins.plugins.storage.reports;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableSet;

import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

/**
 * Unit test for {@link ProjectGcsUploadReport}.
 */
public class ProjectGcsUploadReportTest {

  @Mock private FreeStyleProject project;
  @Mock private FreeStyleBuild build;
  @Mock private FreeStyleBuild noUploadBuild;
  @Mock private FreeStyleProject noUploadProject;
  @Mock private BuildGcsUploadReport buildUploadReport;
  @Mock 
  private DescribableList<Publisher, Descriptor<Publisher>> noUploadPublishers;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    // set up for a case where the last build did have some uploads.
    when(project.getLastBuild()).thenReturn(build);
    when(build.getAction(BuildGcsUploadReport.class)).thenReturn(
        buildUploadReport);
    // set up for a case where the last build didn't have any upload.
    when(noUploadBuild.getProject()).thenReturn(noUploadProject);
    when(noUploadProject.getLastBuild()).thenReturn(noUploadBuild);
    when(noUploadProject.getPublishersList()).thenReturn(noUploadPublishers);
  }

  @Test
  public void getters_noUpload() {
    /* in case there are no upload, test that empty lists are returned */
    ProjectGcsUploadReport underTest =
        new ProjectGcsUploadReport(noUploadProject);
    assertEquals(0, underTest.getBuckets().size());
    assertEquals(0, underTest.getStorageObjects().size());
  }

  @Test
  public void getters_hasUploads() {
    /* In case there are uploads, test that the project report delegates to
     * the report of the last build. */
    ProjectGcsUploadReport underTest = new ProjectGcsUploadReport(project);
    Set<String> buckets = ImmutableSet.of("bucket");
    Set<String> objects = ImmutableSet.of("object1", "object2");
    int buildNumber = 42;
    String projectId = "project Id";
    when(buildUploadReport.getBuckets()).thenReturn(buckets);
    when(buildUploadReport.getStorageObjects()).thenReturn(objects);
    when(buildUploadReport.getBuildNumber()).thenReturn(buildNumber);

    assertEquals(buckets, underTest.getBuckets());
    assertEquals(objects, underTest.getStorageObjects());
    assertEquals(buildNumber, underTest.getBuildNumber().intValue());
  }
}
