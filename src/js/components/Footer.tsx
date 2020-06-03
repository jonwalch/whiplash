import {Link} from "react-router-dom";
import React from "react";
import {baseUrl} from "../config/const";

export function Footer() {
    return(
        <footer role="contentinfo" className="site-footer">
            <section className="site-navigation container">
                <div className="site-branding">
                    <p className="site-branding__title">
                        <a href="/">
                            <img
                                src={baseUrl + "/img/logos/whiplash-horizontal-4c.svg"}
                                alt="Whiplash"
                                width="165"
                                height="36"
                                className="site-logo"
                            />
                        </a>
                    </p>
                </div>
                <nav className="navigation">
                    <ul className="navigation__list">
                        <li>
                            <Link className="navigation__link" to="/about">About</Link>
                        </li>
                        <li>
                            <a className="navigation__link"
                               href="https://streamelements.com/whiplash_gg/tip"
                               target="_blank">
                                Donate
                            </a>
                        </li>
                    </ul>
                </nav>
                {/*<nav className="navigation navigation--cta">*/}
                {/*  <ul className="navigation__list">*/}
                {/*    {renderNavCtaButtons()}*/}
                {/*  </ul>*/}
                {/*</nav>*/}
            </section>
            <hr className="site-footer__hr" />
            <section className="container site-footer__content">
                <p>&copy; Whiplash. All Rights Reserved.</p>
                <p><strong>Need help?</strong> Contact us at <a href="mailto:support@whiplashesports.com" target="_blank" rel="noreferrer">support@whiplashesports.com</a></p>
            </section>
        </footer>
    );
}
