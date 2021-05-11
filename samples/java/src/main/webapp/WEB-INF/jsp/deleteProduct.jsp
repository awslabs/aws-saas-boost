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
  <title>Delete Product</title>
  <link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<div class="container pt-3">
<h2>Are you sure?</h2>
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
  <form:form modelAttribute="product" method="post" action="deleteProduct">
	<form:hidden path="id" />
	<div class="form-check">
	  <input class="form-check-input" type="checkbox" value="" id="confirm" required />
      <label class="form-check-label" for="confirm">Check the box to confirm removal of product ${product.name}. I understand this action cannot be undone.</label>
    </div>
    <div class="form-group row">
  		<div class="col">
  			<a role="button" class="btn btn-secondary" href="cancelProduct">Cancel</a>
  			<button type="submit" class="btn btn-danger">Delete Product</button>
  		</div>
  	</div>
  </form:form>
</div>
<script src="webjars/jquery/3.5.1/jquery.min.js"></script>
<script src="webjars/bootstrap/4.5.0/js/bootstrap.bundle.min.js"></script>
</body>
</html>