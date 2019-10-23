import React from "react";
import { BrowserRouter, Route } from "react-router-dom";
import { Home } from "./components/Home";
import { Signup } from "./components/Signup";

export const App = () => {
  return (
    <BrowserRouter>
      <Route exact path="/" component={Home} />
      <Route path="/signup" render={({match, history}) => <Signup match={match} history={history}/>} />
    </BrowserRouter>
  );
};
