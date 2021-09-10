/**
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
package com.amazon.aws.partners.saasfactory.metering.onboarding;

import com.amazon.aws.partners.saasfactory.metering.common.EventBridgeEvent;
import com.amazon.aws.partners.saasfactory.metering.common.MeteredProduct;
import com.amazon.aws.partners.saasfactory.metering.common.SubscriptionPlan;
import com.amazon.aws.partners.saasfactory.saasboost.ApiGatewayHelper;
import com.amazon.aws.partners.saasfactory.saasboost.ApiRequest;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.StripeResponse;
import com.stripe.param.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;

import java.time.Instant;
import java.util.*;

public class BillingIntegration implements RequestHandler<EventBridgeEvent, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(BillingIntegration.class);
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String BILL_PUBLISH_EVENT = System.getenv("BILL_PUBLISH_EVENT");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private EventBridgeClient eventBridge;

    public BillingIntegration() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }

        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public Object setupBillingSystemListener(EventBridgeEvent event, Context context) {
        LOGGER.info("setupBillingSystemListener: Start System Data Setup in Billing System");
        if (Utils.isBlank(BILL_PUBLISH_EVENT)) {
            throw new IllegalStateException("Missing required environment variable BILL_PUBLISH_EVENT");
        }
        /*
        This will setup the product and prices in Stripe as MASTER DATA.
        If you use a different billing system then this code would be modified.
         */
        try {
            LOGGER.info("setupBillingSystemListener: Get Stripe API Key");
            Stripe.apiKey = getStripeAPIKey();
            LOGGER.info("setupBillingSystemListener: Create Metered Products");
            createMeteredProducts();
            LOGGER.info("setupBillingSystemListener: Create Subscription Products in Stripe");
            createSubscriptionProducts();
        } catch (Exception e) {
            LOGGER.error("setupBillingSystemListener: Error creating metered products.");
            LOGGER.error(Utils.getFullStackTrace(e));
            //throw e;
        }

        LOGGER.info("setupBillingSystemListener: Completed Product Data Setup in Billing System");

        LOGGER.info("setupBillingSystemListener: Enable Event Bus Rule {} for metering event aggregation", BILL_PUBLISH_EVENT);

        //Enable the Event Rule for Billing
        eventBridge.enableRule(EnableRuleRequest.builder()
                        .eventBusName("default")
                        .name(BILL_PUBLISH_EVENT)
                        .build());
        return null;
    }

    private void createMeteredProducts() throws StripeException {
        //loop through the defined metered products and create in product master with pricing in stripe
        for (MeteredProduct productEnum : MeteredProduct.values()) {
            //check if product exists and skip
            try {
                Product productFetch = Product.retrieve(productEnum.name());
                LOGGER.info("Product: " + productEnum.name() + " already exists");
                continue;
            } catch (StripeException e) {
                LOGGER.error("createMeteredProducts: Error fetching product {}", productEnum.name());
                LOGGER.error(Utils.getFullStackTrace(e));
            }
            LOGGER.info("createMeteredProducts: Creating Subscription Product: {}", productEnum.name());
            ProductCreateParams prodParams =
                    ProductCreateParams.builder()
                            .setName(productEnum.getLabel())
                            .setId(productEnum.name())
                            .build();
            try {
                Product product = Product.create(prodParams);
                PriceCreateParams priceParams =
                        PriceCreateParams.builder()
                                .setProduct(productEnum.name())
                                .setUnitAmount(productEnum.getAmount())
                                .setCurrency("usd")
                                .setRecurring(
                                        PriceCreateParams.Recurring.builder()
                                                .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                                .setAggregateUsage(PriceCreateParams.Recurring.AggregateUsage.SUM)
                                                .setIntervalCount(1L)
                                                .setUsageType(PriceCreateParams.Recurring.UsageType.METERED)
                                                .build())
                                .build();

                Price price = Price.create(priceParams);
                LOGGER.info("createMeteredProducts: Stripe Price ID for product: {} is {}", productEnum.name(), price.getId());
            } catch (StripeException e) {
                LOGGER.error("createMeteredProducts: Error creating metered product {}", productEnum.name());
                LOGGER.error(Utils.getFullStackTrace(e));
                throw e;
            }
        }
    }

    private void createSubscriptionProducts() throws StripeException {
        for (SubscriptionPlan planEnum : SubscriptionPlan.values()) {
            //don't process the product_none
            if (planEnum == SubscriptionPlan.product_none)  {
                continue;
            }
            //check if product exists and skip
            try {
                Product productFetch = Product.retrieve(planEnum.name());
                LOGGER.info("Product: " + planEnum.name() + " already exists");
                continue;
            } catch (StripeException e) {
                LOGGER.error("createSubscriptionProducts: Error fetching product {}", planEnum.name());
                LOGGER.error(Utils.getFullStackTrace(e));
            }

            LOGGER.info("createSubscriptionProducts: Creating Subscription Product: {}" + planEnum.name());
            ProductCreateParams prodParams =
                    ProductCreateParams.builder()
                            .setName(planEnum.getLabel())
                            .setId(planEnum.name())
                            .build();
            try {
                Product product = Product.create(prodParams);
                PriceCreateParams priceParams =
                        PriceCreateParams.builder()
                                .setProduct(planEnum.name())
                                .setUnitAmount(planEnum.getAmount())  //In cents
                                .setCurrency("usd")
                                .setRecurring(
                                        PriceCreateParams.Recurring.builder()
                                                .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                                .build())
                                .build();

                Price price = Price.create(priceParams);
                LOGGER.info("createSubscriptionProducts: Stripe Price ID for product: {} is {}", planEnum.name(), price.getId());

            } catch (StripeException e) {
                LOGGER.error("createStripeProduct: Error creating Subscription Product: {}", planEnum.name());
                LOGGER.error(Utils.getFullStackTrace(e));
                throw e;
            }
        }
    }

    //get the API key from param store using saas boost setting service
    private String getStripeAPIKey() {
        //invoke SaaS Boost private API to get API Key for Billing
        String apiKey;
        ApiRequest billingApiKeySecret = ApiRequest.builder()
                .resource("settings/BILLING_API_KEY/secret")
                .method("GET")
                .build();
        SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, billingApiKeySecret);
        try {
            String responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "BillingIntegration");
            Map<String, String> setting = Utils.fromJson(responseBody, HashMap.class);
            if (null == setting) {
                throw new RuntimeException("responseBody is invalid");
            }            
            apiKey = setting.get("value");
        } catch (Exception e) {
            LOGGER.error("getStripeAPIKey: Error invoking API settings/BILLING_API/ref");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return apiKey;
      }


     /*
    Triggered by event for "Billing Tenant Setup"
    Will create a customer and subscription for the tenant in the billing system, based on the plan
     */
    public Object setupTenantBillingListener(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);
        LOGGER.info("setupTenantBillingListener: Start Tenant Setup in Billing System");
        try {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            provisionTenantInStripe((String) detail.get("tenantId"), (String) detail.get("planId"));
            LOGGER.info("setupTenantBillingListener: Completed Tenant Setup in Billing System");
        } catch (StripeException e) {
            LOGGER.error("setupTenantBillingListener: Error setting up Tenant in Stripe.");
        }
        return null;
    }

    /*
    Triggered by event for "Billing Tenant Disable"
    Will create a customer and subscription for the tenant in the billing system, based on the plan
    */
    public Object disableTenantBillingListener(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);
        LOGGER.info("disableTenantBillingListener: Start Tenant Disable in Billing System");
        try {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            cancelSubscriptionInStripe((String) detail.get("tenantId"));
            LOGGER.info("disableTenantBillingListener: Completed Tenant Disable in Billing System");
        } catch (StripeException e) {
            LOGGER.error("disableTenantBillingListener: Error disabling Tenant in Stripe.");
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return null;
    }

    private void provisionTenantInStripe(String tenantId, String planId) throws StripeException {
        LOGGER.info("provisionTenantInStripe: Starting...");

        Stripe.apiKey = getStripeAPIKey();

        if (Utils.isBlank(tenantId)) {
            throw new RuntimeException("provisionTenantInStripe: No TenantID found in the event detail");
        }
        if (Utils.isBlank(planId)) {
            throw new RuntimeException("provisionTenantInStripe: No PlanId found in the event detail");
        }
        SubscriptionPlan planEnum;
        try {
            planEnum = SubscriptionPlan.valueOf(planId.toLowerCase());
        } catch (IllegalArgumentException e) {
            LOGGER.error("provisionTenantInStripe: No PlanID: " + planId + " matched to SubscriptionPlanEnum");
            throw new RuntimeException("provisionTenantInStripe: Invalid PlanID: '" + planId + "' in the event detail");
        }

        final String customerName = "tenant-" + tenantId.split("-")[0];  //get first segment
        LOGGER.info("provisionTenantInStripe: Setting up Tenant: {} as a customer and creating subscription", customerName);

        Customer customer = getStripeCustomer(customerName);

        //Only attempt to setup tenant if customer does not already exist for this tenant
        if (null == customer) {
            LOGGER.error("No existing customer: {} found in Stripe, so one will be created.", customerName);
            if (planEnum == SubscriptionPlan.product_none)  {
                LOGGER.info("Plan of {} for tenant {} will not be setup for billing.", planEnum.name(), tenantId);
                return;
            }
            try {
                CustomerCreateParams params =
                        CustomerCreateParams.builder()
                                .setName(customerName)
                                //.setEmail("bogus@example.com")
                                .setPaymentMethod("pm_card_visa")
                                .setInvoiceSettings(
                                        CustomerCreateParams.InvoiceSettings.builder()
                                                .setDefaultPaymentMethod("pm_card_visa")
                                                .build())
                                .setDescription(customerName + " from AWS SaaS Boost")
                                .build();
                customer = Customer.create(params);
                //create the subscription
                createStripeSubscription(customer, planEnum, tenantId);
                LOGGER.info("provisionTenantInStripe: Customer created with id: " + customer.getId());
            } catch (StripeException e) {
                LOGGER.error("provisionTenantInStripe: Error creating Customer for Tenant: " + tenantId);
                LOGGER.error(Utils.getFullStackTrace(e));
                throw e;
            }
        } else {
            LOGGER.info("provisionTenantInStripe: Customer: {} create skipped, already exists with id: {} ", customerName, customer.getId());
            //If subscription plan has changed then cancel the old subscriptions and create a new.
            //Ideally, the subscription information for the tenant would be stored in a DDB table so we do not have to hit Stripe to find if it has changed

            //Compare plan with what is on subscription item in Stripe to determine if plan has changed

            //get subscriptions for customer
            try {
                final SubscriptionListParams subscriptionListParams = SubscriptionListParams.builder().setCustomer(customer.getId()).build();
                final SubscriptionCollection collection = Subscription.list(subscriptionListParams);
                for (final Subscription subscription : collection.getData()) {
                    LOGGER.info("provisionTenantInStripe: check Stripe subscription items for matching plan");
                    for (SubscriptionItem si : subscription.getItems().getData()) {
                        if (si.getPrice().getProduct().equalsIgnoreCase(planId)) {
                            LOGGER.info("Subscription Plan has not changed for the tenant, nothing to do");
                            return;
                        }
                    }
                    //NOTE: Do queued metered events need to sent to Stripe before canceling the subscription?

                    //attempt to cancel the subscription
                    LOGGER.info("provisionTenantInStripe: No match in Stripe subscription items for plan {}", planId);
                    LOGGER.info("provisionTenantInStripe: Attempt to Cancel Subscription: {} ", subscription.getId());
                    Subscription canceledSubscription = subscription.cancel();
                    LOGGER.info("provisionTenantInStripe: Canceled Subscription: {} ", subscription.getId());
                }

            } catch (StripeException se) {
                //this means cancel failed
                LOGGER.error("Error canceling subscription in Stripe for customer: {}", customerName);
                LOGGER.error(Utils.getFullStackTrace(se));
                throw se;
            }

            //create the subscription for the new plan id.
            if (planEnum == SubscriptionPlan.product_none)  {
                LOGGER.info("Plan of {} for tenant {} will not be setup for billing.", planEnum.name(), tenantId);
                return;
            }

            createStripeSubscription(customer, planEnum, tenantId);
        }
    }

    /*
    Cancels active subscriptions in Stripe for a Tenant
     */
    private void cancelSubscriptionInStripe(String tenantId) throws StripeException {
        LOGGER.info("cancelSubscriptionInStripe: Starting...");

        try {
            Stripe.apiKey = getStripeAPIKey();
        } catch (Exception e) {
            LOGGER.error("No api key found so skipping subscription cancellation");
            return;
        }

        if (Utils.isBlank(tenantId)) {
            throw new RuntimeException("cancelSubscriptionInStripe: No tenantId found in the event detail");
        }

        final String customerName = "tenant-" + tenantId.split("-")[0];  //get first segment
        LOGGER.info("cancelSubscriptionInStripe: Cancel Tenant: " + customerName + " subscriptions");

        Customer customer = getStripeCustomer(customerName);
        if (null != customer) {
            LOGGER.info("cancelSubscriptionInStripe: Customer: {} exists, cancel active subscriptions for customer id: {}", customerName, customer.getId());
        } else {
            LOGGER.info("cancelSubscriptionInStripe: Customer: {} not found in Stripe, skip subscription cancellation", customerName);
            return;
        }

        //get subscriptions for customer and then disable
        try {
            final SubscriptionListParams subscriptionListParams = SubscriptionListParams.builder().setCustomer(customer.getId()).build();
            final SubscriptionCollection collection = Subscription.list(subscriptionListParams);
            for (final Subscription subscription : collection.getData()) {
                //NOTE: Do queued metered events need to sent to Stripe before canceling the subscription?

                //attempt to cancel the subscription
                LOGGER.info("cancelSubscriptionInStripe: Cancel Subscription: {} ", subscription.getId());
                Subscription canceledSubscription = subscription.cancel();
            }
        } catch (StripeException se) {
            //this means cancel failed
            LOGGER.error("Error canceling subscription in Stripe for customer: {}", customerName);
            LOGGER.error(Utils.getFullStackTrace(se));
        }
    }

    private Customer getStripeCustomer(final String customerId) throws StripeException {
        //Only attempt to disable tenant if customer exists
        Map<String, Customer> custIdMap = new LinkedHashMap<>();
        boolean hasMore = false;
        String startingAfter = null;
        LOGGER.info("Find Stripe customer with name: {} ", customerId);
        do {
            CustomerListParams customerListParams = CustomerListParams.builder().setLimit(100l).setStartingAfter(startingAfter).build();
            CustomerCollection customerCollection = Customer.list(customerListParams);
            hasMore = customerCollection.getHasMore();
            for (Customer customer : customerCollection.getData()) {
                if (customerId.equals(customer.getName())) {
                    return customer;
                }
                startingAfter = customer.getId();
            }
        } while (hasMore);
        return null;
    }


    /*
    Creates a subscription in Stripe
    The Subscription will have one item for the monthly plan and additional items for metered products
    For the metered products, a message is sent to Event Bus to add that record to the Dynanamo table mapping
     the Tenant internal product code to the Stripe Subscription id which is unique for each tenant and product.
     */
    private void createStripeSubscription(Customer customer, SubscriptionPlan plan, String fullTenantId) throws StripeException{
        //get the PRICE ID for the product code of the plan.  This is for the monthly subscription
        final String planPriceId = getPriceId(plan.name());
        List<SubscriptionCreateParams.Item> items = new ArrayList<>();
        items.add(SubscriptionCreateParams.Item.builder()
                .setPrice(planPriceId)
                .build());

        //loop through the metered items for this plan and build items for the metered products on this plan
        MeteredProduct[] meteredProducts = plan.getMeteredProducts();
        if (null != meteredProducts) {
            for (int i = 0; i < meteredProducts.length; i++) {
                MeteredProduct product = meteredProducts[i];
                final String priceId = getPriceId(product.name());
                items.add(SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .build());
            }
        }

        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customer.getId())
                .addAllItem(items).build();

        try {
            Subscription subscription = Subscription.create(params);
            LOGGER.info(("createStripeSubscription: Subscription created with id: " + subscription.getId()));
            //Add a config record into DDB table for the subscription plan by sending a message to EventBridge
            LOGGER.info("createStripeSubscription: Send message to add Plan Id to Tenant Billing config record");
            Map<String, Object> systemApiRequest = new HashMap<>();
            systemApiRequest.put("tenantId", fullTenantId);
            systemApiRequest.put("internalProductCode", "plan_id");
            systemApiRequest.put("externalProductCode", plan.toString());
            systemApiRequest.put("timestamp", Instant.now().toEpochMilli());     //epoch time in UTC
            putTenantProductOnboardEvent(Utils.toJson(systemApiRequest));

            //now process each of the metered products.
            for (SubscriptionItem item : subscription.getItems().getData()) {
                LOGGER.info("createStripeSubscription: Item :" + item.getId() + ", " + item.getPrice() + " has subscription id: " + item.getSubscription());
                try {
                    MeteredProduct product = MeteredProduct.valueOf(item.getPrice().getProduct());
                } catch (IllegalArgumentException e) {
                    LOGGER.debug("createStripeSubscription: Product {} is not a metered product", item.getPrice().getProduct());
                    continue;
                    //eat this exception as it means the item is not a metered product
                }
                //Add a config record into DDB table for each metered product by sending a message to EventBridge
                LOGGER.info("createStripeSubscription: Send message to add Tenant Product record");
                systemApiRequest = new HashMap<>();
                systemApiRequest.put("tenantId", fullTenantId);
                systemApiRequest.put("internalProductCode", item.getPrice().getProduct());
                systemApiRequest.put("externalProductCode", item.getId());
                systemApiRequest.put("timestamp", Instant.now().toEpochMilli());     //epoch time in UTC
                putTenantProductOnboardEvent(Utils.toJson(systemApiRequest));
            }
        } catch (StripeException e) {
            LOGGER.error("createStripeSubscription: Error creating Subscription for Tenant: " + customer.getId());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
    }

    /*
    Get Price ID from Stripe using the productId
     */
    private static String getPriceId(final String productId) {
        PriceListParams listParams =
                PriceListParams.builder()
                        .setProduct(productId)
                        .build();
        String retVal = null;
        try {
            PriceCollection prices = Price.list(listParams);
            List<Price> priceList = prices.getData();
            Price price = priceList.get(0);
            LOGGER.error("Price ID for product: " + productId + " is " + price.getId());
            retVal = price.getId();
        } catch (StripeException e) {
            LOGGER.error("Error getting price for Product: " + productId, e);
        }
        return retVal;
    }

    /*
    Put metering event on EventBridge
    */
    private void putTenantProductOnboardEvent(String eventBridgeDetail) {
        PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                .eventBusName(SAAS_BOOST_EVENT_BUS)
                .detailType("Tenant Product Onboard")
                .source("saas-boost")
                .detail(eventBridgeDetail)
                .build();
        PutEventsResponse eventBridgeResponse = this.eventBridge.putEvents(r -> r
                .entries(systemApiCallEvent)
        );

        for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
            if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                LOGGER.info("Put event success {} {}", entry.toString(), systemApiCallEvent.toString());
            } else {
                LOGGER.error("Put event failed {}", entry.toString());
            }
        }
    }

    @Override
    public Object handleRequest(EventBridgeEvent eventBridgeEvent, Context context) {
        return null;
    }
}

