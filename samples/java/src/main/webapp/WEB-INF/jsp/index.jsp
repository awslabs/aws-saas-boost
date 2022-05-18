<!DOCTYPE html>
<!--
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<html lang="en">

<head>
  <title>Home Page</title>
  <link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
</head>

<body>
  <div class="container pt-3">
    <p>Hello Tenant ${tenantId}</p>

    <c:set var="now" value="<%= new java.util.Date()%>" />
    <p><fmt:formatDate type="both" timeStyle="medium" dateStyle="long" value="${now}"></fmt:formatDate></p>

    <ul>
      <li><a href="categories">Categories</a></li>
      <li><a href="products">Products</a></li>
      <li>Orders</li>
    </ul>
    <p></p>
    <ul>
      <li><a href="load.html">Artificial CPU load</a></li>
      <li><a href="metric.html?type=storage&tier=basic">Artificial Metric for Storage</a></li>
      <li><a href="meter.html?count=10&productCode=product_requests">Artificial Meter Event for Billing</a></li>
    </ul>
  </div>
</body>

</html>