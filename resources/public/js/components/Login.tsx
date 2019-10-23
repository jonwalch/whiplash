import React, { useState, useEffect, ChangeEvent } from "react";
import { Link } from "react-router-dom";

export function Login(props: any) {
  const [screenName, setScreenName] = useState("");
  const [password, setPassword] = useState("");

  const toggleValid = () => {
    //TODO: add length constraints to all of these
    return !(screenName && password);
  };

  const login = async () => {
    const response = await fetch("http://localhost:3000/v1/user/login", {
      headers: { "Content-Type": "application/json" },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({ password: password, screen_name: screenName })
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
  };

  return (
    <div>
      <h3>Log in ya fuck</h3>
      <input
        placeholder="Screen Name"
        value={screenName}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setScreenName(e.currentTarget.value);
        }}
        type="text"
      />
      <input
        placeholder="Password"
        value={password}
        onChange={(e: ChangeEvent<HTMLInputElement>) => {
          setPassword(e.currentTarget.value);
        }}
        type="text"
      />
      <button onClick={login} disabled={toggleValid()}>
        Fucking click it
      </button>
      <Link to="/signup">Sign Up Now!</Link>
    </div>
  );
}
