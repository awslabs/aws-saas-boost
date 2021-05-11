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
	<title><c:choose><c:when test="${not empty category.id}">Edit Category</c:when><c:otherwise>Add Category</c:otherwise></c:choose></title>
	<link href="webjars/bootstrap/4.5.0/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<div class="container pt-3">
	<div class="row">
		<div class="col">
		  <h2><c:choose><c:when test="${not empty category.id}">Edit Category</c:when><c:otherwise>Add Category</c:otherwise></c:choose></h2>
		</div>
	  </div>
	<form:form modelAttribute="category" method="post" action="editCategory">
	<form:hidden path="id" />
	<div class="form-group row">
	  <spring:bind path="name">
	      <div class="col-2">
		    <form:label path="name" for="name" class="col-form-label">Category</form:label>
		  </div>
		  <div class="col-10">
			  <form:input path="name" type="text" class="form-control ${status.error ? 'is-invalid' : ''}" style="width:auto" placeholder="Category Name" />
			  <form:errors path="name" class="invalid-feedback" />
		  </div>
	  </spring:bind>
	</div>
	<div class="form-group row">
		<div class="col-10 offset-2">
			<a role="button" class="btn btn-secondary" href="cancelCategory">Cancel</a>
			<button type="submit" class="btn btn-primary">Submit</button>
		</div>
	</div>
	</form:form> 
</div>
</body>
</html>