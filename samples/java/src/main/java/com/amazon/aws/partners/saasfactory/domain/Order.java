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
import java.util.Date;
import java.util.List;

public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Date orderDate;
    private Date shipDate;
    private Purchaser purchaser;
    private Address shipAddress;
    private Address billAddress;
    private List<OrderLineItem> lineItems = new ArrayList<>();

    public Order() {
        this(null, null, null, null, null, null, null);
    }

    public Order(Integer id, Date orderDate, Date shipDate, Purchaser purchaser, Address shipAddress, Address billAddress, List<OrderLineItem> lineItems) {
        this.id = id;
        this.orderDate = orderDate;
        this.shipDate = shipDate;
        this.purchaser = purchaser;
        this.shipAddress = shipAddress;
        this.billAddress = billAddress;
        this.lineItems = lineItems != null ? lineItems : new ArrayList<>();
    }

    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for(OrderLineItem lineItem : getLineItems()) {
            total = total.add(lineItem.getExtendedPurchasePrice());
        }
        return total;
    }

    public Order(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    public Date getShipDate() {
        return shipDate;
    }

    public void setShipDate(Date shipDate) {
        this.shipDate = shipDate;
    }

    public Purchaser getPurchaser() {
        return purchaser;
    }

    public void setPurchaser(Purchaser purchaser) {
        this.purchaser = purchaser;
    }

    public Address getShipAddress() {
        return shipAddress;
    }

    public void setShipAddress(Address shipAddress) {
        this.shipAddress = shipAddress;
    }

    public Address getBillAddress() {
        return billAddress;
    }

    public void setBillAddress(Address billAddress) {
        this.billAddress = billAddress;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems != null ? lineItems : new ArrayList<OrderLineItem>();
    }
}
