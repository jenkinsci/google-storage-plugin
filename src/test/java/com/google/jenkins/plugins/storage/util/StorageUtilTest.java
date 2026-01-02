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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.FilePath;
import java.io.File;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link StorageUtil}. */
class StorageUtilTest {
    private FilePath workspace;
    private FilePath nonWorkspace;

    @TempDir
    private File tempDir;

    @BeforeAll
    static void beforeAll() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
    }

    @BeforeEach
    void beforeEach() {
        workspace = new FilePath(makeTempDir("workspace"));
        nonWorkspace = new FilePath(makeTempDir("non-workspace"));
    }

    @Test
    void getRelativePositiveTest() throws Exception {
        FilePath one = workspace.child(FIRST_NAME);

        assertEquals(FIRST_NAME, StorageUtil.getRelative(one, workspace));

        FilePath second = one.child(SECOND_NAME);
        assertEquals(SECOND_NAME, StorageUtil.getRelative(second, one));
        assertEquals(FIRST_NAME + '/' + SECOND_NAME, StorageUtil.getRelative(second, workspace));
    }

    @Test
    void getRelativeNegativeTest() throws Exception {
        FilePath one = workspace.child(FIRST_NAME);

        assertEquals(workspace.getRemote(), "/" + StorageUtil.getRelative(workspace, one));
        assertEquals(nonWorkspace.getRemote(), "/" + StorageUtil.getRelative(nonWorkspace, workspace));
    }

    private File makeTempDir(String name) {
        File dir = new File(tempDir, name);
        dir.mkdir();
        return dir;
    }

    private static final String FIRST_NAME = "foo";
    private static final String SECOND_NAME = "bar";
}
