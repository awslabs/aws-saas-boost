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
import { PropTypes } from 'prop-types'
import React from 'react'
import { Input, Label, FormGroup } from 'reactstrap'

const timePeriodOptions = [
  { value: 'HOUR_1', description: 'Last Hour' },
  { value: 'HOUR_2', description: 'Last 2 hours' },
  { value: 'HOUR_4', description: 'Last 4 hours' },
  { value: 'HOUR_8', description: 'Last 8 hours' },
  { value: 'HOUR_10', description: 'Last 10 hours' },
  { value: 'HOUR_12', description: 'Last 12 hours' },
  { value: 'HOUR_24', description: 'Last 24 hours' },
  { value: 'TODAY', description: 'Today' },
  { value: 'DAY_7', description: 'Last 7 days' },
  { value: 'THIS_WEEK', description: 'This week' },
  { value: 'THIS_MONTH', description: 'This month' },
  { value: 'DAY_30', description: 'Last 30 days' },
]

const addTimePeriodOption = (option) => {
  return (
    <option value={option.value} key={option.value}>
      {option.description}
    </option>
  )
}

SelectTimePeriodComponent.propTypes = {
  selectTimePeriod: PropTypes.func,
  timePeriods: PropTypes.array,
}

export default function SelectTimePeriodComponent(props) {
  const DEFAULT_TIME_PERIOD = 'DAY_7'
  const timePeriod = props.selectTimePeriod
  const { timePeriods = [] } = props
  let periodsToDisplay = []
  if (timePeriods.length === 0) {
    timePeriodOptions.forEach((option) => {
      periodsToDisplay.push(addTimePeriodOption(option))
    })
  } else {
    timePeriods.forEach((timePeriod) => {
      timePeriodOptions.forEach((option) => {
        if (option.value === timePeriod) {
          periodsToDisplay.push(addTimePeriodOption(option))
        }
      })
    })
  }

  let selectedTimePeriod = DEFAULT_TIME_PERIOD
  const selectTimePeriod = (e) => {
    if (e.target.value !== '') {
      selectedTimePeriod = e.target.value
      timePeriod(selectedTimePeriod)
    }
  }

  return (
    <FormGroup className="form-inline mb-0">
      <Label htmlFor="stpcTimePeriod" className="mr-2">Time Period: </Label>
      <div>
        <Input
          type="select"
          name="stpcTimePeriod"
          id="stpcTimePeriod"
          onChange={selectTimePeriod}
          defaultValue="DAY_7"
        >
          {periodsToDisplay}
        </Input>
      </div>
    </FormGroup>
  )
}
