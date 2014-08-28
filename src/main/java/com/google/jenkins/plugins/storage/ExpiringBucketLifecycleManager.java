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

import static java.util.logging.Level.WARNING;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.api.services.storage.model.Bucket;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import hudson.Extension;

/**
 * A simple implementation of the bucket lifecycle extension point
 * that surfaces object expiration (aka TTL).
 */
public class ExpiringBucketLifecycleManager
    extends AbstractBucketLifecycleManager {
  private static final Logger logger =
      Logger.getLogger(ExpiringBucketLifecycleManager.class.getName());

  /**
   * Construct the simple lifecycle manager from a TLL and the
   * common properties.
   */
  @DataBoundConstructor
  public ExpiringBucketLifecycleManager(String bucket,
      @Nullable UploadModule module, Integer ttl,
      // Legacy arguments for backwards compatibility
      @Deprecated @Nullable String bucketNameWithVars,
      @Deprecated @Nullable Integer bucketObjectTTL) {
    super(Objects.firstNonNull(bucket, bucketNameWithVars), module);

    this.bucketObjectTTL = Objects.firstNonNull(ttl, bucketObjectTTL);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDetails() {
    return Messages.ExpiringBucketLifecycleManager_DetailsMessage(
        getTtl());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Bucket checkBucket(Bucket bucket)
      throws InvalidAnnotationException {
    Bucket.Lifecycle lifecycle = bucket.getLifecycle();
    if (lifecycle == null) {
      throw new InvalidAnnotationException(bucket);
    }

    if (lifecycle.getRule().size() != 1) {
      // TODO(mattmoor): Consider allowing this plugin to augment, rather
      // than replace existing lifecycle rules.
      if (lifecycle.getRule().size() > 1) {
        logger.log(WARNING, "Found complex lifecycle rule on: " +
            bucket.getName());
      }
      throw new InvalidAnnotationException(bucket);
    }

    for (Bucket.Lifecycle.Rule rule : lifecycle.getRule()) {
      if (!rule.getAction().getType().equalsIgnoreCase(DELETE)) {
        continue;
      }
      Bucket.Lifecycle.Rule.Condition condition = rule.getCondition();
      if (condition.size() != 1) {
        continue;
      }
      Integer age = condition.getAge();
      if (age == null) {
        continue;
      }
      if (age != getTtl()) {
        continue;
      }

      return bucket;
    }
    logger.log(WARNING, "Mismatched lifecycle rule on: " +
        bucket.getName());
    throw new InvalidAnnotationException(bucket);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Bucket decorateBucket(Bucket bucket) {
    Bucket.Lifecycle.Rule rule = new Bucket.Lifecycle.Rule()
        .setCondition(new Bucket.Lifecycle.Rule.Condition()
            .setAge(getTtl())) // age is in days
        .setAction(new Bucket.Lifecycle.Rule.Action()
            .setType(DELETE));

    List<Bucket.Lifecycle.Rule> rules = Lists.newArrayList();

    // TODO(mattmoor): Consider allowing this plugin to augment, rather
    // than replace existing lifecycle rules.
    // NOTE: This would require us to ~filter out incompatible clauses.
    //
    // Bucket.Lifecycle lifecycle = bucket.getLifecycle();
    // if (lifecycle != null) {
    //   if (lifecycle.getRule() != null) {
    //     rules.addAll(lifecycle.getRule());
    //   }
    // }

    // Add our new rule
    rules.add(rule);

    // Put the newly composed set of rules into the lifecycle of the bucket
    bucket.setLifecycle(new Bucket.Lifecycle().setRule(rules));

    return bucket;
  }

  /**
   * Surface the TTL for objects contained within the bucket for roundtripping
   * to the jelly UI.
   */
  public int getTtl() {
    return bucketObjectTTL;
  }
  /** NOTE: old name kept for deserialization */
  private final int bucketObjectTTL;

  private static final String DELETE = "Delete";

  /**
   * Denotes this is an {@link AbstractUpload} plugin
   */
  @Extension
  public static class DescriptorImpl
      extends AbstractBucketLifecycleManagerDescriptor {
    public DescriptorImpl() {
      this(ExpiringBucketLifecycleManager.class);
    }

    public DescriptorImpl(
      Class<? extends ExpiringBucketLifecycleManager> clazz) {
      super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.ExpiringBucketLifecycleManager_DisplayName();
    }
  }
}
