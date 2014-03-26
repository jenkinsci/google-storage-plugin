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

import java.io.IOException;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.google.jenkins.plugins.storage.StdoutUpload.DescriptorImpl;

import hudson.util.FormValidation;

/**
 * Unit test for {@link StdoutUpload} and friends.
 */
public class StdoutUploadTest {

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void doCheckLogNameTest() throws IOException {
    DescriptorImpl descriptor = new DescriptorImpl();

    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckLogName("asdf").kind);
    // Successfully resolved
    assertEquals(FormValidation.Kind.OK,
        descriptor.doCheckLogName("asdf$BUILD_NUMBER").kind);
    // UN-successfully resolved
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckLogName("$foo").kind);
    // Escaped $BUILD_NUMBER
    assertEquals(FormValidation.Kind.ERROR,
        descriptor.doCheckLogName("$$BUILD_NUMBER").kind);
  }
}
