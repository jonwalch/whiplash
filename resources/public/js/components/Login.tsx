import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import { Link } from "react-router-dom";
import "../../css/App.css";
import { LoginContext, defaultLoggedIn } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"
import { getCSRFToken } from "../common";
import { Signup } from "./Signup";

export function Login(props: any) {
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [showSignup, setShowSignup] = useState(false);
  const { loggedInState, setLoggedInState } = useContext(LoginContext);

  useEffect(() => {
    loggedIn();
  }, []);

  const toggleValid = () => {
    //TODO: add validation
    return !(userName && password);
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
      body: JSON.stringify({ password: password, user_name: userName })
    });
    console.log(response.status);
    if (response.status == 200) {
      setLoggedInState({ userName: userName, cash: loggedInState.cash});
      setShowSignup(false);
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
      setLoggedInState({ userName: resp["user/name"], cash: loggedInState.cash});
      setShowSignup(false);
    } else {
      setLoggedInState({ userName: "", cash: loggedInState.cash})
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
      setLoggedInState({userName: "", cash: 0});
    } else {
      alert("Failed to hit server to logout");
    }
  };

  const loginOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      login();
    }
  };

  const renderContent = () => {
    if (loggedInState.userName === null) {
      return <div>Loading</div>;
    } else if (loggedInState.userName) {
      return (
        <>
          <p>User: {loggedInState.userName}</p>
          <p>Cash: ${loggedInState.cash}</p>
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
            placeholder="User Name"
            value={userName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setUserName(e.currentTarget.value);
            }}
            onKeyPress={(e) => {loginOnKeyPress(e)}}
            type="text"
            maxLength={100}
          />
          <input
            placeholder="Password"
            value={password}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setPassword(e.currentTarget.value);
            }}
            onKeyPress={(e) => {loginOnKeyPress(e)}}
            type="password"
            maxLength={100}
          />
          <button type="button" onClick={login} disabled={toggleValid()}>
            Log In
          </button>
          <button
            type="button"
            onClick={() => {
              setShowSignup(!showSignup);
            }}
          >
            Show Sign Up
          </button>
        </>
      );
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

  return (
    <div className="login-bar">
      {renderContent()}
      {renderSignup()}
    </div>
  );
}
