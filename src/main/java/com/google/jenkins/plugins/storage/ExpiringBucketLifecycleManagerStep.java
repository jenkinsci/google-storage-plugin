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
package com.google.jenkins.plugins.storage;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
    Preconditions.checkArgument(!Strings.isNullOrEmpty(credentialsId));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(bucket));
    Preconditions.checkNotNull(ttl);
    this.credentialsId = credentialsId;
    upload = new ExpiringBucketLifecycleManager(bucket, module.orElse(null), ttl, null, null);
  }

  /** @return The unique ID for the credentials we are using to authenticate with GCS. */
  public String getCredentialsId() {
    return credentialsId;
  }

  /** @return Name of our GCS bucket. */
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
      throw new IOException(Messages.ExpiringBucketLifecycleManager_FailToSetExpiration(), e);
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

    /**
     * This callback validates the {@code bucket} input field's values.
     *
     * @param bucket GCS Bucket in which to alter the time to live.
     * @return Valid form validation result or error message if invalid.
     * @throws IOException If there was an issue validating the bucket provided.
     */
    public FormValidation doCheckBucket(@QueryParameter final String bucket) throws IOException {
      return ExpiringBucketLifecycleManager.DescriptorImpl.staticDoCheckBucket(bucket);
    }
  }
}
