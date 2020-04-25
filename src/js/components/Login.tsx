import React, { useState, ChangeEvent, useContext } from "react";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"
import {getUser} from "../common/getUser";

export function Login(props: any) {
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [logInWaitingForResp, setLogInWaitingForResp] = useState<boolean>(false);
  const [forgotWaitingForResp, setForgotWaitingForResp] = useState<boolean>(false);

  const toggleValidLogIn = () => {
    //TODO: add validation
    return !(userName && password);
  };

  const toggleValidForgot = () => {
    //TODO: add validation
    return !(userName);
  };

  const login = async () => {
    setLogInWaitingForResp(true);
    const response = await fetch(baseUrl + "user/login", {
      headers: {
        "Content-Type": "application/json",
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({ password: password, user_name: userName })
    });
    if (response.status == 200) {
      getUser(setLoggedInState);
      setLogInWaitingForResp(false);
      props.setShowSignup(false);
    } else {
      const resp = await response.json();
      setLogInWaitingForResp(false);
      alert(resp.message);
    }
  };

  const requestRecovery = async () => {
    setForgotWaitingForResp(true);
    const response = await fetch(baseUrl + "user/password/request-recovery", {
      headers: {
        "Content-Type": "application/json",
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({ user: userName })
    });
    const resp = await response.json();
    setForgotWaitingForResp(false);
    alert(resp.message);
  };

  const loginOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValidLogIn()) {
      login();
    }
  };

  const renderContent = () => {
    if (loggedInState.userName === null || loggedInState.status === "user.status/unauth") {
      return (
          <form className="form form--login container" name="login">
            <hr className="form__hr"/>
            <header className="form__header">
              <h2 className="form__title">Log In</h2>
              <p className="form__description">Enter your username and password to log in.</p>
            </header>
            <fieldset className="form__fieldset">
              <div className="form__group">
                <label className="form__label" htmlFor="userName">Username or Email</label>
                <input
                    className="form__input"
                    value={userName}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => {
                      setUserName(e.currentTarget.value);
                    }}
                    onKeyPress={(e) => {
                      loginOnKeyPress(e)
                    }}
                    type="text"
                    maxLength={100}
                    name="userName"
                    id="userName"
                />
              </div>
              <div className="form__group">
                <label className="form__label" htmlFor="password">Password</label>
                <input
                    className="form__input"
                    value={password}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => {
                      setPassword(e.currentTarget.value);
                    }}
                    onKeyPress={(e) => {
                      loginOnKeyPress(e)
                    }}
                    type="password"
                    maxLength={100}
                    name="password"
                    id="password"
                />
              </div>
              <div className="login__buttons">
                <button
                    className={"button form__button form__button__margin-top button--login" +
                    (!toggleValidLogIn() ? "is-active" : "")}
                    type="button"
                    onClick={login}
                    disabled={toggleValidLogIn()}>
                  <div className={logInWaitingForResp ? "loading" : ""}>
                    {logInWaitingForResp ? "" : "Log In"}
                  </div>
                </button>
                <button
                    className={"button form__button form__button__margin-top button--login" +
                    (!toggleValidForgot() ? "is-active" : "")}
                    type="button"
                    onClick={requestRecovery}
                    disabled={toggleValidForgot()}>
                  <div className={forgotWaitingForResp ? "loading" : ""}>
                    {forgotWaitingForResp ? "" : "Forgot Password?"}
                  </div>
                </button>
              </div>
            </fieldset>
          </form>
      );
    }
    // Show nothing, user is logged in
    return;
  };

  return (
    <>
      {renderContent()}
    </>
  );
}
