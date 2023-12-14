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

package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.marketplacecatalog.MarketplaceCatalogClient;

import java.util.Collection;
import java.util.Collections;

public class AwsMarketplaceApi extends AbstractBillingProviderApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeApi.class);
    private final MarketplaceCatalogClient marketplace;

    public AwsMarketplaceApi(IamCredentials credentials, String catalogEntityId) {
        this(credentials, new DefaultDependencyFactory(catalogEntityId));
    }

    public AwsMarketplaceApi(IamCredentials credentials, AwsMarketplaceApiDependencyFactory init) {
        super(credentials);
        this.marketplace = init.marketplace();
    }

    @Override
    public Collection<BillingPlan> getPlans() {
        // Marketplace Product & Pricing Dimensions
        return Collections.emptyList();
    }

    interface AwsMarketplaceApiDependencyFactory {

        String catalogEntityId();

        MarketplaceCatalogClient marketplace();
    }

    private static final class DefaultDependencyFactory implements AwsMarketplaceApiDependencyFactory {

        private String catalogEntityId;

        public DefaultDependencyFactory(String catalogEntityId) {
            this.catalogEntityId = catalogEntityId;
        }

        @Override
        public String catalogEntityId() {
            return catalogEntityId;
        }

        @Override
        public MarketplaceCatalogClient marketplace() {
            return Utils.sdkClient(MarketplaceCatalogClient.builder(), MarketplaceCatalogClient.SERVICE_NAME);
        };
    }
}
