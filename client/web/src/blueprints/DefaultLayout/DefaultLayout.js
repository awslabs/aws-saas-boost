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

import React, { Component, Suspense } from "react";
import { withRouter, Switch, Route, Redirect } from "react-router-dom";
import * as router from "react-router-dom";
import { Container } from "reactstrap";
import { Auth } from "aws-amplify";
import { connect } from "react-redux";
import { fetchSettings, fetchConfig } from "../../settings/ducks";

import {
  AppAside,
  AppFooter,
  AppHeader,
  AppSidebar,
  AppSidebarFooter,
  AppSidebarForm,
  AppSidebarHeader,
  AppSidebarMinimizer,
  AppBreadcrumb2 as AppBreadcrumb,
  AppSidebarNav2 as AppSidebarNav,
} from "@coreui/react";

// sidebar nav config
import navigation from "../../_nav";
// routes config
import routes from "../../routes/extras";

const DefaultHeader = React.lazy(() => import("./DefaultHeader"));
const DefaultFooter = React.lazy(() => import("./DefaultFooter"));
const SaasBoostChangePassword = React.lazy(() =>
  import("../../components/Auth/SaasBoostChangePassword1")
);

const mapStateToProps = (state) => {
  return { settings: state.settings, setup: state.settings.setup };
};

class DefaultLayout extends Component {
  constructor(props) {
    super(props);
    this.state = {
      showChangePassword: false,
      user: undefined,
    };

    this.handleSignOut = this.handleSignOut.bind(this);
    this.handleProfileClick = this.handleProfileClick.bind(this);
    this.showChangePassword = this.showChangePassword.bind(this);
    this.closeChangePassword = this.closeChangePassword.bind(this);
  }

  async componentDidMount() {
    const user = await Auth.currentUserInfo();
    this.setState((state, props) => {
      return {
        user: user,
      };
    });
  }

  handleSignOut = async () => {
    await Auth.signOut();
  };

  handleProfileClick = () => {
    const { history } = this.props;
    try {
      const userInfo = this.state.user;
      history.push(`/users/${userInfo.username}`);
    } catch (err) {
      console.error(err);
    }
  };

  showChangePassword = () => {
    this.setState((state) => {
      return {
        showChangePassword: true,
      };
    });
  };

  closeChangePassword = () => {
    this.setState((state) => {
      return {
        showChangePassword: false,
      };
    });
  };

  loading = () => (
    <div className="animated fadeIn pt-1 text-center">Loading...</div>
  );

  render() {
    const { showChangePassword, user } = this.state;
    const { setup, location } = this.props;
    if (!setup) {
      navigation.items.forEach((nav, index) => {
        if (nav.name === "Application") {
          navigation.items[index].badge = {};
        }

        if (nav.name === "Onboarding") {
          navigation.items[index].attributes.disabled = false;
        }

        if (nav.name === "Tenants") {
          navigation.items[index].attributes.disabled = false;
        }
      });
    }
    return (
      <div className="app">
        <AppHeader fixed>
          <Suspense fallback={this.loading()}>
            <DefaultHeader
              onLogout={this.handleSignOut}
              handleProfileClick={this.handleProfileClick}
              handleChangePasswordClick={this.showChangePassword}
              user={user}
            />
          </Suspense>
        </AppHeader>
        <div className="app-body">
          <AppSidebar fixed display="lg">
            <AppSidebarHeader />
            <AppSidebarForm />
            <Suspense>
              <AppSidebarNav
                navConfig={navigation}
                setup={setup ? "true" : "false"}
                router={router}
                location={location}
              />
            </Suspense>
            <AppSidebarFooter />
            <AppSidebarMinimizer />
          </AppSidebar>
          <main className="main">
            <AppBreadcrumb appRoutes={routes} router={router} />
            <Container fluid>
              <Suspense fallback={this.loading()}>
                <Switch>
                  {routes.map((route, idx) => {
                    return route.component ? (
                      <Route
                        key={idx}
                        path={route.path}
                        exact={route.exact}
                        name={route.name}
                        render={(props) => <route.component {...props} />}
                      />
                    ) : null;
                  })}
                  <Redirect from="/" to="/summary" />
                </Switch>
              </Suspense>
            </Container>
            <AppAside />
          </main>
        </div>
        <AppFooter>
          <Suspense fallback={this.loading()}>
            <DefaultFooter />
          </Suspense>
        </AppFooter>
        <SaasBoostChangePassword
          show={showChangePassword}
          handleClose={this.closeChangePassword}
        />
      </div>
    );
  }
}

export default connect(mapStateToProps, null)(withRouter(DefaultLayout));
