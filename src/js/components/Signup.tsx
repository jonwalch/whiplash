import React, { useState, ChangeEvent } from "react";
import { baseUrl } from "../config/const";

const { gtag } = require('ga-gtag');

export function Signup(props: any) {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [repeatPassword, setRepeatPassword] = useState("");
  const [email, setEmail] = useState("");
  const [signUpWaitingForResp, setSignUpWaitingForResp] = useState<boolean>(false);

  const toggleValid = () => {
    //TODO: add validation
    return !(firstName && lastName && userName && password && repeatPassword && email);
  };

  const submitSignUp = () => {
    if (password == repeatPassword){
      createUser()
    } else {
      alert("Passwords don't match!")
    }
  };

  const createUser = async () => {
    setSignUpWaitingForResp(true);
    const response = await fetch(baseUrl + "user/create", {
      headers: {
        "Content-Type": "application/json",
        // "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({
        first_name: firstName,
        last_name: lastName,
        user_name: userName,
        password: password,
        email: email
      })
    });
    const resp = await response.json();
    if (response.status == 200) {
      props.setShowSignup(false)
      alert("Successful Signup!");
    } else {
      alert(resp.message);
    }
    setSignUpWaitingForResp(false);

    gtag('event', 'submit-sign-up-form', {
      event_category: 'Sign Up',
    });
  };

  const signupOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      submitSignUp();
    }
  };

  return (
    <form className="form form--signup container" name="signUp">
      <hr className="form__hr" />
      <header className="form__header">
        <h2 className="form__title">Sign Up</h2>
        <p className="form__description">Create a username and password to sign up.</p>
      </header>
      <fieldset className="form__fieldset">
        <div className="form__group">
          <label className="form__label" htmlFor="email">Email</label>
          <input
            className="form__input"
            value={email}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setEmail(e.currentTarget.value);
            }}
            onKeyPress={(e) => {signupOnKeyPress(e)}}
            type="email"
            maxLength={100}
            minLength={5}
            name="email"
            id="email"
          />
        </div>
        <div className="form__group">
          <label className="form__label" htmlFor="userName">Username</label>
          <input
            className="form__input"
            value={userName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setUserName(e.currentTarget.value);
            }}
            onKeyPress={(e) => {signupOnKeyPress(e)}}
            type="text"
            maxLength={100}
            minLength={1}
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
            onKeyPress={(e) => {signupOnKeyPress(e)}}
            type="password"
            maxLength={100}
            minLength= {8}
            name="password"
            id="password"
          />
        </div>
        <div className="form__group">
          <label className="form__label" htmlFor="repeatPassword">Confirm Password</label>
          <input
              className="form__input"
              value={repeatPassword}
              onChange={(e: ChangeEvent<HTMLInputElement>) => {
                setRepeatPassword(e.currentTarget.value);
              }}
              onKeyPress={(e) => {signupOnKeyPress(e)}}
              type="password"
              maxLength={100}
              minLength= {8}
              name="repeatPassword"
              id="repeatPassword"
          />
        </div>
        <div className="form__group">
          <label className="form__label" htmlFor="firstName">First Name</label>
          <input
            className="form__input"
            value={firstName}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              setFirstName(e.currentTarget.value);
            }}
            onKeyPress={(e) => {signupOnKeyPress(e)}}
            type="text"
            maxLength={30}
            minLength={2}
            name="firstName"
            id="firstName"
          />
        </div>
        <div className="form__group">
          <label className="form__label" htmlFor="lastName">Last Name</label>
          <input
            className="form__input"
            value={lastName}
              onChange={(e: ChangeEvent<HTMLInputElement>) => {
                setLastName(e.currentTarget.value);
              }}
              onKeyPress={(e) => {signupOnKeyPress(e)}}
            type="text"
            maxLength={30}
            minLength={2}
            name="lastName"
            id="lastName"
          />
        </div>
        <button
          className="button form__button form__button__margin-top"
          type="button"
          onClick={submitSignUp}
          disabled={toggleValid()}>
          <div className={signUpWaitingForResp ? "loading" : ""}>
            {signUpWaitingForResp ? "" : "Sign Up"}
          </div>
        </button>
      </fieldset>
    </form>
  );
}
