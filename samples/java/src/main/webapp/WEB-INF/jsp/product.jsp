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
	<title>Product Detail</title>
	<link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<div class="container pt-3">
  <div class="row">
    <div class="col"><h2>Product Detail</h2></div>
  </div>
  <c:if test="${not empty msg}">
  <div class="row">
    <div class="col">
      <div class="alert alert-${css} alert-dismissible fade show" role="alert">
        <strong>${msg}</strong>
        <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>
      </div>
    </div>
  </div>
  </c:if>
  <div class="row">
    <div class="col-2"><strong>ID</strong></div>
    <div class="col-10">${product.id}</div>
  </div>
  <div class="row">
    <div class="col-2"><strong>SKU</strong></div>
    <div class="col-10">${product.sku}</div>
  </div>
  <div class="row">
    <div class="col-2"><strong>Name</strong></div>
    <div class="col-10">${product.name}</div>
  </div>
  <div class="row">
    <div class="col-2"><strong>Price</strong></div>
    <div class="col-10"><fmt:formatNumber value="${product.price}" type="currency"/></div>
  </div>
  <div class="row">
    <div class="col-2"><strong>Categories</strong></div>
    <div class="col-10"><c:forEach items="${product.categories}" var="category">
      ${category.name}<br/>
    </c:forEach></div>
  </div>
  <div class="row">
    <div class="col-2"><strong>Image</strong></div>
    <div class="col-10">${product.imageName}<br />
    <c:if test="${not empty product.imagePath}"><img src="${pageContext.request.contextPath}/images/${product.imagePath}"/></c:if></div>
  </div>
</div>
<script src="webjars/jquery/3.5.1/jquery.min.js"></script>
<script src="webjars/bootstrap/4.5.0/js/bootstrap.bundle.min.js"></script>
</body>
</html>