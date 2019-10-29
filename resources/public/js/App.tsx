import React from "react";
import { BrowserRouter, Route } from "react-router-dom";
import { Home } from "./components/Home";
import { Signup } from "./components/Signup";
import { Leaderboard } from "./components/Leaderboard";
import { LoginProvider } from "./contexts/LoginContext";

export const App = () => {
  return (
    <LoginProvider>
      <BrowserRouter>
        <Route exact path="/" component={Home} />
        {/* <Route
          path="/signup"
          render={({ match, history }) => (
            <Signup match={match} history={history} />
          )}
        /> */}

        {/* <Route exact path="/leaderboard" component={Leaderboard} /> */}
      </BrowserRouter>
    </LoginProvider>
  );
};
