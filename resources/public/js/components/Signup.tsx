import React, { useState, useEffect, ChangeEvent } from "react";
import { baseUrl } from "../config/const";
import { getCSRFToken } from "../common";

export function Signup(props: any) {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [screenName, setScreenName] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");

  const toggleValid = () => {
    //TODO: add validation
    return !(firstName && lastName && screenName && password && email);
  };

  const createUser = async () => {
    const response = await fetch(baseUrl + "user/create", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({
        first_name: firstName,
        last_name: lastName,
        screen_name: screenName,
        password: password,
        email: email
      })
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
    if (response.status == 200) {
      // props.history.push("/");
      props.setShowSignup(false)
    } else {
      alert(resp.message);
    }
  };

  return (
    <form className="signup" name="signUp">
      <h2>Sign up ya filthy animal</h2>
      <label for="email">Email</label>
      <input
        value={email}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setEmail(e.currentTarget.value);
        }}
        type="email"
        maxLength={100}
        minLength={5}
        name="email"
        id="email"
      />
      <label for="screenName">Screen Name</label>
      <input
        value={screenName}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setScreenName(e.currentTarget.value);
        }}
        type="text"
        maxLength={100}
        minLength={1}
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
        minLength= {8}
        name="password"
        id="password"
      />
      <label for="firstName">First Name</label>
      <input
        value={firstName}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setFirstName(e.currentTarget.value);
        }}
        type="text"
        maxLength={30}
        minLength={2}
        name="firstName"
        id="firstName"
      />
      <label for="lastName">Last Name</label>
      <input
        value={lastName}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setLastName(e.currentTarget.value);
        }}
        type="text"
        maxLength={30}
        minLength={2}
        name="lastName"
        id="lastName"
      />
      <button type="button" onClick={createUser} disabled={toggleValid()}>
        Sign up
      </button>
    </form><!-- .signup -->
  );
}
