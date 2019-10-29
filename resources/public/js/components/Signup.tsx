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
    <div>
      <h2>Sign up ya filthy animal</h2>
      <div className="signup">
        <input
          placeholder="Email"
          value={email}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setEmail(e.currentTarget.value);
          }}
          type="text"
          maxLength={100}
          minLength={5}
        />
        <input
          placeholder="Screen Name"
          value={screenName}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setScreenName(e.currentTarget.value);
          }}
          type="text"
          maxLength={50}
          minLength={1}
        />
        <input
          placeholder="Password"
          value={password}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setPassword(e.currentTarget.value);
          }}
          type="password"
          maxLength={100}
          minLength= {8}
        />
        <input
          placeholder="First Name"
          value={firstName}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setFirstName(e.currentTarget.value);
          }}
          type="text"
          maxLength={30}
          minLength={2}
        />
        <input
          placeholder="Last Name"
          value={lastName}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setLastName(e.currentTarget.value);
          }}
          type="text"
          maxLength={30}
          minLength={2}
        />
        <button type="button" onClick={createUser} disabled={toggleValid()}>
          Sign up
        </button>
      </div>
    </div>
  );
}
