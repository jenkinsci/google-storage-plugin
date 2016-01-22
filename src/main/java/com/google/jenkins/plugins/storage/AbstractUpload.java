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

import static java.util.logging.Level.SEVERE;

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
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.remoting.RoleChecker;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jenkins.plugins.storage.AbstractUploadDescriptor.GCS_SCHEME;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.ObjectAccessControl;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.metadata.MetadataContainer;
import com.google.jenkins.plugins.storage.reports.BuildGcsUploadReport;
import com.google.jenkins.plugins.util.ConflictException;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.ForbiddenException;
import com.google.jenkins.plugins.util.NotFoundException;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.Callable;

/**
 * This new extension point is used for surfacing different kinds of
 * Google Cloud Storage (GCS) uploads.  The most obvious implementations
 * are provided as:
 * @see ClassicUpload
 * @see StdoutUpload
 *
 * We provide the following hooks for implementations to inject additional
 * functionality:
 * <ul>
 *   <li> Required {@link #getDetails}: provides detail information for the
 * GCS upload report.
 *   <li> Required {@link #getInclusions}: surfaces the set of
 * {@link UploadSpec} for the base class to upload to GCS.
 *
 *   <li> Optional {@link #forResult}: determines the build states for which
 * uploading should be performed.
 *   <li> Optional {@link #getMetadata}: allows the implementation to surface
 * additional metadata on the storage object
 *   <li> Optional {@link #annotateObject}: allows the implementation to
 * ~arbitrarily rewrite parts of the object prior to insertion.
 * </ul>
 */
public abstract class AbstractUpload
    implements Describable<AbstractUpload>, ExtensionPoint, Serializable {
  private static final Logger logger =
      Logger.getLogger(AbstractUpload.class.getName());
  private static final ImmutableMap<String, String> CONTENT_TYPES =
      ImmutableMap.of(
        "css", "text/css"
      );

  /**
   * Construct the base upload from a handful of universal properties.
   *
   * @param bucket The unresolved name of the storage bucket within
   * which to store the resulting objects.
   * @param sharedPublicly Whether to publicly share the objects being uploaded
   * @param forFailedJobs Whether to perform the upload regardless of the
   * build's outcome
   * @param pathPrefix Path prefix to strip from uploaded files when determining
   * the filename in GCS. Null indicates no stripping. Filenames that do not
   * start with this prefix will not be modified. Trailing slash is
   * automatically added if it is missing.
   */
  public AbstractUpload(String bucket, boolean sharedPublicly,
      boolean forFailedJobs, @Nullable String pathPrefix,
      @Nullable UploadModule module) {
    if (module != null) {
      this.module = module;
    } else {
      this.module = getDescriptor().getModule();
    }
    this.bucketNameWithVars = checkNotNull(bucket);
    this.sharedPublicly = sharedPublicly;
    this.forFailedJobs = forFailedJobs;
    if (pathPrefix != null && !pathPrefix.endsWith("/")) {
      pathPrefix += "/";
    }
    this.pathPrefix = pathPrefix;
  }

  /**
   * The main action entrypoint of this extension.  This uploads the
   * contents included by the implementation to our resolved storage
   * bucket.
   */
  public final void perform(GoogleRobotCredentials credentials,
      AbstractBuild<?, ?> build, TaskListener listener)
      throws UploadException {
    if (!forResult(build.getResult())) {
      // Don't upload for the given build state.
      return;
    }

    try {
      // Turn paths containing things like $BUILD_NUMBER and $JOB_NAME into
      // their fully resolved forms.
      String bucketNameResolvedVars = Util.replaceMacro(
          getBucket(), build.getEnvironment(listener));

      if (!bucketNameResolvedVars.startsWith(GCS_SCHEME)) {
        listener.error(module.prefix(
            Messages.AbstractUploadDescriptor_BadPrefix(
                bucketNameResolvedVars, GCS_SCHEME)));
        return;
      }
      // Lop off the GCS_SCHEME prefix.
      bucketNameResolvedVars =
          bucketNameResolvedVars.substring(GCS_SCHEME.length());

      UploadSpec uploads = getInclusions(
          build, checkNotNull(build.getWorkspace()), listener);

      if (uploads != null) {
        BuildGcsUploadReport links = BuildGcsUploadReport.of(build);
        links.addBucket(bucketNameResolvedVars);

        initiateUploadsAtWorkspace(credentials, build, bucketNameResolvedVars,
            uploads, listener);
      }
    } catch (InterruptedException e) {
      throw new UploadException(Messages.AbstractUpload_UploadException(), e);
    } catch (IOException e) {
      throw new UploadException(Messages.AbstractUpload_UploadException(), e);
    }
  }

  /**
   * This tuple is used to return the modified workspace and collection of
   * {@link FilePath}s to upload to {@link #perform}.
   *
   * NOTE: The workspace is simply used to determine the path the object will
   * be stored in relative to the bucket.  If it is relative to the workspace,
   * that relative path will be appended to the storage prefix.  If it is
   * not, then the absolute path will be appended.
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
   * Implementations override this interface in order to surface the set of
   * {@link FilePath}s the core logic should upload.
   *
   * @see UploadSpec for further details.
   */
  @Nullable
  protected abstract UploadSpec getInclusions(
      AbstractBuild<?, ?> build, FilePath workspace, TaskListener listener)
      throws UploadException;

  /**
   * Provide detail information summarizing this download for the GCS
   * upload report.
   */
  public abstract String getDetails();

  /**
   * This hook is intended to give implementations the opportunity to further
   * annotate the {@link StorageObject} with metadata before uploading it to
   * cloud storage.
   *
   * NOTE: The base implementation does not do anything, so calling
   * {@code super.annotateObject()} is unnecessary.
   */
  protected void annotateObject(StorageObject object, TaskListener listener)
      throws UploadException {
    ;
  }

  /**
   * Retrieves the metadata to attach to the storage object.
   *
   * NOTE: This can be overriden to surface additional (or less) information.
   */
  protected Map<String, String> getMetadata(AbstractBuild<?, ?> build) {
    return MetadataContainer.of(build).getSerializedMetadata();
  }

  /**
   * Determine whether we should upload the pattern for the given
   * build result.
   */
  public boolean forResult(Result result) {
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
   * The bucket name specified by the user, which potentially contains
   * unresolved symbols, such as $JOB_NAME and $BUILD_NUMBER.
   */
  public String getBucket() {
    return bucketNameWithVars;
  }
  /** NOTE: old name kept for deserialization */
  private final String bucketNameWithVars;

  /**
   * Whether to surface the file being uploaded to anyone with the link.
   */
  public boolean isSharedPublicly() {
    return sharedPublicly;
  }
  private final boolean sharedPublicly;

  /**
   * Whether to attempt the upload, even if the job failed.
   */
  public boolean isForFailedJobs() {
    return forFailedJobs;
  }
  private final boolean forFailedJobs;

  /**
   * The path prefix that will be stripped from uploaded files. May be null
   * if no path prefix needs to be stripped.
   */
  @Nullable
  public String getPathPrefix() {
    return pathPrefix;
  }
  private final String pathPrefix;

  /**
   * The module to use for providing dependencies.
   */
  protected final UploadModule module;

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public static DescriptorExtensionList<AbstractUpload,
      AbstractUploadDescriptor> all() {
    return checkNotNull(Hudson.getInstance()).<AbstractUpload,
        AbstractUploadDescriptor>getDescriptorList(AbstractUpload.class);
  }

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public AbstractUploadDescriptor getDescriptor() {
    return (AbstractUploadDescriptor) checkNotNull(Hudson.getInstance())
        .getDescriptor(getClass());
  }

  /**
   * Execute the {@link UploadSpec} for this {@code build} to the bucket
   * specified by {@code storagePrefix} using the authority of
   * {@code credentials} and logging any information to {@code listener}.
   *
   * @throws UploadException if anything goes awry
   */
  private void initiateUploadsAtWorkspace(GoogleRobotCredentials credentials,
      final AbstractBuild build, String storagePrefix, final UploadSpec uploads,
      final TaskListener listener) throws UploadException {
    try {
      // Break things down to a compatible format:
      //   foo  /  bar / baz / blah.log
      //  ^---^   ^--------------------^
      //  bucket      storage-object
      //
      // TODO(mattmoor): Test objectPrefix on Windows, where '\' != '/'
      // Must we translate?  Can we require them to specify in unix-style
      // and still have things work?
      String[] halves = checkNotNull(storagePrefix).split("/", 2);
      final String bucketName = halves[0];
      final String objectPrefix = (halves.length == 1) ? "" : halves[1];

      // Within the workspace, upload all of the files, using a remotable
      // credential to access the storage service from the remote machine.
      final GoogleRobotCredentials remoteCredentials =
          checkNotNull(credentials).forRemote(module.getRequirement());
      final Map<String, String> metadata = getMetadata(build);

      uploads.workspace.act(
          new Callable<Void, UploadException>() {
            @Override
            public Void call() throws UploadException {
              performUploads(metadata, bucketName, objectPrefix,
                  remoteCredentials, uploads, listener);
              return (Void) null;
            }

            @Override
            public void checkRoles(RoleChecker checker)
                throws SecurityException {
              // We know by definition that this is the correct role;
              // the callable exists only in this method context.
            }
          });

      // We can't do this over the wire, so do it in bulk here
      BuildGcsUploadReport report = BuildGcsUploadReport.of(build);
      for (FilePath include : uploads.inclusions) {
        report.addUpload(getStrippedFilename(
            getRelative(include, uploads.workspace)), storagePrefix);
      }

    } catch (IOException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (InterruptedException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (GeneralSecurityException e) {
      throw new UploadException(
          Messages.AbstractUpload_RemoteCredentialError(), e);
    }
  }

  /**
   * This is the workhorse API for performing the actual uploads.  It is
   * performed at the workspace, so that all of the {@link FilePath}s should
   * be local.
   */
  private void performUploads(Map<String, String> metadata, String bucketName,
      String objectPrefix, GoogleRobotCredentials credentials,
      UploadSpec uploads, TaskListener listener) throws UploadException {
    try {
      Storage service = module.getStorageService(credentials);
      Executor executor = module.newExecutor();

      // Ensure the bucket exists, fetching it regardless so that we can
      // attach its default ACLs to the objects we upload.
      Bucket bucket = getOrCreateBucket(service, credentials, executor,
          bucketName);

      for (FilePath include : uploads.inclusions) {
        String relativePath = getRelative(include, uploads.workspace);
        String uploadedFileName = getStrippedFilename(relativePath);

        StorageObject object = new StorageObject()
            .setName(FilenameUtils.concat(objectPrefix, uploadedFileName))
            .setMetadata(metadata)
            .setContentDisposition(
                HttpHeaders.getContentDisposition(include.getName()))
            .setContentType(
                detectMIMEType(include.getName()))
            .setSize(BigInteger.valueOf(include.length()));

        if (isSharedPublicly()) {
          object.setAcl(addPublicReadAccess(
              getDefaultObjectAcl(bucket, listener)));
        }

        // Give clients an opportunity to decorate the storage
        // object before we store it.
        annotateObject(object, listener);

        // Log that we are uploading the file and begin executing the upload.
        listener.getLogger().println(module.prefix(
            Messages.AbstractUpload_Uploading(relativePath)));
        performUploadWithRetry(executor, service, bucket, object, include);
      }
    } catch (ForbiddenException e) {
      // If the user doesn't own a bucket then they will end up here.
      throw new UploadException(
          Messages.AbstractUpload_ForbiddenFileUpload(), e);
    } catch (ExecutorException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (IOException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionFileUpload(), e);
    } catch (InterruptedException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionFileUpload(), e);
    }
  }

  /**
   * Auxiliar method for detecting web-related filename extensions, so
   * setting correctly Content-Type.
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
   * We need our own storage retry logic because we must recreate the
   * input stream for the media uploader.
   */
  private void performUploadWithRetry(Executor executor, Storage service,
      Bucket bucket, StorageObject object, FilePath include)
      throws ExecutorException, IOException, InterruptedException {
    IOException lastIOException = null;
    InterruptedException lastInterruptedException = null;
    for (int i = 0; i < module.getInsertRetryCount(); ++i) {
      try {
        // Create the insertion operation with the decorated object and
        // an input stream of the file contents.
        Storage.Objects.Insert insertion =
            service.objects().insert(bucket.getName(), object,
                new InputStreamContent(
                    object.getContentType(), include.read()));

        // Make the operation non-resumable because we have seen a dramatic
        // (e.g. 1000x) speedup from this.
        MediaHttpUploader mediaUploader = insertion.getMediaHttpUploader();
        if (mediaUploader != null) {
          mediaUploader.setDirectUploadEnabled(true);
        }

        executor.execute(insertion);
        return;
      } catch (IOException e) {
        logger.log(SEVERE, Messages.AbstractUpload_UploadError(i), e);
        lastIOException = e;
      } catch (InterruptedException e) {
        logger.log(SEVERE, Messages.AbstractUpload_UploadError(i), e);
        lastInterruptedException = e;
      }

      // Pause before we retry
      executor.sleep();
    }

    // NOTE: We only reach here along paths that encountered an exception.
    // The "happy path" returns from the "try" statement above.
    if (lastIOException != null) {
      throw lastIOException;
    }
    throw checkNotNull(lastInterruptedException);
  }

  // Fetch the default object ACL for this bucket. Return an empty list if
  // we cannot.
  private static List<ObjectAccessControl> getDefaultObjectAcl(Bucket bucket,
      TaskListener listener) {
    List<ObjectAccessControl> defaultAcl = bucket.getDefaultObjectAcl();
    if (defaultAcl == null) {
      listener.error(Messages.AbstractUpload_BucketObjectAclsError(
          bucket.getName()));
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
    boolean alreadyShared = Iterables.tryFind(acl,
        new Predicate<ObjectAccessControl>() {
          @Override
          public boolean apply(ObjectAccessControl access) {
            return Objects.equal(access.getEntity(), publicEntity);
          }
        }).isPresent();
    /* If the entity 'allUsers' didn't already has READER or OWNER access, grant
       READER. This is to avoid having both an OWNER record and a READER record
       for that same entity */
    if (!alreadyShared) {
      acl.add(new ObjectAccessControl()
          .setEntity("allUsers")
          .setRole("READER"));
    }
    return acl;
  }

  /**
   * Fetches or creates an instance of the bucket with the given name with the
   * specified storage service.
   *
   * @param credentials The credentials with which to fetch/create the bucket
   * @param bucketName The top-level bucket name to ensure exists
   * @return an instance of the named bucket, created or retrieved.
   * @throws UploadException if any issues are encountered
   */
  protected Bucket getOrCreateBucket(Storage service,
      GoogleRobotCredentials credentials, Executor executor, String bucketName)
      throws UploadException {
    try {
      try {
        return executor.execute(service.buckets()
            .get(bucketName)
            .setProjection("full")); // to retrieve the bucket ACLs
      } catch (NotFoundException e) {
        try {
          // This is roughly the opposite of how the command-line sample does
          // things.  We do things this way to optimize for the case where the
          // bucket already exists.
          Bucket bucket = new Bucket().setName(bucketName);
          bucket = executor.execute(service.buckets()
              .insert(credentials.getProjectId(), bucket)
              .setProjection("full")); // to retrieve the bucket ACLs

          return bucket;
        } catch (ConflictException ex) {
          // If we get back a "Conflict" response, it means that the bucket
          // was inserted between when we first tried to get it and were able
          // to successfully insert one.
          // NOTE: This could be due to an initial insertion attempt succeeding
          // but returning an exception, or a race with another service.
          return executor.execute(service.buckets()
              .get(bucketName)
              .setProjection("full")); // to retrieve the bucket ACLs
        }
      }
    } catch (ExecutorException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
    } catch (IOException e) {
      throw new UploadException(
          Messages.AbstractUpload_ExceptionGetBucket(bucketName), e);
    }
  }

  /**
   * If a path prefix to strip has been specified, and the input string
   * starts with that prefix, returns the portion of the input after that
   * prefix. Otherwise, returns the unmodified input.
   */
  protected String getStrippedFilename(String filename) {
    if (pathPrefix != null && filename != null
        && filename.startsWith(pathPrefix)) {
      return filename.substring(pathPrefix.length());
    }
    return filename;
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
}
