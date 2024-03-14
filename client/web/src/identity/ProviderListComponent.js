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
import {PropTypes} from 'prop-types'
import React from 'react'
import {
    Card, CardBody, CardHeader, CardGroup, CardFooter,
    Input,
    Label,
    Form, FormGroup,
    Button,
    Row,
    Col,
    Alert,
} from 'reactstrap'
import {ReactComponent as Auth0Logo} from './svg/auth0.svg';
import {ReactComponent as CognitoLogo} from './svg/cognito.svg';
import {ReactComponent as KeyCloakLogo} from './svg/keycloak.svg';

ProviderListItem.propTypes = {
    provider: PropTypes.object,
    handleProviderClick: PropTypes.func,
}

function ProviderListItem({provider, handleProviderClick}) {
    return (
        <Card
            className="my-2"
            outline
            style={{
                width: '12rem',
                alignItems: "center"
            }}
        >
            <CardHeader style={{width: '100%'}}>
                <FormGroup>
                    <div style={{marginLeft: '10px'}}>
                        <Input type="radio" name={'provider'} onClick={() => {
                            handleProviderClick(provider.id)
                        }}
                               disabled={provider.type == 'COGNITO' ? false : true}/>
                        <Label check>
                            {provider.name}
                        </Label>
                    </div>
                </FormGroup>
            </CardHeader>
            <CardBody>
                {getLogo(provider.type)}
            </CardBody>
        </Card>
    );
}

function showError(error, handleError) {
    return (
        <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
            <h4 className="alert-heading">Error</h4>
            <p>{error}</p>
        </Alert>
    )
}

ProviderList.propTypes = {
    providers: PropTypes.array,
    loading: PropTypes.string,
    error: PropTypes.string,
    handleProvisionProvider: PropTypes.func,
    handleProviderClick: PropTypes.func,
    handleCreateProvider: PropTypes.func,
    handleRefresh: PropTypes.func,
    handleError: PropTypes.func,
}

const getLogo = (type) => {
    let Component = CognitoLogo;
    if (type === 'AUTH0') {
        Component = Auth0Logo
    } else if (type === 'KEYCLOAK') {
        Component = KeyCloakLogo
    }
    return <Component style={{
        height: 80,
        width: '10rem'
    }}/>;
};

function ProviderList({
                          providers,
                          loading,
                          cancel,
                          error,
                          handleProviderClick,
                          handleCreateProvider,
                          handleRefresh,
                          handleError,
                      }) {

    return (
        <div className="animated fadeIn">
            <Row>
                <Col>{error && showError(error, handleError)}</Col>
            </Row>
            <Card>
                <CardHeader>
                    Identity Providers
                </CardHeader>
                <CardBody>
                    <Form>
                        <CardGroup>
                            {providers.map((provider) => (
                                <ProviderListItem key={provider.name}
                                                  provider={provider}
                                                  handleProviderClick={handleProviderClick}/>
                            ))}
                        </CardGroup>
                    </Form>
                </CardBody>
                <CardFooter>
                    <Button color="danger" onClick={cancel} type="button">
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        color="primary"
                        className="ml-2"
                        onClick={handleCreateProvider}
                    >Submit
                    </Button>
                </CardFooter>
            </Card>
        </div>
    )
}

export default ProviderList
