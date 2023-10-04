package com.amazon.aws.partners.saasfactory.saasboost.compute;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.AbstractCompute;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.AbstractComputeTier;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.ecs.EcsCompute;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.ecs.EcsComputeTier;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class AbstractComputeTest {
    @Test
    public void deserialize_ecsCompute_basic() {
        String ecsJson = "{\"type\":\"ECS\", \"ecsExecEnabled\": true, \"tiers\":{"
                + "\"Free\":{\"instanceType\":\"t3.medium\", \"cpu\":512, \"memory\":1024, \"min\":1, \"max\":2, \"ec2min\":0, \"ec2max\":5}," 
                + "\"Gold\":{\"instanceType\":\"t3.large\", \"cpu\":1024, \"memory\":2048, \"min\":2, \"max\":4, \"ec2min\":5, \"ec2max\":9}}}";
        AbstractCompute compute = Utils.fromJson(ecsJson, AbstractCompute.class);
        assertEquals(EcsCompute.class, compute.getClass());
        assertEquals(Boolean.TRUE, ((EcsCompute) compute).getEcsExecEnabled());
        assertNotNull(compute.getTiers());
        for (Map.Entry<String, ? extends AbstractComputeTier> tierEntry : compute.getTiers().entrySet()) {
            String tierName = tierEntry.getKey();
            assertEquals(EcsComputeTier.class, tierEntry.getValue().getClass());
            EcsComputeTier tier = (EcsComputeTier) tierEntry.getValue();
            switch (tierName) {
                case "Free": {
                    assertEquals("t3.medium", tier.getInstanceType());
                    assertEquals(Integer.valueOf(512), tier.getCpu());
                    assertEquals(Integer.valueOf(1024), tier.getMemory());
                    assertEquals(Integer.valueOf(1), tier.getMin());
                    assertEquals(Integer.valueOf(2), tier.getMax());
                    assertEquals(Integer.valueOf(0), tier.getEc2min());
                    assertEquals(Integer.valueOf(5), tier.getEc2max());
                    break;
                }
                case "Gold": {
                    assertEquals("t3.large", tier.getInstanceType());
                    assertEquals(Integer.valueOf(1024), tier.getCpu());
                    assertEquals(Integer.valueOf(2048), tier.getMemory());
                    assertEquals(Integer.valueOf(2), tier.getMin());
                    assertEquals(Integer.valueOf(4), tier.getMax());
                    assertEquals(Integer.valueOf(5), tier.getEc2min());
                    assertEquals(Integer.valueOf(9), tier.getEc2max());
                    break;
                }
                default: fail("Deserialize ecs compute JSON found an unexpected tier. "
                        + "Wanted [Free|Gold] but found " + tierName);
            }
        }
    }
}
