package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.Properties;

public class OnboardingAppStackParameters extends AbstractStackParameters {

    static Properties DEFAULTS = new Properties();

    static {
        DEFAULTS.put("Environment", "");
        DEFAULTS.put("TenantId", "");
        DEFAULTS.put("Tier", "");
        DEFAULTS.put("ServiceName", "");
        DEFAULTS.put("ServiceResourceName", "");
        DEFAULTS.put("ContainerRepository", "");
        DEFAULTS.put("ContainerRepositoryTag", "latest");
        DEFAULTS.put("ECSCluster", "");
        DEFAULTS.put("EnableECSExec", "false");
        DEFAULTS.put("PubliclyAddressable", "true");
        DEFAULTS.put("PublicPathRoute", "/*");
        DEFAULTS.put("PublicPathRulePriority", "1");
        DEFAULTS.put("VPC", "");
        DEFAULTS.put("SubnetPrivateA", "");
        DEFAULTS.put("SubnetPrivateB", "");
        DEFAULTS.put("PrivateRouteTable", "");
        DEFAULTS.put("ServiceDiscoveryNamespace", "");
        DEFAULTS.put("ECSLoadBalancerHttpListener", "");
        DEFAULTS.put("ECSLoadBalancerHttpsListener", "");
        DEFAULTS.put("ECSSecurityGroup", "");
        DEFAULTS.put("ContainerOS", "");
        DEFAULTS.put("ClusterInstanceType", "");
        DEFAULTS.put("TaskLaunchType", "");
        DEFAULTS.put("TaskMemory", "");
        DEFAULTS.put("TaskCPU", "");
        DEFAULTS.put("MinTaskCount", "");
        DEFAULTS.put("MaxTaskCount", "");
        DEFAULTS.put("MinAutoScalingGroupSize", "");
        DEFAULTS.put("MaxAutoScalingGroupSize", "");
        DEFAULTS.put("ContainerPort", "");
        DEFAULTS.put("ContainerHealthCheckPath", "");
        DEFAULTS.put("UseRDS", "false");
        DEFAULTS.put("RDSInstanceClass", "");
        DEFAULTS.put("RDSEngine", "");
        DEFAULTS.put("RDSEngineVersion", "");
        DEFAULTS.put("RDSParameterGroupFamily", "");
        DEFAULTS.put("RDSUsername", "");
        DEFAULTS.put("RDSPasswordParam", "");
        DEFAULTS.put("RDSPort", "");
        DEFAULTS.put("RDSDatabase", "");
        DEFAULTS.put("RDSBootstrap", "");
        DEFAULTS.put("MetricsStream", "");
        DEFAULTS.put("EventBus", "");
        DEFAULTS.put("FileSystemMountPoint", "");
        DEFAULTS.put("UseEFS", "false");
        DEFAULTS.put("EncryptEFS", "true");
        DEFAULTS.put("EFSLifecyclePolicy", "NEVER");
        DEFAULTS.put("UseFSx", "false");
        DEFAULTS.put("ActiveDirectoryId", "");
        DEFAULTS.put("ActiveDirectoryDnsIps", "");
        DEFAULTS.put("ActiveDirectoryDnsName", "");
        DEFAULTS.put("FSxFileSystemType", "");
        DEFAULTS.put("FSxWindowsMountDrive", "");
        DEFAULTS.put("FileSystemStorage", "");
        DEFAULTS.put("FileSystemThroughput", "");
        DEFAULTS.put("FSxBackupRetention", "");
        DEFAULTS.put("FSxDailyBackupTime", "");
        DEFAULTS.put("FSxWeeklyMaintenanceTime", "");
        DEFAULTS.put("OntapVolumeSize", "");
        DEFAULTS.put("Disable", "false");
        DEFAULTS.put("OnboardingDdbTable", "");
        DEFAULTS.put("TenantStorageBucket", "");
        DEFAULTS.put("WIN2022FULL", "/aws/service/ami-windows-latest/Windows_Server-2022-English-Full-ECS_Optimized/image_id");
        DEFAULTS.put("WIN2022CORE", "/aws/service/ami-windows-latest/Windows_Server-2022-English-Core-ECS_Optimized/image_id");
        DEFAULTS.put("WIN2019FULL", "/aws/service/ami-windows-latest/Windows_Server-2019-English-Full-ECS_Optimized/image_id");
        DEFAULTS.put("WIN2019CORE", "/aws/service/ami-windows-latest/Windows_Server-2019-English-Core-ECS_Optimized/image_id");
        DEFAULTS.put("WIN2016FULL", "/aws/service/ami-windows-latest/Windows_Server-2016-English-Full-ECS_Optimized/image_id");
        DEFAULTS.put("AMZNLINUX2", "/aws/service/ecs/optimized-ami/amazon-linux-2/recommended/image_id");
    }

    public OnboardingAppStackParameters() {
        super(DEFAULTS);
    }
}
