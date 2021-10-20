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
using SaaSBoostHelloWorld.Repository;
using System;
using System.Collections.Generic;
using System.IO;
using System.Web;
using System.Web.Mvc;

namespace SaaSBoostHelloWorld.Controllers
{
    public class ProductController : Controller
    {
        private static readonly ILog LOGGER = LogManager.GetLogger(typeof(ProductController));
        private IProductDao productDao = new ProductDao();
        private ICategoryDao categoryDao = new CategoryDao();

        // GET: Product
        public ActionResult Index()
        {
            LOGGER.Info("ProductController::Index");
            IList<Product> products = productDao.GetProducts();
            LOGGER.Info(String.Format("Returning {0} products", products.Count));
            ViewData["msg"] = TempData["msg"];
            ViewData["css"] = TempData["css"];

            return View(products);
        }

        // GET: Product/{id}
        public ActionResult Detail(int id)
        {
            LOGGER.Info("ProductController::Detail GET");
            Product product = productDao.GetProduct(id);
            return View(product);
        }

        // GET: Product/Cancel
        public ActionResult Cancel()
        {
            LOGGER.Info("ProductController::Cancel");
            ModelState.Clear();
            return RedirectToAction("Index");
        }

        // GET: Product/Create
        public ActionResult Create()
        {
            LOGGER.Info("ProductController::Create GET");
            IList<Category> categories = categoryDao.GetCategories();
            ViewData["categories"] = categories;
            return View(new Product());
        }

        // POST: Product/Create
        [HttpPost]
        public ActionResult Create(FormCollection collection)
        {
            LOGGER.Info("ProductController::Create POST");
            if (ModelState.IsValid)
            {
                try
                {
                    Product product = new Product
                    {
                        Sku = collection["Sku"],
                        Name = collection["Name"],
                        Price = Convert.ToDecimal(collection["Price"]),
                        ImageName = default
                    };
                    string categories = collection["Categories"];
                    if (!String.IsNullOrEmpty(categories))
                    {
                        string[] categoryIds = categories.ToString().Split(new char[] { ',' }, StringSplitOptions.RemoveEmptyEntries);
                        LOGGER.Info($"Looping through {categoryIds.Length} checked category ids");
                        for (int i = 0; i < categoryIds.Length; i++)
                        {
                            string categoryId = categoryIds[i];
                            LOGGER.Info($"Converting {categoryId} to Int");
                            product.Categories.Add(new Category(Convert.ToInt32(categoryId)));
                        }
                    }
                    product = productDao.SaveProduct(product);
                    TempData["msg"] = "New product added";
                    TempData["css"] = "success";
                    return RedirectToAction("Index");
                }
                catch (Exception e)
                {
                    LOGGER.Error($"Error saving product: {e}");
                    return View();
                }
            }
            else
            {
                LOGGER.Warn("ModelState is invalid");
                return View();
            }
        }

        // GET: Product/Edit/{id}
        public ActionResult Edit(int id)
        {
            LOGGER.Info("ProductController::Edit GET");
            IList<Category> categories = categoryDao.GetCategories();
            ViewData["categories"] = categories;
            Product product = productDao.GetProduct(id);
            IList<int> existingCategories = new List<int>();
            foreach (Category category in product.Categories)
            {
                existingCategories.Add(category.Id);
            }
            ViewData["existingCategories"] = existingCategories;
            return View(product);
        }

        // POST: Product/Edit/{id}
        [HttpPost]
        public ActionResult Edit(FormCollection collection, HttpPostedFileBase file)
        {
            LOGGER.Info("ProductController::Edit POST");
            if (ModelState.IsValid)
            {
                try
                {
                    Product product = new Product
                    {
                        Id = Convert.ToInt32(collection["Id"]),
                        Sku = collection["Sku"],
                        Name = collection["Name"],
                        Price = Convert.ToDecimal(collection["Price"]),
                        ImageName = default
                    };
                    
                    string categories = collection["Categories"];
                    if (!String.IsNullOrEmpty(categories))
                    {
                        string[] categoryIds = categories.ToString().Split(new char[] { ',' }, StringSplitOptions.RemoveEmptyEntries);
                        LOGGER.Info($"Looping through {categoryIds.Length} checked category ids");
                        for (int i = 0; i < categoryIds.Length; i++)
                        {
                            string categoryId = categoryIds[i];
                            LOGGER.Info($"Converting {categoryId} to Int");
                            product.Categories.Add(new Category(Convert.ToInt32(categoryId)));
                        }
                    }

                    if (file != null && file.ContentLength > 0)
                    {
                        LOGGER.Info($"Processing uploaded file {file.FileName}");
                        string fileName = Path.GetFileName(file.FileName);
                        product.ImageName = fileName;
                        string filePath = product.GetImagePath();
                        //string path = Path.Combine(Server.MapPath("~/App_Data/uploads"), fileName);
                        //string tenantId = Environment.GetEnvironmentVariable("TENANT_ID") ?? "Unknown";
                        //string path = Path.Combine("C:\\Images", filePath);
                        string path = Path.Combine(Server.MapPath("~/Images"), filePath);
                        LOGGER.Info($"Saving uploaded file to {path}");
                        try
                        {
                            new FileInfo(path).Directory.Create();
                            file.SaveAs(path);
                        } catch (Exception e)
                        {
                            LOGGER.Error($"Error saving file: {e}");
                        }
                    } else
                    {
                        LOGGER.Info("Uploaded file is null");
                    }
                    product = productDao.SaveProduct(product);
                    TempData["msg"] = "Product updated";
                    TempData["css"] = "success";
                    return RedirectToAction("Index");
                }
                catch (Exception e)
                {
                    LOGGER.Error($"Error saving product: {e}");
                    return View();
                }
            }
            else
            {
                LOGGER.Warn("ModelState is invalid");
                return View();
            }
        }

        // GET: Product/Delete/{id}
        public ActionResult Delete(int id)
        {
            LOGGER.Info("ProductController::Delete GET");
            Product product = productDao.GetProduct(id);
            if (product == null)
            {
                TempData["msg"] = $"No product for id {id}";
                TempData["css"] = "danger";
            }
            return View(product);
        }

        // POST: Product/Delete/{id}
        [HttpPost]
        public ActionResult Delete(int id, FormCollection collection)
        {
            LOGGER.Info("ProductController::Delete POST");
            try
            {
                Product product = new Product
                {
                    Id = Convert.ToInt32(collection["Id"]),
                    Name = collection["Name"]
                };
                productDao.DeleteProduct(product);
                TempData["msg"] = "Product deleted";
                TempData["css"] = "success";
                return RedirectToAction("Index");
            }
            catch (Exception e)
            {
                LOGGER.Error($"Error deleting product: {e}");
                return View();
            }
        }
    }
}