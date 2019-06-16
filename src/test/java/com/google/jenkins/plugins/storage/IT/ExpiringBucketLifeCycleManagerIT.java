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

public class ExpiringBucketLifeCycleManagerIT {
  private static final Logger LOGGER =
      Logger.getLogger(ClassicUploadStepPipelineIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static String credentialsId;
  // This IT does not need to make explicit use of pattern.
  private static final String pattern = "test";
  private static String bucket;
  private static StorageClient storageClient;
  private static EnvVars envVars;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing ExpiringBucketLifeCycleManagerIT");

    envVars = initializePipelineITEnvironment(pattern, jenkinsRule);
    credentialsId = getCredentialsId();
    storageClient = new ClientFactory(jenkinsRule.jenkins, credentialsId).storageClient();
    // Create new test bucket to change lifecycle of objects on.
    // TODO: every class has dummy bucket?
    bucket = formatRandomName("test");
    envVars.put("BUCKET", bucket);
  }

  @Test
  public void testClassicUploadStepSuccessful() throws Exception {
      WorkflowJob testProject =
          jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

      testProject.setDefinition(
          new CpsFlowDefinition(
              loadResource(getClass(), "expiringBucketLifecycleManagerStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
  }

  @Test
  public void testMalformedClassicUploadStepFailure() throws Exception {
    WorkflowJob testProject =
        jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

    testProject.setDefinition(
        new CpsFlowDefinition(
            loadResource(getClass(), "malformedExpiringBucketLifecycleManagerStepPipeline.groovy"),
            true));
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
