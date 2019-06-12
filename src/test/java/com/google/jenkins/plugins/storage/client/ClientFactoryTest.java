package com.google.jenkins.plugins.storage.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import java.security.PrivateKey;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests {@link ClientFactory}. */
@RunWith(MockitoJUnitRunner.class)
public class ClientFactoryTest {
  public static final PrivateKey PRIVATE_KEY;
  public static final String ACCOUNT_ID = "test-account-id";
  public static final String PK_ALGO = "test";
  public static final String PK_FORMAT = "test";
  public static final byte[] PK_BYTES = new byte[0];

  static {
    PRIVATE_KEY =
        new PrivateKey() {
          @Override
          public String getAlgorithm() {
            return PK_ALGO;
          }

          @Override
          public String getFormat() {
            return PK_FORMAT;
          }

          @Override
          public byte[] getEncoded() {
            return PK_BYTES;
          }
        };
  }

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ServiceAccountConfig serviceAccountConfig;

  @Before
  public void init() {
    Mockito.when(serviceAccountConfig.getAccountId()).thenReturn(ACCOUNT_ID);
    Mockito.when(serviceAccountConfig.getPrivateKey()).thenReturn(PRIVATE_KEY);
  }

  @Test
  public void defaultTransport() throws Exception {
    Credentials c =
        (Credentials) new GoogleRobotPrivateKeyCredentials(ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    ClientFactory cf =
        new ClientFactory(
            r.jenkins,
            ImmutableList.<DomainRequirement>of(),
            ACCOUNT_ID,
            Optional.<HttpTransport>empty());
    Assert.assertNotNull(cf.storageClient());
  }
}
