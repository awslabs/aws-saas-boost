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
package com.amazon.aws.partners.saasfactory.service;

import com.amazon.aws.partners.saasfactory.domain.Category;
import com.amazon.aws.partners.saasfactory.domain.Product;
import com.amazon.aws.partners.saasfactory.repository.CategoryDao;
import com.amazon.aws.partners.saasfactory.repository.ProductDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductServiceImpl.class);

    @Autowired
    Environment env;
    @Autowired
    private ProductDao productDao;
    @Autowired
    private CategoryDao categoryDao;

    @Override
    public List<Product> getProducts() {
        LOGGER.info("ProductService::getProducts");
        StopWatch timer = new StopWatch();
        timer.start();
        List<Product> products = productDao.getProducts();
        timer.stop();
        LOGGER.info("ProductService::getProducts exec " + timer.getTotalTimeMillis());
        return products;
    }

    @Override
    public Product getProduct(Integer productId) {
        LOGGER.info("ProductService::getProduct " + productId);
        StopWatch timer = new StopWatch();
        timer.start();
        Product product = productDao.getProduct(productId);
        timer.stop();
        LOGGER.info("ProductService::getProduct " + productId + " exec " + timer.getTotalTimeMillis());
        return product;
    }

    @Override
    public Product saveProduct(Product product) {
        Integer productId = product != null ? product.getId() : null;
        LOGGER.info("ProductService::saveProduct " + productId);
        StopWatch timer = new StopWatch();
        timer.start();
        product = productDao.saveProduct(product);

        String imageName = product.getImageName();
        byte[] image = product.getImage();
        if (image != null && image.length > 0 && imageName != null && !imageName.isEmpty()) {
            Path imagePath = Paths.get(env.getProperty("MOUNT_POINT", "/mnt"), "/" + product.getImagePath());
            LOGGER.info("Saving product image at path {}", imagePath);
            try {
                if (Files.exists(imagePath)) {
                    if (Files.size(imagePath) != image.length) {
                        LOGGER.info("Updating existing image file");
                        Files.write(imagePath, image, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } else {
                        LOGGER.info("Skipping existing image file of exact same size");
                    }
                } else {
                    LOGGER.info("Writing new image file");
                    Files.createDirectories(imagePath.getParent());
                    Files.write(imagePath, image, StandardOpenOption.CREATE);
                }
            } catch (IOException ioe) {
                LOGGER.error("Can't update image file", ioe);
                throw new RuntimeException(ioe);
            }
        }

        timer.stop();
        LOGGER.info("ProductService::saveProduct exec " + timer.getTotalTimeMillis());
        return product;
    }

    @Override
    public Product deleteProduct(Product product) {
        Integer productId = product != null ? product.getId() : null;
        LOGGER.info("ProductService::deleteProduct " + productId);
        StopWatch timer = new StopWatch();
        timer.start();
        product = productDao.deleteProduct(product);
        if (product.getImagePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(env.getProperty("MOUNT_POINT", "/mnt"), "/" + product.getImagePath()));
            } catch (IOException ioe) {
                LOGGER.error("Can't delete product image from disk", ioe);
            }
        }
        timer.stop();
        LOGGER.info("ProductService::deleteProduct exec " + timer.getTotalTimeMillis());
        return product;
    }

    @Override
    public List<Category> getCategories() {
        LOGGER.info("ProductService::getCategories");
        StopWatch timer = new StopWatch();
        timer.start();
        List<Category> categories = categoryDao.getCategories();
        timer.stop();
        LOGGER.info("ProductService::getCategories exec " + timer.getTotalTimeMillis());
        return categories;
    }

    @Override
    public Category getCategory(Integer categoryId) {
        LOGGER.info("ProductService::getCategory");
        StopWatch timer = new StopWatch();
        timer.start();
        Category category = categoryDao.getCategory(categoryId);
        timer.stop();
        LOGGER.info("ProductService::getCategory exec " + timer.getTotalTimeMillis());
        return category;
    }

    @Override
    public Category getCategoryByName(String name) {
        LOGGER.info("ProductService::getCategoryByName");
        StopWatch timer = new StopWatch();
        timer.start();
        Category category = categoryDao.getCategoryByName(name);
        timer.stop();
        LOGGER.info("ProductService::getCategoryByName exec " + timer.getTotalTimeMillis());
        return category;
    }

    @Override
    public Category saveCategory(Category category) {
        LOGGER.info("ProductService::saveCategory");
        StopWatch timer = new StopWatch();
        timer.start();
        category = categoryDao.saveCategory(category);
        timer.stop();
        LOGGER.info("ProductService::saveCategory exec " + timer.getTotalTimeMillis());
        return category;
    }

    @Override
    public Category deleteCategory(Category category) {
        Integer categoryId = category != null ? category.getId() : null;
        LOGGER.info("ProductService::deleteCategory " + categoryId);
        StopWatch timer = new StopWatch();
        timer.start();
        category = categoryDao.deleteCategory(category);
        timer.stop();
        LOGGER.info("ProductService::deleteCategory exec " + timer.getTotalTimeMillis());
        return category;
    }
}
