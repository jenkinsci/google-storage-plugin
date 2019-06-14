package com.google.jenkins.plugins.storage;

import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Build Step wrapper for {@link ExpiringBucketLifecycleManager} to be run in pipelines. Run only in
 * post step.
 */
@RequiresDomain(StorageScopeRequirement.class)
public class ExpiringBucketLifecycleManagerStep extends Recorder
    implements SimpleBuildStep, Serializable {
  private ExpiringBucketLifecycleManager upload;
  private final String credentialsId;

  /**
   * Constructs a new {@link ExpiringBucketLifecycleManagerStep}.
   *
   * @param credentialsId The credentials to utilize for authenticating with GCS.
   * @param bucket GCS Bucket in which to alter the time to live.
   * @param ttl The number of days after which to delete data stored in the GCS bucket.
   */
  @DataBoundConstructor
  public ExpiringBucketLifecycleManagerStep(String credentialsId, String bucket, Integer ttl) {
    this(credentialsId, bucket, Optional.ofNullable(null), ttl);
  }

  /**
   * Construct the ExpiringBucketLifecycleManager uploader to use the provided credentials to set
   * number of days after which to delete data stored in the specified GCS bucket.
   *
   * @param credentialsId The credentials to utilize for authenticating with GCS.
   * @param bucket GCS Bucket in which to alter the time to live.
   * @param module Helper class for connecting to the GCS API.
   * @param ttl The number of days after which to delete data stored in the GCS bucket.
   */
  public ExpiringBucketLifecycleManagerStep(
      String credentialsId, String bucket, Optional<UploadModule> module, Integer ttl) {
    this.credentialsId = credentialsId;
    upload = new ExpiringBucketLifecycleManager(bucket, module.orElse(null), ttl, null, null);
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  public String getBucket() {
    return upload.getBucket();
  }

  /**
   * @return Surface the TTL for objects contained within the bucket for roundtripping to the jelly
   *     UI.
   */
  public int getTtl() {
    return upload.getTtl();
  }

  /** {@inheritDoc} * */
  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws IOException {
    try {
      upload.perform(getCredentialsId(), run, workspace, listener);
    } catch (UploadException e) {
      // TODO: create new message
      throw new IOException(Messages.StdoutUpload_FailToUpload(), e);
    }
  }

  /** Descriptor for {@link ExpiringBucketLifecycleManagerStep} */
  @Extension
  @Symbol("googleStorageBucketLifecycle")
  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.ExpiringBucketLifecycleManager_BuildStepDisplayName();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    /** This callback validates the {@code bucket} input field's values. */
    public FormValidation doCheckBucket(@QueryParameter final String bucket) throws IOException {
      return ExpiringBucketLifecycleManager.DescriptorImpl.staticDoCheckBucket(bucket);
    }
  }
}
