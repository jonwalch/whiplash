import React, {useContext, useState} from "react";
import {Link} from "react-router-dom";
import {Login} from "./Login";
import {Signup} from "./Signup";
import {scrollToTop} from "../common";
import {LoginContext} from "../contexts/LoginContext";
import {baseUrl} from "../config/const";
import {logout} from "../common/logout";

export function Header() {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [showSignup, setShowSignup] = useState(false);
  const [showLogin, setShowLogin] = useState(false);

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
          {showLogin ? "Cancel" : "Log In"}
      </button>
    );
  };

  const renderLogoutButton = () => {
    return (
      <button
        type="button"
        className="navigation__link"
        onClick={() => {
          logout(setLoggedInState);
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
          {showSignup ? "Cancel" : "Sign Up"}
      </button>
    );
  };

  const renderNavCtaButtons = () => {
      // Show log in button, user is not logged in
    if (loggedInState.userName === null) {
      return (
        <>
          <li>{renderLoginButton()}</li>
          <li>{renderSignupButton()}</li>
        </>
      )
    } else if (loggedInState.status === "user.status/unauth") {
        return (
            <>
                <li className="navigation__item">{loggedInState.userName}</li>
                <li className="navigation__item"><span className="navigation__highlight">Whipcash:</span> ${loggedInState.cash}</li>
                <li>{renderLoginButton()}</li>
                <li>{renderSignupButton()}</li>
            </>
        )
        // Show log out button, user is logged in
    } else {
      return (
        <>
          <li className="navigation__item"><Link to="/account">{loggedInState.userName}</Link></li>
          <li className="navigation__item"><span className="navigation__highlight">Whipcash:</span> ${loggedInState.cash}</li>
          <li>{renderLogoutButton()}</li>
        </>
      )
    }
  };

    const renderNotifications = () => {
        return (
            <>
                {loggedInState.notifications.map((notif: any) => {
                    if (notif["notification/type"] == "notification.type/bailout") {
                        return (
                            <li className="navigation__item navigation__fade-in">
                                <span className="navigation__highlight">You got bailed out! Your Whipcash was set to $100!</span>
                            </li>
                        );
                    } else if (notif["notification/type"] == "notification.type/no-bailout") {
                        return (
                            <li className="navigation__item navigation__fade-in">
                                <span className="navigation__highlight">Make an account to get bailed out when your Whipcash drops too low!</span>
                            </li>
                        );
                    }
                    else if (notif["notification/type"] == "notification.type/payout") {
                        const text = "You won $" + notif["bet/payout"] +
                            " Whipcash because you bet " + (notif["proposition/result?"] ? "Yes" : "No") +
                            " on '" + notif["proposition/text"] + "'!";
                        return (
                            <li className="navigation__item navigation__fade-in">
                                <span className="navigation__highlight">{text}</span>
                            </li>
                        );
                    }
                })
                }
            </>
        );
    };

  return (
    <header role="banner" className="site-header">
      <div className="site-navigation">
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
            {renderNotifications()}
            {renderNavCtaButtons()}
          </ul>
        </nav>
      </div>
      {renderLoginForm()}
      {renderSignupForm()}
    </header>
  );
}
