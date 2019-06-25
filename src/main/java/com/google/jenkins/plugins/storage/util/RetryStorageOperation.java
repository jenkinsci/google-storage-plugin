/*
 * Copyright 2017 Google LLC
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
package com.google.jenkins.plugins.storage.util;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_UNAUTHORIZED;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.logging.Level.SEVERE;

import com.google.api.client.http.HttpResponseException;
import com.google.jenkins.plugins.storage.Messages;
import com.google.jenkins.plugins.util.Executor;
import com.google.jenkins.plugins.util.ExecutorException;
import java.io.IOException;
import java.util.logging.Logger;

/** A class to facilitate retries on storage operations. */
public class RetryStorageOperation {
  private static final Logger logger = Logger.getLogger(RetryStorageOperation.class.getName());
  // Only attempt to refresh the remote credentials once per 401 received.
  public static final int MAX_REMOTE_CREDENTIAL_EXPIRED_RETRIES = 1;

  /** An operation to be retried */
  public interface Operation {

    public void act() throws IOException, InterruptedException, ExecutorException;
  }

  /**
   * Perform the given operation retrying on error. The operation is retried if either IOException
   * or InterruptedException occur. After the given number of retries, throws the last error
   * received. Any other exceptions are passed through.
   *
   * @param executor The executor to use for the operation
   * @param a The operation to execute.
   * @param attempts How many attempts to make. Must be at least 1.
   * @throws IOException If performing the operation threw an IOException.
   * @throws InterruptedException If performing the operation threw an InterruptedException.
   * @throws ExecutorException If the executor threw an exception while performing the operation.
   */
  public static void performRequestWithRetry(Executor executor, Operation a, int attempts)
      throws IOException, InterruptedException, ExecutorException {
    IOException lastIOException = null;
    InterruptedException lastInterruptedException = null;
    for (int i = 0; i < attempts; ++i) {
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

  /** An action that may fail because of expired credentials. */
  public interface RepeatOperation<Ex extends Throwable> {

    public void initCredentials() throws IOException, Ex;

    public void act()
        throws HttpResponseException, IOException, InterruptedException, ExecutorException, Ex;

    public boolean moreWork();
  }

  /**
   * Keeps performing actions until credentials expire. When they do, calls initCredentials() and
   * continues. It relies on the RepeatOperation to keep any state required to start where it left
   * off.
   *
   * <p>HttpResponseException with the code Unauthorized is caught. Any other exceptions are passed
   * through.
   *
   * @param a Operation to execute
   * @param retries How many times to attempt to refresh credentials if there is no progress. (Every
   *     time an action successfully completes, the retry budget is reset)
   * @param <Ex> An action-specific exception that might be throwns.
   * @throws IOException If performing the operation threw an IOException.
   * @throws InterruptedException If performing the operation threw an InterruptedException.
   * @throws ExecutorException If the executor threw an exception while performing the operation.
   * @throws Ex Custom exception thrown by the {@link Operation}.
   */
  public static <Ex extends Throwable> void performRequestWithReinitCredentials(
      RepeatOperation<Ex> a, int retries)
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
          throw new IOException(Messages.AbstractUpload_ExceptionFileUpload(), e);
        }
      }
      // Other exceptions are raised further for the client to deal with
    } while (a.moreWork());
  }

  public static <Ex extends Throwable> void performRequestWithReinitCredentials(
      RepeatOperation<Ex> a) throws IOException, InterruptedException, ExecutorException, Ex {
    performRequestWithReinitCredentials(a, MAX_REMOTE_CREDENTIAL_EXPIRED_RETRIES);
  }
}
