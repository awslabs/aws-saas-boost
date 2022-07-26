# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
FROM public.ecr.aws/docker/library/amazoncorretto:11-alpine-jdk AS build
RUN ["/usr/lib/jvm/default-jvm/bin/jlink", "--compress=2", "--no-man-pages", "--module-path", "/usr/lib/jvm/default-jvm/jmods", "--add-modules", "java.base,java.logging,java.xml,jdk.unsupported,java.sql,java.sql.rowset,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.net.http", "--output", "/jdk-mini"]

FROM public.ecr.aws/docker/library/alpine:latest
COPY --from=build /jdk-mini /opt/jdk/
VOLUME /mnt
ENV PATH=$PATH:/opt/jdk/bin
COPY ./target/HelloWorld.war /usr/src/app/app.war
ENTRYPOINT ["java", "-jar", "/usr/src/app/app.war"]
ENV TENANT_ID=""
EXPOSE 8080/tcp
