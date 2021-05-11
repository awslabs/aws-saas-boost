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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String sku;
    private String name;
    private BigDecimal price;
    private List<Category> categories = new ArrayList<>();
    private String imageName;
    private byte[] image;

    public Product() {
        this(null, null, null, null, null);
    }

    public Product(Integer id, String sku, String name, BigDecimal price, String imageName) {
        this(id, sku, name, price, imageName, null);
    }

    public Product(Integer id, String sku, String name, BigDecimal price, String imageName, List<Category> categories) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.imageName = imageName;
        this.categories = categories != null ? categories : new ArrayList<>();
    }

    public Product(Product copyMe) {
        if (copyMe != null) {
            this.id = copyMe.getId();
            this.sku = copyMe.getSku();
            this.name = copyMe.getName();
            this.price = copyMe.getPrice();
            this.imageName = copyMe.getImageName();
            this.categories = copyMe.getCategories();
        }
    }

    @Override
    public String toString() {
        StringBuilder product = new StringBuilder();
        product.append(super.toString());
        product.append(" {\"id\":");
        product.append(getId());
        product.append(",\"sku\":\"");
        product.append(getSku());
        product.append("\",\"name\":\"");
        product.append(getName());
        product.append("\",\"price\":");
        product.append(getPrice());
        product.append(",\"categories\":[");
        for (Iterator<Category> iter = getCategories().iterator(); iter.hasNext();) {
            product.append(iter.next().toString());
            if (iter.hasNext()) {
                product.append(",");
            }
        }
        product.append("]");
        product.append(",\"imageName\":\"");
        product.append(getImageName());
        product.append("\"}");
        return product.toString();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public String getImagePath() {
        String path = null;
        if (getImageName() != null && !getImageName().isEmpty()) {
            path = String.format("%d/%s", getId(), getImageName());
        }
        return path;
    }
}
