package com.google.jenkins.plugins.storage;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.io.ByteStreams;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
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
  private static String pattern;
  private static String localDir;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing ClassicUploadStepPipelineIT");

    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);
    bucket = System.getenv("GOOGLE_BUCKET");
    assertNotNull("GOOGLE_BUCKET env var must be set", bucket);
    pattern = System.getenv("GOOGLE_DOWNLOAD_PATTERN");
    assertNotNull("GOOGLE_DOWNLOAD_PATTERN env var must be set", pattern);

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
    //    envVars.put("PATTERN", pattern);
    jenkinsRule.jenkins.getGlobalNodeProperties().add(prop);

    // create file to download
  }

  // test a working one
  @Test
  public void testDownloadStepSuccessful() throws Exception {
    try {
      WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, "test");
      FilePath workspace = jenkinsRule.jenkins.getWorkspaceFor(testProject);
      LOGGER.info("workspace is " + workspace.readToString());
      FilePath tempFile = workspace.createTempFile("hello_integration", "txt");
      envVars.put("PATTERN", "hello_integration.txt");
      testProject.setDefinition(
          new CpsFlowDefinition(loadResource(getClass(), "downloadStepPipeline.groovy"), true));
      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
      assertNotNull(run);
      jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(run));
      dumpLog(LOGGER, run);
      workspace.deleteRecursive();
    } catch (Exception e) {
      throw e;
    }
  }

  //  @Test
  //  public void testMalformedDownloadStepFailure() throws Exception {
  //    try {
  //      WorkflowJob testProject = jenkinsRule.createProject(WorkflowJob.class, "test2");
  //
  //      testProject.setDefinition(
  //          new CpsFlowDefinition(
  //              loadResource(getClass(), "malformedDownloadStepPipeline.groovy"), true));
  //      WorkflowRun run = testProject.scheduleBuild2(0).waitForStart();
  //      assertNotNull(run);
  //      jenkinsRule.assertBuildStatus(Result.FAILURE, jenkinsRule.waitForCompletion(run));
  //      dumpLog(LOGGER, run);
  //    } catch (Exception e) {
  //      throw e;
  //    }
  //  }

  // TODO: do I need to remove from directory?

  /**
   * TODO: move this to ITUtil Loads the content of the specified resource.
   *
   * @param testClass The test class the resource is being loaded for.
   * @param name The name of the resource being loaded.
   * @return The contents of the loaded resource.
   * @throws java.io.IOException If an error occurred during loading.
   */
  static String loadResource(Class testClass, String name) throws IOException {
    return new String(ByteStreams.toByteArray(testClass.getResourceAsStream(name)));
  }

  /**
   * TODO: move to ITUtil Dumps the logs from the specified {@link hudson.model.Run}.
   *
   * @param logger The {@link java.util.logging.Logger} to be written to.
   * @param run The {@link hudson.model.Run} from which the logs will be read.
   * @throws java.io.IOException If an error occurred while dumping the logs.
   */
  static void dumpLog(Logger logger, Run<?, ?> run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());
    String line = null;
    while ((line = reader.readLine()) != null) {
      logger.info(line);
    }
  }
}
