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
package com.amazon.aws.partners.saasfactory.domain;

import java.io.Serializable;

public class Category implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;

    public Category() {
        this(null, null);
    }

    public Category(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Category(Category copyMe) {
        if (copyMe != null) {
            this.id = copyMe.getId();
            this.name = copyMe.getName();
        }
    }

    @Override
    public String toString() {
        StringBuilder category = new StringBuilder();
        category.append(super.toString());
        category.append(" {\"id\":");
        category.append(getId());
        category.append("\",\"name\":");
        if (getName() == null) {
            category.append("null");
        } else {
            category.append("\"");
            category.append(getName());
            category.append("\"");
        }
        category.append("}");
        return category.toString();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
