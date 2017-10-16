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
package com.google.jenkins.plugins.storage.util;

import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.Logger;

import static com.google.api.client.http.HttpStatusCodes
    .STATUS_CODE_UNAUTHORIZED;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpResponseException;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.storage.Messages;
import com.google.jenkins.plugins.storage.UploadException;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * A class to fascilitate retries on storage operations.
 */
public class RetryStorageOperation {
  private static final Logger logger =
      Logger.getLogger(RetryStorageOperation.class.getName());

  /**
   * An operation to be retried
   */
  public interface Action {
    public void act()
        throws IOException, InterruptedException, ExecutorException;
  }

  /**
   * Perform the given operation retrying on error.
   * The operation is retried if either IOException or InterruptedException
   * occur. After the given number of retries, throws the last error received.
   * Any other exceptions are passed through.
   *
   * @param executor The executor to use for the operation
   * @param a The operation to execute.
   * @param retries How many retries to attempt
   */
  public static void performRequestWithRetry(Executor executor, Action a,
      int retries)
      throws IOException, InterruptedException, ExecutorException {
    IOException lastIOException = null;
    InterruptedException lastInterruptedException = null;
    for (int i = 0; i < retries; ++i) {
      try {
        a.act();
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

  // An action that may fail because of expired credentials.
  public interface RepeatAction<Ex extends Throwable> {
    public void initCredentials() throws Ex;

    public void act()
        throws HttpResponseException, IOException, InterruptedException,
        ExecutorException, Ex;

    public boolean moreWork();
  }

  /**
   * Keeps performing actions until credentials expire. When they do, calls
   * initCredentials() and continues. It relies on the RepeatAction to keep any
   * state required to start where it left off.
   *
   * HttpResponseException with the code Unauthorized is caught. Any other
   * exceptions are passed through.
   *
   * @param a Action to execute
   * @param retries How many times to attempt to refresh credentials if there
   * is no progress. (Every time an action successfully completes, the retry
   * budget is reset)
   * @param <Ex> An action-specific exception that might be throwns.
   */

  public static <Ex extends Throwable> void
  performRequestWithReinitCredentials(RepeatAction<Ex> a, int retries)
      throws IOException, InterruptedException, ExecutorException, Ex {
    int budget = retries;
    do {
      a.initCredentials();
      try {
        while (a.moreWork()) {
          a.act();
          budget = retries;
        }
      } catch (HttpResponseException e) {
        if (budget > 0 && e.getStatusCode() == STATUS_CODE_UNAUTHORIZED) {
          logger.fine("Remote credentials expired, retrying.");
          budget--;
        } else {
          throw new IOException(Messages.AbstractUpload_ExceptionFileUpload(),
              e);
        }
      }
      // Other exceptions are raised further for the client to deal with
    } while (a.moreWork());
  }
}
