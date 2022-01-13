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
import com.amazon.aws.partners.saasfactory.domain.UniqueCategoryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Collections;
import java.util.List;

@Repository
public class CategoryDaoImpl implements CategoryDao {

    private final static Logger LOGGER = LoggerFactory.getLogger(CategoryDaoImpl.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public Category getCategory(Integer categoryId) {
        LOGGER.info("CategoryDao::getCategory " + categoryId);
        return jdbc.queryForObject("SELECT category_id, category FROM category WHERE category_id = ?", new CategoryRowMapper(), categoryId);
    }

    @Override
    public Category getCategoryByName(String name) {
        LOGGER.info("CategoryDao::getCategoryByName " + name);
        return jdbc.queryForObject("SELECT category_id, category FROM category WHERE category = ?", new CategoryRowMapper(), name);
    }

    @Override
    public List<Category> getCategories() {
        LOGGER.info("CategoryDao::getCategories");
        List<Category> categories = jdbc.query("SELECT category_id, category FROM category ORDER BY category ASC", new CategoryRowMapper());
        if (categories == null) {
            categories = Collections.emptyList();
        }
        LOGGER.info("CategoryDao::getCategories returning " + categories.size() + " categories");
        return categories;
    }

    @Override
    public Category saveCategory(Category category) {
        LOGGER.info("CategoryDao::saveCategory " + category);
        Category updated = null;
        try {
            if (category.getId() != null && category.getId() > 0) {
                updated = updateCategory(category);
            } else {
                updated = insertCategory(category);
            }
        } catch (DuplicateKeyException e) {
            throw new UniqueCategoryException(e.getMessage(), e);
        }
        return updated;
    }

    private Category insertCategory(Category category) {
        LOGGER.info("CategoryDao::insertCategory " + category);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO category (category) VALUES (?)", Statement.RETURN_GENERATED_KEYS );
            ps.setString(1, category.getName());
            return ps;
        }, keyHolder);
        try {
            LOGGER.info(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(keyHolder));
        } catch (Exception e) {
        }
        if (!keyHolder.getKeys().isEmpty()) {
            LOGGER.info("keyHolder is a map. Getting key from category_id");
            keyHolder.getKeys().entrySet().forEach(e -> LOGGER.info("{} => {}", e.getKey(), e.getValue()));
            if (keyHolder.getKeys().containsKey("insert_id")) {
                // MySQL/MariaDB
                category.setId(((Long) keyHolder.getKeys().get("insert_id")).intValue());
            } else if (keyHolder.getKeys().containsKey("category_id")) {
                // PostgreSQL
                category.setId((Integer) keyHolder.getKeys().get("category_id"));
            } else if (keyHolder.getKeys().containsKey("GENERATED_KEYS")) {
                // SQL Server
                category.setId(((BigDecimal) keyHolder.getKeys().get("GENERATED_KEYS")).intValue());
            }
        } else {
            LOGGER.info("keyHolder is an object. Getting INT value from key.");
            try {
                category.setId(keyHolder.getKey().intValue());
            } catch (ClassCastException cce) {
                category.setId(Long.valueOf(keyHolder.getKey().longValue()).intValue());
            }
        }
        LOGGER.info("Set new category id to {}", category.getId());
        return category;
    }

    private Category updateCategory(Category category) {
        LOGGER.info("CategoryDao::updateCategory " + category);
        jdbc.update("UPDATE category SET category = ? WHERE category_id = ?", new Object[]{category.getName(), category.getId()});
        return category;
    }

    @Override
    public Category deleteCategory(Category category) {
        LOGGER.info("CategoryDao::deleteCategory " + category);
        int affectedRows = jdbc.update("DELETE FROM category WHERE category_id = ?", new Object[]{category.getId()});
        if (affectedRows != 1) {
            throw new RuntimeException("Delete failed for category " + category.getId());
        }
        return category;
    }

    private static final class CategoryRowMapper implements RowMapper<Category> {
        @Override
        public Category mapRow(ResultSet result, int rowMapper) throws SQLException {
            return new Category(result.getInt("category_id"), result.getString("category"));
        }
    }
}
