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
package com.amazon.aws.partners.saasfactory.controller;

import com.amazon.aws.partners.saasfactory.domain.Category;
import com.amazon.aws.partners.saasfactory.domain.UniqueCategoryException;
import com.amazon.aws.partners.saasfactory.domain.metrics.*;
import com.amazon.aws.partners.saasfactory.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;

import java.util.List;

@Controller
public class CategoryController {

    private String tenantId = System.getenv("TENANT_ID");
    private final static Logger LOGGER = LoggerFactory.getLogger(CategoryController.class);
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));
    private final static String UNKNOWN = "UNKNOWN"; //have to instrument this for later
    private final String streamName = System.getenv("METRICS_STREAM");


    @Autowired
    private ProductService productService;

    @GetMapping("categories")
    public String getCategories(Model model) {
        List<Category> categories = productService.getCategories();
        model.addAttribute("categories", categories);
        LOGGER.info("getCategories: METRICS_STREAM: " + streamName);
        if (null != streamName && !streamName.isEmpty() && !"N/A".equalsIgnoreCase(streamName)) {
            logMetric();
        }
        return "categories";
    }

    @GetMapping("newCategory")
    public String newCategory(Model model) {
        model.addAttribute("category", new Category());
        return "editCategory";
    }

    @GetMapping("updateCategory")
    public String editCategory(@RequestParam("id") Integer id, Model model) {
        Category category = productService.getCategory(id);
        if (category == null) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "No category for id " + id);
        }
        model.addAttribute("category", category);
        return "editCategory";
    }

    @PostMapping("editCategory")
    public String saveCategory(@ModelAttribute Category category, BindingResult binding, Model model, final RedirectAttributes redirectAttributes) {
        String view = null;
        if (category.getName() == null || category.getName().isEmpty()) {
            binding.addError(new FieldError("category", "name", "Category name is required"));
            view = "editCategory";
        } else {
            try {
                boolean isNew = (category.getId() == null || category.getId() < 1);
                productService.saveCategory(category);
                redirectAttributes.addFlashAttribute("css", "success");
                if (isNew) {
                    redirectAttributes.addFlashAttribute("msg", "New category added");
                } else {
                    redirectAttributes.addFlashAttribute("msg", "Category updated");
                }
                view = "redirect:/categories";
            } catch (UniqueCategoryException e) {
                binding.addError(new FieldError("category", "name", "Category already exists"));
                view = "editCategory";
            }
        }
        return view;
    }

    @GetMapping("cancelCategory")
    public String cancelCategory() {
        return "redirect:/categories";
    }

    @GetMapping("deleteCategory")
    public String deleteCategoryConfirm(@RequestParam("id") Integer id, Model model) {
        Category category = productService.getCategory(id);
        if (category == null) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "No category for id " + id);
        }
        model.addAttribute("category", category);
        return "deleteCategory";
    }

    @PostMapping("deleteCategory")
    public String deleteCategory(@ModelAttribute Category category, BindingResult binding, Model model, final RedirectAttributes redirectAttributes) {
        String view = null;
        try {
            productService.deleteCategory(category);
            redirectAttributes.addFlashAttribute("css", "success");
            redirectAttributes.addFlashAttribute("msg", "Category deleted");
            view = "redirect:/categories";
        } catch (Exception e) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "Category has dependent Products");
            view = "deleteCategory";
        }
        return view;
    }

    /*
    Basic example of logging a metric
     */
    private void logMetric() {
        String metricName = "request";
        long value = 1l;
        String unit = "each";

        LOGGER.info("logMetric: start");
        long startTimeMillis = System.currentTimeMillis();
        MetricEvent event = new MetricEventBuilder()
                .withType(MetricEvent.Type.Application)
                .withWorkload("Application")
                .withContext("Catalog")
                .withMetric(new MetricBuilder()
                        .withName(metricName)
                        .withUnit(unit)  //update to the desired unit for the metric
                        .withValue(value)
                        .build()
                )
                .withTenant(new TenantBuilder()
                        .withId(tenantId)
                        .withName(tenantId)
                        .withTier(UNKNOWN)  //TODO: figure out tier
                        .build())
                .addMetaData("user", "111")  //update with your application user info
                .addMetaData("resource", "metrics")  //update with your application resource info
                .build();
        try {
            logSingleEvent(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("logMetric: Error " + e.getMessage(),e);
            throw new RuntimeException(e);
        }
        LOGGER.info("logMetric: finish");
    }

    private void logSingleEvent(MetricEvent event) throws JsonProcessingException {
        MetricEventLogger logger = MetricEventLogger.getLoggerFor(streamName, AWS_REGION);
        logger.log(event);
    }

}
