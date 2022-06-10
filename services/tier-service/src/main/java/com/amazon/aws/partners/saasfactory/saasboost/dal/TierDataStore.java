/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost.dal;

import com.amazon.aws.partners.saasfactory.saasboost.TierService;
import com.amazon.aws.partners.saasfactory.saasboost.dal.exception.TierNotFoundException;
import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;

import java.util.List;

/**
 * TierDataStore represents the backing datastore required for the {@link TierService} to function.
 *
 * Implementations of this interface connect the DAL with the actual backing datastore and will need an adapter to
 * convert between the model definition of {@link Tier} and the datastore required definition.
 */
public interface TierDataStore {
    Tier getTier(String id);

    List<Tier> listTiers();

    Tier createTier(Tier tier);

    void deleteTier(String id);

    /**
     * Updates the {@link Tier} with the same id as the provided Tier.
     *
     * @param newTier the Tier to update and the new data to supplant the old data
     * @returns the updated Tier
     * @throws TierNotFoundException if there is no Tier with that id
     */
    Tier updateTier(Tier newTier);
}