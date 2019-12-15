import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {Login} from "./Login";
import {Signup} from "./Signup";
import {getCSRFToken, scrollToTop} from "../common";
import {LoginContext} from "../contexts/LoginContext";
import {baseUrl} from "../config/const";

export function Header() {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [showSignup, setShowSignup] = useState(false);
  const [showLogin, setShowLogin] = useState(false);

  useEffect(() => {
    loggedIn();
  }, []);

  const loggedIn = async () => {
    const response = await fetch(baseUrl + "user/login", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });

    if (response.status == 200){
      const resp = await response.json();
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
        // "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      setLoggedInState({userName: "", cash: 0});
    } else {
      alert("Failed to hit server to logout");
    }
  };

  const renderLoginForm = () => {
    if (showLogin) {
      return (
        <Login setShowSignup={setShowSignup}/>
      );
    }
  };

  const renderSignupForm = () => {
    if (showSignup) {
      return (
        <Signup setShowSignup={setShowSignup}/>
      );
    }
  };

  const renderLoginButton = () => {
    return (
      <button
        type="button"
        className="navigation__link"
        onClick={() => {
          scrollToTop();
          setShowLogin(!showLogin);
          setShowSignup(false);
        }}>
        Log In
      </button>
    );
  };

  const renderLogoutButton = () => {
    return (
      <button
        type="button"
        className="navigation__link"
        onClick={() => {
          logout()
          setShowLogin(false);
        }}>
        Log Out
      </button>
    );
  };

  const renderSignupButton = () => {
    return (
      <button
        type="button"
        className="button navigation__button"
        onClick={() => {
          scrollToTop();
          setShowSignup(!showSignup);
          setShowLogin(false);
        }}>
        Sign Up
      </button>
    );
  };

  const renderNavCtaButtons = () => {
    // No userName, currently loading
    if (loggedInState.userName === null) {
      return (
        <>
          <li>{renderSignupButton()}</li>
        </>
      )
      // Show log in button, user is not logged in
    } else if (loggedInState.userName === '') {
      return (
        <>
          <li>{renderLoginButton()}</li>
          <li>{renderSignupButton()}</li>
        </>
      )
      // Show log out button, user is logged in
    } else {
      return (
        <>
          <li className="navigation__item">{loggedInState.userName}</li>
          <li className="navigation__item"><span className="navigation__highlight">Whiplash Cash:</span> ${loggedInState.cash}</li>
          <li>{renderLogoutButton()}</li>
        </>
      )
    }
  };

  return (
    <header role="banner" className="site-header">
      <div className="site-navigation container">
        <div className="site-branding">
          <h1 className="site-branding__title">
            <a href="/">
              <img
                src={baseUrl + "/img/logos/whiplash-horizontal-4c.svg"}
                alt="Whiplash"
                width="165"
                height="36"
                className="site-logo"
              />
            </a>
          </h1>
        </div>
        <nav className="navigation">
          <ul className="navigation__list">
            <li><Link className="navigation__link" to="/about">About</Link></li>
            <li><a className="navigation__link" href="mailto:support@whiplashesports.com">Contact</a></li>
          </ul>
        </nav>
        <nav className="navigation navigation--cta">
          <ul className="navigation__list">
            {renderNavCtaButtons()}
          </ul>
        </nav>
      </div>
      {renderLoginForm()}
      {renderSignupForm()}
    </header>
  );
}
