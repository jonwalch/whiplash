import React, { useState, useEffect, ChangeEvent } from "react";
import { baseUrl } from "../config/const";
import { getCSRFToken } from "../common";

export function Signup(props: any) {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");

  const toggleValid = () => {
    //TODO: add validation
    return !(firstName && lastName && userName && password && email);
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
        user_name: userName,
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
      alert("Successful Signup!");
    } else {
      alert(resp.message);
    }
  };
  
  const signupOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      createUser();
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
          onKeyPress={(e) => {signupOnKeyPress(e)}}
          type="text"
          maxLength={100}
          minLength={5}
        />
        <input
          placeholder="User Name"
          value={userName}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setUserName(e.currentTarget.value);
          }}
          onKeyPress={(e) => {signupOnKeyPress(e)}}
          type="text"
          maxLength={100}
          minLength={1}
        />
        <input
          placeholder="Password"
          value={password}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setPassword(e.currentTarget.value);
          }}
          onKeyPress={(e) => {signupOnKeyPress(e)}}
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
          onKeyPress={(e) => {signupOnKeyPress(e)}}
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
          onKeyPress={(e) => {signupOnKeyPress(e)}}
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
