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

import java.io.IOException;

import org.kohsuke.stapler.QueryParameter;

import com.google.jenkins.plugins.util.Resolve;

import hudson.util.FormValidation;

/**
 * The descriptor for our new {@link AbstractBucketLifecycleManager}
 * extension point.
 */
public abstract class AbstractBucketLifecycleManagerDescriptor
    extends AbstractUploadDescriptor {
  public AbstractBucketLifecycleManagerDescriptor(
      Class<? extends AbstractBucketLifecycleManager> clazz) {
    super(clazz);
  }

  /**
   * This specialized override of the bucket name form validation
   * disallows multi-part storage prefixes (just the bucket name).
   */
  @Override
  public FormValidation doCheckBucketNameWithVars(
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

    if (resolvedInput.contains("/")) {
      return FormValidation.error(
          Messages.AbstractBucketLifecycleManagerDescriptor_MultiPartBucket(
              resolvedInput));
    }

    return super.doCheckBucketNameWithVars(bucketNameWithVars);
  }
}
