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
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<html lang="en">
<head>
	<title><c:choose><c:when test="${not empty product.id}">Edit Product</c:when><c:otherwise>Add Product</c:otherwise></c:choose></title>
	<link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<div class="container pt-3">
  <div class="row">
    <div class="col">
      <h2><c:choose><c:when test="${not empty product.id}">Edit Product</c:when><c:otherwise>Add Product</c:otherwise></c:choose></h2>
    </div>
  </div>
	<form:form modelAttribute="product" method="post" action="editProduct" enctype="multipart/form-data">
	<form:hidden path="id" />
	<div class="form-group row">
	  <spring:bind path="name">
	    <div class="col-2">
	      <form:label path="name" for="name" class="col-form-label">Product</form:label>
	    </div>
	    <div class="col-10">
	      <form:input path="name" type="text" class="form-control ${status.error ? 'is-invalid' : ''}" style="width:auto" placeholder="Product Name" />
	      <form:errors path="name" class="invalid-feedback" />
	    </div>
	  </spring:bind>
	</div>
	<div class="form-group row">
      <spring:bind path="sku">
        <div class="col-2">
          <form:label path="sku" for="sku" class="col-form-label">SKU</form:label>
        </div>
        <div class="col-10">
          <form:input path="sku" type="text" class="form-control ${status.error ? 'is-invalid' : ''}" style="width:auto" placeholder="SKU" />
          <form:errors path="sku" class="invalid-feedback" />
        </div>
      </spring:bind>
  </div>
	<div class="form-group row">
      <spring:bind path="price">
        <div class="col-2">
          <form:label path="price" for="price" class="col-form-label">Price</form:label>
        </div>
        <div class="col-10">
          <form:input path="price" type="number" min="0.00" step="0.01" class="form-control ${status.error ? 'is-invalid' : ''}" style="width:auto" placeholder="Price" />
          <form:errors path="price" class="invalid-feedback" />
        </div>
      </spring:bind>
  </div>
	<div class="form-group row">
      <spring:bind path="categories">
        <div class="col-2">
          <form:label path="categories" for="categories" class="col-form-label">Categories</form:label>
        </div>
        <div class="col-10">
          <c:forEach items="${categories}" var="category" varStatus="loop">
          <div class="form-check ${status.error ? 'is-invalid' : ''}">
          <c:choose>
            <c:when test="${not empty existingCategories[category.id]}">
              <form:checkbox path="categories" class="form-check-input" value="${category.id}" checked="checked" />
            </c:when>
            <c:otherwise>
              <form:checkbox path="categories" class="form-check-input" value="${category.id}" />
            </c:otherwise>
          </c:choose>
            <form:label path="categories" class="form-check-label" for="categories${loop.count}">${category.name}</form:label>
          </div>
          </c:forEach>
          <form:errors path="categories" class="invalid-feedback" />
        </div>
      </spring:bind>
  </div>
	<div class="form-group row">
        <div class="col-2">
          <form:label path="image" for="file" class="col-form-label">Image</form:label>
        </div>
        <div class="col-10">
          <input type="file" class="form-control-file ${status.error ? 'is-invalid' : ''}" id="image" name="image" style="width:auto" />
          <form:errors path="image" class="invalid-feedback" />
        </div>
  </div>
	<div class="form-group row">
	  <div class="col-10 offset-2">
	    <a role="button" class="btn btn-secondary" href="cancelProduct">Cancel</a>
	    <button type="submit" class="btn btn-primary">Submit</button>
	  </div>
	</div>
	<spring:bind path="imageName"><form:input path="imageName" type="hidden" /></spring:bind>
	</form:form>
</div>
</body>
</html>