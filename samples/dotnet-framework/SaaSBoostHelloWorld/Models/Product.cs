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
using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Text;

namespace SaaSBoostHelloWorld.Models
{
    public class Product
    {
        private int _id;
        private string _sku;
        private string _name;
        private decimal? _price;
        private IList<Category> _categories = new List<Category>();
        private string _imageName;
        private byte[] _image;

        public Product() : this(0, null, null, null, null)
        {
        }

        public Product(int id, string sku, string name, decimal? price, string imageName)
        {
            _id = id;
            _sku = sku;
            _name = name;
            _price = price;
            _imageName = imageName;
        }

        public Product(int id, string sku, string name, decimal? price, string imageName, IList<Category> categories)
        {
            _id = id;
            _sku = sku;
            _name = name;
            _price = price;
            _imageName = imageName;
            _categories = categories != null ? categories : new List<Category>();
        }

        public override string ToString()
        {
            //{base.ToString()}
            StringBuilder buffer = new StringBuilder();
            buffer.Append("{\"id\":");
            buffer.Append(Id.ToString());
            buffer.Append(", \"name\":");
            buffer.Append(Name == null ? "null" : $"\"{Name}\"");
            buffer.Append(", \"sku\":");
            buffer.Append(Sku == null ? "null" : $"\"{Sku}\"");
            buffer.Append(", \"price\":");
            buffer.Append(Price == null ? "null" : Price.ToString());
            buffer.Append(", \"imageName\":");
            buffer.Append(ImageName == null ? "null" : $"\"{ImageName}\"");
            buffer.Append("}");
            return buffer.ToString();
        }

        public int Id
        {
            get => _id;
            set => _id = value;
        }

        public string Sku
        {
            get => _sku;
            set => _sku = value;
        }

        [Required]
        public string Name
        {
            get => _name;
            set => _name = value;
        }

        public decimal? Price
        {
            get => _price;
            set => _price = value;
        }

        public IList<Category> Categories
        {
            get => _categories;
            set => _categories = value != null ? value : new List<Category>();
        }

        public string ImageName
        {
            get => _imageName;
            set => _imageName = value;
        }

        public byte[] Image
        {
            get => _image;
            set => _image = value;
        }

        public string GetImagePath()
        {
            string path = default;
            if (!String.IsNullOrEmpty(this.ImageName))
            {
                path = String.Format("{0}/{1}", this.Id, this.ImageName);
            }
            return path;
        }
    }
}