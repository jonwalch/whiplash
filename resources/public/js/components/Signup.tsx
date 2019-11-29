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
    <form className="form form--signup container" name="signUp">
      <h2>Sign up</h2>
      <label htmlFor="email">Email</label>
      <input
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
      <label htmlFor="userName">Username</label>
      <input
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
      <label htmlFor="password">Password</label>
      <input
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
      <label htmlFor="firstName">First Name</label>
      <input
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
      <label htmlFor="lastName">Last Name</label>
      <input
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
      <button type="button" onClick={createUser} disabled={toggleValid()}>
        Register
      </button>
    </form>
  );
}
