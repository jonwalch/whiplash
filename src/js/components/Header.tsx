import React, {useContext, useEffect, useRef, useState} from "react";
import {Link} from "react-router-dom";
import {Login} from "./Login";
import {Signup} from "./Signup";
import {scrollToTop, usePrevious} from "../common";
import {LoginContext} from "../contexts/LoginContext";
import {HeaderContext} from "../contexts/HeaderContext";
import {baseUrl} from "../config/const";
import {logout} from "../common/logout";

const { gtag } = require('ga-gtag');

import UIfx from 'uifx';
// @ts-ignore
import kaChing from '../sfx/ka-ching.mp3'

const kc = new UIfx(
    kaChing,
    {
        volume: 0.4, // number between 0.0 ~ 1.0
        throttleMs: 100
    }
)

export function Header(props:any) {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const { headerState, setHeaderState } = useContext(HeaderContext);
    const [currentNotification, setCurrentNotification] = useState<any>(<></>)
    const [hamburgerOpen, setHamburgerOpen] = useState<boolean>(false);

    const prevLoggedInState = usePrevious(loggedInState)

    const navigation = useRef(null);
    const hamburger = useRef(null);

    // @ts-ignore
    const pulseP = useRef(null);

    function handleResize() {
        if (window.innerWidth <= 600) {
            // @ts-ignore
            navigation.current?.style.display = "none"
            // @ts-ignore
            hamburger.current?.style.display = "block"
        } else {
            // @ts-ignore
            navigation.current?.style.display = "flex"
            // @ts-ignore
            hamburger.current?.style.display = "none"
        }
    }

    useEffect( () => {
        if (window.innerWidth <= 600) {
            setHamburgerOpen(true)
        }
        handleResize()
        window.addEventListener('resize', handleResize)
    },[])

    useEffect( () => {
        // @ts-ignore
        pulseP.current?.classList.remove("profit-pulse")
        // @ts-ignore
        if (prevLoggedInState && prevLoggedInState.cash < loggedInState.cash){
            // @ts-ignore
            pulseP.current?.classList.add("profit-pulse")
        }

    }, [loggedInState.cash])

    const createNotificationMarkup = () => {
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
                                <span className="navigation__highlight">Verify your email address to get bailed out when your Whipcash drops too low!</span>
                            </li>
                        );
                    }
                    else if ((notif["notification/type"] == "notification.type/payout") &&
                        !(notif["proposition/result"] == "proposition.result/cancelled")) {
                        const text = "You won $" + notif["bet/payout"] +
                            " Whipcash because you bet " +
                            (notif["proposition/result"] === "proposition.result/true" ? "Yes" : "No") +
                            " on '" + notif["proposition/text"] + "'!";
                        if (props.sfx) {
                            kc.play()
                        }
                        return (
                            <li className="navigation__item navigation__fade-in">
                                <span className="navigation__highlight">{text}</span>
                            </li>
                        );
                    }
                    else if ((notif["notification/type"] == "notification.type/payout") &&
                        (notif["proposition/result"] == "proposition.result/cancelled")) {
                        const text = "You got your $" + notif["bet/payout"] +
                            " Whipcash back because you bet on '" +
                            notif["proposition/text"] + "' and it was cancelled.";
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

    useEffect(() => {
        if (loggedInState.notifications.length > 0) {
            setCurrentNotification(createNotificationMarkup())
            const timer = setTimeout(() => {
                setCurrentNotification(<></>);
            }, 5000);
            return () => clearTimeout(timer);
        }
        else {
            setCurrentNotification(<></>);
        }
    }, [loggedInState.notifications])

    const renderLoginForm = () => {
        if (headerState.showLogin) {
            return (
                <Login/>
            );
        }
    };

    const renderSignupForm = () => {
        if (headerState.showSignup) {
            return (
                <Signup/>
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
                    setHeaderState({showLogin: !headerState.showLogin, showSignup: false});
                }}>
                {headerState.showLogin ? "Cancel" : "Log In"}
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
                    setHeaderState({showLogin: false, showSignup: headerState.showSignup});
                }}>
                Log Out
            </button>
        );
    };

    const renderSignupButton = () => {
        return (
            <button
                type="button"
                className="button navigation__button__tiny"
                onClick={() => {
                    scrollToTop();
                    setHeaderState({showLogin: false, showSignup: !headerState.showSignup});
                    gtag('event', 'open-sign-up-form', {
                        event_category: 'Sign Up',
                    });
                }}>
                {headerState.showSignup ? "Cancel" : "Sign Up"}
            </button>
        );
    };

    const renderSocialCTAs = () => {
        return (
            <>
                <button
                    type="button"
                    style={{fontSize: "0.5rem", marginRight: "0.5rem"}}
                    className="button navigation__button__tiny"
                    onClick={() => {
                        const win = window.open("https://discord.gg/GsG2G9t", '_blank');
                        // @ts-ignore
                        win.focus();
                    }}>
                    <img src={baseUrl + "/img/logos/Discord-Logo-Wordmark-White.svg"}/>
                </button>
                <button
                    type="button"
                    className="button navigation__button__tiny"
                    style={{fontSize: "0.5rem"}}
                    onClick={() => {
                        const win = window.open("https://twitter.com/WhiplashGG", '_blank');
                        // @ts-ignore
                        win.focus();
                    }}>
                    <img style={{maxWidth: "35%"}} src={baseUrl + "/img/logos/Twitter_Logo_WhiteOnImage.svg"}/>
                </button>
            </>
        );
    }

    const renderNavCtaButtons = () => {
        // Show log in button, user is not logged in
        if (loggedInState.userName === null) {
            return (
                <>
                    {renderSocialCTAs()}
                    <li>{renderLoginButton()}</li>
                    <li>{renderSignupButton()}</li>
                </>
            )
        }
        // else if (loggedInState.status === "user.status/unauth") {
        //     return (
        //         <>
        //             <li className="navigation__item">{loggedInState.userName}</li>
        //             <li className="navigation__item">
        //                 <span className="navigation__highlight">W$:</span> {loggedInState.cash}
        //             </li>
        //             <li>{renderLoginButton()}</li>
        //             <li>{renderSignupButton()}</li>
        //         </>
        //     )
        //     // Show log out button, user is logged in
        // }
        else {
            return (
                <>
                    {/*<button*/}
                    {/*    type="button"*/}
                    {/*    className="button navigation__button__tiny"*/}
                    {/*    onClick={() => {*/}
                    {/*        // Trigger Google Analytics event*/}
                    {/*        gtag('event', 'clicked-button', {*/}
                    {/*            event_category: 'buy-whipcash',*/}
                    {/*            event_label: loggedInState.userName,*/}
                    {/*        });*/}
                    {/*    }}>*/}
                    {/*    Buy Whipcash*/}
                    {/*</button>*/}
                    {renderSocialCTAs()}
                    <li className="navigation__item">
                        <span className="navigation__highlight">W$: </span><span ref={pulseP}>{loggedInState.cash}</span>
                    </li>
                    <li className="navigation__item">
                        <Link to="/account">{loggedInState.userName}</Link>
                    </li>
                    <li>{renderLogoutButton()}</li>
                </>
            )
        }
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
                <div ref={navigation}>
                    <nav className="navigation">
                        <ul className="navigation__list">
                            <li><Link className="navigation__link" to="/about">About</Link></li>
                            <li>
                                <a className="navigation__link"
                                   href="https://streamelements.com/whiplash_gg/tip"
                                   target="_blank">
                                    Donate
                                </a>
                            </li>
                            <li><Link className="navigation__link" to="/leaderboard">Leaderboard</Link></li>
                        </ul>
                    </nav>
                    <nav className="navigation navigation--cta">
                        <ul className="navigation__list">
                            {currentNotification}
                            {renderNavCtaButtons()}
                        </ul>
                    </nav>
                </div>
                <div>
                    <button
                        ref={hamburger}
                        style={{minWidth: "2em", borderBottom: "none", padding: "0.25em"}}
                        type="button"
                        className="button"
                        aria-label="menu"
                        onClick={() => {setHamburgerOpen(!hamburgerOpen)}}>
                        <div className="hamburger"/>
                        <div className="hamburger"/>
                        <div className="hamburger"/>
                        </button>
                </div>
            </div>
            {hamburgerOpen &&
            <div  className="site-navigation">
                <ul className="navigation__list" style={{display: "flex", flexWrap: "wrap", alignItems: "center"}}>
                    <li><Link className="navigation__link" to="/about">About</Link></li>
                    <li>
                        <a className="navigation__link"
                           href="https://streamelements.com/whiplash_gg/tip"
                           target="_blank">
                            Donate
                        </a>
                    </li>
                    <li><Link className="navigation__link" to="/leaderboard">Leaderboard</Link></li>
                    {renderNavCtaButtons()}
                </ul>
            </div>
            }
            {renderLoginForm()}
            {renderSignupForm()}
        </header>
    );
}
