/*
 * Copyright 2013 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.storage;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.jenkins.plugins.util.Resolve;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/** Descriptor from which Upload extensions must derive their descriptor. */
public abstract class AbstractUploadDescriptor extends Descriptor<AbstractUpload> {
  // The URI "scheme" that prefixes GCS URIs
  public static final String GCS_SCHEME = "gs://";
  private final UploadModule module;

  /**
   * Create the descriptor of the Upload from it's type on associated module for instantiating
   * dependencies.
   *
   * @param clazz Class that extends {@link AbstractUpload}.
   * @param module Helper class methods to use for execution.
   */
  protected AbstractUploadDescriptor(Class<? extends AbstractUpload> clazz, UploadModule module) {
    super(checkNotNull(clazz));
    this.module = module;
  }

  /**
   * Create the descriptor of the Upload from it's type of {@link AbstractUpload}.
   *
   * @param clazz Class that extends {@link AbstractUpload}.
   */
  protected AbstractUploadDescriptor(Class<? extends AbstractUpload> clazz) {
    this(checkNotNull(clazz), new UploadModule());
  }

  /**
   * @return Retrieve the module to use for instantiating dependencies for instances described by
   *     this descriptor.
   */
  public synchronized UploadModule getModule() {
    if (this.module == null) {
      return new UploadModule();
    }
    return this.module;
  }

  /**
   * This callback validates the {@code bucketNameWithVars} input field's values.
   *
   * @param bucketNameWithVars GCS bucket.
   * @return Valid form validation result or error message if invalid.
   */
  public static FormValidation staticDoCheckBucket(final String bucketNameWithVars) {
    String resolvedInput = Resolve.resolveBuiltin(bucketNameWithVars);
    if (!resolvedInput.startsWith(GCS_SCHEME)) {
      return FormValidation.error(
          Messages.AbstractUploadDescriptor_BadPrefix(resolvedInput, GCS_SCHEME));
    }
    // Lop off the prefix.
    resolvedInput = resolvedInput.substring(GCS_SCHEME.length());

    if (resolvedInput.isEmpty()) {
      return FormValidation.error(Messages.AbstractUploadDescriptor_EmptyBucketName());
    }
    if (resolvedInput.contains("$")) {
      // resolved bucket name still contains variable markers
      return FormValidation.error(
          Messages.AbstractUploadDescriptor_BadBucketChar(
              "$", Messages.AbstractUploadDescriptor_DollarSuggest()));
    }
    // TODO(mattmoor): This has a much more constrained form than the
    // glob, since it:
    //  - No wildcards
    //  - Must be unix
    //  - Must be relative path
    // TODO(mattmoor): See if we can get a validity pattern used in the
    // Cloud Console.
    // TODO(mattmoor): Check availability or ownership of the bucket
    return FormValidation.ok();
  }

  /**
   * This callback validates the {@code bucketNameWithVars} input field's values.
   *
   * @param bucketNameWithVars GCS bucket.
   * @return Valid form validation result or error message if invalid.
   * @throws IOException If there was an issue validating the bucket.
   */
  public FormValidation doCheckBucketNameWithVars(@QueryParameter final String bucketNameWithVars)
      throws IOException {
    return staticDoCheckBucket(bucketNameWithVars);
  }

  /**
   * Form validation for bucket parameter.
   *
   * @param bucket GCS bucket.
   * @return Valid form validation result or error message if invalid.
   * @throws IOException If there was an issue validating the bucket.
   */
  public FormValidation doCheckBucket(@QueryParameter final String bucket) throws IOException {
    return staticDoCheckBucket(bucket);
  }

  /** {@inheritDoc} */
  @Override
  public AbstractUpload newInstance(StaplerRequest req, JSONObject formData) throws FormException {
    // Since the config form lists the optional parameter pathPrefix as inline,
    // it will be passed through even if stripPathPrefix is false. This might
    // cause problems if the user, for example, fills in the field and then
    // unchecks the checkbox. So, explicitly remove pathPrefix whenever
    // stripPathPrefix is false.
    if (Boolean.FALSE.equals(formData.remove("stripPathPrefix"))) {
      formData.remove("pathPrefix");
    }
    return super.newInstance(req, formData);
  }
}
