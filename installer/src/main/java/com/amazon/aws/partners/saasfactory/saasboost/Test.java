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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class Test {
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));

    public static void main(String[] args) throws IOException, URISyntaxException {
/*
        String s = "arn:aws:cloudformation:us-west-2:123456789:stack/Tenant-dbbaf53e/44aa05c0-dceb-11ea-acf3-0aa643d29db2";
        String myUrl = "https://test.com?a=" + s;
        URL u = new URL(myUrl);

        System.out.println("Encoded url = " + URLEncoder.encode(myUrl, StandardCharsets.UTF_8.toString()));
        String newUrl = new URI(
                u.getProtocol(),
                u.getAuthority(),
                u.getPath(),
                u.getQuery(),
                u.getRef()).toASCIIString();
        System.out.println("new Url" + newUrl);

//        SaaSBoostInstall sb = new SaaSBoostInstall();
//        System.out.print("Version Info: " + sb.getVersionInfo());

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        File file = new File("../resources/saas1-boost.yaml");

        Map<String, Object> map = mapper.readValue(file, HashMap.class);
        Map<String, Object> parameterObjectMap = (LinkedHashMap<String, Object>) map.get("Parameters");
        //System.out.println("SaaSBoost Bucket = " + ((Map) parameterObjectMap.get("SaaSBoostBucket")).get("Default"));

        ObjectMapper mapper1 = new ObjectMapper();
        Map<String, CloudFormationParameter> parameterMap = new LinkedHashMap<>();
        for(Map.Entry<String, Object> entry : parameterObjectMap.entrySet()) {
            System.out.println("Entry = " + entry.getKey());
            CloudFormationParameter p = mapper1.convertValue((Map) entry.getValue(), CloudFormationParameter.class);
            System.out.println("Parameter = " + p.toString());
            parameterMap.put(entry.getKey(), p);
        }


        String existingLambdaSourceFolder = "lambdas-2020-09-22-14-59";
        String s3ArtifactBucket = "sb-sb-artifacts-e0d0e7e0-d3b3";
        System.out.println("Delete previous Lambda files from S3 artifacts folder: " + existingLambdaSourceFolder);
        AmazonS3 s3 = AmazonS3Client.builder().withRegion("us-west-2").build();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(s3ArtifactBucket).withPrefix(existingLambdaSourceFolder + "/");
        ObjectListing listing = s3.listObjects(listObjectsRequest);
        List<S3ObjectSummary> objects = listing.getObjectSummaries();
        ArrayList<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        for (S3ObjectSummary obj : objects) {
            System.out.println("Key to delete: " + obj.getKey());
            //keys.add(new DeleteObjectsRequest.KeyVersion(obj.getKey()));
        }
        keys.add(new DeleteObjectsRequest.KeyVersion("lambdas-2020-09-22-14-59/RdsBootstrap-lambda.zip"));
        DeleteObjectsRequest request = new DeleteObjectsRequest(s3ArtifactBucket).withKeys(keys);
        s3.deleteObjects(request);
 */

        System.out.println("Update API Gateway Deployment");
        ApiGatewayClient apiGatewayClient = ApiGatewayClient.builder().region(AWS_REGION).build();
        String envName = "sb-sep21";

        GetRestApisResponse response = apiGatewayClient.getRestApis();
        for (RestApi api : response.items()) {
            System.out.println(api);
            if (api.name().equalsIgnoreCase("sb-public-api-" + envName + "-" + AWS_REGION.id())
                    || api.name().equalsIgnoreCase("sb-private-api-" + envName + "-" + AWS_REGION.id())) {
                System.out.println("Create deployment for " + api.name());
                apiGatewayClient.createDeployment(CreateDeploymentRequest.builder()
                        .restApiId(api.id())
                        .stageName("v1")
                        .build());
            }
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloudFormationParameter {
        String description;
        String  type;
        String minLength;
        String defaultValue;
        List<String> allowedValues;
        String allowedPattern;

        @JsonProperty("Default")
        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
        @JsonProperty("Description")
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @JsonProperty("Type")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @JsonProperty("MinLength")
        public String getMinLength() {
            return minLength;
        }

        public void setMinLength(String minLength) {
            this.minLength = minLength;
        }

        @JsonProperty("AllowedValues")
        public List getAllowedValues() {
            return allowedValues;
        }

        public void setAllowedValues(List allowedValues) {
            this.allowedValues = allowedValues;
        }

        @JsonProperty("AllowedPattern")
        public String getAllowedPattern() {
            return allowedPattern;
        }

        public void setAllowedPattern(String allowedPattern) {
            this.allowedPattern = allowedPattern;
        }

        @Override
        public String toString() {
            return "Parameter{" +
                    "description='" + description + '\'' +
                    ", type='" + type + '\'' +
                    ", minLength='" + minLength + '\'' +
                    ", allowedValues='" + (null == allowedValues ? "" : Arrays.toString(allowedValues.toArray())) + '\'' +
                    ", allowedPattern='" + allowedPattern + '\'' +
                    '}';
        }
    }
}
