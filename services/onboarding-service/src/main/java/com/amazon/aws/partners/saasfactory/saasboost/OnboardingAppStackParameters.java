package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class OnboardingAppStackParameters extends AbstractStackParameters {

    static final Properties DEFAULTS = new Properties();
    static final List<String> REQUIRED_FOR_CREATE = List.of("Environment", "TenantId", "Tier", "VPC", "SubnetPrivateA",
            "SubnetPrivateB", "ECSCluster", "ECSSecurityGroup", "ContainerRepository", "ContainerRepositoryTag");

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
        DEFAULTS.put("TaskMemory", "1024");
        DEFAULTS.put("TaskCPU", "512");
        DEFAULTS.put("MinTaskCount", "1");
        DEFAULTS.put("MaxTaskCount", "1");
        DEFAULTS.put("MinAutoScalingGroupSize", "1");
        DEFAULTS.put("MaxAutoScalingGroupSize", "1");
        DEFAULTS.put("ContainerPort", "0");
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
        DEFAULTS.put("FSxFileSystemType", "FSX_WINDOWS");
        DEFAULTS.put("FSxWindowsMountDrive", "");
        DEFAULTS.put("FileSystemStorage", "0");
        DEFAULTS.put("FileSystemThroughput", "0");
        DEFAULTS.put("FSxBackupRetention", "0");
        DEFAULTS.put("FSxDailyBackupTime", "");
        DEFAULTS.put("FSxWeeklyMaintenanceTime", "");
        DEFAULTS.put("OntapVolumeSize", "20");
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

    @Override
    protected void validateForCreate() {
        List<String> invalidParameters = new ArrayList<>();
        if (Utils.isBlank(getProperty("ECSLoadBalancerHttpListener"))
                && Utils.isBlank(getProperty("ECSLoadBalancerHttpsListener"))) {
            invalidParameters.add("ECSLoadBalancerHttpListener");
            invalidParameters.add("ECSLoadBalancerHttpsListener");
        }
        for (String requiredParameter : REQUIRED_FOR_CREATE) {
            if ("ECSLoadBalancerHttpListener".equals(requiredParameter)) {
                continue;
            }
            if ("ECSLoadBalancerHttpsListener".equals(requiredParameter)) {
                continue;
            }
            if (Utils.isBlank(getProperty(requiredParameter))) {
                invalidParameters.add(requiredParameter);
            }
        }
        if (!invalidParameters.isEmpty()) {
            throw new RuntimeException("Missing values for required parameters "
                    + String.join(",", invalidParameters));
        }
    }
}
