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

package com.google.jenkins.plugins.storage.IT;

import static com.google.jenkins.plugins.storage.IT.ITUtil.dumpLog;
import static com.google.jenkins.plugins.storage.IT.ITUtil.formatRandomName;
import static com.google.jenkins.plugins.storage.IT.ITUtil.getCredentialsId;
import static com.google.jenkins.plugins.storage.IT.ITUtil.initializePipelineITEnvironment;
import static com.google.jenkins.plugins.storage.IT.ITUtil.loadResource;
import static org.junit.Assert.assertNotNull;

import com.google.jenkins.plugins.storage.client.ClientFactory;
import com.google.jenkins.plugins.storage.client.StorageClient;
import hudson.EnvVars;
import hudson.model.Result;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** Tests the {@link StdoutUploadStep} for use-cases involving the Jenkins Pipeline DSL. */
public class StdoutUploadStepPipelineIT {
  private static final Logger LOGGER = Logger.getLogger(StdoutUploadStepPipelineIT.class.getName());

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  public static String credentialsId;
  private static final String pattern = "build_log.txt";
  private static String bucket;
  private static StorageClient storageClient;
  private static EnvVars envVars;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing StdoutUploadStepPipelineIT");

    envVars = initializePipelineITEnvironment(pattern, jenkinsRule);
    credentialsId = getCredentialsId();
    storageClient = new ClientFactory(jenkinsRule.jenkins, credentialsId).storageClient();
    bucket = formatRandomName("test");
    envVars.put("BUCKET", bucket);
  }

  @Test
  public void testStdoutUploadStepSuccessful() throws Exception {
    WorkflowJob testProject =
        jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

    testProject.setDefinition(
        new CpsFlowDefinition(loadResource(getClass(), "stdoutUploadStepPipeline.groovy"), true));
    WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
    assertNotNull(run);
    jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
    dumpLog(LOGGER, run);
    storageClient.deleteFromBucket(bucket, pattern);
  }

  @Test
  public void testMalformedStdoutUploadStepFailure() throws Exception {
    WorkflowJob testProject =
        jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

    testProject.setDefinition(
        new CpsFlowDefinition(
            loadResource(getClass(), "malformedStdoutUploadStepPipeline.groovy"), true));
    WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
    assertNotNull(run);
    jenkinsRule.assertBuildStatus(Result.FAILURE, jenkinsRule.waitForCompletion(run));
    dumpLog(LOGGER, run);
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    storageClient.deleteBucket(bucket);
  }
}
