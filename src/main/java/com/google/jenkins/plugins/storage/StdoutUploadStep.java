package com.google.jenkins.plugins.storage;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build Step wrapper for StdoutUpload to be run in pipelines. Should ideally be run post build
 * instead of a regular post step.
 */
public class StdoutUploadStep extends Builder implements SimpleBuildStep, Serializable {

  @Nonnull private StdoutUpload upload;

  @DataBoundConstructor
  public StdoutUploadStep(String credentialsId, String bucket, String logName) {
    this(credentialsId, bucket, null, logName);
  }

  /**
   * Construct the classic upload step.
   *
   * @see ClassicUpload#ClassicUpload
   */
  public StdoutUploadStep(
      String credentialsId, String bucket, @Nullable UploadModule module, String logName) {
    this.credentialsId = credentialsId;
    upload = new StdoutUpload(bucket, module, logName, null);

    // Build steps will not be executed following a failed build.
    // Pipeline steps performed sequentually will not be executed
    //   following a failed step
    // If we ever get to execute this on a failed build, that must
    // have been done intentionally, e.g., using "post" with appropriate
    // flags. This should be allowed.
    upload.setForFailedJobs(true);
  }

  /** Whether to surface the file being uploaded to anyone with the link. */
  @DataBoundSetter
  public void setSharedPublicly(boolean sharedPublicly) {
    upload.setSharedPublicly(sharedPublicly);
  }

  public boolean isSharedPublicly() {
    return upload.isSharedPublicly();
  }

  /**
   * Whether to indicate in metadata that the file should be viewable inline in web browsers, rather
   * than requiring it to be downloaded first.
   */
  @DataBoundSetter
  public void setShowInline(boolean showInline) {
    upload.setShowInline(showInline);
  }

  public boolean isShowInline() {
    return upload.isShowInline();
  }

  /**
   * The path prefix that will be stripped from uploaded files. May be null if no path prefix needs
   * to be stripped.
   *
   * <p>Filenames that do not start with this prefix will not be modified. Trailing slash is
   * automatically added if it is missing.
   */
  @DataBoundSetter
  public void setPathPrefix(@Nullable String pathPrefix) {
    upload.setPathPrefix(pathPrefix);
  }

  @Nullable
  public String getPathPrefix() {
    return upload.getPathPrefix();
  }

  public String getLogName() {
    return upload.getLogName();
  }

  public String getBucket() {
    return upload.getBucket();
  }

  /** The unique ID for the credentials we are using to authenticate with GCS. */
  public String getCredentialsId() {
    return credentialsId;
  }

  private final String credentialsId;

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws IOException {
    // setForFailedJobs was set to true in the constructor. However,
    // some jobs might have been created before that bug fix.
    // For those, set this here as well.
    upload.setForFailedJobs(true);

    try {
      upload.perform(getCredentialsId(), run, workspace, listener);
    } catch (UploadException e) {
      throw new IOException("Could not perform upload", e);
    }
  }

  /** Descriptor for the class. */
  @Extension
  @Symbol("googleStorageUpload")
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      // TODO: don't hard code here
      return "Google Build Log Upload";
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
      // Since the config form lists the optional parameter pathPrefix as
      // inline, it will be passed through even if stripPathPrefix is false.
      // This might cause problems if the user, for example, fills in the field
      // and then unchecks the checkbox. So, explicitly remove pathPrefix
      // whenever stripPathPrefix is false.
      if (Boolean.FALSE.equals(formData.remove("stripPathPrefix"))) {
        formData.remove("pathPrefix");
      }
      return super.newInstance(req, formData);
    }

    /** This callback validates the {@code bucketNameWithVars} input field's values. */
    public FormValidation doCheckBucket(@QueryParameter final String bucket) throws IOException {
      return StdoutUpload.DescriptorImpl.staticDoCheckBucket(bucket);
    }

    public static FormValidation doCheckLogName(@QueryParameter final String logName)
        throws IOException {
      return new StdoutUpload.DescriptorImpl().doCheckLogName(logName);
    }
  }
}
