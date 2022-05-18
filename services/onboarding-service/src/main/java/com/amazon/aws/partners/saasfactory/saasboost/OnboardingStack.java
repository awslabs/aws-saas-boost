package com.amazon.aws.partners.saasfactory.saasboost;

public class OnboardingStack {

    private String name;
    private String arn;
    private boolean baseStack;
    private String status;
    private String pipeline;
    private String pipelineStatus;

    private OnboardingStack() {
    }

    private OnboardingStack(Builder builder) {
        this.name = builder.name;
        this.arn = builder.arn;
        this.baseStack = builder.baseStack;
        this.status = builder.status;
        this.pipeline = builder.pipeline;
        this.pipelineStatus = builder.pipelineStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(OnboardingStack copyMe) {
        return new Builder()
                .name(copyMe.name)
                .arn(copyMe.arn)
                .baseStack(copyMe.baseStack)
                .status(copyMe.status)
                .pipeline(copyMe.pipeline)
                .pipelineStatus(copyMe.pipelineStatus);
    }

    public String getName() {
        return name;
    }

    public String getArn() {
        return arn;
    }

    public boolean isBaseStack() {
        return baseStack;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPipeline() {
        return pipeline;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public String getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(String pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public boolean isComplete() {
        return "CREATE_COMPLETE".equals(getStatus()) || "UPDATE_COMPLETE".equals(getStatus());
    }

    public boolean isDeployed() {
        return (isComplete() && isBaseStack()) || (isComplete() && "SUCCEEDED".equals(getPipelineStatus()));
    }

    public boolean isDeleted() {
        return "DELETE_COMPLETE".equals(getStatus());
    }

    public boolean isCreated() {
        return "CREATE_COMPLETE".equals(getStatus());
    }

    public boolean isUpdated() {
        return "UPDATE_COMPLETE".equals(getStatus());
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

    public static final class Builder {

        private String name;
        private String arn;
        private boolean baseStack;
        private String status;
        private String pipeline;
        private String pipelineStatus;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arn(String arn) {
            this.arn = arn;
            return this;
        }

        public Builder baseStack(boolean baseStack) {
            this.baseStack = baseStack;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder pipeline(String pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder pipelineStatus(String pipelineStatus) {
            this.pipelineStatus = pipelineStatus;
            return this;
        }

        public OnboardingStack build() {
            return new OnboardingStack(this);
        }
    }
}
