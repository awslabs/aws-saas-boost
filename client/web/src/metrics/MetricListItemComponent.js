/*
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

import React from "react";
import Moment from "react-moment";

export const MetricListItemComponent = ({ metric }) => {
  // this.topten =  metric.top10List.array.forEach(element => {
  //   tenantId: {element.tenantId}
  //   value: {element.value}
  // });
  return (
    <tr className="pointer" key={metric.time}>
      {/* <th scope="row">{metric.time}</th>       */}
      <td>
        <Moment date={metric.time} format="LLL" />
      </td>
      <td>
        <div>
          {metric.top10List.map((txt) => (
            <p>
              {txt.tenantId} : {txt.value}
            </p>
          ))}
        </div>
      </td>
    </tr>
  );
};
