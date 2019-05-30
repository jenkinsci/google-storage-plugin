package com.google.jenkins.plugins.storage;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.EnvVars;
import java.util.logging.Logger;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

public class ClassicUploadStepPipelineIT {
  private static final Logger LOGGER =
      Logger.getLogger(ClassicUploadStepPipelineIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static EnvVars envVars;
  private static String projectId;
  private static String credentialsId;

  @BeforeClass
  public static void init() throws Exception {
    LOGGER.info("Initializing KubernetesEngineBuilderPipelineIT");

    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

    LOGGER.info("Creating credentials");
    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);
    credentialsId = projectId;
    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(credentialsId, sac, null);
    CredentialsStore store =
        new SystemCredentialsProvider.ProviderImpl().getStore(jenkinsRule.jenkins);
    store.addCredentials(Domain.global(), c);
  }
}
