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
import com.amazon.aws.partners.saasfactory.domain.Product;
import com.amazon.aws.partners.saasfactory.domain.UniqueProductException;
import com.amazon.aws.partners.saasfactory.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ProductController {

    private final static Logger LOGGER = LoggerFactory.getLogger(ProductController.class);
    private final static ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRODUCT_CODE = "ProductCode";
    private static final String TENANT_ID = "TenantId";
    private static final String QUANTITY = "Quantity";
    private static final String TIMESTAMP = "Timestamp";
    private final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private final String tenantId = System.getenv("TENANT_ID");

    @Autowired
    private ProductService productService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Category.class, new CategoryEditor());
    }

    @GetMapping("products")
    public String getProducts(Model model) throws Exception {
        List<Product> products = productService.getProducts();

        //Add metering event
        if (SAAS_BOOST_EVENT_BUS != null && !SAAS_BOOST_EVENT_BUS.isEmpty()) {
            addMeterEvent(tenantId);
        }
        model.addAttribute("products", products);
        return "products";
    }

    @GetMapping("productDetail")
    public String getProduct(@RequestParam("id") Integer id, Model model) {
        Product product = productService.getProduct(id);
        if (product == null) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "No product for id " + id);
        }
        model.addAttribute("product", product);
        return "product";
    }

    @GetMapping("newProduct")
    public String newProduct(Model model) {
        List<Category> categories = productService.getCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("product", new Product());
        return "editProduct";
    }

    @GetMapping("updateProduct")
    public String editProduct(@RequestParam("id") Integer id, Model model) {
        List<Category> categories = productService.getCategories();
        model.addAttribute("categories", categories);
        Product product = productService.getProduct(id);
        if (product == null) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "No product for id " + id);
        }
        model.addAttribute("product", product);
        // Hack to make checkbox selection with JSTL easier
        Map<Integer, Category> existingCategories = product.getCategories()
                .stream()
                .collect(
                        Collectors.toMap(Category::getId, category -> category)
                );
        model.addAttribute("existingCategories", existingCategories);
        return "editProduct";
    }

    @PostMapping("editProduct")
    public String saveProduct(@ModelAttribute Product product, BindingResult binding, @RequestParam(name = "image", required = false) MultipartFile image,
            Model model, final RedirectAttributes redirectAttributes) {
        String view = null;

        if (product.getName() == null || product.getName().isEmpty()) {
            binding.addError(new FieldError("product", "name", "Product name is required"));
            view = "editProduct";
        } else if (product.getSku() == null || product.getSku().isEmpty()) {
            binding.addError(new FieldError("product", "sku", "Product SKU is required"));
            view = "editProduct";
        } else {
            try {
                boolean isNew = (product.getId() == null || product.getId() < 1);
                if (image != null && !image.isEmpty()) {
                    LOGGER.info("Saving uploaded file as product image {} {} {}", image.getSize(), image.getContentType(), image.getOriginalFilename());
                    product.setImageName(image.getOriginalFilename());
                    product.setImage(image.getBytes());
                }
                productService.saveProduct(product);
                redirectAttributes.addFlashAttribute("css", "success");
                if (isNew) {
                    redirectAttributes.addFlashAttribute("msg", "New product added");
                } else {
                    redirectAttributes.addFlashAttribute("msg", "Product updated");
                }
                view = "redirect:/products";
            } catch (UniqueProductException e) {
                binding.addError(new FieldError("product", "name", "Product already exists"));
                view = "editProduct";
            } catch (IOException ioe) {
                LOGGER.error("I/O error", ioe);
                binding.addError(new FieldError("product", "image", "Product image upload failed"));
                view = "editProduct";
            }
        }
        return view;
    }

    @GetMapping("cancelProduct")
    public String cancelProduct() {
        return "redirect:/products";
    }

    @GetMapping("deleteProduct")
    public String deleteProductConfirm(@RequestParam("id") Integer id, Model model) {
        Product product = productService.getProduct(id);
        if (product == null) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "No product for id " + id);
        }
        model.addAttribute("product", product);
        return "deleteProduct";
    }

    @PostMapping("deleteProduct")
    public String deleteProduct(@ModelAttribute Product product, BindingResult binding, Model model, final RedirectAttributes redirectAttributes) {
        String view = null;
        try {
            productService.deleteProduct(product);
            redirectAttributes.addFlashAttribute("css", "success");
            redirectAttributes.addFlashAttribute("msg", "Product deleted");
            view = "redirect:/products";
        } catch (Exception e) {
            model.addAttribute("css", "danger");
            model.addAttribute("msg", "Product has dependent Orders");
            view = "deleteProduct";
        }
        return view;
    }

    private void addMeterEvent(String tenantId) {
        /*     productCode is the internal product code. For each tenant, the DDB billing table has a config
               item to map internal product code to the Billing system subscription item.
               This is configured when SaaS Boost Tenant onboarded.
         */
        LOGGER.info("addMeterEvent: Add a request metric for tenant: " + tenantId);
        String productCode = "product_requests";
        int meterVal = 1;
        try {
            ObjectNode systemApiRequest = MAPPER.createObjectNode();
            systemApiRequest.put(TENANT_ID, tenantId);
            systemApiRequest.put(PRODUCT_CODE, productCode);
            systemApiRequest.put(QUANTITY, meterVal);
            systemApiRequest.put(TIMESTAMP, Instant.now().toEpochMilli());     //epoch time in UTC
            putMeteringEvent(MAPPER.writeValueAsString(systemApiRequest));
            LOGGER.info("addMeterEvent: Complete adding request metric for tenant: " + tenantId);
        } catch ( JsonProcessingException ioe) {
            LOGGER.error("events::addMeterEvent", ioe);
            throw new RuntimeException(ioe);
        }
    }

    /*
        Put metering event on EventBridge
     */
    private void putMeteringEvent(String eventBridgeDetail) {
        try {
            EventBridgeClient eventBridgeClient = null;
            try {
                eventBridgeClient = EventBridgeClient.builder()
                        .credentialsProvider(ContainerCredentialsProvider.builder().build())
                        .build();
            } catch (Exception e) {
                eventBridgeClient = EventBridgeClient.builder()
                        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                        .build();
            }

            PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType("BILLING")  //billing is required to send to billing system
                    .source("saas-boost")
                    .detail(eventBridgeDetail)
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridgeClient.putEvents(r -> r
                    .entries(systemApiCallEvent)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    LOGGER.info(String.format("putMeteringEvent: Put event success ", entry.toString(), systemApiCallEvent.toString()));
                } else {
                    LOGGER.error(String.format("putMeteringEvent: Put event failed {}", entry.toString()));
                    throw new RuntimeException("putMeteringEvent: Put event failed.");
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            LOGGER.error("putMeteringEvent: Error " + eventBridgeError.getMessage(), eventBridgeError);
            throw eventBridgeError;
        }
    }

    private final class CategoryEditor extends PropertyEditorSupport {
        @Override
        public String getAsText() {
            Category category = (Category) getValue();
            return category == null ? "" : category.getId().toString();
        }

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            Category category = null;
            try {
                category = productService.getCategory(Integer.valueOf(text));
            } catch (Exception e) {
                LOGGER.error("Can't look up category by id {}", text);
            }
            setValue(category);
        }
    }
}
