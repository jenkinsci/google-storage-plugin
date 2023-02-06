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
package com.google.jenkins.plugins.storage.reports;

import com.google.api.client.util.Sets;
import com.google.jenkins.plugins.storage.util.BucketPath;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import java.util.Collections;
import java.util.Set;

/**
 * A build {@link hudson.model.Action} to surface direct links of objects uploaded through the
 * {@link com.google.jenkins.plugins.storage.StdoutUpload} Listener to the Jenkins UI.
 */
public class BuildGcsUploadReport extends AbstractGcsUploadReport {

  private final Set<String> buckets;
  private final Set<String> files;

  public BuildGcsUploadReport(Run<?, ?> run) {
    super(run);
    this.buckets = Sets.newHashSet();
    this.files = Sets.newHashSet();
  }

  /**
   * @param project a project to get {@link BuildGcsUploadReport} for.
   * @return the {@link BuildGcsUploadReport} of the last build, as returned by {@link #of(Run)}, or
   *     null of no build existed.
   */
  @Nullable
  public static BuildGcsUploadReport of(AbstractProject<?, ?> project) {
    // TODO(nghia) : Put more thoughts into whether we want getLastBuild()
    // or getLastSuccessfulBuild() here.
    //
    // Displaying the last build has the advantage that logs for failed builds
    // are also easily accessible through these links.
    //
    // On the other hand, last successful builds have a more complete set of
    // links.
    //
    // May we only display the last build _if_ the project has uploads for
    // failed build as well?
    AbstractBuild<?, ?> lastBuild = project.getLastBuild();
    return lastBuild == null ? null : BuildGcsUploadReport.of(lastBuild);
  }

  /**
   * @param run the run to get {@link BuildGcsUploadReport} for.
   * @return the existing {@link BuildGcsUploadReport} of a build. If none, create a new {@link
   *     BuildGcsUploadReport} and return.
   */
  public static synchronized BuildGcsUploadReport of(Run<?, ?> run) {
    BuildGcsUploadReport links = run.getAction(BuildGcsUploadReport.class);
    if (links != null) {
      return links;
    }
    links = new BuildGcsUploadReport(run);
    run.addAction(links);
    return links;
  }

  /** @param bucketName the name of the destination bucket. */
  public void addBucket(String bucketName) {
    buckets.add(bucketName);
  }

  /**
   * @param relativePath the relative path (to the workspace) of the uploaded file.
   * @param bucket the directory location in the cloud
   */
  public void addUpload(String relativePath, BucketPath bucket) {
    synchronized (files) {
      files.add(bucket.getPath() + "/" + relativePath);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getBuckets() {
    return Collections.unmodifiableSet(buckets);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getStorageObjects() {
    return Collections.unmodifiableSet(files);
  }

  /** {@inheritDoc} */
  @Override
  public Integer getBuildNumber() {
    return ((AbstractBuild<?, ?>) getParent()).getNumber();
  }
}
