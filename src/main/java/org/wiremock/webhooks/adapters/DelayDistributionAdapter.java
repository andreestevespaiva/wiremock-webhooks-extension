/*
 * Copyright (C) 2015-2021 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wiremock.webhooks.adapters;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.tomakehurst.wiremock.http.DelayDistribution;
import com.github.tomakehurst.wiremock.http.LogNormal;
import com.github.tomakehurst.wiremock.http.UniformDistribution;

/**
 * Distribution that models delays.
 *
 * <p>Implementations should be thread safe.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = LogNormal.class, name = "lognormal"),
  @JsonSubTypes.Type(value = UniformDistribution.class, name = "uniform"),
  @JsonSubTypes.Type(value = FixedDelayDistribution.class, name = "fixed")
})
public interface DelayDistributionAdapter extends DelayDistribution {
  /**
   * Samples a delay in milliseconds from the distribution.
   *
   * @return next delay in millis
   */
  long sampleMillis();
}
