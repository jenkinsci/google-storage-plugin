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

import com.google.api.services.storage.model.Bucket;

/**
 * This exception is intended to be thrown by implementations of the hook {@link
 * AbstractBucketLifecycleManager#checkBucket} when a bucket is not properly annotated.
 */
public class InvalidAnnotationException extends Exception {
    /**
     * Constructor for the exception.
     *
     * @param bucket Name of the GCS bucket.
     */
    public InvalidAnnotationException(Bucket bucket) {
        this.bucket = checkNotNull(bucket);
    }

    /**
     * @return The bucket that isn't properly annotated.
     */
    public Bucket getBucket() {
        return this.bucket;
    }

    private final Bucket bucket;
}
