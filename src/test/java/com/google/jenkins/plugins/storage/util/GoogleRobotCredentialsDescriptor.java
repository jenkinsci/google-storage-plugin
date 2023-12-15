package com.google.jenkins.plugins.storage.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentialsModule;
import com.google.jenkins.plugins.credentials.oauth.Messages;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.FormValidation;
import java.security.GeneralSecurityException;
import org.kohsuke.stapler.QueryParameter;

/**
 * Dev Memo: 
 * In earlier versions, {@code AbstractGoogleRobotCredentialsDescriptor} was a standalone
 * abstract class responsible for handling descriptors for Google robot account credential extensions. 
 * Now, {@code AbstractGoogleRobotCredentialsDescriptor} has been restructured and
 * integrated as a static inner class within {@code GoogleRobotCredentials}.
 *
 * This change affects how the descriptor is accessed and used within the codebase. No major
 * changes in the API usage are expected, but minor adjustments might be necessary in how the class
 * is instantiated or accessed
 */
public class GoogleRobotCredentialsDescriptor extends GoogleRobotCredentials {

    protected GoogleRobotCredentialsDescriptor(
            CredentialsScope scope,
            String id,
            String projectId,
            String description,
            GoogleRobotCredentialsModule module) {
        super(scope, id, projectId, description, module);
    }

    @Override
    public Credential getGoogleCredential(GoogleOAuth2ScopeRequirement requirement) throws GeneralSecurityException {
        return null;
    }

    @NonNull
    @Override
    public String getUsername() {
        return null;
    }

    /**
     * Abstract class for testing Google Robot Credentials Descriptor. This class extends 
     * {@link GoogleRobotCredentials.AbstractGoogleRobotCredentialsDescriptor} and is used for creating test
     * instances of Google Robot Credentials descriptors.
     */
    public abstract static class AbstractGoogleRobotCredentialsTestDescriptor
            extends GoogleRobotCredentials.AbstractGoogleRobotCredentialsDescriptor {
        protected AbstractGoogleRobotCredentialsTestDescriptor(
                Class<? extends GoogleRobotCredentials> clazz, GoogleRobotCredentialsModule module) {
            super(clazz);
            this.module = checkNotNull(module);
        }

        /** The module to use for instantiating depended upon resources */
        public GoogleRobotCredentialsModule getModule() {
            return module;
        }

        private final GoogleRobotCredentialsModule module;

        /** Validate project-id entries */
        public FormValidation doCheckProjectId(@QueryParameter String projectId) {
            if (!Strings.isNullOrEmpty(projectId)) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.GoogleRobotMetadataCredentials_ProjectIDError());
            }
        }
    }
}
