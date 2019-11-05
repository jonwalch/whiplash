import React, { useState, useEffect } from "react";
import queryString from "query-string";
import { baseUrl } from "../config/const";
import { getCSRFToken } from "../common";
import { Link } from "react-router-dom";

const defaultMessage = "Verifying your email address...";

export function Verify(props: any) {
  const [verifyMessage, setVerifyMessage] = useState<string>(defaultMessage);
  const verifyEmail = async () => {
    const response = await fetch(baseUrl + "user/verify", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify(queryString.parse(props.location.search))
    });

    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
      setVerifyMessage(resp.message);
    } else {
      setVerifyMessage(
        "Failed to verify email address. Please contact us at support@whiplashesports.com."
      );
    }
  };

  useEffect(() => {
    verifyEmail();
  }, []);

  return (
    <>
      <p>{verifyMessage}</p>
      <Link to={"/"}>Back to home page</Link>
    </>
  );
}
