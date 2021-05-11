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
<html lang="en">
<head>
  <title>Products</title>
  <link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
  <style type="text/css">
    tr.clickable-row {
      cursor: pointer;
    }
  </style>
</head>
<body>
<div class="container pt-3">
  <div class="row">
    <div class="col-10">
      <h2>Products</h2>
    </div>
    <div class="col-2 align-self-center">
      <a role="button" class="btn btn-success float-right" href="newProduct">Add Product</a>
    </div>
  </div>
  <c:if test="${not empty msg}">
  <div class="row">
    <div class="col-12">
      <div class="alert alert-${css} alert-dismissible fade show" role="alert">
        <strong>${msg}</strong>
        <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>
      </div>
    </div>
  </div>
  </c:if>
  <div class="row">
    <table class="table table-hover">
      <thead class="thead-light">
        <tr>
          <th style="width: 10%" scope="col">ID</th>
          <th style="width: 10%" scope="col">SKU</th>
          <th style="width: 40%" scope="col">Name</th>
          <th style="width: 10%" scope="col">Price</th>
          <th style="width: 30%" scope="col"></th>
        </tr>
      </thead>
      <c:forEach items="${products}" var="product">
      <tr class="clickable-row" data-href="productDetail?id=${product.id}">
        <th scope="row">${product.id}</td>
        <td>${product.sku}</td>
        <td>${product.name}</td>
        <td>${product.price}</td>
        <td><div class="float-right"><a role="button" class="btn btn-info" href="updateProduct?id=${product.id}">Edit</a> <a role="button" class="btn btn-danger" href="deleteProduct?id=${product.id}">Delete</a></div></td>
      </tr>
      </c:forEach>
    </table>
  </div>
</div>
<script src="webjars/jquery/3.5.1/jquery.min.js"></script>
<script src="webjars/bootstrap/4.5.0/js/bootstrap.bundle.min.js"></script>
<script type="text/javascript">
$(document).ready(function($) {
  $(".clickable-row").click(function() {
    window.location.assign($(this).data("href"));
  });
});
</script>
</body>
</html>