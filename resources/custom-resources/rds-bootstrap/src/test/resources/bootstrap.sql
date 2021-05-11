-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
--
-- Licensed under the Apache License, Version 2.0 (the "License").
-- You may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

CREATE TABLE IF NOT EXISTS category (
	category_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	category VARCHAR(255) NOT NULL UNIQUE CHECK (category <> '')
)
ENGINE 'InnoDB';

CREATE TABLE IF NOT EXISTS product (
	product_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	sku VARCHAR(32) NOT NULL UNIQUE CHECK (sku <> ''),
	product VARCHAR(255) NOT NULL UNIQUE CHECK (product <> ''),
	price DECIMAL(9,2) NOT NULL,
	image VARCHAR(255)
)
ENGINE 'InnoDB';

CREATE TABLE IF NOT EXISTS product_categories (
	product_id INT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE ON UPDATE CASCADE,
	category_id INT NOT NULL REFERENCES category (category_id) ON DELETE RESTRICT ON UPDATE CASCADE,
	CONSTRAINT product_categories_pk PRIMARY KEY (product_id, category_id)
)
ENGINE 'InnoDB';

CREATE TABLE IF NOT EXISTS purchaser (
	purchaser_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	first_name VARCHAR(64),
	last_name VARCHAR(64),
	UNIQUE(first_name, last_name)
)
ENGINE 'InnoDB';

CREATE TABLE IF NOT EXISTS order_fulfillment (
	order_fulfillment_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	order_date DATE NOT NULL,
	ship_date DATE,
	purchaser_id INTEGER NOT NULL REFERENCES purchaser (purchaser_id) ON DELETE RESTRICT ON UPDATE CASCADE,
	ship_to_line1 VARCHAR(128),
	ship_to_line2 VARCHAR(128),
	ship_to_city VARCHAR(128),
	ship_to_state VARCHAR(128),
	ship_to_postal_code VARCHAR(128),
	bill_to_line1 VARCHAR(128),
	bill_to_line2 VARCHAR(128),
	bill_to_city VARCHAR(128),
	bill_to_state VARCHAR(128),
	bill_to_postal_code VARCHAR(128)
)
ENGINE 'InnoDB';

CREATE TABLE IF NOT EXISTS order_line_item (
	order_line_item_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	order_fulfillment_id INT NOT NULL REFERENCES order_fulfillment (order_fulfillment_id) ON DELETE RESTRICT ON UPDATE CASCADE,
	product_id INT NOT NULL REFERENCES product (product_id) ON DELETE RESTRICT ON UPDATE CASCADE,
	quantity INT NOT NULL CHECK (quantity > 0),
	unit_purchase_price DECIMAL(9, 2) NOT NULL
)
ENGINE 'InnoDB';
