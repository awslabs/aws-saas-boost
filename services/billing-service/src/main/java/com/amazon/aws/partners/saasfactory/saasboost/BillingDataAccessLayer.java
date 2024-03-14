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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BillingDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingDataAccessLayer.class);
    private final String billingTable;
    private final DynamoDbClient ddb;
    private final SecretsManagerClient secrets;
    private final String providerConfigSecretId;

    public BillingDataAccessLayer(DynamoDbClient ddb, String billingTable,
                                  SecretsManagerClient secrets, String providerConfigSecretId) {
        this.billingTable = billingTable;
        this.ddb = ddb;
        this.secrets = secrets;
        this.providerConfigSecretId = providerConfigSecretId;

        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(request -> request.tableName(billingTable));
    }

    public List<BillingPlan> getPlans() {
        BillingProviderConfig providerConfig = getProviderConfig();
        List<BillingPlan> plans = new ArrayList<>();

        plans.addAll(mockBillingPlans());
        /*
        if (providerConfig != null) {
            try {
                QueryResponse response = ddb.query(request -> request
                        .tableName(billingTable)
                );
                response.items().forEach(item ->
                        plans.add(fromAttributeValueMap(item))
                );
            } catch (DynamoDbException e) {
                LOGGER.error(Utils.getFullStackTrace(e));
                throw new RuntimeException(e);
            }
        }
        */
        return plans;
    }

    private List<BillingPlan> mockBillingPlans() {
        BillingPlan standardPlan = BillingPlan.builder()
                .id(UUID.randomUUID().toString())
                .build();
        BillingPlan enterprisePlan = BillingPlan.builder()
                .id(UUID.randomUUID().toString())
                .build();
        return List.of(standardPlan, enterprisePlan);
    }

    public List<BillingProviderConfig> getAvailableProviders() {
        List<BillingProviderConfig> providers = new ArrayList<>();
        BillingProviderConfig activeProvider = getProviderConfig();
        for (BillingProvider.ProviderType type : BillingProvider.ProviderType.values()) {
            if (activeProvider != null && type == activeProvider.getType()) {
                providers.add(activeProvider);
            } else {
                providers.add(new BillingProviderConfig(type));
            }
        }
        return providers;
    }

    public BillingProviderConfig getProviderConfig() {
        GetSecretValueResponse response = secrets.getSecretValue(request -> request
                .secretId(providerConfigSecretId)
        );
        return Utils.fromJson(response.secretString(), BillingProviderConfig.class);
    }

    public void setProviderConfig(BillingProviderConfig providerConfig) {
        secrets.putSecretValue(request -> request
                .secretId(providerConfigSecretId)
                .secretString(Utils.toJson(providerConfig))
        );
    }
}
