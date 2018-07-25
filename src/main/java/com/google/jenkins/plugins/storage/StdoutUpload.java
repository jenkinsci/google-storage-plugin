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

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.ByteStreams.copy;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.jenkins.plugins.storage.util.StorageUtil;
import com.google.jenkins.plugins.util.Resolve;

import hudson.Extension;
import hudson.FilePath;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

/**
 * This upload extension allow the user to upload the build log for the Jenkins
 * build to a given bucket, with a specified file name. By default, the file is
 * named "build-log.txt".
 */
public class StdoutUpload extends AbstractUpload {

    public static <T> T firstNonNull(T... objs) {
        for (T obj : objs) {
            if (obj != null) {
                return obj;
            }
        }
        throw new NullPointerException();
    }

    /**
     * Construct the Upload with the stock properties, and the additional
     * information about how to name the build log file.
     */
    @DataBoundConstructor
    public StdoutUpload(@Nullable String bucket,
            @Nullable UploadModule module, String logName,
            // Legacy arguments for backwards compatibility
            @Deprecated @Nullable String bucketNameWithVars) {
        super(firstNonNull(bucket, bucketNameWithVars), module);
        this.logName = checkNotNull(logName);
    }

    /**
     * The name to give the file we upload for the build log.
     */
    public String getLogName() {
        return logName;
    }

    private final String logName;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDetails() {
        return Messages.StdoutUpload_DetailsMessage(getLogName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean forResult(Result result) {
        if (result == null) {
            return true;
        }
        if (result == Result.NOT_BUILT) {
            return true;
        }
        return super.forResult(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    protected UploadSpec getInclusions(Run<?, ?> run,
            FilePath workspace, TaskListener listener) throws UploadException {
        try {
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                FilePath logDir = new FilePath(run.getLogFile()).getParent();

                String resolvedLogName = StorageUtil
                        .replaceMacro(getLogName(), run, listener);
                FilePath logFile = new FilePath(logDir, resolvedLogName);

               outputStream = new PlainTextConsoleOutputStream(logFile.write());
               inputStream = run.getLogInputStream();
               copy(inputStream, outputStream);

                return new UploadSpec(logDir, ImmutableList.of(logFile));
            } finally {
                Closeables.close(outputStream, true /* swallowIOException */);
                Closeables.close(inputStream, true /* swallowIOException */);
            }
        } catch (InterruptedException e) {
            throw new UploadException(
                    Messages.AbstractUpload_IncludeException(), e);
        } catch (IOException e) {
            throw new UploadException(
                    Messages.AbstractUpload_IncludeException(), e);
        }
    }

    /**
     * Denotes this is an {@link AbstractUpload} plugin
     */
    @Extension
    public static class DescriptorImpl extends AbstractUploadDescriptor {

        public DescriptorImpl() {
            this(StdoutUpload.class);
        }

        public DescriptorImpl(
                Class<? extends StdoutUpload> clazz) {
            super(clazz);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.StdoutUpload_DisplayName();
        }

        /**
         * This callback validates the {@code logName} input field's values.
         */
        public FormValidation doCheckLogName(
                @QueryParameter final String logName) throws IOException {
            String resolvedInput = Resolve.resolveBuiltin(logName);
            if (resolvedInput.isEmpty()) {
                return FormValidation.error(
                        Messages.StdoutUpload_LogNameRequired());
            }

            if (resolvedInput.contains("$")) {
                // resolved file name still contains variable marker
             return FormValidation.error(
                    Messages.StdoutUpload_BadChar("$",
                            Messages.AbstractUploadDescriptor_DollarSuggest()));
            }
            // TODO(mattmoor): Proper filename validation
            return FormValidation.ok();
        }
    }
}
