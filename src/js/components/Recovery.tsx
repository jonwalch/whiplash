import React, {useState, ChangeEvent, useContext} from "react";
import {LoginContext} from "../contexts/LoginContext";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {baseUrl} from "../config/const";
import queryString from "query-string";

export function Recovery(props: any) {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const [password, setPassword] = useState<string>("");
    const [repeatPassword, setRepeatPassword] = useState<string>("");

    const toggleValid = () => {
        //TODO: add validation
        return !(password && repeatPassword);
    };

    const submitSignUp = () => {
        if (password == repeatPassword){
            updatePassword()
        } else {
            alert("Passwords don't match!")
        }
    };

    const updatePassword = async () => {
        const response = await fetch(baseUrl + "user/password/recover", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify(
                {...queryString.parse(props.location.search), new_password: password}
            )
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully changed your password!");
            props.history.push("/")
        } else {
            alert(resp.message);
        }
    };

    const changePasswordOnKeyPress = (e: any) => {
        const key = e.key;
        if (key == "Enter" && !toggleValid()) {
            submitSignUp();
        }
    };

    function renderRecoveryMarkup() {
        if (!loggedInState.userName) {
            return (
                <form className="form form--account container" name="account">
                    <header className="form__header">
                        <h2 className="form__title">Reset your password</h2>
                    </header>
                    <fieldset className="form__fieldset">
                        <div className="form__group">
                            <label className="form__label" htmlFor="password">New Password</label>
                            <input
                                className="form__input"
                                value={password}
                                onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                    setPassword(e.currentTarget.value);
                                }}
                                onKeyPress={(e) => {changePasswordOnKeyPress(e)}}
                                type="password"
                                maxLength={100}
                                minLength= {8}
                                name="password"
                                id="password"
                            />
                        </div>
                        <div className="form__group">
                            <label className="form__label" htmlFor="repeatPassword">Confirm New Password</label>
                            <input
                                className="form__input"
                                value={repeatPassword}
                                onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                    setRepeatPassword(e.currentTarget.value);
                                }}
                                onKeyPress={(e) => {changePasswordOnKeyPress(e)}}
                                type="password"
                                maxLength={100}
                                minLength= {8}
                                name="repeatPassword"
                                id="repeatPassword"
                            />
                        </div>
                        <button
                            className="button form__button form__button__margin-top"
                            type="button"
                            onClick={submitSignUp}
                            disabled={toggleValid()}>
                            Reset Password
                        </button>
                    </fieldset>
                </form>
            );
        } else {
            return (
                <article>
                    <header className="article__header">
                        <div className="article__container">
                            <h2>Log out to access this page.</h2>
                        </div>
                    </header>
                </article>
            );
        }
    }

    return (
        <>
            <Header/>
            <main id="content" role="main" className="article">
                {renderRecoveryMarkup()}
            </main>
            <Footer/>
        </>
    );
}
