import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import { Link } from "react-router-dom";
import "../../css/App.css";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"
import { getCSRFToken } from "../common";
import { Signup } from "./Signup";

export function Login(props: any) {
  const [screenName, setScreenName] = useState("");
  const [password, setPassword] = useState("");
  const [showSignup, setShowSignup] = useState(false);
  const { state, setState } = useContext(LoginContext);

  useEffect(() => {
    loggedIn();
  }, []);

  const toggleValid = () => {
    //TODO: add validation
    return !(screenName && password);
  };

  const login = async () => {
    const response = await fetch(baseUrl + "user/login", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({ password: password, screen_name: screenName })
    });
    console.log(response.status);
    if (response.status == 200) {
      setState({ userLoggedIn: true });
    } else {
      const resp = await response.text();
      console.log(resp);
      alert(resp);
    }
  };

  const loggedIn = async () => {
    const response = await fetch(baseUrl + "user/login", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);

    setState({ userLoggedIn: response.status == 200 });
  };

  const logout = async () => {
    const response = await fetch(baseUrl + "user/logout", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
    if (response.status == 200) {
      setState({ userLoggedIn: false });
    } else {
      alert("Failed to hit server to logout");
    }
  };

  const renderSignup = () => {
    if (showSignup) {
      return (
        <>
          <Signup setShowSignup={setShowSignup}/>
        </>
      );
    }
  };

  const renderContent = () => {
    if (state.userLoggedIn === null) {
      return <div>Loading</div>;
    } else if (state.userLoggedIn) {
      return (
        <>
          <button type="button" onClick={logout}>
            Sign Out
          </button>
        </>
      );
    } else {
      return (
        <>
          <label for="screenName">Screen Name</label>
          <input
            value={screenName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setScreenName(e.currentTarget.value);
            }}
            type="text"
            maxLength={100}
            name="screenName"
            id="screenName"
          />
          <label for="password">Password</label>
          <input
            value={password}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setPassword(e.currentTarget.value);
            }}
            type="password"
            maxLength={100}
            name="password"
            id="password"
          />
          <button type="button" onClick={login} disabled={toggleValid()}>
            Log In
          </button>
          {/* <Link to="/signup">Sign Up Now!</Link> */}
        </>
      );
    }
  };

  return (
    <form className="login" name="login">
      {renderContent()}
    </form><!-- .login -->
    <button type="button" onClick={() => {setShowSignup(!showSignup)}}>
      Sign Up
    </button>
    {renderSignup()}
  );
}
