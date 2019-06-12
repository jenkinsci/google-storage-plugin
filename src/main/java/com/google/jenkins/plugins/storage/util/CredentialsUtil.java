package com.google.jenkins.plugins.storage.util;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.storage.StorageScopeRequirement;
import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import java.security.GeneralSecurityException;

/** Provides a library of utility functions for credentials-related work. */
public class CredentialsUtil {
  /**
   * Get the Google Robot Credentials for the given credentialsId.
   *
   * @param itemGroup A handle to the Jenkins instance. Must be non-null.
   * @param domainRequirements A list of domain requirements. Must be non-null.
   * @param credentialsId The ID of the GoogleRobotCredentials to be retrieved from Jenkins and
   *     utilized for authorization. Must be non-empty or non-null and exist in credentials store.
   * @return Google Robot Credential for the given credentialsId.
   * @throws hudson.AbortException If there was an issue retrieving the Google Robot Credentials.
   */
  public static GoogleRobotCredentials getRobotCredentials(
      ItemGroup itemGroup,
      ImmutableList<DomainRequirement> domainRequirements,
      String credentialsId)
      throws AbortException {
    Preconditions.checkNotNull(itemGroup);
    Preconditions.checkNotNull(domainRequirements);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(credentialsId));

    GoogleRobotCredentials robotCreds =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                GoogleRobotCredentials.class, itemGroup, ACL.SYSTEM, domainRequirements),
            CredentialsMatchers.withId(credentialsId));

    if (robotCreds == null) {
      throw new AbortException("meow");
    }

    return robotCreds;
  }

  /**
   * Get the Credential from the Google robot credentials for GKE access.
   *
   * @param robotCreds Google Robot Credential for desired service account.
   * @return Google Credential for the service account.
   * @throws AbortException if there was an error initializing HTTP transport.
   */
  public static Credential getGoogleCredential(GoogleRobotCredentials robotCreds)
      throws AbortException {
    Credential credential;
    try {
      credential = robotCreds.getGoogleCredential(new StorageScopeRequirement());
    } catch (GeneralSecurityException gse) {
      throw new AbortException("meow");
    }

    return credential;
  }
}
