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

import org.kohsuke.stapler.QueryParameter;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.jenkins.plugins.util.Resolve;

import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * Descriptor from which Upload extensions must derive their descriptor.
 */
public abstract class AbstractUploadDescriptor
    extends Descriptor<AbstractUpload> {
  /**
   * Create the descriptor of the Upload from it's type on associated module
   * for instantiating dependencies.
   */
  protected AbstractUploadDescriptor(
      Class<? extends AbstractUpload> clazz,
      UploadModule module) {
    super(checkNotNull(clazz));
    this.module = module;
  }

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  protected AbstractUploadDescriptor(
      Class<? extends AbstractUpload> clazz) {
    this(checkNotNull(clazz), new UploadModule());
  }

  /**
   * Retrieve the module to use for instantiating dependencies
   * for instances described by this descriptor.
   */
  public UploadModule getModule() {
    return module;
  }
  private final UploadModule module;

  /**
   * This callback validates the {@code bucketNameWithVars} input field's
   * values.
   */
  public static FormValidation staticDoCheckBucket(
      @QueryParameter final String bucketNameWithVars)
      throws IOException {
    String resolvedInput = Resolve.resolveBuiltin(bucketNameWithVars);
    if (!resolvedInput.startsWith(GCS_SCHEME)) {
      return FormValidation.error(
          Messages.AbstractUploadDescriptor_BadPrefix(
              resolvedInput, GCS_SCHEME));
    }
    // Lop off the prefix.
    resolvedInput = resolvedInput.substring(GCS_SCHEME.length());

    if (resolvedInput.isEmpty()) {
      return FormValidation.error(
          Messages.AbstractUploadDescriptor_EmptyBucketName());
    }
    if (resolvedInput.contains("$")) {
      // resolved bucket name still contains variable markers
      return FormValidation.error(
          Messages.AbstractUploadDescriptor_BadBucketChar("$",
              Messages.AbstractUploadDescriptor_DollarSuggest()));
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

  public FormValidation doCheckBucketNameWithVars(
      @QueryParameter final String bucketNameWithVars)
      throws IOException {
    return staticDoCheckBucket(bucketNameWithVars);
  }

  public FormValidation doCheckBucket(
      @QueryParameter final String bucket)
      throws IOException {
    return staticDoCheckBucket(bucket);
  }

  /**
   * The URI "scheme" that prefixes GCS URIs
   */
  public static final String GCS_SCHEME = "gs://";
}
