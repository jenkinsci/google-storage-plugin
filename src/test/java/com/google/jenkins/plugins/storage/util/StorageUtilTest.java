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
package com.google.jenkins.plugins.storage.util;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.WithoutJenkins;

import hudson.FilePath;

/**
 * Tests for {@link StorageUtil}.
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
