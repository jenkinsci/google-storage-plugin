/*
 * Copyright 2013-2019 Google LLC
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
package com.google.jenkins.plugins.storage.util;

import java.io.Serializable;

import static com.google.jenkins.plugins.storage.AbstractUploadDescriptor.GCS_SCHEME;

import com.google.jenkins.plugins.storage.Messages;

/**
 * Handles cloud uris and their parsing.
 */
public class BucketPath implements Serializable {

  /**
   * Prepares a new BucketPath.
   *
   * @param uri path to the bucket object, of the form
   * "gs://bucket_name/path/to/object". May contain other characters (i.e., *),
   * no verification is done in this class.
   */
  public BucketPath(String uri)
      throws IllegalArgumentException {
    // Ensure the uri starts with "gs://"
    if (!uri.startsWith(GCS_SCHEME)) {
      throw new IllegalArgumentException(
          Messages.AbstractUploadDescriptor_BadPrefix(uri, GCS_SCHEME));
    }

    // Lop off the GCS_SCHEME prefix.
    uri = uri.substring(GCS_SCHEME.length());

    // Break things down to a compatible format:
    //   foo  /  bar / baz / blah.log
    //  ^---^   ^--------------------^
    //  bucket      storage-object
    //
    // TODO(mattmoor): Test objectPrefix on Windows, where '\' != '/'
    // Must we translate?  Can we require them to specify in unix-style
    // and still have things work?
    String[] halves = uri.split("/", 2);
    this.bucket = halves[0];
    this.object = (halves.length == 1) ? "" : halves[1];
  }

  /**
   * Initializes BucketPath directly, with no parsing or substitutions.
   */
  public BucketPath(String bucket, String object) {
    this.bucket = bucket;
    this.object = object;
  }

  public boolean error() {
    // The bucket cannot be empty under normal circumstances.
    return getBucket().length() <= 0;
  }

  /**
   * Regenerate the path (without gs:// prefix)
   */
  public String getPath() {
    return bucket + (object.isEmpty() ? "" : "/" + object);
  }


  /**
   * The Bucket portion of the URI
   */
  public String getBucket() {
    return bucket;
  }

  /**
   * The object portion of the URI
   */
  public String getObject() {
    return object;
  }

  private final String bucket;
  private final String object;
}