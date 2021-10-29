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
using System.ComponentModel.DataAnnotations;

namespace SaaSBoostHelloWorld.Models
{
    public class Category
    {

        private int _id;
        private string _name;

        public Category() : this(0)
        {
        }

        public Category(int id) : this(id, null)
        {
        }

        public Category(int id, string name)
        {
            _id = id;
            _name = name;
        }

        public override string ToString()
        {
            return $"{base.ToString()} {{\"id\":{Id}, \"name\":{(Name == null ? "null" : $"\"{Name}\"")}}}";
        }

        public int Id
        {
            get => _id;
            set => _id = value;
        }

        [Required]
        public string Name
        {
            get => _name;
            set => _name = value;
        }
    }
}