package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IllegalFormatException;

public enum AwsResource {

    RDS_CLUSTER("https://%s.console.aws.amazon.com/rds/home#database:id=%s;is-cluster=true",
            "arn:%s:rds:%s:%s:cluster:%s",
            "AWS::RDS::DBCluster", false),
    RDS_INSTANCE("https://%s.console.aws.amazon.com/rds/home?region=%s#dbinstance:id=%s",
            "arn:%s:rds:%s:%s:db:%s",
            "AWS::RDS::DBInstance", true),
    ECS_CLUSTER("https://%s.console.aws.amazon.com/ecs/home#/clusters/%s",
            "arn:%s:ecs:%s:%s:cluster/%s",
            "AWS::ECS::Cluster", false),
    LOG_GROUP("https://%s.console.aws.amazon.com/cloudwatch/home?region=%s#logsV2:log-groups/log-group/%s",
            "arn:%s:logs:%s:%s:log-group:%s",
            "AWS::Logs::LogGroup", true),
    VPC("https://%s.console.aws.amazon.com/vpc/home?region=%s#vpcs:search=%s",
            "arn:%s:ec2:%s:%s:vpc/%s",
            "AWS::EC2::VPC", true),
    PRIVATE_SUBNET_A("https://%s.console.aws.amazon.com/vpc/home?region=%s#SubnetDetails:subnetId=%s",
            "arn:%s:ec2:%s:%s:subnet/%s",
            "AWS::EC2::Subnet", true),
    PRIVATE_SUBNET_B("https://%s.console.aws.amazon.com/vpc/home?region=%s#SubnetDetails:subnetId=%s",
            "arn:%s:ec2:%s:%s:subnet/%s",
            "AWS::EC2::Subnet", true),
    PRIVATE_ROUTE_TABLE("https://%s.console.aws.amazon.com/vpc/home?region=%s#RouteTables:routeTableId=%s",
            "arn:%s:ec2:%s:%s:route-table/%s",
            "AWS::EC2::RouteTable", true),
    CODE_PIPELINE("https://%s.console.aws.amazon.com/codesuite/codepipeline/pipelines/%s/view",
            "arn:%s:codepipeline:%s:%s:%s",
            "AWS::CodePipeline::Pipeline", false),
    ECR_REPO("https://%s.console.aws.amazon.com/ecr/repositories/%s/",
            "arn:%s:ecr:%s:%s:repository/%s",
            "AWS::ECR::Repository", false),
    LOAD_BALANCER("https://%s.console.aws.amazon.com/ec2/v2/home?region=%s#LoadBalancers:search=%s",
            "arn:%s:elasticloadbalancing:%s:%s:loadbalancer/app/%s", // ${LoadBalancerName}/${LoadBalancerId}
            "AWS::ElasticLoadBalancingV2::LoadBalancer", true),
    HTTP_LISTENER("https://%s.console.aws.amazon.com/ec2/v2/home?region=%s#LoadBalancers:search=%s",
            "arn:%s:elasticloadbalancing:%s:%s:listener/app/%s", // ${LoadBalancerName}/${LoadBalancerId}/${ListenerId}
            "AWS::ElasticLoadBalancingV2::Listener", true),
    HTTPS_LISTENER("https://%s.console.aws.amazon.com/ec2/v2/home?region=%s#LoadBalancers:search=%s",
            "arn:%s:elasticloadbalancing:%s:%s:listener/app/%s", // ${LoadBalancerName}/${LoadBalancerId}/${ListenerId}
            "AWS::ElasticLoadBalancingV2::Listener", true),
    CLOUDFORMATION("https://%s.console.aws.amazon.com/cloudformation/home?region=%s#/stacks/stackinfo?filteringStatus=active&viewNested=true&hideStacks=false&stackId=%s",
            "arn:%s:cloudformation:%s:%s:stack/%s",
            "AWS::CloudFormation::Stack", true),
    ECS_SECURITY_GROUP("https://%s.console.aws.amazon.com/ec2/v2/home?region=%s#SecurityGroup:groupId=%s",
            "arn:%s:ec2:%s:%s:security-group/%s",
            "AWS::EC2::SecurityGroup", true),
    PRIVATE_SERVICE_DISCOVERY_NAMESPACE("https://%s.console.aws.amazon.com/cloudmap/home/namespaces/%s",
            "arn:%s:servicediscovery:%s:%s:namespace/%s",
            "AWS::ServiceDiscovery::PrivateDnsNamespace", false);

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsResource.class);

    private final String urlFormat;
    private final String arnFormat;
    private final String resourceType;
    private final boolean repeatRegion;

    AwsResource(String urlFormat, String arnFormat, String resourceType, boolean repeatRegion) {
        this.urlFormat = urlFormat;
        this.arnFormat = arnFormat;
        this.resourceType = resourceType;
        this.repeatRegion = repeatRegion;
    }

    public String getUrlFormat() {
        return this.urlFormat;
    }

    public String getArnFormat() {
        return arnFormat;
    }

    public String getResourceType() {
        return this.resourceType;
    }

    public String formatUrl(String region, String resourceId) {
        String url;
        try {
            if (this.repeatRegion) {
                url = String.format(this.urlFormat, region, region, resourceId);
            } else {
                url = String.format(this.urlFormat, region, resourceId);
            }
        } catch (IllegalFormatException e) {
            LOGGER.error("Error formatting URL for {}", this.name(), e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return url;
    }

    public String formatArn(String partition, String region, String accountId, String resourceId) {
        String arn;
        try {
            arn = String.format(this.arnFormat, partition, region, accountId, resourceId);
        } catch (IllegalFormatException e) {
            LOGGER.error("Error formatting ARN for {}", this.name(), e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return arn;
    }

}
