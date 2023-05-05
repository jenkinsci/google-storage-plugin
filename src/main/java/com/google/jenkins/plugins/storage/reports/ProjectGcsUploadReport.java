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

import com.google.common.collect.ImmutableSet;
import hudson.model.AbstractProject;
import java.util.Set;

/**
 * The model for contributing project actions aren't the same as build action. Instead of
 * calling @{code project.addAction(someProjectAction)} we will need to contribute through a
 * project's build steps. This is done by overriding {@link
 * hudson.tasks.BuildStep#getProjectAction(AbstractProject)}. When the project UI is rendered,
 * Jenkins will the overriden method to ask build steps to contribute their project actions.
 *
 * <p>Since the project UI is rendered infrequently, we can't just provide a static action for the
 * latest build. Instead, in this {@link ProjectGcsUploadReport} action we will need to dynamically
 * look for the latest build's {@link BuildGcsUploadReport} and return the values that such report
 * returns.
 */
public class ProjectGcsUploadReport extends AbstractGcsUploadReport {

  public ProjectGcsUploadReport(AbstractProject<?, ?> project) {
    super(project);
  }

  /**
   * @return the project that this {@link ProjectGcsUploadReport} belongs to.
   */
  public AbstractProject<?, ?> getProject() {
    return (AbstractProject<?, ?>) getParent();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getBuckets() {
    BuildGcsUploadReport links = BuildGcsUploadReport.of(getProject());
    return links == null ? ImmutableSet.<String>of() : links.getBuckets();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getStorageObjects() {
    BuildGcsUploadReport links = BuildGcsUploadReport.of(getProject());
    return links == null ? ImmutableSet.<String>of() : links.getStorageObjects();
  }

  /** {@inheritDoc} */
  @Override
  public Integer getBuildNumber() {
    BuildGcsUploadReport links = BuildGcsUploadReport.of(getProject());
    return links == null ? null : links.getBuildNumber();
  }
}
