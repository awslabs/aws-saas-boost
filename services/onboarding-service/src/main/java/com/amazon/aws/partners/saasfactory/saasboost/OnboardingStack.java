package com.amazon.aws.partners.saasfactory.saasboost;

public class OnboardingStack {

    private String name;
    private String arn;
    private boolean baseStack;
    private String status;

    public OnboardingStack() {
        this(null, null, false, null);
    }

    public OnboardingStack(String name, String arn, boolean baseStack, String status) {
        this.name = name;
        this.arn = arn;
        this.baseStack = baseStack;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArn() {
        return arn;
    }

    public void setArn(String arn) {
        this.arn = arn;
    }

    public boolean isBaseStack() {
        return baseStack;
    }

    public void setBaseStack(boolean baseStack) {
        this.baseStack = baseStack;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCloudFormationUrl() {
        String url = null;
        if (arn != null) {
            String[] stackId = arn.split(":");
            if (stackId.length > 4) {
                String region = stackId[3];
                url = String.format(
                        "https://%s.console.aws.amazon.com/cloudformation/home?region=%s#/stacks/stackinfo?filteringText=&filteringStatus=active&viewNested=true&hideStacks=false&stackId=%s",
                        region,
                        region,
                        arn
                );
            }
        }
        return url;
    }
}
