/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.util.BucketPath;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation.Operation;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation.RepeatOperation;
import com.google.jenkins.plugins.storage.util.StorageUtil;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

/**
 * A step to allow Download from Google Cloud Storage as a Build step and in
 * pipeline.
 */
@RequiresDomain(value = StorageScopeRequirement.class)
public class DownloadStep extends Builder implements SimpleBuildStep,
    Serializable {

  private static final Logger logger =
      Logger.getLogger(DownloadStep.class.getName());

  /**
   * Construct the download step.
   */
  @DataBoundConstructor
  public DownloadStep(String credentialsId, String bucketUri,
      String localDirectory) {
    this(credentialsId, bucketUri, localDirectory, null);
  }

  public DownloadStep(String credentialsId, String bucketUri,
      String localDirectory, @Nullable UploadModule module) {
    if (module != null) {
      this.module = module;
    } else {
      this.module = getDescriptor().getModule();
    }

    this.bucketUri = bucketUri;
    this.credentialsId = credentialsId;
    this.localDirectory = localDirectory;
  }

  /**
   * The bucket uri specified by the user, which potentially contains
   * unresolved symbols, such as $JOB_NAME and $BUILD_NUMBER.
   */
  public String getBucketUri() {
    return bucketUri;
  }

  private final String bucketUri;

  /**
   * The local directory in the Jenkins workspace that will receive the files.
   * This might contain unresolved symbols, such as $JOB_NAME and
   * $BUILD_NUMBER.
   */
  public String getLocalDirectory() {
    return localDirectory;
  }

  private final String localDirectory;

  /**
   * The path prefix that will be stripped from downloaded files. May be null if
   * no path prefix needs to be stripped.
   *
   * Filenames that do not start with this prefix will not be modified. Trailing
   * slash is automatically added if it is missing.
   */
  @DataBoundSetter
  public void setPathPrefix(@Nullable String pathPrefix) {
    if (pathPrefix != null && !pathPrefix.endsWith("/")) {
      pathPrefix += "/";
    }
    this.pathPrefix = pathPrefix;
  }

  @Nullable
  public String getPathPrefix() {
    return pathPrefix;
  }

  private String pathPrefix;

  /**
   * The module to use for providing dependencies.
   */
  protected final UploadModule module;

  /**
   * The unique ID for the credentials we are using to
   * authenticate with GCS.
   */
  public String getCredentialsId() {
    return credentialsId;
  }

  private final String credentialsId;

  /**
   * The credentials we are using to authenticate with GCS.
   */
  public GoogleRobotCredentials getCredentials() {
    return GoogleRobotCredentials.getById(getCredentialsId());
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace,
      @Nonnull Launcher launcher, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    try {
      String version = module.getVersion();
      String path = StorageUtil.replaceMacro(getBucketUri(), run, listener);
      BucketPath bucketPath = new BucketPath(path);
      if (bucketPath.error()) {
        throw new IOException("Invalid bucket path: " + getBucketUri());
      }

      String dirName = StorageUtil
          .replaceMacro(getLocalDirectory(), run, listener);
      FilePath dirPath = workspace.child(dirName);

      List<StorageObject> objects = resolveBucketPath(bucketPath,
          getCredentials(), version);

      logger.info("Downloading " + objects.size() + " objects.");

      DownloadSpec spec = new DownloadSpec(dirPath, objects);
      // TODO(agoulti): add a download report.

      String resolvedPrefix = StorageUtil
          .replaceMacro(pathPrefix, run, listener);

      initiateDownloadsAtWorkspace(getCredentials(), run, spec, dirPath,
          version, resolvedPrefix);
    } catch (ExecutorException e) {
      throw new IOException(Messages.Download_DownloadException(), e);
    }
  }

  private void performDownloadWithRetry(final Executor executor,
      final Storage service,
      final StorageObject obj, final FilePath localName,
      final UploadModule module)
      throws IOException, InterruptedException, ExecutorException {
    Operation a = new Operation() {
      public void act()
          throws IOException, InterruptedException, ExecutorException {
        logger.info("Downloading " + obj.getName() + " to " + localName);
        Storage.Objects.Get getObject = service.objects()
            .get(obj.getBucket(), obj.getName());
        MediaHttpDownloader downloader = getObject.getMediaHttpDownloader();
        if (downloader != null) {
          downloader.setDirectDownloadEnabled(true);
        }

        InputStream is = module.executeMediaAsInputStream(getObject);
        localName.copyFrom(is);
      }
    };

    RetryStorageOperation
        .performRequestWithRetry(executor, a, module.getInsertRetryCount());
  }

  private void performDownloads(final GoogleRobotCredentials credentials,
      final FilePath localDir, final DownloadSpec spec, final String version,
      final String resolvedPrefix) throws IOException {
    RepeatOperation<IOException> a = new RepeatOperation<IOException>() {
      private Queue<StorageObject> objects = new LinkedList<StorageObject>(
          spec.objects);
      Executor executor = module.newExecutor();

      Storage service;

      public void initCredentials() throws IOException {
        service = module.getStorageService(credentials, version);
      }

      public boolean moreWork() {
        return !objects.isEmpty();
      }

      public void act()
          throws IOException, InterruptedException, ExecutorException {
        StorageObject obj = objects.peek();

        String addPath = StorageUtil
            .getStrippedFilename(obj.getName(), resolvedPrefix);
        FilePath localName = localDir.withSuffix("/" + addPath);

        performDownloadWithRetry(executor, service, obj, localName, module);
        objects.remove();
      }
    };

    try {
      RetryStorageOperation.performRequestWithReinitCredentials(a);
    } catch (ExecutorException e) {
      throw new IOException(Messages.Download_DownloadException(), e);
    } catch (InterruptedException e) {
      throw new IOException(Messages.Download_DownloadException(), e);
    }
  }

  private void initiateDownloadsAtWorkspace(
      final GoogleRobotCredentials credentials,
      final Run run, final DownloadSpec spec, final FilePath localDir,
      final String version, final String resolvedPrefix)
      throws IOException, InterruptedException {
    try {
      // Use remotable credential to access the storage service from the
      // remote machine.
      final GoogleRobotCredentials remoteCredentials =
          checkNotNull(credentials).forRemote(module.getRequirement());

      localDir.act(
          new Callable<Void, IOException>() {
            @Override
            public Void call() throws IOException {
              performDownloads(remoteCredentials, localDir, spec, version,
                  resolvedPrefix);
              return (Void) null;
            }

            @Override
            public void checkRoles(RoleChecker checker)
                throws SecurityException {
              // We know by definition that this is the correct role;
              // the callable exists only in this method context.
            }
          });
    } catch (GeneralSecurityException e) {
      throw new IOException(
          Messages.AbstractUpload_RemoteCredentialError(), e);
    }
  }

  /**
   * DownloadSpec is a way to encode what needs to be downloaded.
   */
  protected static class DownloadSpec implements Serializable {

    public DownloadSpec(FilePath localDir, List<StorageObject> objects) {
      this.localDir = checkNotNull(localDir);
      this.objects = Collections.unmodifiableCollection(objects);
    }

    // The Jenkins directory that will receive the objects
    public final FilePath localDir;
    // Objects in the cloud that need to be downloaded
    public final Collection<StorageObject> objects;
  }

  /**
   * Take the bucket path and return a list of objects in the cloud that match
   * it.
   *
   * This will eventually handle wildcards, but for now is limited to directly
   * specifying the object.
   */
  private List<StorageObject> resolveBucketPath(BucketPath bucketPath,
      GoogleRobotCredentials credentials, String version)
      throws IOException, ExecutorException {
    Storage service = module.getStorageService(credentials, version);
    Executor executor = module.newExecutor();

    List<StorageObject> result = new ArrayList<StorageObject>();

    // TODO(agoulti): add handling of wildcards.
    Storage.Objects.Get obj = service.objects()
        .get(bucketPath.getBucket(), bucketPath.getObject());

    result.add(executor.execute(obj));

    return result;
  }

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) checkNotNull(Hudson.getInstance())
        .getDescriptor(getClass());
  }

  /**
   * Descriptor for the DownloadStep
   */
  @Extension
  @Symbol("googleStorageDownload")
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    private final UploadModule module;

    public UploadModule getModule() {
      return module;
    }

    public DescriptorImpl() {
      this.module = new UploadModule();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.Download_BuildStepDisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    public Builder newInstance(StaplerRequest req, JSONObject formData)
        throws FormException {
      if (Boolean.FALSE.equals(formData.remove("stripPathPrefix"))) {
        formData.remove("pathPrefix");
      }
      return super.newInstance(req, formData);
    }

    /**
     * This callback validates the {@code bucketNameWithVars} input field's
     * values.
     */
    public FormValidation doCheckBucketUri(
        @QueryParameter final String bucketUri)
        throws IOException {
      return ClassicUpload.DescriptorImpl.staticDoCheckBucket(bucketUri);
    }
  }
}
