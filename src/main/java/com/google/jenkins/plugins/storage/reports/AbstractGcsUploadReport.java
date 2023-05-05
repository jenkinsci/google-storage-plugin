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

import static com.google.common.base.Preconditions.checkNotNull;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Action;
import hudson.model.Actionable;
import java.util.Set;

/**
 * Common functionalities for {@link BuildGcsUploadReport} and {@link ProjectGcsUploadReport}. See
 * {@link ProjectGcsUploadReport} for details on why we will need at least two different kind of
 * reports.
 *
 * @see ProjectGcsUploadReport
 */
public abstract class AbstractGcsUploadReport implements Action {

  /** @see #getParent(). */
  private Actionable parent;

  /**
   * @param parent the parent object of this action.
   * @see #getParent().
   */
  AbstractGcsUploadReport(Actionable parent) {
    this.parent = checkNotNull(parent);
  }

  /** {@inheritDoc} */
  @Override
  public String getIconFileName() {
    return "save.gif";
  }

  /** {@inheritDoc} */
  @Override
  public String getDisplayName() {
    return Messages.AbstractGcsUploadReport_DisplayName();
  }

  /** {@inheritDoc} */
  @Override
  public String getUrlName() {
    /* stapler will match this URL name to our action page */
    return "gcsObjects";
  }

  /**
   * @return the parent object of this action. For a build action, this is the containing build. For
   *     a project action, this is the containing project.
   */
  public Actionable getParent() {
    return parent;
  }

  /** @return the build number of this report. */
  @Nullable
  public abstract Integer getBuildNumber();

  /** @return the uploaded objects (qualified with bucket name). */
  public abstract Set<String> getStorageObjects();

  /** @return the buckets that were used as upload destinations. */
  public abstract Set<String> getBuckets();
}
