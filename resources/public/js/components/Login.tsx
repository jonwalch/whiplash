import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import { Link } from "react-router-dom";
import "../../css/App.css";
import { LoginContext, defaultLoggedIn } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"
import { getCSRFToken } from "../common";
import { Signup } from "./Signup";

export function Login(props: any) {
  const [screenName, setScreenName] = useState("");
  const [password, setPassword] = useState("");
  const [showSignup, setShowSignup] = useState(false);
  const { loggedInState, setLoggedInState } = useContext(LoginContext);

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
      setLoggedInState({ userName: screenName});
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

    if (response.status == 200){
      const resp = await response.json();
      console.log(resp);
      console.log(response.status);
      setLoggedInState({ userName: resp["whiplash/screen-name"]});
    } else {
      console.log
      setLoggedInState({ userName: ""})
    }
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
      setLoggedInState(defaultLoggedIn);
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
    if (loggedInState.userName === null) {
      return <div>Loading</div>;
    } else if (loggedInState.userName) {
      return (
        <>
          <p>Logged in as {loggedInState.userName}</p>
          <button type="button" onClick={logout}>
            Sign out
          </button>
        </>
      );
    } else {
      return (
        <>
          <h3>Log in</h3>
          <input
            placeholder="Screen Name"
            value={screenName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setScreenName(e.currentTarget.value);
            }}
            type="text"
            maxLength={100}
          />
          <input
            placeholder="Password"
            value={password}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setPassword(e.currentTarget.value);
            }}
            type="password"
            maxLength={100}
          />
          <button type="button" onClick={login} disabled={toggleValid()}>
            Log In
          </button>
          <button type="button" onClick={() => {setShowSignup(!showSignup)}}>
            Show Sign Up
          </button>
          {/* <Link to="/signup">Sign Up Now!</Link> */}
        </>
      );
    }
  };

  return (
    <div className="login-bar">
      {renderContent()}
      {renderSignup()}
    </div>
  );
}
