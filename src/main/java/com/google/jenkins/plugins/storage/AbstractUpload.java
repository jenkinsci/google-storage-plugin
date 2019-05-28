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

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ObjectAccessControl;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.metadata.MetadataContainer;
import com.google.jenkins.plugins.storage.reports.BuildGcsUploadReport;
import com.google.jenkins.plugins.storage.util.BucketPath;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation.Operation;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation.RepeatOperation;
import com.google.jenkins.plugins.storage.util.StorageUtil;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.NotFoundException;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * This new extension point is used for surfacing different kinds of Google Cloud Storage (GCS)
 * uploads. The most obvious implementations are provided as:
 *
 * @see ClassicUpload
 * @see StdoutUpload
 *     <p>We provide the following hooks for implementations to inject additional functionality:
 *     <ul>
 *       <li>Required {@link #getDetails}: provides detail information for the GCS upload report.
 *       <li>Required {@link #getInclusions}: surfaces the set of {@link UploadSpec} for the base
 *           class to upload to GCS.
 *       <li>Optional {@link #forResult}: determines the build states for which uploading should be
 *           performed.
 *       <li>Optional {@link #getMetadata}: allows the implementation to surface additional metadata
 *           on the storage object
 *       <li>Optional {@link #annotateObject}: allows the implementation to ~arbitrarily rewrite
 *           parts of the object prior to insertion.
 *     </ul>
 */
public abstract class AbstractUpload
    implements Describable<AbstractUpload>, ExtensionPoint, Serializable {

  private static final Logger logger = Logger.getLogger(AbstractUpload.class.getName());
  private static final ImmutableMap<String, String> CONTENT_TYPES =
      ImmutableMap.of(
          "css", "text/css",
          "js", "application/javascript",
          "svg", "image/svg+xml",
          "woff2", "font/woff2");

  /**
   * Construct the base upload from a handful of universal properties.
   *
   * @param bucket The unresolved name of the storage bucket within which to store the resulting
   *     objects.
   * @param module An {@link UploadModule} to use for execution.
   */
  public AbstractUpload(String bucket, @Nullable UploadModule module) {
    if (module != null) {
      this.module = module;
    } else {
      this.module = getDescriptor().getModule();
    }
    this.bucketNameWithVars = checkNotNull(bucket);
  }

  /** Allow old signature for compatibility. */
  public final void perform(String credentialsId, AbstractBuild<?, ?> build, TaskListener listener)
      throws UploadException, IOException {
    perform(credentialsId, build, build.getWorkspace(), listener);
  }

  /**
   * The main action entrypoint of this extension. This uploads the contents included by the
   * implementation to our resolved storage bucket.
   */
  public final void perform(
      String credentialsId, Run<?, ?> run, FilePath workspace, TaskListener listener)
      throws UploadException, IOException {
    GoogleRobotCredentials credentials = StorageUtil.lookupCredentials(credentialsId);

    if (!forResult(run.getResult())) {
      // Don't upload for the given build state.
      return;
    }

    try {
      // Turn paths containing things like $BUILD_NUMBER and $JOB_NAME into
      // their fully resolved forms.
      String resolvedBucket = StorageUtil.replaceMacro(getBucket(), run, listener);
      BucketPath storagePrefix = new BucketPath(resolvedBucket);

      UploadSpec uploads = getInclusions(run, checkNotNull(workspace), listener);

      if (uploads != null) {
        BuildGcsUploadReport links = BuildGcsUploadReport.of(run);
        links.addBucket(storagePrefix.getBucket());

        initiateUploadsAtWorkspace(credentials, run, storagePrefix, uploads, listener);
      }
    } catch (InterruptedException e) {
      throw new UploadException(Messages.AbstractUpload_UploadException(), e);
    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_UploadException(), e);
    }
  }

  /**
   * This tuple is used to return the modified workspace and collection of {@link FilePath}s to
   * upload to {@link #perform}.
   *
   * <p>NOTE: The workspace is simply used to determine the path the object will be stored in
   * relative to the bucket. If it is relative to the workspace, that relative path will be appended
   * to the storage prefix. If it is not, then the absolute path will be appended.
   */
  protected static class UploadSpec implements Serializable {

    public UploadSpec(FilePath workspace, List<FilePath> inclusions) {
      this.workspace = checkNotNull(workspace);
      this.inclusions = Collections.unmodifiableCollection(inclusions);
    }

    public final FilePath workspace;
    public final Collection<FilePath> inclusions;
  }

  /**
   * Implementations override this interface in order to surface the set of {@link FilePath}s the
   * core logic should upload.
   *
   * @see UploadSpec for further details.
   */
  @Nullable
  protected abstract UploadSpec getInclusions(
      Run<?, ?> run, FilePath workspace, TaskListener listener) throws UploadException;

  /** Provide detail information summarizing this download for the GCS upload report. */
  public abstract String getDetails();

  /**
   * This hook is intended to give implementations the opportunity to further annotate the {@link
   * StorageObject} with metadata before uploading it to cloud storage.
   *
   * <p>NOTE: The base implementation does not do anything, so calling {@code
   * super.annotateObject()} is unnecessary.
   */
  protected void annotateObject(StorageObject object, TaskListener listener)
      throws UploadException {
    ;
  }

  /**
   * Retrieves the metadata to attach to the storage object.
   *
   * <p>NOTE: This can be overriden to surface additional (or less) information.
   */
  protected Map<String, String> getMetadata(Run<?, ?> run) {
    return MetadataContainer.of(run).getSerializedMetadata();
  }

  /** Determine whether we should upload the pattern for the given build result. */
  public boolean forResult(Result result) {
    if (result == null) {
      // We might have unfinished builds, e.g., through pipeline of Build Step.
      // Always run for those.
      return true;
    }
    if (result == Result.SUCCESS) {
      // We always run on successful builds.
      return true;
    }
    if (result == Result.FAILURE || result == Result.UNSTABLE) {
      return isForFailedJobs();
    }
    // else NOT_BUILT
    return false;
  }

  /**
   * The bucket name specified by the user, which potentially contains unresolved symbols, such as
   * $JOB_NAME and $BUILD_NUMBER.
   */
  public String getBucket() {
    return bucketNameWithVars;
  }

  /** NOTE: old name kept for deserialization */
  private final String bucketNameWithVars;

  /** Whether to surface the file being uploaded to anyone with the link. */
  @DataBoundSetter
  public void setSharedPublicly(boolean sharedPublicly) {
    this.sharedPublicly = sharedPublicly;
  }

  public boolean isSharedPublicly() {
    return sharedPublicly;
  }

  private boolean sharedPublicly;

  /** Whether to attempt the upload, even if the job failed. */
  @DataBoundSetter
  public void setForFailedJobs(boolean forFailedJobs) {
    this.forFailedJobs = forFailedJobs;
  }

  public boolean isForFailedJobs() {
    return forFailedJobs;
  }

  private boolean forFailedJobs;

  /**
   * Whether to indicate in metadata that the file should be viewable inline in web browsers, rather
   * than requiring it to be downloaded first.
   */
  @DataBoundSetter
  public void setShowInline(boolean showInline) {
    this.showInline = showInline;
  }

  public boolean isShowInline() {
    return showInline;
  }

  private boolean showInline;

  /**
   * The path prefix that will be stripped from uploaded files. May be null if no path prefix needs
   * to be stripped.
   *
   * <p>Filenames that do not start with this prefix will not be modified. Trailing slash is
   * automatically added if it is missing.
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

  /** The module to use for providing dependencies. */
  protected final UploadModule module;

  /**
   * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public static DescriptorExtensionList<AbstractUpload, AbstractUploadDescriptor> all() {
    return checkNotNull(Hudson.getInstance())
        .<AbstractUpload, AbstractUploadDescriptor>getDescriptorList(AbstractUpload.class);
  }

  /**
   * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public AbstractUploadDescriptor getDescriptor() {
    return (AbstractUploadDescriptor) checkNotNull(Hudson.getInstance()).getDescriptor(getClass());
  }

  /**
   * Execute the {@link UploadSpec} for this {@code build} to the bucket specified by {@code
   * storagePrefix} using the authority of {@code credentials} and logging any information to {@code
   * listener}.
   *
   * @throws UploadException if anything goes awry
   */
  private void initiateUploadsAtWorkspace(
      final GoogleRobotCredentials credentials,
      final Run run,
      final BucketPath storagePrefix,
      final UploadSpec uploads,
      final TaskListener listener)
      throws UploadException {
    try {
      try {
        // Use remotable credential to access the storage service from the
        // remote machine.
        final GoogleRobotCredentials remoteCredentials =
            checkNotNull(credentials).forRemote(module.getRequirement());
        final String version = module.getVersion();

        uploads.workspace.act(
            new Callable<Void, UploadException>() {
              @Override
              public Void call() throws UploadException {
                performUploads(
                    storagePrefix.getBucket(),
                    storagePrefix.getObject(),
                    remoteCredentials,
                    uploads,
                    listener,
                    version);
                return (Void) null;
              }

              @Override
              public void checkRoles(RoleChecker checker) throws SecurityException {
                // We know by definition that this is the correct role;
                // the callable exists only in this method context.
              }
            });
      } catch (GeneralSecurityException e) {
        throw new UploadException(Messages.AbstractUpload_RemoteCredentialError(), e);
      }

      // We can't do this over the wire, so do it in bulk here
      BuildGcsUploadReport report = BuildGcsUploadReport.of(run);
      for (FilePath include : uploads.inclusions) {
        report.addUpload(
            StorageUtil.getStrippedFilename(
                StorageUtil.getRelative(include, uploads.workspace), pathPrefix),
            storagePrefix);
      }

    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (InterruptedException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionFileUpload(), e);
    }
  }

  /**
   * This is the workhorse API for performing the actual uploads. It is performed at the workspace,
   * so that all of the {@link FilePath}s should be local.
   */
  private void performUploads(
      final String bucketName,
      final String objectPrefix,
      final GoogleRobotCredentials credentials,
      final UploadSpec uploads,
      final TaskListener listener,
      final String version)
      throws UploadException {
    RepeatOperation<UploadException> a =
        new RepeatOperation<UploadException>() {
          private Queue<FilePath> paths = new LinkedList<>(uploads.inclusions);;
          Executor executor = module.newExecutor();;

          Storage service;
          Bucket bucket;

          @Override
          public void initCredentials() throws UploadException, IOException {
            service = module.getStorageService(credentials, version);
            // Ensure the bucket exists, fetching it regardless so that we can
            // attach its default ACLs to the objects we upload.
            bucket = getOrCreateBucket(service, credentials, executor, bucketName);
          }

          @Override
          public void act()
              throws HttpResponseException, UploadException, IOException, InterruptedException,
                  ExecutorException {
            FilePath include = paths.peek();
            String relativePath = StorageUtil.getRelative(include, uploads.workspace);
            String uploadedFileName = StorageUtil.getStrippedFilename(relativePath, pathPrefix);
            String finalName =
                FilenameUtils.separatorsToUnix(
                    FilenameUtils.concat(objectPrefix, uploadedFileName));

            StorageObject object =
                new StorageObject()
                    .setName(finalName)
                    .setContentDisposition(
                        HttpHeaders.getContentDisposition(include.getName(), isShowInline()))
                    .setContentType(detectMIMEType(include.getName()))
                    .setSize(BigInteger.valueOf(include.length()));

            if (isSharedPublicly()) {
              object.setAcl(addPublicReadAccess(getDefaultObjectAcl(bucket, listener)));
            }

            // Give clients an opportunity to decorate the storage
            // object before we store it.
            annotateObject(object, listener);

            // Log that we are uploading the file and begin executing the upload.
            listener
                .getLogger()
                .println(module.prefix(Messages.AbstractUpload_Uploading(relativePath)));

            performUploadWithRetry(executor, service, bucket, object, include);
            paths.remove();
          }

          @Override
          public boolean moreWork() {
            return !paths.isEmpty();
          }
        };

    try {
      RetryStorageOperation.performRequestWithReinitCredentials(a);
    } catch (ForbiddenException e) {
      // If the user doesn't own a bucket then they will end up here.
      throw new UploadException(Messages.AbstractUpload_ForbiddenFileUpload(), e);
    } catch (ExecutorException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (InterruptedException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionFileUpload(), e);
    }
  }

  /**
   * Auxiliar method for detecting web-related filename extensions, so setting correctly
   * Content-Type.
   */
  private String detectMIMEType(String filename) {
    String extension = Files.getFileExtension(filename);
    if (CONTENT_TYPES.containsKey(extension)) {
      return CONTENT_TYPES.get(extension);
    } else {
      return URLConnection.guessContentTypeFromName(filename);
    }
  }

  /**
   * We need our own storage retry logic because we must recreate the input stream for the media
   * uploader.
   */
  private void performUploadWithRetry(
      final Executor executor,
      final Storage service,
      final Bucket bucket,
      final StorageObject object,
      final FilePath include)
      throws ExecutorException, IOException, InterruptedException {
    Operation a =
        new Operation() {
          public void act() throws IOException, InterruptedException, ExecutorException {
            // Create the insertion operation with the decorated object and
            // an input stream of the file contents.
            Storage.Objects.Insert insertion =
                service
                    .objects()
                    .insert(
                        bucket.getName(),
                        object,
                        new InputStreamContent(object.getContentType(), include.read()));

            // Make the operation non-resumable because we have seen a dramatic
            // (e.g. 1000x) speedup from this.
            MediaHttpUploader mediaUploader = insertion.getMediaHttpUploader();
            if (mediaUploader != null) {
              mediaUploader.setDirectUploadEnabled(true);
            }

            executor.execute(insertion);
          }
        };

    RetryStorageOperation.performRequestWithRetry(executor, a, module.getInsertRetryCount());
  }

  // Fetch the default object ACL for this bucket. Return an empty list if
  // we cannot.
  private static List<ObjectAccessControl> getDefaultObjectAcl(
      Bucket bucket, TaskListener listener) {
    List<ObjectAccessControl> defaultAcl = bucket.getDefaultObjectAcl();
    if (defaultAcl == null) {
      listener.error(Messages.AbstractUpload_BucketObjectAclsError(bucket.getName()));
      return ImmutableList.of();
    } else {
      return defaultAcl;
    }
  }

  // Add public access to a given access control list
  private static List<ObjectAccessControl> addPublicReadAccess(
      List<ObjectAccessControl> defaultAcl) {
    List<ObjectAccessControl> acl = Lists.newArrayList(defaultAcl);
    final String publicEntity = "allUsers";
    boolean alreadyShared =
        Iterables.tryFind(
                acl,
                new Predicate<ObjectAccessControl>() {
                  @Override
                  public boolean apply(ObjectAccessControl access) {
                    if (access != null) {
                      return Objects.equal(access.getEntity(), publicEntity);
                    } else {
                      throw new NullPointerException();
                    }
                  }
                })
            .isPresent();
    /* If the entity 'allUsers' didn't already has READER or OWNER access, grant
    READER. This is to avoid having both an OWNER record and a READER record
    for that same entity */
    if (!alreadyShared) {
      acl.add(new ObjectAccessControl().setEntity("allUsers").setRole("READER"));
    }
    return acl;
  }

  /**
   * Fetches or creates an instance of the bucket with the given name with the specified storage
   * service.
   *
   * @param credentials The credentials with which to fetch/create the bucket
   * @param bucketName The top-level bucket name to ensure exists
   * @return an instance of the named bucket, created or retrieved.
   * @throws UploadException if any issues are encountered
   */
  protected Bucket getOrCreateBucket(
      Storage service, GoogleRobotCredentials credentials, Executor executor, String bucketName)
      throws UploadException {
    try {
      try {
        return executor.execute(
            service.buckets().get(bucketName).setProjection("full")); // to retrieve the bucket ACLs
      } catch (NotFoundException e) {
        try {
          // This is roughly the opposite of how the command-line sample does
          // things.  We do things this way to optimize for the case where the
          // bucket already exists.
          Bucket bucket = new Bucket().setName(bucketName);
          bucket =
              executor.execute(
                  service
                      .buckets()
                      .insert(credentials.getProjectId(), bucket)
                      .setProjection("full")); // to retrieve the bucket ACLs

          return bucket;
        } catch (ConflictException ex) {
          // If we get back a "Conflict" response, it means that the bucket
          // was inserted between when we first tried to get it and were able
          // to successfully insert one.
          // NOTE: This could be due to an initial insertion attempt succeeding
          // but returning an exception, or a race with another service.
          return executor.execute(
              service
                  .buckets()
                  .get(bucketName)
                  .setProjection("full")); // to retrieve the bucket ACLs
        }
      }
    } catch (ExecutorException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
    }
  }
}
