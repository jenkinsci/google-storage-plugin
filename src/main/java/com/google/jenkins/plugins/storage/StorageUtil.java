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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jenkins.plugins.storage.AbstractUploadDescriptor.GCS_SCHEME;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.storage.AbstractUpload.UploadSpec;
import com.google.jenkins.plugins.storage.reports.BuildGcsUploadReport;
import com.google.jenkins.plugins.util.Resolve;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import javax.annotation.Nullable;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A class to contain common utility operations
 */
public class StorageUtil {

  /**
   * Prefix the given log message with our module.
   */
  public static String prefix(String moduleName, String x) {
    return Messages.UploadModule_PrefixFormat(
        moduleName, x);
  }


  /**
   * Compute the relative path of the given file inclusion, relative to the
   * given workspace.  If the path is absolute, it returns the root-relative
   * path instead.
   *
   * @param include The file whose relative path we are computing
   * @param workspace The workspace containing the included file.
   * @return The unix-style relative path of file.
   * @throws UploadException when the input is malformed
   */
  public static String getRelative(FilePath include, FilePath workspace)
      throws UploadException {
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
   * If a path prefix to strip has been specified, and the input string
   * starts with that prefix, returns the portion of the input after that
   * prefix. Otherwise, returns the unmodified input.
   */
  protected static String getStrippedFilename(String filename, String pathPrefix) {
    if (pathPrefix != null && filename != null
        && filename.startsWith(pathPrefix)) {
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
   */
  public static String replaceMacro(String name, Run<?,?> run, TaskListener listener)
      throws InterruptedException, IOException {
    if(run instanceof AbstractBuild) {
      name = Util.replaceMacro(name, run.getEnvironment(listener));
    }
    return name;
  }

  public static class BucketPath implements Serializable {
    /**
     * Prepares a new BucketPath.
     * @param uri path to the bucket object, of the form "gs://bucket_name/path/to/object". May
     *            contain unresolved variables, which will get resolved for non-pipeline cases.
     *            May contain other characters (i.e., *), no verification is done in this class.
     * @param run The currect run, used for resolving variables
     * @param listener Task listener, used for resolving variables
     * @param moduleName Name of the throwing module, to be included in error messages.
     */
    public BucketPath(String uri, Run<?, ?> run, TaskListener listener, String moduleName) throws IOException, InterruptedException {
      this.original = uri;

      // Replace variables if needed
      uri = StorageUtil.replaceMacro(checkNotNull(uri), run, listener);

      // Ensure the uri starts with "gs://"
      if (!uri.startsWith(GCS_SCHEME)) {
        listener.error(prefix(moduleName,
            Messages.AbstractUploadDescriptor_BadPrefix(
                uri, GCS_SCHEME)));
        bucket = "";
        object = "";
        return;
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
     * Regenerate the path (without gs:// prefix) (will include all substitutions)
     */
    public String getPath() {
      return bucket + (object.isEmpty() ? "" : "/" + object);
    }

    /**
     * Initializes BucketPath directly, with no parsing or substitutions.
     */
    public BucketPath(String bucket, String object) {
      this.original = "";
      this.bucket = bucket;
      this.object = object;
    }
    public boolean error() {
      // The bucket cannot be empty under normal circumstances.
      return bucket.length() <= 0;
    }
    public final String original;
    public final String bucket;
    public final String object;
  }
}
