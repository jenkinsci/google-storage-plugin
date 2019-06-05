package com.google.jenkins.plugins.storage.IT;

import static com.google.jenkins.plugins.storage.IT.ITUtil.deleteFromBucket;
import static com.google.jenkins.plugins.storage.IT.ITUtil.dumpLog;
import static com.google.jenkins.plugins.storage.IT.ITUtil.getService;
import static com.google.jenkins.plugins.storage.IT.ITUtil.loadResource;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import com.google.jenkins.plugins.storage.StringJsonServiceAccountConfig;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DownloadStepPipelineIT {
  private static final Logger LOGGER = Logger.getLogger(DownloadStepPipelineIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static EnvVars envVars;
  private static String projectId;
  private static String credentialsId;
  private static String bucket;
  private static String pattern = "downloadstep_test.txt";

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing DownloadStepPipelineIT");

    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);
    bucket = System.getenv("GOOGLE_BUCKET");
    assertNotNull("GOOGLE_BUCKET env var must be set", bucket);

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

    // create file to download
    Storage service = getService(jenkinsRule.jenkins.get(), credentialsId);

    InputStream stream = DownloadStepPipelineIT.class.getResourceAsStream(pattern);
    String contentType = URLConnection.guessContentTypeFromStream(stream);
    InputStreamContent content = new InputStreamContent(contentType, stream);
    Storage.Objects.Insert insert = service.objects().insert(bucket, null, content);
    insert.setName(pattern);
    insert.execute();
  }

  // test a working one
  @Test
  public void testDownloadStepSuccessful() throws Exception {
    try {
      WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, "test");
      // TODO: not use test name and use formatRandomName instead?
      testProject.setDefinition(
          new CpsFlowDefinition(loadResource(getClass(), "downloadStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void testMalformedDownloadStepFailure() throws Exception {
    try {
      WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, "test2");

      testProject.setDefinition(
          new CpsFlowDefinition(
              loadResource(getClass(), "malformedDownloadStepPipeline.groovy"), true));
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
