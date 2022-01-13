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
package com.amazon.aws.partners.saasfactory.repository;

import com.amazon.aws.partners.saasfactory.domain.Category;
import com.amazon.aws.partners.saasfactory.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Repository
public class ProductDaoImpl implements ProductDao {

    private final static Logger LOGGER = LoggerFactory.getLogger(ProductDaoImpl.class);
    private final static String SELECT_PRODUCT_SQL = "SELECT p.product_id, p.sku, p.product, p.price, p.image, c.category_id, c.category " +
            "FROM product p LEFT OUTER JOIN product_categories pc ON pc.product_id = p.product_id " +
            "LEFT OUTER JOIN category c ON pc.category_id = c.category_id";
    private final static String INSERT_PRODUCT_SQL = "INSERT INTO product (sku, product, price, image) VALUES (?, ?, ?, ?)";
    private final static String UPDATE_PRODUCT_SQL = "UPDATE product SET sku = ?, product = ?, price = ?, image = ? WHERE product_id = ?";
    private final static String DELETE_PRODUCT_SQL = "DELETE FROM product WHERE product_id = ?";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CategoryDao categoryDao;

    @Override
    public Product getProduct(Integer productId) {
        LOGGER.info("ProductDao::getProduct " + productId);
        String sql = SELECT_PRODUCT_SQL.concat(" WHERE p.product_id = ?");
        return jdbc.query(sql, new ProductMapper(), productId);
    }

    @Override
    public List<Product> getProducts() {
        LOGGER.info("ProductDao::getProducts");
        List<Product> products = jdbc.query(SELECT_PRODUCT_SQL, new ProductListMapper());
        if (products == null) {
            products = Collections.emptyList();
        }
        LOGGER.info("ProductDao::getProducts returning " + products.size() + " products");
        return products;
    }

    @Override
    public Product saveProduct(Product product) {
        LOGGER.info("ProductDao::saveProduct " + product);
        if (product.getId() != null && product.getId() > 0) {
            return updateProduct(product);
        } else {
            return insertProduct(product);
        }
    }

    private Product insertProduct(Product product) {
        LOGGER.info("ProductDao::insertProduct " + product);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_PRODUCT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, product.getSku());
            ps.setString(2, product.getName());
            ps.setBigDecimal(3, product.getPrice());
            ps.setString(4, product.getImageName());
            return ps;
        }, keyHolder);
        try {
            LOGGER.info(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(keyHolder));
        } catch (Exception e) {
        }
        if (!keyHolder.getKeys().isEmpty()) {
            LOGGER.info("keyHolder is a map. Getting key from product_id");
            if (keyHolder.getKeys().containsKey("insert_id")) {
                // MySQL/MariaDB
                product.setId(((Long) keyHolder.getKeys().get("insert_id")).intValue());
            } else if (keyHolder.getKeys().containsKey("product_id")) {
                // PostgreSQL
                product.setId((Integer) keyHolder.getKeys().get("product_id"));
            } else if (keyHolder.getKeys().containsKey("GENERATED_KEYS")) {
                // SQL Server
                product.setId(((BigDecimal) keyHolder.getKeys().get("GENERATED_KEYS")).intValue());
            }
        } else {
            LOGGER.info("keyHolder is an object. Getting INT value from key.");
            try {
                product.setId(keyHolder.getKey().intValue());
            } catch (ClassCastException cce) {
                product.setId(Long.valueOf(keyHolder.getKey().longValue()).intValue());
            }
        }
        LOGGER.info("Set new product id to {}", product.getId());
        updateProductCategories(product);
        return product;
    }

    private Product updateProduct(Product product) {
        LOGGER.info("ProductDao::updateProduct " + product);
        for (Category category : product.getCategories()) {
            if (category != null) {
                category = categoryDao.saveCategory(category);
            }
        }
        jdbc.update(UPDATE_PRODUCT_SQL, product.getSku(), product.getName(), product.getPrice(), product.getImageName(), product.getId());
        updateProductCategories(product);
        return product;
    }

    private void updateProductCategories(Product product) {
        jdbc.update("DELETE FROM product_categories WHERE product_id = ?", product.getId());
        if (!product.getCategories().isEmpty()) {
            // replace this
            for (Category category : product.getCategories()) {
                jdbc.update("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", product.getId(), category.getId());
            }
        }
    }

    @Override
    public Product deleteProduct(Product product) {
        LOGGER.info("ProductDao::deleteProduct " + product);
        int affectedRows = jdbc.update(DELETE_PRODUCT_SQL, product.getId());
        if (affectedRows != 1) {
            throw new RuntimeException("Delete failed for product " + product.getId());
        }
        return product;
    }

    private static final class ProductMapper implements ResultSetExtractor<Product> {

        @Override
        public Product extractData(ResultSet resultSet) throws SQLException, DataAccessException {
            Integer productId = null;
            Product product = null;
            while (resultSet.next()) {
                productId = resultSet.getInt("product_id");
                if (product == null) {
                    product = new Product(productId, resultSet.getString("sku"), resultSet.getString("product"), resultSet.getBigDecimal("price"), resultSet.getString("image"));
                }
                Integer categoryId = resultSet.getInt("category_id");
                if (categoryId != null) {
                    Category category = new Category(categoryId, resultSet.getString("category"));
                    product.getCategories().add(category);
                }
            }
            return product;
        }
    }

    private static final class ProductListMapper implements ResultSetExtractor<List<Product>> {

        @Override
        public List<Product> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
            Map<Integer, Product> map = new HashMap<>();
            while (resultSet.next()) {
                Integer productId = resultSet.getInt("product_id");
                Product product = map.get(productId);
                if (product == null) {
                    product = new Product(productId, resultSet.getString("sku"), resultSet.getString("product"), resultSet.getBigDecimal("price"), resultSet.getString("image"));
                    map.put(productId, product);
                }
                Integer categoryId = resultSet.getInt("category_id");
                if (categoryId != null) {
                    Category category = new Category(categoryId, resultSet.getString("category"));
                    product.getCategories().add(category);
                }
            }
            return new ArrayList<>(map.values());
        }
    }
}
