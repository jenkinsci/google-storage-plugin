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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.Executor;
import hudson.Plugin;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import jenkins.model.Jenkins;

// TODO: Will not be needed once https://github.com/jenkinsci/google-storage-plugin/issues/71 is
// done.
/**
 * This module abstracts how the Upload implementations instantiate their connection to the Storage
 * service.
 */
@RequiresDomain(value = StorageScopeRequirement.class)
public class UploadModule {
  private static final String PLUGIN_NAME = "google-storage-plugin";
  /**
   * Interface for requesting the {@link Executor} for executing requests.
   *
   * @return a new {@link Executor} instance for issuing requests.
   */
  public Executor newExecutor() {
    return new Executor.Default();
  }

  /**
   * Returns the scope requirement to access the GCS API.
   *
   * @return Storage scope requirement for the GCS API.
   */
  public StorageScopeRequirement getRequirement() {
    return DomainRequirementProvider.of(getClass(), StorageScopeRequirement.class);
  }

  /** @return the version number of this plugin. */
  public String getVersion() {
    String version = "";
    Plugin plugin = Jenkins.get().getPlugin(PLUGIN_NAME);
    if (plugin != null) {
      version = plugin.getWrapper().getVersion();
    }
    return version;
  }

  /**
   * Given GoogleRobotCredentials and plugin version, return a handle to a Storage object that has
   * already been authenticated.
   *
   * @param credentials Credentials needed to authenticate to the GCS API.
   * @param version Current version of the plugin.
   * @return Handle to a Storage object that has already been authenticated.
   * @throws IOException If there is an issue authenticating with the given credentials.
   */
  public Storage getStorageService(GoogleRobotCredentials credentials, String version)
      throws IOException {
    try {
      String appName = Messages.UploadModule_AppName();
      if (version.length() > 0) {
        version = version.split(" ")[0];
        appName = appName.concat("/").concat(version);
      }
      return new Storage.Builder(
              new NetHttpTransport(),
              new JacksonFactory(),
              credentials.getGoogleCredential(getRequirement()))
          .setApplicationName(appName)
          .build();
    } catch (GeneralSecurityException e) {
      throw new IOException(Messages.UploadModule_ExceptionStorageService(), e);
    }
  }

  /** @return Controls the number of object insertion retries. */
  public int getInsertRetryCount() {
    return 5;
  }

  /**
   * Prefix the given log message with our module name.
   *
   * @param logMessage Log message to log.
   * @return Log message prefixed with module name.
   */
  public String prefix(String logMessage) {
    return Messages.StorageUtil_PrefixFormat(
        Messages.GoogleCloudStorageUploader_DisplayName(), logMessage);
  }

  /**
   * Returns an InputStream for the given GCS object.
   *
   * @param getObject GCS object.
   * @return GCS object in InputStream format.
   * @throws IOException If there was in issue getting the InputStream for the GCS object.
   */
  public InputStream executeMediaAsInputStream(Storage.Objects.Get getObject) throws IOException {
    return getObject.executeMediaAsInputStream();
  }
}
