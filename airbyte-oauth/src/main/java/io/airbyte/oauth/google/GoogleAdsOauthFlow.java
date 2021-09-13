/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.oauth.google;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.config.persistence.ConfigRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GoogleAdsOauthFlow extends GoogleOAuthFlow {

  public GoogleAdsOauthFlow(ConfigRepository configRepository) {
    super(configRepository, "https://www.googleapis.com/auth/adwords");
  }

  @Override
  protected String getClientIdUnsafe(JsonNode config) {
    // the config object containing client ID and secret is nested inside the "credentials" object
    return super.getClientIdUnsafe(config.get("credentials"));
  }

  @Override
  protected String getClientSecretUnsafe(JsonNode config) {
    // the config object containing client ID and secret is nested inside the "credentials" object
    return super.getClientSecretUnsafe(config.get("credentials"));
  }

  @Override
  protected Map<String, Object> completeOAuthFlow(String clientId, String clientSecret, String code, String redirectUrl) throws IOException {
    // the config object containing refresh token is nested inside the "credentials" object
    Map<String, Object> oauthFlowOutput = super.completeOAuthFlow(clientId, clientSecret, code, redirectUrl);
    HashMap<String, Object> nestedOutput = new HashMap<>();
    nestedOutput.put("credentials", oauthFlowOutput);
    return nestedOutput;
  }
}
