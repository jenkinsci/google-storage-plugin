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

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.NotFoundException;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.model.Jenkins;

/**
 * This extension point may be implemented to surface the object lifecycle options available on
 * cloud storage buckets. Generally the expectation is that the UI will ask for the bucket, and
 * surface some additional UI for capturing the lifecycle features of the plugin.
 *
 * <p>This is done by implementing these two overrides:
 *
 * <ul>
 *   <li><code>checkBucket</code>: Validated the annotations on a pre-existing bucket, returning it
 *       if they are satisfactory, and throwing a InvalidAnnotationException if we must update it.
 *   <li><code>decorateBucket</code>: Annotates either a new or existing bucket with the lifecycle
 *       features of the plugin.
 * </ul>
 *
 * NOTE: This extends {@link AbstractUpload}, but isn't really an upload. You could reason about it
 * as an empty upload to a bucket with special bucket annotation properties.
 *
 * <p>TODO(mattmoor): We should factor out a common AbstractStorageOperation base class that this
 * and AbstractUpload can share. The current entrypoint is benign enough (see "perform").
 *
 * @see com.google.jenkins.plugins.storage.ExpiringBucketLifecycleManager
 */
public abstract class AbstractBucketLifecycleManager extends AbstractUpload {
    /**
     * Constructs the base bucket OLM plugin from the bucket name and module.
     *
     * @param bucket GCS Bucket in which to alter the time to live.
     * @param module Helper class methods to use for execution.
     */
    public AbstractBucketLifecycleManager(String bucket, @Nullable UploadModule module) {
        super(bucket, module);
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    protected final UploadSpec getInclusions(Run<?, ?> run, FilePath workspace, TaskListener listener)
            throws UploadException {
        // Return an empty list, we don't actually do any uploads.
        return new UploadSpec(workspace, ImmutableList.<FilePath>of());
    }

    /**
     * This overrides the core implementation to provide additional hooks for decorating storage
     * objects with lifecycle annotations.
     *
     * @param credentials The credentials with which to fetch/create the bucket
     * @param bucketName The top-level bucket name to ensure exists
     * @return an instance of the named bucket, created or retrieved.
     * @throws UploadException if any issues are encountered
     */
    @Override
    protected Bucket getOrCreateBucket(
            Storage service, GoogleRobotCredentials credentials, Executor executor, String bucketName)
            throws UploadException {
        try {
            try {
                try {
                    return checkBucket(executor.execute(
                            service.buckets().get(bucketName).setProjection("full"))); // to retrieve the bucket ACLs
                } catch (NotFoundException e) {
                    try {
                        // This is roughly the opposite of how the command-line sample does
                        // things.  We do things this way to optimize for the case where the
                        // bucket already exists.
                        Bucket bucket = new Bucket().setName(bucketName);

                        // Annotate the bucket with our lifecycle properties.
                        bucket = decorateBucket(bucket);

                        bucket = executor.execute(service.buckets()
                                .insert(credentials.getProjectId(), bucket)
                                .setProjection("full")); // to retrieve the bucket ACLs

                        return bucket;
                    } catch (ConflictException ex) {
                        // If we get back a "Conflict" response, it means that the bucket
                        // was inserted between when we first tried to get it and were able
                        // to successfully insert one.
                        // NOTE: This could be due to an initial insertion attempt
                        // succeeding but returning an exception, or a race with another
                        // service.
                        return checkBucket(executor.execute(service.buckets()
                                .get(bucketName)
                                .setProjection("full"))); // to retrieve the bucket ACLs
                    }
                }
            } catch (InvalidAnnotationException nae) {
                Bucket bucket = nae.getBucket();
                bucket = decorateBucket(bucket);

                // If it exists, but isn't annotated, then update it with the annotated
                // version.
                return executor.execute(
                        service.buckets().update(bucketName, bucket).setProjection("full"));
            }
        } catch (ExecutorException e) {
            throw new UploadException(Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
        } catch (IOException e) {
            throw new UploadException(Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
        }
    }

    /**
     * This is intended to be an identity function that throws when the input is not adequately
     * annotated.
     *
     * @param bucket the pre-existing bucket whose annotations to validate.
     * @throws InvalidAnnotationException if not annotated properly.
     * @return The bucket that was validated.
     */
    protected abstract Bucket checkBucket(Bucket bucket) throws InvalidAnnotationException;

    /**
     * A hook by which extensions may annotate a new or existing bucket.
     *
     * @param bucket The bucket to annotate and return.
     * @return The bucket to annotate and return.
     */
    protected abstract Bucket decorateBucket(Bucket bucket);

    /** {@inheritDoc} */
    public AbstractBucketLifecycleManagerDescriptor getDescriptor() {
        return (AbstractBucketLifecycleManagerDescriptor)
                checkNotNull(Jenkins.get()).getDescriptor(getClass());
    }
}
