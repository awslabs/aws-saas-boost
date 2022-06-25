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
using log4net;
using SaaSBoostHelloWorld.Models;
using System;
using System.Collections.Generic;
using System.Data.SqlClient;

namespace SaaSBoostHelloWorld.Repository
{
    public class CategoryDao : ICategoryDao
    {
        private static readonly ILog LOGGER = LogManager.GetLogger(typeof(CategoryDao));

        private static readonly string DB_HOST = Environment.GetEnvironmentVariable("DB_HOST");
        private static readonly string DB_NAME = Environment.GetEnvironmentVariable("DB_NAME");
        private static readonly string DB_USER = Environment.GetEnvironmentVariable("DB_USER");
        private static readonly string DB_PASS = Environment.GetEnvironmentVariable("DB_PASSWORD");
        private static readonly string CONNECTION_STRING = $"Data Source={DB_HOST};Initial Catalog={DB_NAME};User id={DB_USER};Password={DB_PASS};";
        
        public IList<Category> GetCategories()
        {
            LOGGER.Info("CategoryDao::GetCategories");
            List<Category> categories = new List<Category>();
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("SELECT category_id, category FROM category ORDER BY category ASC", conn);
                conn.Open();
                SqlDataReader reader = sql.ExecuteReader();
                while (reader.Read())
                {
                    Category category = new Category
                    {
                        Id = Convert.ToInt32(reader["category_id"]),
                        Name = reader["category"].ToString()
                    };
                    categories.Add(category);
                }
                reader.Close();
            }
            return categories;
        }

        public Category GetCategory(int categoryId)
        {
            LOGGER.Info($"CategoryDao::GetCategory {categoryId}");
            Category category = null;
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("SELECT category_id, category FROM category WHERE category_id = @categoryId", conn);
                sql.Parameters.AddWithValue("@categoryId", categoryId);
                conn.Open();
                SqlDataReader reader = sql.ExecuteReader();
                while (reader.Read())
                {
                    category = new Category
                    {
                        Id = Convert.ToInt32(reader["category_id"]),
                        Name = reader["category"].ToString()
                    };
                }
                reader.Close();
            }
            return category;
        }

        public Category GetCategoryByName(string name)
        {
            LOGGER.Info($"CategoryDao::GetCategoryByName {name}");
            Category category = null;
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("SELECT category_id, category FROM category WHERE category = @name", conn);
                sql.Parameters.AddWithValue("@name", name);
                conn.Open();
                SqlDataReader reader = sql.ExecuteReader();
                while (reader.Read())
                {
                    category = new Category
                    {
                        Id = Convert.ToInt32(reader["category_id"]),
                        Name = reader["category"].ToString()
                    };
                }
                reader.Close();
            }
            return category;
        }

        public Category SaveCategory(Category category)
        {
            LOGGER.Info($"CategoryDao::SaveCategory {category}");
            Category updated = null;
            int? categoryId = category.Id;
            if (categoryId != null && categoryId > 0)
            {
                updated = UpdateCategory(category);
            }
            else
            {
                updated = InsertCategory(category);
            }
            return updated;
        }

        private Category InsertCategory(Category category)
        {
            LOGGER.Info($"CategoryDao::InsertCategory {category}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("INSERT INTO category (category) OUTPUT INSERTED.category_id VALUES (@categoryName)", conn);
                sql.Parameters.AddWithValue("@categoryName", category.Name);
                conn.Open();
                int categoryId = Convert.ToInt32(sql.ExecuteScalar());
                category.Id = categoryId;
            }
            LOGGER.Info(("Set new category id to {0}", category.Id));
            return category;
        }

        private Category UpdateCategory(Category category)
        {
            LOGGER.Info($"CategoryDao::UpdateCategory {category}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("UPDATE category SET category = @categoryName WHERE category_id = @categoryId", conn);
                sql.Parameters.AddWithValue("@categoryId", category.Id);
                sql.Parameters.AddWithValue("@categoryName", category.Name);
                conn.Open();
                sql.ExecuteNonQuery();
            }
            return category;
        }

        public Category DeleteCategory(Category category)
        {
            LOGGER.Info($"CategoryDao::deleteCategory {category}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("DELETE FROM category WHERE category_id = @categoryId", conn);
                sql.Parameters.AddWithValue("@categoryId", category.Id);
                conn.Open();
                sql.ExecuteNonQuery();
            }
            return category;
        }
    }
}
