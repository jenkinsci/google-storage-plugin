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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Actionable;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test for {@link AbstractGcsUploadReport}. */
@ExtendWith(MockitoExtension.class)
class AbstractGcsUploadReportTest {

    @Mock
    private Actionable parent;

    private AbstractGcsUploadReport underTest;

    @BeforeEach
    void beforeEach() {
        underTest = new AbstractGcsUploadReport(parent) {
            @Override
            public Set<String> getStorageObjects() {
                return null;
            }

            @Override
            public Integer getBuildNumber() {
                return null;
            }

            @Override
            public Set<String> getBuckets() {
                return null;
            }
        };
    }

    @Test
    void getters() {
        assertEquals(parent, underTest.getParent());
        assertEquals(Messages.AbstractGcsUploadReport_DisplayName(), underTest.getDisplayName());
        assertNotNull(underTest.getIconFileName());
        assertNotNull(underTest.getUrlName());
    }
}
