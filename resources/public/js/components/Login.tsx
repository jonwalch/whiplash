import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import { Link } from "react-router-dom";
import "../../css/App.css";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"

export function Login(props: any) {
  const [screenName, setScreenName] = useState("");
  const [password, setPassword] = useState("");
  const { state, setState } = useContext(LoginContext);

  useEffect(() => {
    loggedIn();
  }, []);

  const toggleValid = () => {
    //TODO: add validation
    return !(screenName && password);
  };

  const login = async () => {
    const response = await fetch(baseUrl + "v1/user/login", {
      headers: { "Content-Type": "application/json" },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({ password: password, screen_name: screenName })
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
    if (response.status == 200) {
      setState({ userLoggedIn: true });
    } else {
      alert(resp.message);
    }
  };

  const loggedIn = async () => {
    const response = await fetch(baseUrl + "v1/user/login", {
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
    const response = await fetch(baseUrl + "v1/user/logout", {
      headers: { "Content-Type": "application/json" },
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

  const renderContent = () => {
    if (state.userLoggedIn === null) {
      return <div>Loading</div>;
    } else if (state.userLoggedIn) {
      return (
        <>
          <button type="button" onClick={logout}>
            Sign out
          </button>
          <Link to="/leaderboard">Weekly Leaderboard</Link>
        </>
      );
    } else {
      return (
        <>
          <h3>Log in ya fuck</h3>
          <input
            placeholder="Screen Name"
            value={screenName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setScreenName(e.currentTarget.value);
            }}
            type="text"
            maxLength={20}
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
          <Link to="/signup">Sign Up Now!</Link>
          <Link to="/leaderboard">Weekly Leaderboard</Link>
        </>
      );
    }
  };

  return <div className="login-bar">{renderContent()}</div>;
}