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

import static com.google.jenkins.plugins.storage.integration.ITUtil.dumpLog;
import static com.google.jenkins.plugins.storage.integration.ITUtil.formatRandomName;
import static com.google.jenkins.plugins.storage.integration.ITUtil.initializePipelineITEnvironment;
import static com.google.jenkins.plugins.storage.integration.ITUtil.loadResource;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.jenkins.plugins.storage.ClassicUploadStep;
import com.google.jenkins.plugins.storage.client.ClientFactory;
import com.google.jenkins.plugins.storage.client.StorageClient;
import hudson.EnvVars;
import hudson.model.Result;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Tests the {@link ClassicUploadStep} for use-cases involving the Jenkins Pipeline DSL. */
@WithJenkins
class ClassicUploadStepPipelineIT {
    private static final Logger LOGGER = Logger.getLogger(ClassicUploadStepPipelineIT.class.getName());

    private static JenkinsRule jenkinsRule;

    private static String credentialsId;
    private static final String pattern = "build_environment.txt";
    private static String bucket;
    private static StorageClient storageClient;
    private static EnvVars envVars;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) throws Exception {
        LOGGER.info("Initializing ClassicUploadStepPipelineIT");

        jenkinsRule = rule;
        envVars = initializePipelineITEnvironment(pattern, jenkinsRule);
        credentialsId = envVars.get("CREDENTIALS_ID");
        storageClient = new ClientFactory(jenkinsRule.jenkins, credentialsId).storageClient();
        bucket = formatRandomName("test");
        envVars.put("BUCKET", bucket);
    }

    @Test
    void testClassicUploadStepSuccessful() throws Exception {
        WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

        testProject.setDefinition(
                new CpsFlowDefinition(loadResource(getClass(), "classicUploadStepPipeline.groovy"), true));
        WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
        assertNotNull(run);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
        dumpLog(LOGGER, run);
        storageClient.deleteFromBucket(bucket, pattern);
    }

    @Test
    void testClassicUploadPostStepSuccessful() throws Exception {
        WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

        testProject.setDefinition(
                new CpsFlowDefinition(loadResource(getClass(), "classicUploadPostStepPipeline.groovy"), true));
        WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
        assertNotNull(run);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
        dumpLog(LOGGER, run);
        storageClient.deleteFromBucket(bucket, pattern);
    }

    @Test
    void testMalformedClassicUploadStepFailure() throws Exception {
        WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

        testProject.setDefinition(
                new CpsFlowDefinition(loadResource(getClass(), "malformedClassicUploadStepPipeline.groovy"), true));
        WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
        assertNotNull(run);
        jenkinsRule.assertBuildStatus(Result.FAILURE, jenkinsRule.waitForCompletion(run));
        dumpLog(LOGGER, run);
    }

    @AfterAll
    static void afterAll() throws Exception {
        storageClient.deleteBucket(bucket);
    }
}
