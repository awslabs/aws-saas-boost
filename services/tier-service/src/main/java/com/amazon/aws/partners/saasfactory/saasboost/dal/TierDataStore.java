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
    Tier getTier(String id) throws TierNotFoundException;

    List<Tier> listTiers();

    Tier createTier(Tier tier);

    void deleteTier(String id) throws TierNotFoundException;

    /**
     * Updates the {@link Tier} with the same id as the provided Tier.
     *
     * @param newTier the Tier to update and the new data to supplant the old data
     * @throws TierNotFoundException if there is no Tier with that id
     */
    void updateTier(Tier newTier) throws TierNotFoundException;
}