/*
 * Copyright 2019 Google LLC
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

package com.google.jenkins.plugins.storage.integration;

import com.google.api.client.http.InputStreamContent;
import com.google.jenkins.plugins.storage.DownloadStep;
import com.google.jenkins.plugins.storage.client.ClientFactory;
import com.google.jenkins.plugins.storage.client.StorageClient;
import hudson.EnvVars;
import hudson.model.Result;
import jenkins.util.VirtualFile;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.logging.Logger;

import static com.google.jenkins.plugins.storage.integration.ITUtil.*;
import static org.junit.Assert.*;

/** Tests the {@link DownloadStep} with multiples wildcards for use-cases involving the Jenkins Pipeline DSL. */
public class DownloadWildcardsStepPipelineIT {
  private static final Logger LOGGER = Logger.getLogger(DownloadWildcardsStepPipelineIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static String credentialsId;
  private static final String pattern = "folder/**";
  private static String bucket;
  private static StorageClient storageClient;
  private static EnvVars envVars;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing DownloadWildcardsStepPipelineIT");

    envVars = initializePipelineITEnvironment(pattern, jenkinsRule);
    credentialsId = getCredentialsId();
    storageClient = new ClientFactory(jenkinsRule.jenkins, credentialsId).storageClient();
    bucket = getBucket();

    // create files to download
    InputStream stream = DownloadWildcardsStepPipelineIT.class.getResourceAsStream("downloadstep_test.txt");
    String contentType = URLConnection.guessContentTypeFromStream(stream);
    InputStreamContent content = new InputStreamContent(contentType, stream);
    storageClient.uploadToBucket("folder/foo.txt", bucket, content);
  }

  @Test
  public void testDownloadStepSuccessful() throws Exception {
    String jobName = formatRandomName("test");
    envVars.put("DIR", jobName);
    WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, jobName);
    testProject.setDefinition(
        new CpsFlowDefinition(loadResource(getClass(), "downloadStepPipeline.groovy"), true));
    WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
    assertNotNull(run);
    jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
    dumpLog(LOGGER, run);

    VirtualFile archivedFile = run.getArtifactManager().root().child("folder/foo.txt");
    assertTrue(archivedFile.exists());
  }

  @Test
  public void testMalformedDownloadStepFailure() throws Exception {
    String jobName = formatRandomName("test");
    WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, jobName);
    envVars.put("DIR", jobName);
    testProject.setDefinition(
        new CpsFlowDefinition(
            loadResource(getClass(), "malformedDownloadStepPipeline.groovy"), true));
    WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
    assertNotNull(run);
    jenkinsRule.assertBuildStatus(Result.FAILURE, jenkinsRule.waitForCompletion(run));
    dumpLog(LOGGER, run);
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    storageClient.deleteFromBucket(bucket, pattern);
  }
}
