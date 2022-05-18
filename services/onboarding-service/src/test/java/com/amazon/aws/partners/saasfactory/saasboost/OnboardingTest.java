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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class OnboardingTest {

    @Test
    public void testBaseStacksComplete() {
        OnboardingStack stack1 = OnboardingStack.builder().baseStack(true).build();
        OnboardingStack stack2 = OnboardingStack.builder().baseStack(false).build();

        Onboarding onboarding = new Onboarding();
        assertFalse("No stacks", onboarding.baseStacksComplete());

        onboarding.setStacks(Arrays.asList(stack1, stack2));
        assertFalse("Empty stacks", onboarding.baseStacksComplete());

        stack1.setStatus("CREATE_COMPLETE");
        stack2.setStatus("UPDATE_COMPLETE");
        assertTrue("All base stacks complete", onboarding.baseStacksComplete());

        OnboardingStack stack3 = OnboardingStack.builder().baseStack(true).build();
        onboarding.addStack(stack3);
        assertFalse("Not every base stack is complete", onboarding.baseStacksComplete());
    }

    @Test
    public void testStacksComplete() {
        OnboardingStack stack1 = OnboardingStack.builder().build();
        OnboardingStack stack2 = OnboardingStack.builder().build();

        Onboarding onboarding = new Onboarding();
        assertFalse("No stacks", onboarding.stacksComplete());

        onboarding.setStacks(Arrays.asList(stack1, stack2));
        assertFalse("Empty stacks", onboarding.stacksComplete());

        stack1.setStatus("CREATE_COMPLETE");
        assertFalse("Not every stack is complete", onboarding.stacksComplete());

        stack2.setStatus("UPDATE_COMPLETE");
        assertTrue("All stacks complete", onboarding.stacksComplete());

        onboarding.addStack(OnboardingStack.builder().build());
        assertFalse("Not every stack is complete", onboarding.stacksComplete());
    }

    @Test
    public void testHasAppStacks() {
        OnboardingStack baseStack = OnboardingStack.builder().baseStack(true).build();
        OnboardingStack appStack = OnboardingStack.builder().baseStack(false).build();

        Onboarding onboarding = new Onboarding();
        assertFalse("No stacks", onboarding.hasAppStacks());

        onboarding.addStack(baseStack);
        assertFalse("Only base stacks", onboarding.hasAppStacks());

        onboarding.addStack(appStack);
        assertTrue("App stacks", onboarding.hasAppStacks());
    }

    @Test
    public void testAppStacksDeleted() {
        OnboardingStack baseStack = OnboardingStack.builder().baseStack(true).status("CREATE_COMPLETE").build();
        OnboardingStack appStack1 = OnboardingStack.builder().baseStack(false).status("DELETE_IN_PROGRESS").build();
        OnboardingStack appStack2 = OnboardingStack.builder().baseStack(false).status("DELETE_COMPLETE").build();
        OnboardingStack appStack3 = OnboardingStack.builder().baseStack(false).status("DELETE_COMPLETE").build();

        Onboarding onboarding = new Onboarding();
        assertTrue("No Stacks", onboarding.appStacksDeleted());

        onboarding.addStack(baseStack);
        onboarding.appStacksDeleted();
        assertTrue("Only base stacks", onboarding.appStacksDeleted());

        onboarding.addStack(appStack1);
        assertFalse("App stacks not deleted", onboarding.appStacksDeleted());

        onboarding.addStack(appStack2);
        assertFalse("App stacks not deleted", onboarding.appStacksDeleted());

        onboarding.addStack(appStack3);
        assertFalse("App stacks not deleted", onboarding.appStacksDeleted());

        appStack1.setStatus("DELETE_COMPLETE");
        assertTrue("App stacks deleted", onboarding.appStacksDeleted());
    }
}
