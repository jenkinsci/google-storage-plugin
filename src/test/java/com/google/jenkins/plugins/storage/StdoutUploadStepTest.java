package com.google.jenkins.plugins.storage;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.jenkins.plugins.credentials.oauth.AbstractGoogleRobotCredentialsDescriptor;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.MockExecutor;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link StdoutUploadStep} */
public class StdoutUploadStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Mock private GoogleRobotCredentials credentials;

  private GoogleCredential credential;
  @Mock private AbstractGoogleRobotCredentialsDescriptor descriptor;

  private final MockExecutor executor = new MockExecutor();

  private NotFoundException notFoundException = new NotFoundException();

  private static final String PROJECT_ID = "foo.com:project-build";
  private static final String CREDENTIALS_ID = "creds";

  private static final String BUCKET_NAME = "test-bucket-43";
  private static final String BUCKET_URI = "gs://" + BUCKET_NAME;
  private static final String LOG_NAME = "build-log.txt";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(descriptor.getDisplayName()).thenReturn("Credentials Name");

    when(credentials.getId()).thenReturn(CREDENTIALS_ID);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
    when(credentials.getDescriptor()).thenReturn(descriptor);

    if (jenkins.jenkins != null) {
      SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
    }

    credential = new GoogleCredential();
    when(credentials.getGoogleCredential(isA(GoogleOAuth2ScopeRequirement.class)))
        .thenReturn(credential);
    when(credentials.forRemote(isA(GoogleOAuth2ScopeRequirement.class))).thenReturn(credentials);
  }

  private void ConfigurationRoundTripTest(StdoutUploadStep s) throws Exception {
    StdoutUploadStep after = jenkins.configRoundtrip(s);
    jenkins.assertEqualBeans(s, after, "bucket,logName,pathPrefix,credentialsId");
  }

  @Test
  public void testRoundtrip() throws Exception {
    StdoutUploadStep step = new StdoutUploadStep(CREDENTIALS_ID, "bucket", "logName");

    ConfigurationRoundTripTest(step);

    step.setPathPrefix("prefix");
    ConfigurationRoundTripTest(step);

    step.setSharedPublicly(true);
    ConfigurationRoundTripTest(step);

    step.setShowInline(true);
    ConfigurationRoundTripTest(step);
  }

  @Test
  public void testBuild() throws Exception {
    StdoutUploadStep step =
        new StdoutUploadStep(CREDENTIALS_ID, BUCKET_URI, new MockUploadModule(executor), LOG_NAME);

    FreeStyleProject project = jenkins.createFreeStyleProject("testBuild");

    Bucket bucket = new Bucket();
    bucket.setName(BUCKET_NAME);

    // Perform is run twice: one from scheduleBuild2, one from step.perform
    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(LOG_NAME));

    executor.throwWhen(Storage.Buckets.Get.class, notFoundException);
    executor.passThruWhen(
        Storage.Buckets.Insert.class, MockUploadModule.checkBucketName(BUCKET_NAME));
    executor.passThruWhen(Storage.Objects.Insert.class, MockUploadModule.checkObjectName(LOG_NAME));

    project.getBuildersList().add(step);

    FreeStyleBuild build = project.scheduleBuild2(0).get();

    step.perform(
        build,
        build.getWorkspace(),
        build.getWorkspace().createLauncher(TaskListener.NULL),
        TaskListener.NULL);
  }
}
