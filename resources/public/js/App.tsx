import React from "react";
import { BrowserRouter, Route } from "react-router-dom";
import { Home } from "./components/Home";
import { About } from "./components/About";
import { Verify } from "./components/Verify";
import { LoginProvider } from "./contexts/LoginContext";

export const App = () => {
    return (
        <LoginProvider>
            <BrowserRouter>
                <Route exact path="/" component={Home} />
                <Route exact path="/about" component={About} />
                {/* <Route
          path="/signup"
          render={({ match, history }) => (
            <Signup match={match} history={history} />
          )}
        /> */}

                <Route
                    path="/user/verify"
                    render={({ match, history, location }) => (
                        <Verify match={match} history={history} location={location}/>
                    )}
                />
                {/* <Route exact path="/leaderboard" component={Leaderboard} /> */}
            </BrowserRouter>
        </LoginProvider>
    );
};
