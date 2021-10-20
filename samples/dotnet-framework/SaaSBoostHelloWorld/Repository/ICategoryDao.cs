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
using SaaSBoostHelloWorld.Models;
using System.Collections.Generic;

namespace SaaSBoostHelloWorld.Repository
{
    interface ICategoryDao
    {
        Category GetCategory(int categoryId);
        Category GetCategoryByName(string name);
        IList<Category> GetCategories();
        Category SaveCategory(Category category);
        Category DeleteCategory(Category category);
    }
}
