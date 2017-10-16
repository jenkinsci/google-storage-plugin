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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.WithoutJenkins;

import static com.google.api.client.http.HttpStatusCodes
    .STATUS_CODE_UNAUTHORIZED;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.StubHttpResponseException;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation.Action;
import com.google.jenkins.plugins.storage.util.RetryStorageOperation
    .RepeatAction;
import com.google.jenkins.plugins.util.ExecutorException;
import com.google.jenkins.plugins.util.MockExecutor;

import hudson.FilePath;

/**
 * Tests for {@link StorageUtil}.
 */
public class RetryStorageOperationTest {
  private final MockExecutor executor = new MockExecutor();

  // An action that fails the given number of times before succeeding
  // and then counts the number of successes
  private class FailAction implements Action {
    public int fails;
    public int succeeded;

    public FailAction(int fails) {
      this.fails = fails;
      this.succeeded = 0;
    }

    public void act() throws IOException{
      fails--;
      if(fails >= 0) {
        throw new IOException("bad");
      }
      succeeded++;
    }
  };

  @Test
  @WithoutJenkins
  public void retryNoBudgetTest() throws Exception {
    FailAction action = new FailAction(1);
    try {
      // Fail immediately if there is no retry budget
      RetryStorageOperation.performRequestWithRetry(executor, action, 1);
    } catch (IOException e) {
      assertEquals(0, action.fails);
      assertEquals(0, action.succeeded);
      return;
    }
    Assert.fail("Expected exception");
  }

  @Test
  @WithoutJenkins
  public void retrySuccessTest() throws Exception {
    // Succeed if there is enough budget
    FailAction action = new FailAction(1);
    RetryStorageOperation.performRequestWithRetry(executor, action, 2);
    assertEquals(-1,action.fails);
    assertEquals(1, action.succeeded);
  }

  @Test
  @WithoutJenkins
  public void retryMoreTimesTest() throws Exception {
    // Correctly count retries for larger numbers
    FailAction action = new FailAction(1);
    RetryStorageOperation.performRequestWithRetry(executor, action, 10);
    assertEquals(-1, action.fails);
    assertEquals( 1, action.succeeded);
  }

  @Test
  @WithoutJenkins
  public void retryMoreTimesFailTest() throws Exception {
    FailAction action = new FailAction(9);
    try {
      // Fail immediately if there is no retry budget
      RetryStorageOperation.performRequestWithRetry(executor, action, 5);
    } catch (IOException e) {
      assertEquals( 4, action.fails);
      assertEquals( 0, action.succeeded);
      return;
    }
    Assert.fail("Expected exception");
  }

  @Test
  @WithoutJenkins
  public void retryLostOfBudgetTest() throws Exception {
    // Succeed only once even if there is lots of budget
    FailAction action = new FailAction(1);
    RetryStorageOperation.performRequestWithRetry(executor, action, 10);
    assertEquals(-1, action.fails);
    assertEquals( 1, action.succeeded);
  }

  @Test
  @WithoutJenkins
  public void retryInterruptedException() throws Exception {
    // Interrupted exception is handled as well
    class MixAction implements Action {
      int tries;
      int succeeded = 0;

      MixAction() {
        tries = 10;
      }

      public void act() throws InterruptedException, IOException {
        if (tries == 0) {
          succeeded++;
          return;
        }
        tries--;

        if (tries % 2 == 1) {
          throw new IOException("IOExcpetion");
        }
        throw new InterruptedException();
      }
    };

    MixAction action = new MixAction();

    RetryStorageOperation.performRequestWithRetry(executor, action, 200);
    assertEquals( 0, action.tries);
    assertEquals( 1, action.succeeded);
  }


  private class FailingCredentials implements RepeatAction<NullPointerException>{
    public int credLength;
    public int usesLeft;
    public int stepsLeft;
    public int failures;

    public FailingCredentials(int credLength, int stepsLeft) {
      this.credLength = credLength;
      this.stepsLeft = stepsLeft;
      usesLeft = 0;
      failures = 0;
    }

    public void initCredentials() {
      usesLeft = credLength;
    }

    public void act()
        throws HttpResponseException {
      assert(stepsLeft > 0);

      if (usesLeft <= 0) {
        failures++;
        throw new StubHttpResponseException(STATUS_CODE_UNAUTHORIZED, "No more credentials!");
      }

      usesLeft--;
      stepsLeft--;
    }

    public boolean moreWork() {
      return stepsLeft > 0;
    }
  }

  @Test
  @WithoutJenkins
  public void credsRetry() throws Exception {
    // Perform successful retries
    FailingCredentials cr = new FailingCredentials(2, 10);

    RetryStorageOperation.performRequestWithReinitCredentials(cr, 1);
    assertEquals(0, cr.stepsLeft);
    assertEquals(4, cr.failures); // Needed to refresh 4 times
  }

  @Test
  @WithoutJenkins
  public void credsNoBudget() throws Exception {
    // No retry budget quits after first failure (here that's after credentials
    // expire after 2 steps)
    FailingCredentials cr = new FailingCredentials(2, 10);

    try {
      RetryStorageOperation.performRequestWithReinitCredentials(cr, 0);
    } catch (IOException e) {
      assertEquals(8, cr.stepsLeft);
      return;
    }
    Assert.fail("Expected exception");
  }

  @Test
  @WithoutJenkins
  public void testStuck() throws Exception {
    // This Action gets stuck reloading credentials when 5 steps remaining.
    class StuckCreds extends FailingCredentials {

      public StuckCreds(int credLength, int stepsLeft) {
        super(credLength, stepsLeft);
      }

      public void act()
          throws HttpResponseException {
        if (stepsLeft <= 5) {
          usesLeft = 0;
        }
        super.act();
      }
    }
    StuckCreds cr = new StuckCreds(2, 10);

    try {
      RetryStorageOperation.performRequestWithReinitCredentials(cr, 2);
    } catch (IOException e) {
      assertEquals(5, cr.stepsLeft);
      return;
    }
    Assert.fail("Expected exception");
  }
}
