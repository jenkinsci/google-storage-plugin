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

import hudson.Plugin;
import java.io.Serializable;
import java.security.GeneralSecurityException;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.util.Executor;
import jenkins.model.Jenkins;

/**
 * This module abstracts how the Upload implementations instantiate
 * their connection to the Storage service.
 */
@RequiresDomain(value = StorageScopeRequirement.class)
public class UploadModule implements Serializable {
  /**
   * Interface for requesting the {@link Executor} for executing requests.
   *
   * @return a new {@link Executor} instance for issuing requests
   */
  public Executor newExecutor() {
    return new Executor.Default();
  }

  public StorageScopeRequirement getRequirement() {
    return DomainRequirementProvider.of(getClass(),
        StorageScopeRequirement.class);
  }

  public Storage getStorageService(GoogleRobotCredentials credentials)
      throws UploadException {
    String version = "";
    Plugin plugin = Jenkins.getInstance().getPlugin(PLUGIN_NAME);
    if(plugin != null) {
      version = plugin.getWrapper().getVersion();
    }
    return getStorageService(credentials, version);
  }

  public Storage getStorageService(GoogleRobotCredentials credentials, String version)
      throws UploadException {
    try {
      String appName = Messages.UploadModule_AppName();
      if (version.length() > 0) {
        version = version.split(" ")[0];
        appName = appName.concat("/").concat(version);
      }
      return new Storage.Builder(new NetHttpTransport(), new JacksonFactory(),
          credentials.getGoogleCredential(getRequirement()))
          .setApplicationName(appName)
          .build();
    } catch (GeneralSecurityException e) {
      throw new UploadException(
          Messages.UploadModule_ExceptionStorageService(), e);
    }
  }

  /**
   * Controls the number of object insertion retries.
   */
  public int getInsertRetryCount() {
    return 5;
  }

  /**
   * Prefix the given log message with our module.
   */
  public String prefix(String x) {
    return Messages.StorageUtil_PrefixFormat(
        Messages.GoogleCloudStorageUploader_DisplayName(), x);
  }

  final static String PLUGIN_NAME = "google-storage-plugin";
}
