package com.google.jenkins.plugins.storage.IT;

import static com.google.jenkins.plugins.storage.IT.ITUtil.deleteFromBucket;
import static com.google.jenkins.plugins.storage.IT.ITUtil.dumpLog;
import static com.google.jenkins.plugins.storage.IT.ITUtil.formatRandomName;
import static com.google.jenkins.plugins.storage.IT.ITUtil.loadResource;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import com.google.jenkins.plugins.storage.StringJsonServiceAccountConfig;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ClassicUploadStepPipelineIT {
  private static final Logger LOGGER =
      Logger.getLogger(ClassicUploadStepPipelineIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static EnvVars envVars;
  private static String projectId;
  private static String credentialsId;
  private static String bucket;
  private static String pattern;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing ClassicUploadStepPipelineIT");

    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);
    bucket = System.getenv("GOOGLE_BUCKET");
    assertNotNull("GOOGLE_BUCKET env var must be set", bucket);

    pattern = "build_environment.txt";

    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);
    credentialsId = projectId;
    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(credentialsId, sac, null);
    CredentialsStore store =
        new SystemCredentialsProvider.ProviderImpl().getStore(jenkinsRule.jenkins);
    store.addCredentials(Domain.global(), c);

    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    envVars = prop.getEnvVars();
    envVars.put("CREDENTIALS_ID", credentialsId);
    envVars.put("BUCKET", bucket);
    envVars.put("PATTERN", pattern);
    jenkinsRule.jenkins.getGlobalNodeProperties().add(prop);
  }

  @Test
  public void testClassicUploadStepSuccessful() throws Exception {
    try {
      WorkflowJob testProject =
          jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

      testProject.setDefinition(
          new CpsFlowDefinition(
              loadResource(getClass(), "classicUploadStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void testClassicUploadPostStepSuccessful() throws Exception {
    try {
      WorkflowJob testProject =
          jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

      testProject.setDefinition(
          new CpsFlowDefinition(
              loadResource(getClass(), "classicUploadPostStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void testMalformedClassicUploadStepFailure() throws Exception {
    try {
      WorkflowJob testProject =
          jenkinsRule.createProject(WorkflowJob.class, formatRandomName("test"));

      testProject.setDefinition(
          new CpsFlowDefinition(
              loadResource(getClass(), "malformedClassicUploadStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.FAILURE, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
    } catch (Exception e) {
      throw e;
    }
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    deleteFromBucket(jenkinsRule.jenkins.get(), credentialsId, bucket, pattern);
  }
}
