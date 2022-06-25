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
using System.Collections.Specialized;
using System.Data.SqlClient;

namespace SaaSBoostHelloWorld.Repository
{
    public class ProductDao : IProductDao
    {
        private static readonly ILog LOGGER = LogManager.GetLogger(typeof(ProductDao));

        private static readonly string DB_HOST = Environment.GetEnvironmentVariable("DB_HOST");
        private static readonly string DB_NAME = Environment.GetEnvironmentVariable("DB_NAME");
        private static readonly string DB_USER = Environment.GetEnvironmentVariable("DB_USER");
        private static readonly string DB_PASS = Environment.GetEnvironmentVariable("DB_PASSWORD");
        private static readonly string CONNECTION_STRING = $"Data Source={DB_HOST};Initial Catalog={DB_NAME};User id={DB_USER};Password={DB_PASS};";

        public Product GetProduct(int productId)
        {
            LOGGER.Info($"ProductDao::GetProduct {productId}");
            Product product = null;
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("SELECT p.product_id, p.sku, p.product, p.price, p.image, c.category_id, c.category FROM product p LEFT OUTER JOIN product_categories pc ON pc.product_id = p.product_id LEFT OUTER JOIN category c ON pc.category_id = c.category_id WHERE p.product_id = @productId", conn);
                sql.Parameters.AddWithValue("@productId", productId);
                conn.Open();
                SqlDataReader reader = sql.ExecuteReader();
                while (reader.Read())
                {
                    if (product == null)
                    {
                        product = new Product
                        {
                            Id = reader.GetInt32(0),
                            Sku = reader.GetString(1),
                            Name = reader.GetString(2),
                            Price = reader.GetDecimal(3),
                            ImageName = !reader.IsDBNull(4) ? reader.GetString(4) : default
                        };
                    }
                    if (product != null && !reader.IsDBNull(5))
                    {
                        Category category = new Category
                        {
                            Id = reader.GetInt32(5),
                            Name = reader.GetString(6)
                        };
                        product.Categories.Add(category);
                    }
                }
                reader.Close();
            }
            return product;
        }

        public IList<Product> GetProducts()
        {
            LOGGER.Info("ProductDao::GetProducts");
            List<Product> products = new List<Product>();
            OrderedDictionary map = new OrderedDictionary();
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("SELECT p.product_id, p.sku, p.product, p.price, p.image, c.category_id, c.category FROM product p LEFT OUTER JOIN product_categories pc ON pc.product_id = p.product_id LEFT OUTER JOIN category c ON pc.category_id = c.category_id", conn);
                conn.Open();
                SqlDataReader reader = sql.ExecuteReader();
                while (reader.Read())
                {
                    int productId = reader.GetInt32(0);
                    Product product = (Product)map[productId.ToString()];
                    if (product == null)
                    {
                        product = new Product
                        {
                            Id = reader.GetInt32(0),
                            Sku = reader.GetString(1),
                            Name = reader.GetString(2),
                            Price = reader.GetDecimal(3),
                            ImageName = !reader.IsDBNull(4) ? reader.GetString(4) : default
                        };
                        map[productId.ToString()] = product;
                    }
                    if (!reader.IsDBNull(5))
                    {
                        Category category = new Category
                        {
                            Id = reader.GetInt32(5),
                            Name = reader.GetString(6)
                        };
                        product.Categories.Add(category);
                    }
                }
                reader.Close();
            }
            foreach (Product product in map.Values)
            {
                products.Add(product);
            }
            return products;
        }
        public Product SaveProduct(Product product)
        {
            LOGGER.Info($"ProductDao::SaveProduct {product}");
            Product updated = null;
            int? productId = product.Id;
            if (productId != null && productId > 0)
            {
                updated = UpdateProduct(product);
            }
            else
            {
                updated = InsertProduct(product);
            }
            return updated;
        }

        private Product InsertProduct(Product product)
        {
            LOGGER.Info($"ProductDao::InsertProduct {product}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("INSERT INTO product (sku, product, price, image) OUTPUT INSERTED.product_id VALUES (@sku, @productName, @price, @imageName)", conn);
                sql.Parameters.AddWithValue("@sku", product.Sku);
                sql.Parameters.AddWithValue("@productName", product.Name);
                sql.Parameters.AddWithValue("@price", product.Price);
                sql.Parameters.AddWithValue("@imageName", (object)product.ImageName ?? DBNull.Value);
                conn.Open();
                int productId = Convert.ToInt32(sql.ExecuteScalar());
                product.Id = productId;
            }
            LOGGER.Info(String.Format("Set new product id to {0}", product.Id));
            UpdateProductCategories(product);
            return product;
        }

        private Product UpdateProduct(Product product)
        {
            LOGGER.Info($"ProductDao::UpdateProduct {product}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand sql = new SqlCommand("UPDATE product SET sku = @sku, product = @productName, price = @price, image = @imageName WHERE product_id = @productId", conn);
                sql.Parameters.AddWithValue("@productId", product.Id);
                sql.Parameters.AddWithValue("@sku", product.Sku);
                sql.Parameters.AddWithValue("@productName", product.Name);
                sql.Parameters.AddWithValue("@price", product.Price);
                sql.Parameters.AddWithValue("@imageName", (object)product.ImageName ?? DBNull.Value);
                conn.Open();
                sql.ExecuteNonQuery();
                UpdateProductCategories(product);
            }
            return product;
        }
        private void UpdateProductCategories(Product product)
        {
            LOGGER.Info($"ProductDao::UpdateProductCategories {product} {product.Categories.Count}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                SqlCommand deleteSql = new SqlCommand("DELETE FROM product_categories WHERE product_id = @productId", conn);
                deleteSql.Parameters.AddWithValue("@productId", product.Id);
                conn.Open();
                deleteSql.ExecuteNonQuery();

                if (product.Categories.Count > 0)
                {
                    foreach (Category category in product.Categories)
                    {
                        LOGGER.Info($"Linking product {product.Id} to category {category.Id}");
                        SqlCommand insertSql = new SqlCommand("INSERT INTO product_categories (product_id, category_id) VALUES (@productId, @categoryId)", conn);
                        insertSql.Parameters.AddWithValue("@productId", product.Id);
                        insertSql.Parameters.AddWithValue("@categoryId", category.Id);
                        insertSql.ExecuteNonQuery();
                    }
                }
            }
        }

        public Product DeleteProduct(Product product)
        {
            LOGGER.Info($"ProductDao::DeleteProduct {product}");
            using (SqlConnection conn = new SqlConnection(CONNECTION_STRING))
            {
                // Foreign key constraint cascades to product_categories
                SqlCommand sql = new SqlCommand("DELETE FROM product WHERE product_id = @productId", conn);
                sql.Parameters.AddWithValue("@productId", product.Id);
                conn.Open();
                sql.ExecuteNonQuery();
            }
            return product;
        }
    }
}
