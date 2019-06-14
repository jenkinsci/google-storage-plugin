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

import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import javax.annotation.Nullable;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/** Build Step wrapper for {@link StdoutUpload} to be run in pipelines. Run only in post step. */
@RequiresDomain(StorageScopeRequirement.class)
public class StdoutUploadStep extends Recorder implements SimpleBuildStep, Serializable {
  private StdoutUpload upload;
  private final String credentialsId;

  /**
   * Constructs a new {@link StdoutUploadStep}.
   *
   * @param credentialsId The credentials to utilize for authenticating with GCS.
   * @param bucket GCS bucket to upload build artifacts to.
   * @param logName Name of log file to store to GCS bucket.
   */
  @DataBoundConstructor
  public StdoutUploadStep(String credentialsId, String bucket, String logName) {
    this(credentialsId, bucket, Optional.ofNullable(null), logName);
  }

  /**
   * Construct the StdoutUpload uploader to use the provided credentials to upload build artifacts.
   *
   * @param credentialsId The credentials to utilize for authenticating with GCS.
   * @param bucket GCS bucket to upload build artifacts to.
   * @param module Helper class for connecting to the GCS API.
   * @param logName Name of log file to store to GCS bucket.
   */
  public StdoutUploadStep(
      String credentialsId, String bucket, Optional<UploadModule> module, String logName) {
    this.credentialsId = credentialsId;
    upload = new StdoutUpload(bucket, module.orElse(null), logName, null);
  }

  /**
   * @param sharedPublicly Whether to indicate in metadata that the file should be viewable inline
   *     in web browsers, rather than requiring it to be downloaded first.
   */
  @DataBoundSetter
  public void setSharedPublicly(boolean sharedPublicly) {
    upload.setSharedPublicly(sharedPublicly);
  }

  public boolean isSharedPublicly() {
    return upload.isSharedPublicly();
  }

  /**
   * @param showInline Whether to indicate in metadata that the file should be viewable inline in
   *     web browsers, rather than requiring it to be downloaded first.
   */
  @DataBoundSetter
  public void setShowInline(boolean showInline) {
    upload.setShowInline(showInline);
  }

  public boolean isShowInline() {
    return upload.isShowInline();
  }

  /**
   * @param pathPrefix The path prefix that will be stripped from uploaded files. May be null if no
   *     path prefix needs to be stripped. Filenames that do not start with this prefix will not be
   *     modified. Trailing slash is automatically added if it is missing.
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

  public String getCredentialsId() {
    return credentialsId;
  }

  /** {@inheritDoc} * */
  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  /** {@inheritDoc} * */
  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws IOException {
    try {
      upload.perform(getCredentialsId(), run, workspace, listener);
    } catch (UploadException e) {
      throw new IOException(Messages.StdoutUpload_FailToUpload(), e);
    }
  }

  /** Descriptor for {@link StdoutUploadStep} */
  @Extension
  @Symbol("googleStorageBuildLogUpload")
  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.StdoutUpload_BuildStepDisplayName();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    /** This callback validates the {@code bucket} input field's values. */
    public FormValidation doCheckBucket(@QueryParameter final String bucket) throws IOException {
      return StdoutUpload.DescriptorImpl.staticDoCheckBucket(bucket);
    }

    public static FormValidation doCheckLogName(@QueryParameter final String logName)
        throws IOException {
      return new StdoutUpload.DescriptorImpl().doCheckLogName(logName);
    }

    /** {@inheritDoc} */
    @Override
    public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
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
  }
}
