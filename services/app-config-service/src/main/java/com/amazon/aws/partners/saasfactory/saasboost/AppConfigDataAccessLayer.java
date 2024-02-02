package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class AppConfigDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigDataAccessLayer.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private final String appConfigTable;
    private final DynamoDbClient ddb;
    private final AcmClient acm;
    private final Route53Client route53;

    public AppConfigDataAccessLayer(DynamoDbClient ddb, String appConfigTable, AcmClient acm, Route53Client route53) {
        this.appConfigTable = appConfigTable;
        this.ddb = ddb;
        this.acm = acm;
        this.route53 = route53;
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(request -> request.tableName(appConfigTable));
    }

    public AppConfig getAppConfig() {
        AppConfig appConfig = null;
        try {
            ScanResponse response = ddb.scan(request -> request.tableName(appConfigTable));
            if (response.hasItems()) {
                if (response.items().size() == 1) {
                    appConfig = fromAttributeValueMap(response.items().get(0));
                } else if (response.items().size() > 1) {
                    LOGGER.warn("Unexpected number of appConfig items {}", response.items().size());
                }
            }
        } catch (DynamoDbException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return appConfig;
    }

    public AppConfig insertAppConfig(AppConfig appConfig) {
        // Unique identifier is owned by the DAL
        if (appConfig.getId() != null) {
            throw new IllegalArgumentException("Can't insert a new appConfig that already has an id");
        }
        UUID appConfigId = UUID.randomUUID();
        appConfig.setId(appConfigId);

        // Created and Modified are owned by the DAL since they reflect when the
        // object was persisted
        LocalDateTime now = LocalDateTime.now();
        appConfig.setCreated(now);
        appConfig.setModified(now);
        Map<String, AttributeValue> item = toAttributeValueMap(appConfig);
        try {
            ddb.putItem(request -> request.tableName(appConfigTable).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        return appConfig;
    }

    // Choosing to do a replacement update as you might do in a RDBMS by
    // setting columns = NULL when they do not exist in the updated value
    public AppConfig updateAppConfig(AppConfig appConfig) {
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            appConfig.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(appConfig);
            ddb.putItem(request -> request.tableName(appConfigTable).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::updateOnboarding " + Utils.getFullStackTrace(e));
            throw e;
        }
        return appConfig;
    }

    public void deleteAppConfig(AppConfig appConfig) {
        try {
            ddb.deleteItem(request -> request
                    .tableName(appConfigTable)
                    .key(Map.of("id", AttributeValue.builder().s(appConfig.getId().toString()).build()))
            );
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> rdsOptions() {
        List<Map<String, Object>> orderableOptionsByRegion = new ArrayList<>();
        QueryResponse response = ddb.query(request -> request
                .tableName(appConfigTable)
                .keyConditionExpression("#region = :region")
                .expressionAttributeNames(Map.of("#region", "region"))
                .expressionAttributeValues(Map.of(":region", AttributeValue.builder().s(AWS_REGION).build()))
        );
        response.items().forEach(item ->
                orderableOptionsByRegion.add(fromRdsOptionsAttributeValueMap(item))
        );
        return orderableOptionsByRegion;
    }

    public List<CertificateSummary> acmCertificateOptions() {
        List<CertificateSummary> certificateSummaries = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                // only list certificates that aren't expired, invalid, revoked, or otherwise unusable
                ListCertificatesResponse response = acm.listCertificates(ListCertificatesRequest.builder()
                        .certificateStatuses(List.of(CertificateStatus.PENDING_VALIDATION, CertificateStatus.ISSUED))
                        .nextToken(nextToken)
                        .build());
                LOGGER.info("ACM PENDING_VALIDATION and ISSUED certs: {}", response);
                if (response.certificateSummaryList() != null) {
                    certificateSummaries.addAll(response.certificateSummaryList());
                }
                nextToken = response.nextToken();
            } catch (InvalidArgsException iae) {
                LOGGER.error("Error retrieving certificates", iae);
            }
        } while (nextToken != null);
        return certificateSummaries;
    }

    public List<HostedZone> hostedZoneOptions() {
        List<HostedZone> allHostedZones = new ArrayList<>();
        String marker = null;
        do {
            ListHostedZonesResponse response = route53.listHostedZones(ListHostedZonesRequest.builder()
                    .marker(marker)
                    .build());
            LOGGER.info("Listed hostedZones: {}", response);
            if (response.hasHostedZones() && response.hostedZones() != null) {
                // we only want to list public zones, since we're attaching them to an internet-facing
                // application load balancer for the tenant
                for (HostedZone zone : response.hostedZones()) {
                    if (zone.config() != null && !zone.config().privateZone()) {
                        allHostedZones.add(zone);
                    }
                }
            }
            marker = response.marker();
        } while (marker != null);
        return allHostedZones;
    }

    protected static Map<String, AttributeValue> toAttributeValueMap(AppConfig appConfig) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(appConfig.getId().toString()).build());
        if (appConfig.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(
                    appConfig.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (appConfig.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(
                    appConfig.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (appConfig.getName() != null) {
            item.put("name", AttributeValue.builder().s(appConfig.getName()).build());
        }
        if (appConfig.getDomainName() != null) {
            item.put("domain_name", AttributeValue.builder().s(appConfig.getDomainName()).build());
        }
        if (appConfig.getHostedZone() != null) {
            item.put("hosted_zone", AttributeValue.builder().s(appConfig.getHostedZone()).build());
        }
        if (appConfig.getSslCertificate() != null) {
            item.put("ssl_certificate", AttributeValue.builder().s(appConfig.getSslCertificate()).build());
        }
        if (appConfig.getServices() != null && !appConfig.getServices().isEmpty()) {
            item.put("services", AttributeValue.builder().m(appConfig.getServices().entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> AttributeValue.builder().s(Utils.toJson(entry.getValue())).build())
                    )
            ).build());
        }
        return item;
    }

    protected static AppConfig fromAttributeValueMap(Map<String, AttributeValue> item) {
        AppConfig appConfig = null;
        if (item != null && !item.isEmpty()) {
            appConfig = new AppConfig();
            if (item.containsKey("id")) {
                try {
                    appConfig.setId(UUID.fromString(item.get("id").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse UUID from database: " + item.get("id").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("created")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    appConfig.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("modified").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    appConfig.setModified(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("modified").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("name")) {
                appConfig.setName(item.get("name").s());
            }
            if (item.containsKey("domain_name")) {
                appConfig.setDomainName(item.get("domain_name").s());
            }
            if (item.containsKey("hosted_zone")) {
                appConfig.setHostedZone(item.get("hosted_zone").s());
            }
            if (item.containsKey("ssl_certificate")) {
                appConfig.setSslCertificate(item.get("ssl_certificate").s());
            }
            final Map<String, ServiceConfig> services = new LinkedHashMap<>();
            if (item.containsKey("services")) {
                item.get("services").m().entrySet().forEach(
                        entry -> services.put(entry.getKey(), Utils.fromJson(entry.getValue().s(), ServiceConfig.class))
                );
            }
        }
        return appConfig;
    }

    private static final Comparator<Map<String, String>> INSTANCE_TYPE_COMPARATOR = ((instance1, instance2) -> {
        // T's before M's before R's
        int compare = 0;
        char type1 = instance1.get("instance").charAt(0);
        char type2 = instance2.get("instance").charAt(0);
        if (type1 != type2) {
            if ('T' == type1) {
                compare = -1;
            } else if ('T' == type2) {
                compare = 1;
            } else if ('M' == type1) {
                compare = -1;
            } else if ('M' == type2) {
                compare = 1;
            }
        }
        return compare;
    });

    private static final Comparator<Map<String, String>> INSTANCE_GENERATION_COMPARATOR = ((instance1, instance2) -> {
        Integer gen1 = Integer.valueOf(instance1.get("instance").substring(1, 2));
        Integer gen2 = Integer.valueOf(instance2.get("instance").substring(1, 2));
        return gen1.compareTo(gen2);
    });

    private static final Comparator<Map<String, String>> INSTANCE_SIZE_COMPARATOR = ((instance1, instance2) -> {
        String size1 = instance1.get("instance").substring(3);
        String size2 = instance2.get("instance").substring(3);
        List<String> sizes = Arrays.asList(
                "MICRO",
                "SMALL",
                "MEDIUM",
                "LARGE",
                "XL",
                "2XL",
                "4XL",
                "12XL",
                "24XL"
        );
        return Integer.compare(sizes.indexOf(size1), sizes.indexOf(size2));
    });

    public static final Comparator<Map<String, String>> RDS_INSTANCE_COMPARATOR = INSTANCE_TYPE_COMPARATOR
            .thenComparing(INSTANCE_GENERATION_COMPARATOR)
            .thenComparing(INSTANCE_SIZE_COMPARATOR);

    public static Map<String, Object> fromRdsOptionsAttributeValueMap(Map<String, AttributeValue> item) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("engine", item.get("engine").s());
        option.put("region", item.get("region").s());
        Map<String, AttributeValue> optionAttributes = item.get("options").m();
        option.put("name", optionAttributes.get("name").s());
        option.put("description", optionAttributes.get("description").s());

        List<Map<String, Object>> versions = new ArrayList<>();
        for (AttributeValue versionAttribute : optionAttributes.get("versions").l()) {
            // build the version entry
            Map<String, AttributeValue> versionAttributeMap = versionAttribute.m();
            Map<String, Object> version = new LinkedHashMap<>(); // use a linked map so we can sort
            version.put("description", versionAttributeMap.get("description").s());
            version.put("family", versionAttributeMap.get("family").s());
            version.put("version", versionAttributeMap.get("version").s());

            List<Map<String, String>> instances = new ArrayList<>();
            Map<String, AttributeValue> instancesAttributeMap = versionAttributeMap.get("instances").m();
            for (Map.Entry<String, AttributeValue> instanceAttribute : instancesAttributeMap.entrySet()) {
                Map<String, String> instance = new LinkedHashMap<>(); // use a linked map so we can sort
                instance.put("instance", instanceAttribute.getKey());
                instance.put("class", instanceAttribute.getValue().m().get("class").s());
                instance.put("description", instanceAttribute.getValue().m().get("description").s());
                instances.add(instance);
            }
            Collections.sort(instances, RDS_INSTANCE_COMPARATOR);
            version.put("instances", instances);
            versions.add(version);
        }
        option.put("versions", versions);

        return option;
    }

}
