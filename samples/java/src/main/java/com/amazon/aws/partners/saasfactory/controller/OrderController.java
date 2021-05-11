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

import com.amazon.aws.partners.saasfactory.domain.Order;
import com.amazon.aws.partners.saasfactory.domain.Product;
import com.amazon.aws.partners.saasfactory.service.OrderService;
import com.amazon.aws.partners.saasfactory.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Date;
import java.util.List;

@Controller
public class OrderController {

    private final static Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @GetMapping("/orders")
    public String orders(Model model) throws Exception {
        List<Order> orders = orderService.getOrders();
        List<Product> products = productService.getProducts();

        model.addAttribute("orders", orders);
        model.addAttribute("products", products);

        return "orders";
    }

    @PostMapping("/orders")
    public String insertOrder(@ModelAttribute Order order, Model model) throws Exception {
        LOGGER.info("OrdersController::insertOrder " + order);

        order.setOrderDate(new Date());
        order.setBillAddress(order.getShipAddress());

        order.getLineItems().removeIf(item -> item == null || 0 == item.getQuantity());
        order.getLineItems().forEach(item -> {
            try {
                item.setUnitPurchasePrice(productService.getProduct(item.getProduct().getId()).getPrice());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        orderService.saveOrder(order);

        return "redirect:/orders";
    }

    @PostMapping("/updateOrder")
    public String updateOrder(@ModelAttribute Order order, Model model) throws Exception {
        LOGGER.info("OrdersController::updateOrder " + order);

        order.setBillAddress(order.getShipAddress());

        order.getLineItems().removeIf(item -> item == null || 0 == item.getQuantity());
        order.getLineItems().forEach(item -> {
            try {
                item.setUnitPurchasePrice(productService.getProduct(item.getProduct().getId()).getPrice());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        orderService.saveOrder(order);

        return "redirect:/orders";
    }

    @PostMapping("/deleteOrder")
    public String deleteOrder(@ModelAttribute Order order) throws Exception {
        LOGGER.info("OrdersController::deleteOrder " + order.getId());

        orderService.deleteOrder(order);

        return "redirect:/orders";
    }
}
