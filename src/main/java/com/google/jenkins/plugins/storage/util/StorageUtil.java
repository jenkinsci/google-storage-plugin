/*
 * Copyright 2017 Google LLC
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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.UploadException;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.LinkedList;

/** A class to contain common utility operations */
public class StorageUtil {

  /**
   * Compute the relative path of the given file inclusion, relative to the given workspace. If the
   * path is absolute, it returns the root-relative path instead.
   *
   * @param include The file whose relative path we are computing.
   * @param workspace The workspace containing the included file.
   * @return The unix-style relative path of file.
   * @throws UploadException when the input is malformed
   */
  public static String getRelative(FilePath include, FilePath workspace) throws UploadException {
    LinkedList<String> segments = new LinkedList<String>();
    while (!include.equals(workspace)) {
      segments.push(include.getName());
      include = include.getParent();
      if (Strings.isNullOrEmpty(include.getName())) {
        // When we reach "/" we're done either way.
        break;
      }
    }
    return Joiner.on("/").join(segments);
  }

  /**
   * If a path prefix to strip has been specified, and the input string starts with that prefix,
   * returns the portion of the input after that prefix. Otherwise, returns the unmodified input.
   *
   * @param filename Input string to strip.
   * @param pathPrefix Name of the path prefix to strip on.
   * @return Remaining portion of the input after prefix is stripped.
   */
  public static String getStrippedFilename(String filename, String pathPrefix) {
    if (pathPrefix != null && filename != null && filename.startsWith(pathPrefix)) {
      return filename.substring(pathPrefix.length());
    }
    return filename;
  }

  /**
   * Perform variable expansion for non-pipeline steps.
   *
   * @param name The name, potentially including with variables
   * @param run The current run, used to determine pipeline status and to get environment.
   * @param listener Task listener, used to get environment
   * @return The updated name, with variables resolved
   * @throws InterruptedException If getting the environment of the run throws an
   *     InterruptedException.
   * @throws IOException If getting the environment of the run throws an IOException.
   */
  public static String replaceMacro(String name, Run<?, ?> run, TaskListener listener)
      throws InterruptedException, IOException {
    if (run instanceof AbstractBuild) {
      name = Util.replaceMacro(name, run.getEnvironment(listener));
    }
    return name;
  }

  /**
   * Look up credentials by name.
   *
   * @param credentials The name of the credentials to look up.
   * @return The corresponding {@link GoogleRobotCredentials}.
   * @throws AbortException If credentials not found.
   */
  public static GoogleRobotCredentials lookupCredentials(String credentials) throws AbortException {
    GoogleRobotCredentials result = GoogleRobotCredentials.getById(credentials);
    if (result == null) {
      throw new AbortException("Unknown credentials: " + credentials);
    }
    return result;
  }
}
