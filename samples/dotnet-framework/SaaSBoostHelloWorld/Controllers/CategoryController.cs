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
using System.Web.Mvc;

namespace SaaSBoostHelloWorld.Controllers
{
    public class CategoryController : Controller
    {
        private static readonly ILog LOGGER = LogManager.GetLogger(typeof(CategoryController));
        private ICategoryDao categoryDao = new CategoryDao();

        // GET: Category
        public ActionResult Index()
        {
            LOGGER.Info("CategoryController::Index");
            //IList<Category> categories = new List<Category>();
            IList<Category> categories = categoryDao.GetCategories();
            LOGGER.Info(String.Format("Returning {0} categories", categories.Count));
            ViewData["msg"] = TempData["msg"];
            ViewData["css"] = TempData["css"];

            return View(categories);
        }

        // GET: Category/Cancel
        public ActionResult Cancel()
        {
            LOGGER.Info("CategoryController::Cancel");
            ModelState.Clear();
            return RedirectToAction("Index");
        }

        // GET: Category/Create
        public ActionResult Create()
        {
            LOGGER.Info("CategoryController::Create GET");
            return View(new Category());
        }

        // POST: Category/Create
        [HttpPost]
        public ActionResult Create(FormCollection collection)
        {
            LOGGER.Info("CategoryController::Create POST");
            if (ModelState.IsValid)
            {
                try
                {
                    Category category = new Category
                    {
                        //Id = Convert.ToInt32(collection["Id"]),
                        Name = collection["Name"]
                    };
                    category = categoryDao.SaveCategory(category);
                    TempData["msg"] = "New category added";
                    TempData["css"] = "success";
                    return RedirectToAction("Index");
                }
                catch
                {
                    return View();
                }
            }
            else
            {
                return View();
            }
        }

        // GET: Category/Edit/{id}
        public ActionResult Edit(int id)
        {
            LOGGER.Info("CategoryController::Edit GET");
            //Category category = new Category
            //{
            //    Id = 1,
            //    Name = "JavaScript"
            //};
            Category category = categoryDao.GetCategory(id);
            return View(category);
        }

        // POST: Category/Edit/{id}
        [HttpPost]
        public ActionResult Edit(FormCollection collection)
        {
            LOGGER.Info("CategoryController::Edit POST");
            if (ModelState.IsValid)
            {
                try
                {
                    Category category = new Category
                    {
                        Id = Convert.ToInt32(collection["Id"]),
                        Name = collection["Name"]
                    };
                    category = categoryDao.SaveCategory(category);
                    TempData["msg"] = "New category added";
                    TempData["css"] = "success";
                    return RedirectToAction("Index");
                }
                catch (Exception e)
                {
                    LOGGER.Error($"Error saving category: {e}");
                    return View();
                }
            }
            else
            {
                LOGGER.Warn("ModelState is invalid");
                return View();
            }
        }

        // GET: Category/Delete/{id}
        public ActionResult Delete(int id)
        {
            LOGGER.Info("CategoryController::Delete GET");
            Category category = categoryDao.GetCategory(id);
            if (category == null)
            {
                TempData["msg"] = $"No category for id {id}";
                TempData["css"] = "danger";
            }
            return View(category);
        }

        // POST: Category/Delete/{id}
        [HttpPost]
        public ActionResult Delete(int id, FormCollection collection)
        {
            LOGGER.Info("CategoryController::Delete POST");
            try
            {
                Category category = new Category
                {
                    Id = Convert.ToInt32(collection["Id"]),
                    Name = collection["Name"]
                };
                categoryDao.DeleteCategory(category);
                TempData["msg"] = "Category deleted";
                TempData["css"] = "success";
                return RedirectToAction("Index");
            }
            catch (Exception e)
            {
                LOGGER.Error($"Error deleting category: {e}");
                return View();
            }
        }
    }
}
