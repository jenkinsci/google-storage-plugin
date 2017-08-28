/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.ByteStreams.copy;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.jenkins.plugins.util.Resolve;

import hudson.Extension;
import hudson.FilePath;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.Util;
import hudson.util.FormValidation;

/**
 * This upload extension allow the user to upload the build log
 * for the Jenkins build to a given bucket, with a specified file
 * name.  By default, the file is named "build-log.txt".
 */
public class StdoutUpload extends AbstractUpload {
  /**
   * Construct the Upload with the stock properties, and the additional
   * information about how to name the build log file.
   */
  @DataBoundConstructor
  public StdoutUpload(@Nullable String bucket, boolean sharedPublicly,
      boolean forFailedJobs, boolean showInline, boolean stripPathPrefix,
      @Nullable String pathPrefix,
      @Nullable UploadModule module, String logName,
      // Legacy arguments for backwards compatibility
      @Deprecated @Nullable String bucketNameWithVars) {
    super(Objects.firstNonNull(bucket, bucketNameWithVars), sharedPublicly,
        forFailedJobs, showInline, stripPathPrefix ? pathPrefix : null, module);
    this.logName = checkNotNull(logName);
  }

  /**
   * The name to give the file we upload for the build log.
   */
  public String getLogName() {
    return logName;
  }
  private final String logName;

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDetails() {
    return Messages.StdoutUpload_DetailsMessage(getLogName());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean forResult(Result result) {
    if (result == null) {
      return true;
    }
    if (result == Result.NOT_BUILT) {
      return true;
    }
    return super.forResult(result);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  protected UploadSpec getInclusions(AbstractBuild<?, ?> build,
      FilePath workspace, TaskListener listener) throws UploadException {
    try {
      OutputStream outputStream = null;
      try {
        FilePath logDir = new FilePath(build.getLogFile()).getParent();

        String resolvedLogName =
            Util.replaceMacro(getLogName(), build.getEnvironment(listener));
        FilePath logFile = new FilePath(logDir, resolvedLogName);

        outputStream = new PlainTextConsoleOutputStream(logFile.write());
        copy(build.getLogInputStream(), outputStream);

        return new UploadSpec(logDir, ImmutableList.of(logFile));
      } finally {
        Closeables.close(outputStream, true /* swallowIOException */);
      }
    } catch (InterruptedException e) {
      throw new UploadException(Messages.AbstractUpload_IncludeException(), e);
    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_IncludeException(), e);
    }
  }

  /**
   * Denotes this is an {@link AbstractUpload} plugin
   */
  @Extension
  public static class DescriptorImpl extends AbstractUploadDescriptor {
    public DescriptorImpl() {
      this(StdoutUpload.class);
    }

    public DescriptorImpl(
      Class<? extends StdoutUpload> clazz) {
      super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.StdoutUpload_DisplayName();
    }

    /**
     * This callback validates the {@code logName} input field's values.
     */
    public FormValidation doCheckLogName(
        @QueryParameter final String logName) throws IOException {
      String resolvedInput = Resolve.resolveBuiltin(logName);
      if (resolvedInput.isEmpty()) {
        return FormValidation.error(
            Messages.StdoutUpload_LogNameRequired());
      }

      if (resolvedInput.contains("$")) {
        // resolved file name still contains variable marker
        return FormValidation.error(
            Messages.StdoutUpload_BadChar("$",
                Messages.AbstractUploadDescriptor_DollarSuggest()));
      }
      // TODO(mattmoor): Proper filename validation
      return FormValidation.ok();
    }
  }
}
